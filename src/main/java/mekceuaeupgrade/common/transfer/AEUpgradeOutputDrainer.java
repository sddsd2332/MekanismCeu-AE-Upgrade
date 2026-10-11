package mekceuaeupgrade.common.transfer;

import appeng.api.config.Actionable;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.MachineTransferPlan;
import mekanism.api.processing.NativeOutputAccess;
import mekanism.api.processing.NativeOutputStack;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 将机器内部输出端口回收到 AE 网络的通用工具。
 */
public final class AEUpgradeOutputDrainer {

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeOutputDrainer() {
    }

    /**
     * Drains every non-configuration Provider output port through an atomic machine extraction plan.
     */
    public static boolean drainProviderPorts(AEUpgradeNode node, TileEntity tile, List<MachinePort> ports) {
        if (node == null || tile == null || ports == null || ports.isEmpty()) {
            return false;
        }
        boolean drained = false;
        for (MachinePort port : ports) {
            if (port == null || port.isConfiguration() || !port.role().allowsOutput()) {
                continue;
            }
            Object container = port.container();
            observeContainerMembers(node, container);
            NativeOutputAccess nativeOutput = port.nativeOutput();
            if (nativeOutput != null) {
                drained |= drainNativePort(node, port, nativeOutput);
                if (node.getPendingTransferBuffer().hasOwnedResources()) return drained;
                continue;
            }
            List<MachineResourceStack> stored = new ArrayList<>(port.peekAll());
            for (MachineResourceStack resource : stored) {
                if (node.getPendingTransferBuffer().hasOwnedResources()) {
                    node.markOutputBlocked();
                    return drained;
                }
                drained |= drainProviderResource(node, tile, port, resource);
            }
            if (hasRemainingOutput(port)) {
                node.markOutputBlocked();
            }
        }
        return drained;
    }

    private static boolean drainNativePort(AEUpgradeNode node, MachinePort port, NativeOutputAccess access) {
        AEPendingTransferBuffer buffer = node.getPendingTransferBuffer();
        if (buffer.hasOwnedResources()) { node.markOutputBlocked(); return false; }
        if (!node.canUseNetwork()) { node.markOutputBlocked(); return false; }
        List<NativeOutputStack> candidates = node.callMachineContainerTransaction(access::peekAll);
        boolean drained = false;
        for (NativeOutputStack candidate : candidates) {
            if (buffer.hasOwnedResources()) { node.markOutputBlocked(); break; }
            int accepted;
            switch (candidate.kind()) {
                case ITEM: {
                    ItemStack offered = candidate.itemStack();
                    accepted = acceptedItemAmount(offered, node.injectItem(offered.copy(), Actionable.SIMULATE));
                    break;
                }
                case GAS: {
                    GasStack offered = candidate.gasStack();
                    accepted = acceptedGasAmount(offered, node.injectGas(offered.copy(), Actionable.SIMULATE));
                    break;
                }
                case FLUID: {
                    FluidStack offered = candidate.fluidStack();
                    accepted = acceptedFluidAmount(offered, node.injectFluid(offered.copy(), Actionable.SIMULATE));
                    break;
                }
                default: continue;
            }
            if (accepted <= 0) { node.markOutputBlocked(); continue; }
            NativeOutputStack expected = candidate.withAmount(accepted);
            AEPendingTransferBuffer.Entry pending = prepareNative(buffer, expected);
            boolean extracted;
            try {
                extracted = node.callMachineContainerTransaction(() -> {
                    if (isExtractionGuarded(node, port.container())) return false;
                    NativeOutputStack actual = access.extract(expected, container -> !isExtractionGuarded(node, container));
                    if (actual == null) return false;
                    holdNative(buffer, pending, actual);
                    return true;
                });
            } catch (RuntimeException | LinkageError failure) {
                // A failed restore/notification can make source ownership ambiguous. Preserve, never replay blindly.
                if (pendingIsPrepared(buffer, pending)) holdNative(buffer, pending, expected);
                buffer.markUncertain(pending);
                node.markOutputBlocked();
                return drained;
            }
            if (!extracted) {
                buffer.cancelPrepared(pending);
                node.markOutputBlocked();
                continue;
            }
            drained = true;
            // Cross-network calls occur only after releasing the local machine lock.
            buffer.deliverToNetwork(pending, node);
            if (buffer.contains(pending)) { node.markOutputBlocked(); break; }
        }
        // Output containers are observed before this method: a supported listener schedules an immediate
        // follow-up drain, while unobservable containers use the existing bounded polling retry. Re-reading
        // every slot/tank under the machine lock here only duplicates the earlier snapshot and can block
        // the server thread behind an active machine transaction.
        return drained;
    }

    private static boolean pendingIsPrepared(AEPendingTransferBuffer buffer, AEPendingTransferBuffer.Entry entry) {
        return buffer.isPrepared(entry);
    }

    private static AEPendingTransferBuffer.Entry prepareNative(AEPendingTransferBuffer buffer, NativeOutputStack stack) {
        switch (stack.kind()) {
            case ITEM: return buffer.prepareItem(stack.itemStack());
            case GAS: return buffer.prepareGas(stack.gasStack());
            case FLUID: return buffer.prepareFluid(stack.fluidStack());
            default: throw new IllegalArgumentException("Unsupported native output");
        }
    }

    private static void holdNative(AEPendingTransferBuffer buffer, AEPendingTransferBuffer.Entry pending, NativeOutputStack stack) {
        switch (stack.kind()) {
            case ITEM: buffer.holdItem(pending, stack.itemStack()); break;
            case GAS: buffer.holdGas(pending, stack.gasStack()); break;
            case FLUID: buffer.holdFluid(pending, stack.fluidStack()); break;
            default: throw new IllegalArgumentException("Unsupported native output");
        }
    }

    private static final ClassValue<Boolean> BUILTIN_ITEM_PORT = new ClassValue<Boolean>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return type.getClassLoader() == MachinePort.class.getClassLoader() &&
                  (type.getName().equals("mekanism.api.processing.MachinePort$ItemPort") ||
                   type.getName().equals("mekanism.api.processing.MachinePort$ItemGroupPort"));
        }
    };

    private static boolean hasRemainingOutput(MachinePort port) {
        // Only the core's exact final item-port implementations define peekAll as
        // the nonempty backing slots. Avoid encoding every residual stack merely
        // to ask whether a retry is needed. Custom/codec ports keep their own semantics.
        if (BUILTIN_ITEM_PORT.get(port.getClass())) {
            Object container = port.container();
            if (container instanceof IInventorySlot slot) return !slot.getStack().isEmpty();
            if (container instanceof List<?> slots) {
                for (Object member : slots) {
                    if (!(member instanceof IInventorySlot slot)) return !port.peekAll().isEmpty();
                    if (!slot.getStack().isEmpty()) return true;
                }
                return false;
            }
        }
        return !port.peekAll().isEmpty();
    }

    private static void observeContainerMembers(AEUpgradeNode node, Object container) {
        if (container instanceof List<?> members) {
            for (Object member : members) {
                observeContainerMembers(node, member);
            }
        } else {
            node.observeOutputContainer(container);
        }
    }

    private static boolean drainProviderResource(AEUpgradeNode node, TileEntity tile, MachinePort port,
          MachineResourceStack stored) {
        if (stored == null || stored.amount() <= 0 || port.kind() != stored.kind()) {
            return false;
        }
        Object container = port.container();
        if (isExtractionGuarded(node, container)) {
            return false;
        }
        if (!node.canUseNetwork()) {
            node.markOutputBlocked();
            return false;
        }
        long accepted = simulateAccepted(node, stored);
        if (accepted <= 0) {
            node.markOutputBlocked();
            return false;
        }
        if (isExtractionGuarded(node, container)) {
            return false;
        }
        MachineResourceStack expected = stored.withAmount(accepted);
        AEPendingTransferBuffer buffer = node.getPendingTransferBuffer();
        AEPendingTransferBuffer.Entry pending = prepare(buffer, expected);
        if (pending == null) {
            node.markOutputBlocked();
            return false;
        }
        MachineTransferPlan extraction = MachineTransferPlan.create(tile).addExtract(port, expected);
        boolean extracted;
        try {
            extracted = extraction.execute();
        } catch (RuntimeException | Error failure) {
            buffer.cancelPrepared(pending);
            throw failure;
        }
        if (!extracted) {
            buffer.cancelPrepared(pending);
            node.markOutputBlocked();
            return false;
        }
        MachineResourceStack owned = singleExtracted(extraction.getExtracted());
        if (owned == null) {
            buffer.cancelPrepared(pending);
            node.markOutputBlocked();
            return false;
        }
        boolean extractionConforms = owned.kind() == expected.kind() && owned.amount() == expected.amount() &&
              owned.sameResource(expected);
        if (owned.kind() != expected.kind()) {
            buffer.cancelPrepared(pending);
            pending = prepare(buffer, owned);
            if (pending == null) {
                node.markOutputBlocked();
                return false;
            }
        }
        hold(buffer, pending, owned);
        if (!extractionConforms) {
            node.markOutputBlocked();
        }
        buffer.deliverToNetwork(pending, node);
        if (buffer.contains(pending)) {
            node.markOutputBlocked();
        }
        return true;
    }

    private static long simulateAccepted(AEUpgradeNode node, MachineResourceStack stored) {
        if (stored.amount() > Integer.MAX_VALUE) {
            return 0;
        }
        switch (stored.kind()) {
            case ITEM: {
                ItemStack sent = stored.itemStack();
                if (sent == null || sent.isEmpty()) {
                    return 0;
                }
                ItemStack remainder = node.injectItem(sent.copy(), Actionable.SIMULATE);
                return acceptedItemAmount(sent, remainder);
            }
            case GAS: {
                GasStack sent = stored.gasStack();
                if (sent == null || sent.getGas() == null || sent.amount <= 0) {
                    return 0;
                }
                GasStack remainder = node.injectGas(sent.copy(), Actionable.SIMULATE);
                return acceptedGasAmount(sent, remainder);
            }
            case FLUID: {
                FluidStack sent = stored.fluidStack();
                if (sent == null || sent.getFluid() == null || sent.amount <= 0) {
                    return 0;
                }
                FluidStack remainder = node.injectFluid(sent.copy(), Actionable.SIMULATE);
                return acceptedFluidAmount(sent, remainder);
            }
            default:
                return 0;
        }
    }

    private static int acceptedItemAmount(@Nullable ItemStack sent, @Nullable ItemStack remainder) {
        if (sent == null || sent.isEmpty() || remainder == null || remainder.getCount() < 0) {
            return 0;
        }
        if (remainder.isEmpty()) {
            return sent.getCount();
        }
        if (remainder.getCount() > sent.getCount() || !ItemHandlerHelper.canItemStacksStack(sent, remainder)) {
            return 0;
        }
        return sent.getCount() - remainder.getCount();
    }

    private static int acceptedGasAmount(@Nullable GasStack sent, @Nullable GasStack remainder) {
        if (sent == null || sent.getGas() == null || sent.amount <= 0) {
            return 0;
        }
        if (remainder == null || remainder.amount == 0) {
            return sent.amount;
        }
        if (remainder.amount < 0 || remainder.getGas() == null || remainder.amount > sent.amount ||
            !sent.isGasEqual(remainder)) {
            return 0;
        }
        return sent.amount - remainder.amount;
    }

    private static int acceptedFluidAmount(@Nullable FluidStack sent, @Nullable FluidStack remainder) {
        if (sent == null || sent.getFluid() == null || sent.amount <= 0) {
            return 0;
        }
        if (remainder == null || remainder.amount == 0) {
            return sent.amount;
        }
        if (remainder.amount < 0 || remainder.getFluid() == null || remainder.amount > sent.amount ||
            !sent.isFluidEqual(remainder)) {
            return 0;
        }
        return sent.amount - remainder.amount;
    }

    private static AEPendingTransferBuffer.Entry prepare(AEPendingTransferBuffer buffer,
          MachineResourceStack expected) {
        switch (expected.kind()) {
            case ITEM:
                return buffer.prepareItem(expected.itemStack());
            case GAS:
                return buffer.prepareGas(expected.gasStack());
            case FLUID:
                return buffer.prepareFluid(expected.fluidStack());
            default:
                return null;
        }
    }

    private static void hold(AEPendingTransferBuffer buffer, AEPendingTransferBuffer.Entry pending,
          MachineResourceStack extracted) {
        switch (extracted.kind()) {
            case ITEM:
                buffer.holdItem(pending, extracted.itemStack());
                break;
            case GAS:
                buffer.holdGas(pending, extracted.gasStack());
                break;
            case FLUID:
                buffer.holdFluid(pending, extracted.fluidStack());
                break;
        }
    }

    private static MachineResourceStack singleExtracted(List<MachineResourceStack> extracted) {
        if (extracted == null || extracted.size() != 1) {
            return null;
        }
        MachineResourceStack actual = extracted.get(0);
        return actual != null && actual.amount() > 0 && actual.amount() <= Integer.MAX_VALUE ? actual : null;
    }

    /**
     * 从物品输出槽回收到 AE 物品网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param slot 机器物品输出槽
     * @return 本次是否实际回收了物品
     */
    public static boolean drainItemSlot(AEUpgradeNode node, IInventorySlot slot) {
        return drainItemSlot(node, slot, AutomationType.INTERNAL);
    }

    /**
     * 从物品输出槽回收到 AE 物品网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param slot 机器物品输出槽
     * @param automationType 抽取该槽位时使用的自动化来源
     * @return 本次是否实际回收了物品
     */
    public static boolean drainItemSlot(AEUpgradeNode node, IInventorySlot slot, AutomationType automationType) {
        if (node == null || slot == null || automationType == null) return false;
        node.observeOutputContainer(slot);
        MachinePort port = MachinePort.item("ae_native_item_output", MachinePort.Role.OUTPUT, slot, automationType);
        return drainNativePort(node, port, port.nativeOutput());
    }

    /**
     * 从气体输出储罐回收到 AE 气体网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param tank 机器气体输出储罐
     * @return 本次是否实际回收了气体
     */
    public static boolean drainGasTank(AEUpgradeNode node, IExtendedGasTank tank) {
        if (node == null || tank == null) return false;
        node.observeOutputContainer(tank);
        MachinePort port = MachinePort.gas("ae_native_gas_output", MachinePort.Role.OUTPUT, tank);
        return drainNativePort(node, port, port.nativeOutput());
    }

    /**
     * 从流体输出储罐回收到 AE 流体网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param tank 机器流体输出储罐
     * @return 本次是否实际回收了流体
     */
    public static boolean drainFluidTank(AEUpgradeNode node, IExtendedFluidTank tank) {
        if (node == null || tank == null) return false;
        node.observeOutputContainer(tank);
        MachinePort port = MachinePort.fluid("ae_native_fluid_output", MachinePort.Role.OUTPUT, tank);
        return drainNativePort(node, port, port.nativeOutput());
    }

    private static boolean isExtractionGuarded(AEUpgradeNode node, Object container) {
        if (!node.isContainerExtractionGuarded(container)) {
            return false;
        }
        node.markOutputBlocked();
        return true;
    }
}
