package mekceuaeupgrade.common.recipe;

import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AEPatternInput {

    private final List<ItemStack> stacks;

    public AEPatternInput(@Nonnull ItemStack stack) {
        this(Collections.singletonList(stack));
    }

    public AEPatternInput(@Nonnull List<ItemStack> stacks) {
        List<ItemStack> copied = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copied.add(stack.copy());
        }
        this.stacks = Collections.unmodifiableList(copied);
    }

    @Nonnull
    public ItemStack getStack() {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0).copy();
    }

    @Nonnull
    public List<ItemStack> getStacks() {
        List<ItemStack> copied = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copied.add(stack.copy());
        }
        return copied;
    }
}
