package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;

import ae2.api.config.Actionable;
import ae2.api.storage.MEStorage;
import mekanism.api.gas.GasStack;
import me.ramidzkh.mekae2.ae2.AEGasKey;

import javax.annotation.Nullable;

/**
 * Mekanism gas and AE Supergiant generic storage bridge.
 */
public final class AEUpgradeGasBridge {

    private AEUpgradeGasBridge() {
    }

    public static boolean isAvailable() {
        return true;
    }

    @Nullable
    public static GasStack inject(AEUpgradeNode node, GasStack stack, Actionable action) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0 || !node.canUseNetwork()) {
            return stack;
        }
        MEStorage storage = node.getNetworkStorage();
        AEGasKey key = AEGasKey.of(stack);
        if (storage == null || key == null) {
            return stack;
        }
        long inserted = storage.insert(key, stack.amount, action, node.getActionSource());
        if (inserted >= stack.amount) {
            return null;
        }
        GasStack remainder = stack.copy();
        remainder.amount = (int) Math.max(0, stack.amount - inserted);
        return remainder;
    }

    @Nullable
    public static GasStack extract(AEUpgradeNode node, GasStack request, Actionable action) {
        if (request == null || request.getGas() == null || request.amount <= 0 || !node.canUseNetwork()) {
            return null;
        }
        MEStorage storage = node.getNetworkStorage();
        AEGasKey key = AEGasKey.of(request);
        if (storage == null || key == null) {
            return null;
        }
        long extracted = storage.extract(key, request.amount, action, node.getActionSource());
        return extracted <= 0 ? null : key.toStack(extracted);
    }
}
