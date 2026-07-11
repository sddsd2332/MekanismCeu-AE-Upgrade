package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEFactoryRecipeHost;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import net.minecraft.item.ItemStack;

import java.util.List;

public final class AEAutoProcessingController {

    private AEAutoProcessingController() {
    }

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
        if (!inputPlan.canAcceptOutputs(node)) {
            inputPlan.rollback(node);
            AEUpgradeDebug.log(host, "auto processing rolled back because AE cannot accept outputs={}",
                  AEUpgradeDebug.outputStacks(acceptedRecipe));
            return false;
        }
        boolean accepted = node.callMachineContainerTransaction(() ->
              host.canAcceptAEItemInputs(acceptedRecipe, legacyInputs) && host.acceptAEItemInputs(acceptedRecipe, legacyInputs));
        if (!accepted) {
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
        if (inputPlan == null || inputPlan.isEmpty() || !inputPlan.canExtract(node)) {
            return null;
        }
        List<ItemStack> legacyInputs = inputPlan.getLegacyInputs();
        if (!node.callMachineContainerTransaction(() -> host.canAcceptAEItemInputs(recipe, legacyInputs))) {
            return null;
        }
        if (!recipe.hasSelfReferentialOutput() && !inputPlan.canAcceptOutputs(node)) {
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
