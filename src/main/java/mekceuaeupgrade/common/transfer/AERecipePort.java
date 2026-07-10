package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.recipe.route.AERecipeStackKind;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * AE 配方转移端口的统一抽象。
 *
 * <p>一个端口只负责一种状态的输入或输出：物品、气体或流体。adapter 层只需要把机器内部槽位或储罐包装成端口，
 * {@link AERecipeTransferPlan} 再按端口类型和端口 ID 执行统一的模拟、写入、回滚和输出回收。</p>
 */
public abstract class AERecipePort {

    private final AERecipeStackKind kind;
    private final String portId;

    /**
     * @param kind 端口接受或输出的栈类型
     * @param portId route 中用于匹配该端口的稳定 ID
     */
    private AERecipePort(AERecipeStackKind kind, String portId) {
        this.kind = kind;
        this.portId = portId == null ? "" : portId;
    }

    /**
     * @return 端口处理的栈类型
     */
    public AERecipeStackKind kind() {
        return kind;
    }

    /**
     * @return route 中绑定该端口的 ID
     */
    public String portId() {
        return portId;
    }

    /**
     * 模拟该端口是否能完整接收输入。
     *
     * @param stack 待插入的 route 栈
     * @return 可以完整插入时返回 true
     */
    public abstract boolean canInsert(AERecipeRouteStack stack);

    /**
     * 对该端口执行真实输入写入。
     *
     * @param stack 待插入的 route 栈
     * @return 完整插入时返回 true
     */
    public abstract boolean insert(AERecipeRouteStack stack);

    /**
     * 将该端口中的输出回收到 AE 网络。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @return 本次是否实际回收了内容
     */
    public abstract boolean drain(AEUpgradeNode node);

    /**
     * 读取端口当前状态，用于原子写入失败后的回滚。
     *
     * @return 当前端口状态快照值
     */
    protected abstract Object snapshotValue();

    /**
     * 将端口恢复到之前保存的状态。
     *
     * @param value {@link #snapshotValue()} 保存的快照值
     */
    protected abstract void restoreValue(Object value);

    /**
     * @return 可恢复该端口状态的快照对象
     */
    public Snapshot snapshot() {
        return new Snapshot(this, snapshotValue());
    }

    /**
     * 创建物品槽位端口。
     *
     * @param portId route 中对应的端口 ID
     * @param slot Mekanism 物品槽位
     * @return 槽位存在时返回端口，否则返回 null
     */
    @Nullable
    public static AERecipePort item(String portId, @Nullable IInventorySlot slot) {
        return slot == null ? null : new ItemPort(portId, slot);
    }

    /**
     * 创建气体储罐端口。
     *
     * @param portId route 中对应的端口 ID
     * @param tank Mekanism 气体储罐
     * @return 储罐存在时返回端口，否则返回 null
     */
    @Nullable
    public static AERecipePort gas(String portId, @Nullable IExtendedGasTank tank) {
        return tank == null ? null : new GasPort(portId, tank);
    }

    /**
     * 创建流体储罐端口。
     *
     * @param portId route 中对应的端口 ID
     * @param tank Mekanism 流体储罐
     * @return 储罐存在时返回端口，否则返回 null
     */
    @Nullable
    public static AERecipePort fluid(String portId, @Nullable IExtendedFluidTank tank) {
        return tank == null ? null : new FluidPort(portId, tank);
    }

    /**
     * 批量回收多个输出端口。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param ports 需要尝试回收的端口列表
     * @return 任意端口实际回收了内容时返回 true
     */
    public static boolean drainAll(AEUpgradeNode node, @Nullable AERecipePort... ports) {
        if (ports == null) {
            return false;
        }
        boolean drained = false;
        for (AERecipePort port : ports) {
            if (port != null) {
                drained |= port.drain(node);
            }
        }
        return drained;
    }

    public static boolean drainItem(AEUpgradeNode node, @Nullable IInventorySlot slot) {
        return slot != null && AEUpgradeOutputDrainer.drainItemSlot(node, slot);
    }

    public static boolean drainItems(AEUpgradeNode node, @Nullable IInventorySlot first, @Nullable IInventorySlot second) {
        boolean drained = drainItem(node, first);
        return drainItem(node, second) || drained;
    }

    public static boolean drainGas(AEUpgradeNode node, @Nullable IExtendedGasTank tank) {
        return tank != null && AEUpgradeOutputDrainer.drainGasTank(node, tank);
    }

    public static boolean drainGases(AEUpgradeNode node, @Nullable IExtendedGasTank first, @Nullable IExtendedGasTank second) {
        boolean drained = drainGas(node, first);
        return drainGas(node, second) || drained;
    }

    public static boolean drainFluid(AEUpgradeNode node, @Nullable IExtendedFluidTank tank) {
        return tank != null && AEUpgradeOutputDrainer.drainFluidTank(node, tank);
    }

    public static boolean drainItemAndGas(AEUpgradeNode node, @Nullable IInventorySlot slot, @Nullable IExtendedGasTank tank) {
        boolean drained = drainItem(node, slot);
        return drainGas(node, tank) || drained;
    }

    /**
     * 单个端口的状态快照。
     */
    public static final class Snapshot {

        private final AERecipePort port;
        private final Object value;

        private Snapshot(AERecipePort port, Object value) {
            this.port = port;
            this.value = value;
        }

        /**
         * 将端口恢复到创建快照时的状态。
         */
        public void restore() {
            port.restoreValue(value);
        }
    }

    /**
     * 物品槽位端口实现。
     */
    private static final class ItemPort extends AERecipePort {

        private final IInventorySlot slot;

        /**
         * @param portId route 中对应的端口 ID
         * @param slot Mekanism 物品槽位
         */
        private ItemPort(String portId, IInventorySlot slot) {
            super(AERecipeStackKind.ITEM, portId);
            this.slot = slot;
        }

        @Override
        public boolean canInsert(AERecipeRouteStack stack) {
            return insert(stack, Action.SIMULATE);
        }

        @Override
        public boolean insert(AERecipeRouteStack stack) {
            return insert(stack, Action.EXECUTE);
        }

        /**
         * @param stack 待插入物品 route 栈
         * @param action 执行模式，模拟或真实写入
         * @return 物品完整插入时返回 true
         */
        private boolean insert(AERecipeRouteStack stack, Action action) {
            if (stack == null || stack.kind() != AERecipeStackKind.ITEM || stack.itemStack().isEmpty()) {
                return false;
            }
            return slot.insertItem(stack.itemStack().copy(), action, AutomationType.INTERNAL).isEmpty();
        }

        @Override
        public boolean drain(AEUpgradeNode node) {
            return AEUpgradeOutputDrainer.drainItemSlot(node, slot);
        }

        @Override
        protected Object snapshotValue() {
            return copy(slot.getStack());
        }

        @Override
        protected void restoreValue(Object value) {
            ItemStack stack = value instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
            if (slot instanceof BasicInventorySlot basicSlot) {
                basicSlot.setStackUnchecked(copy(stack));
            } else {
                slot.setStack(copy(stack));
            }
        }
    }

    /**
     * 气体储罐端口实现。
     */
    private static final class GasPort extends AERecipePort {

        private final IExtendedGasTank tank;

        /**
         * @param portId route 中对应的端口 ID
         * @param tank Mekanism 气体储罐
         */
        private GasPort(String portId, IExtendedGasTank tank) {
            super(AERecipeStackKind.GAS, portId);
            this.tank = tank;
        }

        @Override
        public boolean canInsert(AERecipeRouteStack stack) {
            return insert(stack, Action.SIMULATE);
        }

        @Override
        public boolean insert(AERecipeRouteStack stack) {
            return insert(stack, Action.EXECUTE);
        }

        /**
         * @param stack 待插入气体 route 栈
         * @param action 执行模式，模拟或真实写入
         * @return 气体完整插入时返回 true
         */
        private boolean insert(AERecipeRouteStack stack, Action action) {
            GasStack gas = stack == null ? null : stack.gasStack();
            if (stack == null || stack.kind() != AERecipeStackKind.GAS || gas == null || gas.getGas() == null || gas.amount <= 0) {
                return false;
            }
            GasStack remainder = tank.insert(gas.copy(), action, AutomationType.INTERNAL);
            return remainder == null || remainder.amount <= 0;
        }

        @Override
        public boolean drain(AEUpgradeNode node) {
            return AEUpgradeOutputDrainer.drainGasTank(node, tank);
        }

        @Override
        protected Object snapshotValue() {
            return copy(tank.getStack());
        }

        @Override
        protected void restoreValue(Object value) {
            tank.setStackUnchecked(value instanceof GasStack gasStack ? copy(gasStack) : null);
        }
    }

    /**
     * 流体储罐端口实现。
     */
    private static final class FluidPort extends AERecipePort {

        private final IExtendedFluidTank tank;

        /**
         * @param portId route 中对应的端口 ID
         * @param tank Mekanism 流体储罐
         */
        private FluidPort(String portId, IExtendedFluidTank tank) {
            super(AERecipeStackKind.FLUID, portId);
            this.tank = tank;
        }

        @Override
        public boolean canInsert(AERecipeRouteStack stack) {
            return insert(stack, Action.SIMULATE);
        }

        @Override
        public boolean insert(AERecipeRouteStack stack) {
            return insert(stack, Action.EXECUTE);
        }

        /**
         * @param stack 待插入流体 route 栈
         * @param action 执行模式，模拟或真实写入
         * @return 流体完整插入时返回 true
         */
        private boolean insert(AERecipeRouteStack stack, Action action) {
            FluidStack fluid = stack == null ? null : stack.fluidStack();
            if (stack == null || stack.kind() != AERecipeStackKind.FLUID || fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
                return false;
            }
            FluidStack remainder = tank.insert(fluid.copy(), action, AutomationType.INTERNAL);
            return remainder == null || remainder.amount <= 0;
        }

        @Override
        public boolean drain(AEUpgradeNode node) {
            return AEUpgradeOutputDrainer.drainFluidTank(node, tank);
        }

        @Override
        protected Object snapshotValue() {
            return copy(tank.getFluid());
        }

        @Override
        protected void restoreValue(Object value) {
            tank.setStackUnchecked(value instanceof FluidStack fluidStack ? copy(fluidStack) : null);
        }
    }

    /**
     * @param stack 需要复制的物品栈
     * @return 安全副本，空值会被转换为 ItemStack.EMPTY
     */
    private static ItemStack copy(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    /**
     * @param stack 需要复制的气体栈
     * @return 安全副本，空值保持为 null
     */
    @Nullable
    private static GasStack copy(@Nullable GasStack stack) {
        return stack == null ? null : stack.copy();
    }

    /**
     * @param stack 需要复制的流体栈
     * @return 安全副本，空值保持为 null
     */
    @Nullable
    private static FluidStack copy(@Nullable FluidStack stack) {
        return stack == null ? null : stack.copy();
    }
}
