package mekceuaeupgrade.common.recipe;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;

public final class AERecipeStacks {

    private AERecipeStacks() {
    }

    public static long getAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        GenericStack wrapped = GenericStack.unwrapItemStack(stack);
        return wrapped == null ? stack.getCount() : wrapped.amount();
    }

    public static ItemStack scale(ItemStack stack, int multiplier) {
        if (stack == null || stack.isEmpty() || multiplier <= 0) {
            return ItemStack.EMPTY;
        }
        GenericStack wrapped = GenericStack.unwrapItemStack(stack);
        long amount = wrapped == null ? stack.getCount() : wrapped.amount();
        if (amount <= 0 || amount > Long.MAX_VALUE / multiplier) {
            return ItemStack.EMPTY;
        }
        long count = amount * multiplier;
        if (count <= 0 || count > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        if (wrapped != null) {
            return GenericStack.wrapInItemStack(wrapped.what(), count);
        }
        ItemStack scaled = stack.copy();
        scaled.setCount((int) count);
        return scaled;
    }
}
