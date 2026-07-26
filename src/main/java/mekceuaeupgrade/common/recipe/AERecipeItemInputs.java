package mekceuaeupgrade.common.recipe;

import mekanism.common.recipe.processing.MachineRecipeItemInputs;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/** Compatibility facade for the shared Mekanism recipe-input expansion rules. */
public final class AERecipeItemInputs {

    private AERecipeItemInputs() {
    }

    public static List<ItemStack> expand(ItemStack input, Predicate<ItemStack> accepts) {
        return MachineRecipeItemInputs.expand(input, accepts);
    }

    public static List<List<ItemStack>> expandCombinations(List<ItemStack> inputs, Predicate<List<ItemStack>> accepts) {
        return MachineRecipeItemInputs.expandCombinations(inputs, accepts);
    }

    public static boolean isConcreteInput(ItemStack stack) {
        return MachineRecipeItemInputs.isConcreteInput(stack);
    }
}
