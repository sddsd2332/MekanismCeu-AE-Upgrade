package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;
import appeng.me.GridAccessException;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Forge 流体与 AE2 流体存储频道之间的桥接工具。
 */
public final class AEUpgradeFluidBridge {

    private static volatile boolean channelResolved;
    @Nullable
    private static volatile IFluidStorageChannel fluidStorageChannel;

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeFluidBridge() {
    }

    /**
     * @return 当前运行环境是否能访问 AE2 流体频道
     */
    public static boolean isAvailable() {
        return getFluidStorageChannel() != null;
    }

    /**
     * 向 AE 流体网络插入流体。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param stack 要插入的流体栈
     * @param action AE 插入动作，模拟或真实执行
     * @return AE 未接受的剩余流体；完全接受时返回 null
     */
    @Nullable
    public static FluidStack inject(AEUpgradeNode node, FluidStack stack, Actionable action) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0 || !node.canUseNetwork()) {
            return stack;
        }
        IFluidStorageChannel channel = getFluidStorageChannel();
        if (channel == null) {
            return stack;
        }
        try {
            IMEInventory<IAEFluidStack> inventory = node.getInventory(channel);
            IAEFluidStack toInsert = AEFluidStack.fromFluidStack(stack);
            if (toInsert == null) {
                return stack;
            }
            IAEFluidStack remainder = inventory.injectItems(toInsert, action, node.getActionSource());
            return remainder == null ? null : remainder.getFluidStack();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return stack;
        }
    }

    /**
     * 从 AE 流体网络提取流体。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param request 请求提取的流体栈
     * @param action AE 提取动作，模拟或真实执行
     * @return 实际提取到的流体，失败时返回 null
     */
    @Nullable
    public static FluidStack extract(AEUpgradeNode node, FluidStack request, Actionable action) {
        if (request == null || request.getFluid() == null || request.amount <= 0 || !node.canUseNetwork()) {
            return null;
        }
        IFluidStorageChannel channel = getFluidStorageChannel();
        if (channel == null) {
            return null;
        }
        try {
            IMEInventory<IAEFluidStack> inventory = node.getInventory(channel);
            IAEFluidStack toExtract = AEFluidStack.fromFluidStack(request);
            if (toExtract == null) {
                return null;
            }
            IAEFluidStack extracted = inventory.extractItems(toExtract, action, node.getActionSource());
            return extracted == null ? null : extracted.getFluidStack();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean hasAvailable(AEUpgradeNode node, FluidStack request) {
        return request != null && request.getFluid() != null && request.amount > 0 && hasAvailable(node, AEFluidStack.fromFluidStack(request));
    }

    public static boolean hasAvailable(AEUpgradeNode node, @Nullable IAEFluidStack request) {
        if (request == null || request.getStackSize() <= 0 || !node.canUseNetwork()) {
            return false;
        }
        IFluidStorageChannel channel = getFluidStorageChannel();
        if (channel == null) {
            return false;
        }
        try {
            IMEInventory<IAEFluidStack> inventory = node.getInventory(channel);
            if (inventory instanceof IMEMonitor<?> rawMonitor) {
                @SuppressWarnings("unchecked")
                IMEMonitor<IAEFluidStack> monitor = (IMEMonitor<IAEFluidStack>) rawMonitor;
                IAEFluidStack available = monitor.getStorageList().findPrecise(request);
                return available != null && available.getStackSize() >= request.getStackSize();
            }
            IAEFluidStack extracted = inventory.extractItems(request.copy(), Actionable.SIMULATE, node.getActionSource());
            return extracted != null && extracted.getStackSize() >= request.getStackSize();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    private static IFluidStorageChannel getFluidStorageChannel() {
        if (channelResolved) {
            return fluidStorageChannel;
        }
        synchronized (AEUpgradeFluidBridge.class) {
            if (!channelResolved) {
                try {
                    fluidStorageChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                } catch (RuntimeException | LinkageError ignored) {
                    fluidStorageChannel = null;
                }
                channelResolved = true;
            }
            return fluidStorageChannel;
        }
    }
}
