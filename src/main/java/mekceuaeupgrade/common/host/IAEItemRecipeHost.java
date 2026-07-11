package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;

import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface IAEItemRecipeHost extends IAEUpgradeHost {

    default Map<ItemStackInput, ? extends BasicMachineRecipe<?>> getAEItemRecipes() {
        return null;
    }

    default List<AEExposedRecipe> getAEExposedItemRecipes() {
        Map<ItemStackInput, ? extends BasicMachineRecipe<?>> recipeMap = getAEItemRecipes();
        return recipeMap == null ? Collections.emptyList() : AEUpgradeRecipeCache.collectBasicItemRecipes(recipeMap);
    }

    default Object getAERecipeSourceKey() {
        return getAEItemRecipes();
    }

    boolean canAcceptAEItemInput(AEExposedRecipe recipe, ItemStack stack);

    boolean acceptAEItemInput(AEExposedRecipe recipe, ItemStack stack);

    default boolean canAcceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return stacks.size() == 1 && canAcceptAEItemInput(recipe, stacks.get(0));
    }

    default boolean acceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return stacks.size() == 1 && acceptAEItemInput(recipe, stacks.get(0));
    }

    boolean canAcceptAnyAEItemInput();

    default void observeAEInputContainers(Consumer<Object> observer) {
    }

    boolean drainAEItemOutputs(AEUpgradeNode node);
}
