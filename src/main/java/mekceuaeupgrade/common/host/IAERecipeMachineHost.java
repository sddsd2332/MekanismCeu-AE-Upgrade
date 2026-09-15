package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.adapter.AEProviderBackedRecipeAdapter;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public interface IAERecipeMachineHost extends IAEItemRecipeHost {

    IAERecipeMachineAdapter getAERecipeMachineAdapter();

    @Override
    default Object getAERecipeSourceKey() {
        return AEProviderBackedRecipeAdapter.getRecipeSourceKey(this, this::getAERecipeMachineAdapter);
    }

    @Override
    default List<AEExposedRecipe> getAEExposedItemRecipes() {
        return AEProviderBackedRecipeAdapter.getExposedItemRecipes(this, this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean canAcceptAEItemInput(AEExposedRecipe recipe, ItemStack stack) {
        return AEProviderBackedRecipeAdapter.canAcceptItemInput(this, recipe, stack,
              this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean acceptAEItemInput(AEExposedRecipe recipe, ItemStack stack) {
        return AEProviderBackedRecipeAdapter.acceptItemInput(this, recipe, stack,
              this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean canAcceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return AEProviderBackedRecipeAdapter.canAcceptItemInputs(this, recipe, stacks,
              this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean acceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return AEProviderBackedRecipeAdapter.acceptItemInputs(this, recipe, stacks,
              this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean canAcceptAnyAEItemInput() {
        return AEProviderBackedRecipeAdapter.canAcceptAnyItemInput(this, this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean canAttemptAEItemInput() {
        return AEProviderBackedRecipeAdapter.canAttemptItemInput(this, this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean tryAcceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return AEProviderBackedRecipeAdapter.tryAcceptItemInputs(this, recipe, stacks, this::getAERecipeMachineAdapter);
    }

    @Override
    default void observeAEInputContainers(Consumer<Object> observer) {
        AEProviderBackedRecipeAdapter.observeInputContainers(this, observer, this::getAERecipeMachineAdapter);
    }

    @Override
    default void observeAEOutputContainers(Consumer<Object> observer) {
        AEProviderBackedRecipeAdapter.observeOutputContainers(this, observer, this::getAERecipeMachineAdapter);
    }

    @Override
    default boolean drainAEItemOutputs(AEUpgradeNode node) {
        return getAERecipeMachineAdapter().drainItemOutputs(this, node);
    }
}
