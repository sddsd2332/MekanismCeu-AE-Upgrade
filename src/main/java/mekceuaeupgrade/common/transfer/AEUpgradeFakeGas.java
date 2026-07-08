package mekceuaeupgrade.common.transfer;

import ae2.api.stacks.GenericStack;
import mekanism.api.gas.GasStack;
import me.ramidzkh.mekae2.ae2.AEGasKey;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Bridges Mekanism gases to AE Supergiant generic stack wrapper items for the existing machine adapter path.
 */
public final class AEUpgradeFakeGas {

    private AEUpgradeFakeGas() {
    }

    public static boolean isAvailable() {
        return true;
    }

    public static ItemStack packOutput(GasStack stack) {
        AEGasKey key = AEGasKey.of(stack);
        return key == null ? ItemStack.EMPTY : GenericStack.wrapInItemStack(key, stack.amount);
    }

    @Nullable
    public static GasStack unpackInput(ItemStack stack) {
        GenericStack genericStack = GenericStack.fromItemStack(stack);
        if (genericStack == null || !(genericStack.what() instanceof AEGasKey gasKey) || genericStack.amount() <= 0) {
            return null;
        }
        return gasKey.toStack(genericStack.amount());
    }

    public static boolean inputContains(ItemStack supplied, ItemStack expected) {
        GasStack suppliedGas = unpackInput(supplied);
        GasStack expectedGas = unpackInput(expected);
        return suppliedGas != null && expectedGas != null && suppliedGas.isGasEqual(expectedGas) &&
              suppliedGas.amount >= expectedGas.amount;
    }

    public static boolean outputMatches(ItemStack expected, GasStack actual) {
        GasStack expectedGas = unpackInput(expected);
        return expectedGas != null && actual != null && actual.getGas() != null && expectedGas.isGasEqual(actual) &&
              expectedGas.amount == actual.amount;
    }

    public static boolean outputMatches(ItemStack expected, GasStack actual, int multiplier) {
        if (actual == null || actual.getGas() == null) {
            return false;
        }
        long amount = (long) actual.amount * Math.max(1, multiplier);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return false;
        }
        GasStack scaled = actual.copy();
        scaled.amount = (int) amount;
        return outputMatches(expected, scaled);
    }
}
