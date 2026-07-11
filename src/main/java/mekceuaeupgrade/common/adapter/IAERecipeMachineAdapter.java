package mekceuaeupgrade.common.adapter;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public interface IAERecipeMachineAdapter {

    default Object getRecipeSourceKey(IAEItemRecipeHost host) {
        return null;
    }

    default List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
        return Collections.emptyList();
    }

    default boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
        return false;
    }

    default boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
        return false;
    }

    default boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
        return stacks.size() == 1 && canAcceptItemInput(host, recipe, stacks.get(0));
    }

    default boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
        return stacks.size() == 1 && acceptItemInput(host, recipe, stacks.get(0));
    }

    default boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
        return false;
    }

    default void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
    }

    default boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
        return false;
    }
}
