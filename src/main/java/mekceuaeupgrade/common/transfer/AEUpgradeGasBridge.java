package mekceuaeupgrade.common.transfer;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventory;
import appeng.me.GridAccessException;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import mekanism.api.gas.GasStack;
import mekceuaeupgrade.common.host.AEUpgradeNode;

import javax.annotation.Nullable;

/** Mekanism gas bridge for the required Mekanism Energistics storage channel. */
public final class AEUpgradeGasBridge {

    private static volatile boolean channelResolved;
    @Nullable
    private static volatile IGasStorageChannel gasStorageChannel;

    private AEUpgradeGasBridge() {
    }

    public static boolean isAvailable() {
        return getGasStorageChannel() != null;
    }

    @Nullable
    public static GasStack inject(AEUpgradeNode node, GasStack stack, Actionable action) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0 || !node.canUseNetwork()) {
            return stack;
        }
        IGasStorageChannel channel = getGasStorageChannel();
        if (channel == null) {
            return stack;
        }
        try {
            IAEGasStack toInsert = AEGasStack.of(stack);
            if (toInsert == null) {
                return stack;
            }
            IMEInventory<IAEGasStack> inventory = node.getInventory(channel);
            IAEGasStack remainder = inventory.injectItems(toInsert, action, node.getActionSource());
            return toGasStack(remainder);
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return stack;
        }
    }

    @Nullable
    public static GasStack extract(AEUpgradeNode node, GasStack request, Actionable action) {
        if (request == null || request.getGas() == null || request.amount <= 0 || !node.canUseNetwork()) {
            return null;
        }
        IGasStorageChannel channel = getGasStorageChannel();
        if (channel == null) {
            return null;
        }
        try {
            IAEGasStack toExtract = AEGasStack.of(request);
            if (toExtract == null) {
                return null;
            }
            IMEInventory<IAEGasStack> inventory = node.getInventory(channel);
            return toGasStack(inventory.extractItems(toExtract, action, node.getActionSource()));
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean hasAvailable(AEUpgradeNode node, GasStack request) {
        if (request == null || request.getGas() == null || request.amount <= 0) {
            return false;
        }
        try {
            IAEGasStack availabilityRequest = AEGasStack.of(request);
            return availabilityRequest != null && hasAvailable(node, availabilityRequest);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    public static Object createAvailabilityRequest(GasStack request) {
        if (request == null || request.getGas() == null || request.amount <= 0) {
            return null;
        }
        try {
            return getGasStorageChannel() == null ? null : AEGasStack.of(request);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean hasAvailable(AEUpgradeNode node, @Nullable Object availabilityRequest) {
        if (!(availabilityRequest instanceof IAEGasStack requested) || requested.getStackSize() <= 0 || !node.canUseNetwork()) {
            return false;
        }
        IGasStorageChannel channel = getGasStorageChannel();
        if (channel == null) {
            return false;
        }
        try {
            IMEInventory<IAEGasStack> inventory = node.getInventory(channel);
            IAEGasStack extracted = inventory.extractItems(requested.copy(), Actionable.SIMULATE, node.getActionSource());
            return extracted != null && extracted.getStackSize() >= requested.getStackSize();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    private static GasStack toGasStack(@Nullable IAEGasStack stack) {
        return stack == null || stack.getStackSize() <= 0 ? null : stack.getGasStack();
    }

    @Nullable
    private static IGasStorageChannel getGasStorageChannel() {
        if (channelResolved) {
            return gasStorageChannel;
        }
        synchronized (AEUpgradeGasBridge.class) {
            if (!channelResolved) {
                try {
                    gasStorageChannel = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
                } catch (RuntimeException | LinkageError ignored) {
                    gasStorageChannel = null;
                }
                channelResolved = true;
            }
            return gasStorageChannel;
        }
    }
}
