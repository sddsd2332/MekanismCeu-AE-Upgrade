package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEFactoryRecipeHost;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * AE 自动处理升级的配方驱动输入控制器。
 *
 * <p>控制器从已启用的暴露配方中选一条，按 route 类型从 AE 网络抽取物品、气体或流体，
 * 再复用机器 adapter 的旧物品输入入口把内容写入机器。这样 AE 合成和自动处理共享同一套
 * 配方匹配、端口写入和机器输出回收逻辑。</p>
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
        for (AEExposedRecipe recipe : recipes) {
            if (tryProcessRecipe(node, host, recipe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对单条配方执行“模拟抽取、模拟写入、真实抽取、真实写入”的原子流程。
     */
    private static boolean tryProcessRecipe(AEUpgradeNode node, IAEItemRecipeHost host, AEExposedRecipe recipe) {
        PreparedRecipe prepared = prepareRecipe(node, host, recipe);
        if (prepared == null && host instanceof IAEFactoryRecipeHost && recipe.getCraftAmount() > 1) {
            prepared = findLargestFactoryBatch(node, host, recipe);
        }
        if (prepared == null) {
            return false;
        }
        AEExposedRecipe acceptedRecipe = prepared.recipe;
        AERecipeNetworkTransferPlan inputPlan = prepared.inputPlan;
        List<ItemStack> legacyInputs = prepared.legacyInputs;
        if (!inputPlan.extract(node)) {
            return false;
        }
        if (!host.acceptAEItemInputs(acceptedRecipe, legacyInputs)) {
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

    private static PreparedRecipe prepareRecipe(AEUpgradeNode node, IAEItemRecipeHost host, AEExposedRecipe recipe) {
        AERecipeNetworkTransferPlan inputPlan = recipe.getAutoProcessingPlan();
        if (inputPlan == null || inputPlan.isEmpty()) {
            return null;
        }
        if (!inputPlan.canExtract(node)) {
            return null;
        }
        List<ItemStack> legacyInputs = inputPlan.getLegacyInputs();
        if (!host.canAcceptAEItemInputs(recipe, legacyInputs)) {
            return null;
        }
        if (!inputPlan.canAcceptOutputs(node)) {
            return null;
        }
        return new PreparedRecipe(recipe, inputPlan, legacyInputs);
    }

    private static PreparedRecipe findLargestFactoryBatch(AEUpgradeNode node, IAEItemRecipeHost host, AEExposedRecipe recipe) {
        PreparedRecipe best = prepareRecipe(node, host, recipe.withCraftAmount(1));
        if (best == null) {
            return null;
        }
        int low = 2;
        int high = recipe.getCraftAmount() - 1;
        while (low <= high) {
            int amount = low + (int) (((long) high - low) / 2);
            PreparedRecipe candidate = prepareRecipe(node, host, recipe.withCraftAmount(amount));
            if (candidate == null) {
                high = amount - 1;
            } else {
                best = candidate;
                low = amount + 1;
            }
        }
        return best;
    }

    private static final class PreparedRecipe {

        private final AEExposedRecipe recipe;
        private final AERecipeNetworkTransferPlan inputPlan;
        private final List<ItemStack> legacyInputs;

        private PreparedRecipe(AEExposedRecipe recipe, AERecipeNetworkTransferPlan inputPlan, List<ItemStack> legacyInputs) {
            this.recipe = recipe;
            this.inputPlan = inputPlan;
            this.legacyInputs = legacyInputs;
        }
    }
}
