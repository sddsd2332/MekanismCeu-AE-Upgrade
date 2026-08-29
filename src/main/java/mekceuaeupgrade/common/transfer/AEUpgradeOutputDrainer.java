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
            List<MachineResourceStack> stored = new ArrayList<>(port.peekAll());
            for (MachineResourceStack resource : stored) {
                if (node.getPendingTransferBuffer().hasOwnedResources()) {
                    node.markOutputBlocked();
                    return drained;
                }
                drained |= drainProviderResource(node, tile, port, resource);
            }
            if (!port.peekAll().isEmpty()) {
                node.markOutputBlocked();
            }
        }
        return drained;
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
        if (slot == null || automationType == null) {
            return false;
        }
        node.observeOutputContainer(slot);
        ItemStack stored = slot.getStack();
        if (stored.isEmpty()) {
            return false;
        }
        if (isExtractionGuarded(node, slot)) {
            return false;
        }
        if (!node.canUseNetwork()) {
            node.markOutputBlocked();
            return false;
        }
        ItemStack remainder = node.injectItem(stored.copy(), Actionable.SIMULATE);
        int accepted = acceptedItemAmount(stored, remainder);
        if (accepted <= 0) {
            node.markOutputBlocked();
            return false;
        }
        if (isExtractionGuarded(node, slot)) {
            return false;
        }
        ItemStack expected = stored.copy();
        expected.setCount(accepted);
        AEPendingTransferBuffer buffer = node.getPendingTransferBuffer();
        AEPendingTransferBuffer.Entry pending = buffer.prepareItem(expected);
        ItemStack extracted;
        try {
            extracted = slot.extractItem(accepted, Action.EXECUTE, automationType);
        } catch (RuntimeException | LinkageError e) {
            buffer.cancelPrepared(pending);
            throw e;
        }
        if (extracted.isEmpty()) {
            buffer.cancelPrepared(pending);
            node.markOutputBlocked();
            return false;
        }
        buffer.holdItem(pending, extracted);
        buffer.deliverToNetwork(pending, node);
        if (buffer.contains(pending)) {
            buffer.deliverItem(pending, stack -> slot.insertItem(stack, Action.EXECUTE, AutomationType.INTERNAL));
        }
        if (buffer.contains(pending) || !slot.getStack().isEmpty()) {
            node.markOutputBlocked();
        }
        return true;
    }

    /**
     * 从气体输出储罐回收到 AE 气体网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param tank 机器气体输出储罐
     * @return 本次是否实际回收了气体
     */
    public static boolean drainGasTank(AEUpgradeNode node, IExtendedGasTank tank) {
        if (tank == null) {
            return false;
        }
        node.observeOutputContainer(tank);
        GasStack stored = tank.getGas();
        if (stored == null || stored.getGas() == null || stored.amount <= 0) {
            return false;
        }
        if (isExtractionGuarded(node, tank)) {
            return false;
        }
        if (!node.canUseNetwork()) {
            node.markOutputBlocked();
            return false;
        }
        GasStack remainder = node.injectGas(stored.copy(), Actionable.SIMULATE);
        int accepted = acceptedGasAmount(stored, remainder);
        if (accepted <= 0) {
            node.markOutputBlocked();
            return false;
        }
        if (isExtractionGuarded(node, tank)) {
            return false;
        }
        GasStack expected = stored.copy();
        expected.amount = accepted;
        AEPendingTransferBuffer buffer = node.getPendingTransferBuffer();
        AEPendingTransferBuffer.Entry pending = buffer.prepareGas(expected);
        GasStack extracted;
        try {
            extracted = tank.extract(accepted, Action.EXECUTE, AutomationType.INTERNAL);
        } catch (RuntimeException | LinkageError e) {
            buffer.cancelPrepared(pending);
            throw e;
        }
        if (extracted == null || extracted.amount <= 0) {
            buffer.cancelPrepared(pending);
            node.markOutputBlocked();
            return false;
        }
        buffer.holdGas(pending, extracted);
        buffer.deliverToNetwork(pending, node);
        if (buffer.contains(pending)) {
            buffer.deliverGas(pending, stack -> tank.insert(stack, Action.EXECUTE, AutomationType.INTERNAL));
        }
        GasStack remaining = tank.getGas();
        if (buffer.contains(pending) || (remaining != null && remaining.amount > 0)) {
            node.markOutputBlocked();
        }
        return true;
    }

    /**
     * 从流体输出储罐回收到 AE 流体网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param tank 机器流体输出储罐
     * @return 本次是否实际回收了流体
     */
    public static boolean drainFluidTank(AEUpgradeNode node, IExtendedFluidTank tank) {
        if (tank == null) {
            return false;
        }
        node.observeOutputContainer(tank);
        FluidStack stored = tank.getFluid();
        if (stored == null || stored.getFluid() == null || stored.amount <= 0) {
            return false;
        }
        if (isExtractionGuarded(node, tank)) {
            return false;
        }
        if (!node.canUseNetwork()) {
            node.markOutputBlocked();
            return false;
        }
        FluidStack remainder = node.injectFluid(stored.copy(), Actionable.SIMULATE);
        int accepted = acceptedFluidAmount(stored, remainder);
        if (accepted <= 0) {
            node.markOutputBlocked();
            return false;
        }
        if (isExtractionGuarded(node, tank)) {
            return false;
        }
        FluidStack expected = stored.copy();
        expected.amount = accepted;
        AEPendingTransferBuffer buffer = node.getPendingTransferBuffer();
        AEPendingTransferBuffer.Entry pending = buffer.prepareFluid(expected);
        FluidStack extracted;
        try {
            extracted = tank.extract(accepted, Action.EXECUTE, AutomationType.INTERNAL);
        } catch (RuntimeException | LinkageError e) {
            buffer.cancelPrepared(pending);
            throw e;
        }
        if (extracted == null || extracted.amount <= 0) {
            buffer.cancelPrepared(pending);
            node.markOutputBlocked();
            return false;
        }
        buffer.holdFluid(pending, extracted);
        buffer.deliverToNetwork(pending, node);
        if (buffer.contains(pending)) {
            buffer.deliverFluid(pending, stack -> tank.insert(stack, Action.EXECUTE, AutomationType.INTERNAL));
        }
        FluidStack remaining = tank.getFluid();
        if (buffer.contains(pending) || (remaining != null && remaining.amount > 0)) {
            node.markOutputBlocked();
        }
        return true;
    }

    private static boolean isExtractionGuarded(AEUpgradeNode node, Object container) {
        if (!node.isContainerExtractionGuarded(container)) {
            return false;
        }
        node.markOutputBlocked();
        return true;
    }
}
