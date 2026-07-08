package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import net.minecraft.item.ItemStack;

import java.util.List;

public interface IAERecipeMachineHost extends IAEItemRecipeHost {

    IAERecipeMachineAdapter getAERecipeMachineAdapter();

    @Override
    default Object getAERecipeSourceKey() {
        return getAERecipeMachineAdapter().getRecipeSourceKey(this);
    }

    @Override
    default List<AEExposedRecipe> getAEExposedItemRecipes() {
        return getAERecipeMachineAdapter().getExposedItemRecipes(this);
    }

    @Override
    default boolean canAcceptAEItemInput(AEExposedRecipe recipe, ItemStack stack) {
        return getAERecipeMachineAdapter().canAcceptItemInput(this, recipe, stack);
    }

    @Override
    default boolean acceptAEItemInput(AEExposedRecipe recipe, ItemStack stack) {
        return getAERecipeMachineAdapter().acceptItemInput(this, recipe, stack);
    }

    @Override
    default boolean canAcceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return getAERecipeMachineAdapter().canAcceptItemInputs(this, recipe, stacks);
    }

    @Override
    default boolean acceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return getAERecipeMachineAdapter().acceptItemInputs(this, recipe, stacks);
    }

    @Override
    default boolean canAcceptAnyAEItemInput() {
        return getAERecipeMachineAdapter().canAcceptAnyItemInput(this);
    }

    @Override
    default boolean drainAEItemOutputs(AEUpgradeNode node) {
        return getAERecipeMachineAdapter().drainItemOutputs(this, node);
    }
}
