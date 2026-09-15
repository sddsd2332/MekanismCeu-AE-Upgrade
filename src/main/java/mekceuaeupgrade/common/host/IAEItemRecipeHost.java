package mekceuaeupgrade.common.host;

import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
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

    /** Cheap machine-specific gate, read live even when the shared admission result is cached. */
    default boolean isAEAdmissionBlocked() {
        return false;
    }

    /** Unknown adapters retain their original admission contract. Provider hosts can use structural hints. */
    default boolean canAttemptAEItemInput() {
        return canAcceptAnyAEItemInput();
    }

    /** One dispatch operation. Legacy hosts keep both their validation and insertion callbacks. */
    default boolean tryAcceptAEItemInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        return canAcceptAEItemInputs(recipe, stacks) && acceptAEItemInputs(recipe, stacks);
    }

    /**
     * 枚举自动处理使用的输入槽位和储罐。
     *
     * @param observer 接收输入容器的观察器
     */
    default void observeAEInputContainers(Consumer<Object> observer) {
    }

    default void observeAEOutputContainers(Consumer<Object> observer) {
    }

    boolean drainAEItemOutputs(AEUpgradeNode node);
}
