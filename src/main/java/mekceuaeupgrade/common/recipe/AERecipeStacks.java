package mekceuaeupgrade.common.recipe;

import net.minecraft.item.ItemStack;

public final class AERecipeStacks {

    private AERecipeStacks() {
    }

    public static ItemStack scale(ItemStack stack, int multiplier) {
        if (stack == null || stack.isEmpty() || multiplier <= 0) {
            return ItemStack.EMPTY;
        }
        long count = (long) stack.getCount() * multiplier;
        if (count <= 0 || count > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        ItemStack scaled = stack.copy();
        scaled.setCount((int) count);
        return scaled;
    }
}
