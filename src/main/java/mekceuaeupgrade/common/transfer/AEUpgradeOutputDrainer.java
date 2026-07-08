package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;

import ae2.api.config.Actionable;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

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
     * 从物品输出槽回收到 AE 物品网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param slot 机器物品输出槽
     * @return 本次是否实际回收了物品
     */
    public static boolean drainItemSlot(AEUpgradeNode node, IInventorySlot slot) {
        if (slot == null || !node.canUseNetwork()) {
            return false;
        }
        ItemStack stored = slot.getStack();
        if (stored.isEmpty()) {
            return false;
        }
        ItemStack remainder = node.injectItem(stored.copy(), Actionable.SIMULATE);
        int accepted = stored.getCount() - (remainder.isEmpty() ? 0 : remainder.getCount());
        if (accepted <= 0) {
            return false;
        }
        ItemStack extracted = slot.extractItem(accepted, Action.EXECUTE, AutomationType.INTERNAL);
        if (extracted.isEmpty()) {
            return false;
        }
        ItemStack finalRemainder = node.injectItem(extracted, Actionable.MODULATE);
        if (!finalRemainder.isEmpty()) {
            slot.insertItem(finalRemainder, Action.EXECUTE, AutomationType.INTERNAL);
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
        if (tank == null || !node.canUseNetwork() || !AEUpgradeGasBridge.isAvailable()) {
            return false;
        }
        GasStack stored = tank.getGas();
        if (stored == null || stored.getGas() == null || stored.amount <= 0) {
            return false;
        }
        GasStack remainder = node.injectGas(stored.copy(), Actionable.SIMULATE);
        int accepted = stored.amount - (remainder == null ? 0 : remainder.amount);
        if (accepted <= 0) {
            return false;
        }
        GasStack extracted = tank.extract(accepted, Action.EXECUTE, AutomationType.INTERNAL);
        if (extracted == null || extracted.amount <= 0) {
            return false;
        }
        GasStack finalRemainder = node.injectGas(extracted, Actionable.MODULATE);
        if (finalRemainder != null && finalRemainder.amount > 0) {
            tank.insert(finalRemainder, Action.EXECUTE, AutomationType.INTERNAL);
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
        if (tank == null || !node.canUseNetwork() || !AEUpgradeFluidBridge.isAvailable()) {
            return false;
        }
        FluidStack stored = tank.getFluid();
        if (stored == null || stored.getFluid() == null || stored.amount <= 0) {
            return false;
        }
        FluidStack remainder = node.injectFluid(stored.copy(), Actionable.SIMULATE);
        int accepted = stored.amount - (remainder == null ? 0 : remainder.amount);
        if (accepted <= 0) {
            return false;
        }
        FluidStack extracted = tank.extract(accepted, Action.EXECUTE, AutomationType.INTERNAL);
        if (extracted == null || extracted.amount <= 0) {
            return false;
        }
        FluidStack finalRemainder = node.injectFluid(extracted, Actionable.MODULATE);
        if (finalRemainder != null && finalRemainder.amount > 0) {
            tank.insert(finalRemainder, Action.EXECUTE, AutomationType.INTERNAL);
        }
        return true;
    }
}
