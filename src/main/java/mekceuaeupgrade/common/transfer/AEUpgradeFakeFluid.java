package mekceuaeupgrade.common.transfer;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Bridges Forge fluids to AE Supergiant generic stack wrapper items for the existing machine adapter path.
 */
public final class AEUpgradeFakeFluid {

    private AEUpgradeFakeFluid() {
    }

    public static boolean isAvailable() {
        return true;
    }

    public static ItemStack packOutput(FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        return key == null ? ItemStack.EMPTY : GenericStack.wrapInItemStack(key, stack.amount);
    }

    @Nullable
    public static FluidStack unpackInput(ItemStack stack) {
        GenericStack genericStack = GenericStack.fromItemStack(stack);
        if (genericStack == null || !(genericStack.what() instanceof AEFluidKey fluidKey) || genericStack.amount() <= 0) {
            return null;
        }
        return fluidKey.toStack((int) Math.min(genericStack.amount(), Integer.MAX_VALUE));
    }

    public static boolean inputContains(ItemStack supplied, ItemStack expected) {
        FluidStack suppliedFluid = unpackInput(supplied);
        FluidStack expectedFluid = unpackInput(expected);
        return suppliedFluid != null && expectedFluid != null && suppliedFluid.isFluidEqual(expectedFluid) &&
              suppliedFluid.amount >= expectedFluid.amount;
    }

    public static boolean outputMatches(ItemStack expected, FluidStack actual) {
        FluidStack expectedFluid = unpackInput(expected);
        return expectedFluid != null && actual != null && actual.getFluid() != null && expectedFluid.isFluidEqual(actual) &&
              expectedFluid.amount == actual.amount;
    }

    public static boolean outputMatches(ItemStack expected, FluidStack actual, int multiplier) {
        if (actual == null || actual.getFluid() == null) {
            return false;
        }
        long amount = (long) actual.amount * Math.max(1, multiplier);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return false;
        }
        FluidStack scaled = actual.copy();
        scaled.amount = (int) amount;
        return outputMatches(expected, scaled);
    }
}
