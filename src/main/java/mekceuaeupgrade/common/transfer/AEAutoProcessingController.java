package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.adapter.AEProviderBackedRecipeAdapter;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekanism.common.tile.factory.TileEntityFactory;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.util.AEUpgradeDebug;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * AE 自动处理升级的配方驱动输入控制器。
 *
 * <p>控制器从已启用的暴露配方中选一条，按 route 类型从 AE 网络抽取物品、气体或流体，
 * 默认 provider 机器仅补齐输入缺口；合成投料保留完整订单输入契约。
 * 实际产物由共享输出模块独立回推，不在这里预留机器或 AE 的预计输出空间。</p>
 */
public final class AEAutoProcessingController {

    /**
     * 工具类不允许实例化。
     */
    private AEAutoProcessingController() {
    }

    /**
     * 尝试执行一次自动处理输入。
     *
     * @param node 当前机器的 AE 网络节点
     * @param host 当前机器的配方 host
     * @param recipes 已按配置过滤和排序后的暴露配方
     * @return 成功向机器写入一次配方输入时返回 true
     */
    public static boolean process(AEUpgradeNode node, IAEItemRecipeHost host, List<AEExposedRecipe> recipes) {
        if (node == null || host == null || recipes == null || recipes.isEmpty() || !node.canUseNetwork()) {
            return false;
        }
        return process(node, host, new AEAutoProcessingProgram(recipes));
    }

    public static boolean process(AEUpgradeNode node, IAEItemRecipeHost host, AEAutoProcessingProgram program) {
        if (node == null || host == null || program == null || !node.canUseNetwork() || !program.enter()) return false;
        try {
            AEProviderBackedRecipeAdapter.ContextCache contexts = node.getProviderContextCache();
            if (contexts != null) contexts.setAutoProcessingRecipes(program.recipes());
            for (int i = 0; i < program.size(); i++) {
                // A failed rollback must be recovered before another recipe can own resources.
                if (node.getPendingTransferBuffer().hasOwnedResources()) return false;
                if (tryProcessRecipe(node, host, program.entry(i))) return true;
            }
            return false;
        } finally {
            program.exit();
        }
    }

    /**
     * 对单条配方执行“模拟抽取、模拟写入、真实抽取、真实写入”的原子流程。
     */
    private static boolean tryProcessRecipe(AEUpgradeNode node, IAEItemRecipeHost host, AEAutoProcessingProgram.Entry entry) {
        AEProviderBackedRecipeAdapter.AutoProcessingSearch search = host instanceof IAERecipeMachineHost providerHost ?
              node.callMachineContainerTransaction(() ->
                    AEProviderBackedRecipeAdapter.beginAutoProcessingSearch(providerHost, entry.recipe)) : null;
        PreparedRecipe prepared = (host instanceof TileEntityFactory
              || search != null || AEProviderBackedRecipeAdapter.supportsAutoProcessingRefill(host)) &&
              entry.recipe.getCraftAmount() > 1 ?
              findLargestFactoryBatch(node, host, entry, search) : prepareRecipe(node, host, entry, search);
        if (prepared == null) {
            return false;
        }
        if (prepared.candidate != null) {
            AEProviderBackedRecipeAdapter.AutoProcessingCandidate candidate = prepared.candidate;
            AEProviderBackedRecipeAdapter.AutoProcessingInputs refill =
                  node.callMachineContainerTransaction(candidate::capture);
            if (refill == null) return false;
            // The search's stock cache is never an extraction authority. Rebuild requests
            // from the fresh quote and query AE again before taking any network resource.
            AERecipeNetworkTransferPlan actual = AERecipeNetworkTransferPlan.fromInputs(refill.missingInputs());
            if (actual == null || !actual.canExtract(node)) return false;
            prepared = new PreparedRecipe(prepared.recipe, actual, actual.getLegacyInputs(), refill, null);
        }
        PreparedRecipe accepted = prepared;
        AEExposedRecipe acceptedRecipe = prepared.recipe;
        AERecipeNetworkTransferPlan inputPlan = prepared.inputPlan;
        if (inputPlan.isEmpty()) return false; // Existing stock already covers this target.
        List<ItemStack> legacyInputs = prepared.legacyInputs;
        if (!inputPlan.extract(node)) {
            return false;
        }
        inputPlan.beginMachineDelivery();
        boolean delivered;
        try {
            delivered = node.callMachineContainerTransaction(() -> accepted.refill != null
                  ? accepted.refill.execute() : host.tryAcceptAEItemInputs(acceptedRecipe, legacyInputs));
        } catch (RuntimeException | LinkageError e) {
            inputPlan.rollback(node);
            throw e;
        }
        if (!delivered) {
            inputPlan.rollback(node);
            AEUpgradeDebug.log(host, "auto processing rolled back inputs={} outputs={}",
                  AEUpgradeDebug.inputStacks(acceptedRecipe), AEUpgradeDebug.outputStacks(acceptedRecipe));
            return false;
        }
        inputPlan.commit();
        AEUpgradeDebug.log(host, "auto processing accepted inputs={} outputs={}",
              AEUpgradeDebug.inputStacks(acceptedRecipe), AEUpgradeDebug.outputStacks(acceptedRecipe));
        return true;
    }

    private static PreparedRecipe prepareRecipe(AEUpgradeNode node, IAEItemRecipeHost host,
          AEAutoProcessingProgram.Entry entry, AEProviderBackedRecipeAdapter.AutoProcessingSearch search) {
        AEExposedRecipe recipe = entry.recipe;
        if (search != null) {
            AEProviderBackedRecipeAdapter.AutoProcessingCandidate candidate =
                  node.callMachineContainerTransaction(() -> search.prepare(recipe.getCraftAmount()));
            if (candidate == null) return null;
            AERecipeNetworkTransferPlan plan = AERecipeNetworkTransferPlan.fromInputs(candidate.missingInputs());
            if (plan == null || !plan.canExtract(node)) return null;
            return new PreparedRecipe(recipe, plan, java.util.Collections.emptyList(), null, candidate);
        }
        if (AEProviderBackedRecipeAdapter.supportsAutoProcessingRefill(host)) {
            AEProviderBackedRecipeAdapter.AutoProcessingInputs refill = node.callMachineContainerTransaction(() ->
                  AEProviderBackedRecipeAdapter.prepareAutoProcessingInputs((IAERecipeMachineHost) host, recipe));
            if (refill == null) return null;
            AERecipeNetworkTransferPlan plan = AERecipeNetworkTransferPlan.fromInputs(refill.missingInputs());
            if (plan == null || !plan.canExtract(node)) return null;
            return new PreparedRecipe(recipe, plan, plan.getLegacyInputs(), refill, null);
        }
        AERecipeNetworkTransferPlan inputPlan = entry.plan;
        if (inputPlan == null || inputPlan.isEmpty()) {
            return null;
        }
        if (!inputPlan.canExtract(node)) {
            return null;
        }
        List<ItemStack> legacyInputs = inputPlan.getLegacyInputs();
        if (!node.callMachineContainerTransaction(() -> host.canAcceptAEItemInputs(recipe, legacyInputs))) {
            return null;
        }
        return new PreparedRecipe(recipe, inputPlan, legacyInputs, null, null);
    }

    private static PreparedRecipe findLargestFactoryBatch(AEUpgradeNode node, IAEItemRecipeHost host,
          AEAutoProcessingProgram.Entry entry, AEProviderBackedRecipeAdapter.AutoProcessingSearch search) {
        AEExposedRecipe recipe = entry.recipe;
        // Local capacity reads hold the machine transaction; AE simulations stay outside it.
        int hint = search != null ? node.callMachineContainerTransaction(search::suggestBatch) :
              host instanceof IAERecipeMachineHost providerHost ?
              node.callMachineContainerTransaction(() ->
                    AEProviderBackedRecipeAdapter.suggestAutoProcessingBatch(providerHost, recipe)) : recipe.getCraftAmount();
        int multiple = Math.max(1, node.getAutoProcessingBatchMultiple());
        if (multiple > 1 && recipe.getCraftAmount() >= multiple) {
            PreparedRecipe balanced = AEFactoryBatchSearch.find(recipe.getCraftAmount() / multiple,
                  Math.max(1, hint / multiple), rounds -> prepareRecipe(node, host, entry.batch(rounds * multiple), search));
            if (balanced != null) return balanced;
        }
        return AEFactoryBatchSearch.find(recipe.getCraftAmount(), hint,
              amount -> prepareRecipe(node, host, entry.batch(amount), search));
    }

    private static final class PreparedRecipe {

        private final AEExposedRecipe recipe;
        private final AERecipeNetworkTransferPlan inputPlan;
        private final List<ItemStack> legacyInputs;
        private final AEProviderBackedRecipeAdapter.AutoProcessingInputs refill;
        private final AEProviderBackedRecipeAdapter.AutoProcessingCandidate candidate;

        private PreparedRecipe(AEExposedRecipe recipe, AERecipeNetworkTransferPlan inputPlan, List<ItemStack> legacyInputs,
                               AEProviderBackedRecipeAdapter.AutoProcessingInputs refill,
                               AEProviderBackedRecipeAdapter.AutoProcessingCandidate candidate) {
            this.recipe = recipe;
            this.inputPlan = inputPlan;
            this.legacyInputs = legacyInputs;
            this.refill = refill;
            this.candidate = candidate;
        }
    }
}
