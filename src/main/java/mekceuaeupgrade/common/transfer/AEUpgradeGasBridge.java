package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import mekanism.api.gas.GasStack;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Mekanism 气体与 AE 气体存储频道之间的桥接工具。
 *
 * <p>气体频道来自 Mekanism Energistics，因此这里使用反射延迟绑定，避免该依赖不存在时直接崩溃。</p>
 */
public final class AEUpgradeGasBridge {

    private static final String MEK_ENG_MODID = "mekeng";
    private static volatile boolean attempted;
    @Nullable
    private static volatile Bridge bridge;

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeGasBridge() {
    }

    /**
     * @return 当前运行环境是否能访问 Mekanism Energistics 气体频道
     */
    public static boolean isAvailable() {
        return getBridge() != null;
    }

    /**
     * 向 AE 气体网络插入气体。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param stack 要插入的气体栈
     * @param action AE 插入动作，模拟或真实执行
     * @return AE 未接受的剩余气体；完全接受时返回 null
     */
    @Nullable
    public static GasStack inject(AEUpgradeNode node, GasStack stack, Actionable action) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0 || !node.canUseNetwork()) {
            return stack;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null) {
            return stack;
        }
        try {
            Object aeStack = activeBridge.toAEGasStack.invoke(null, stack);
            if (!(aeStack instanceof IAEStack)) {
                return stack;
            }
            IAEStack remainder = (IAEStack) activeBridge.getInventory(node).injectItems((IAEStack) aeStack, action, node.getActionSource());
            return activeBridge.toGasStack(remainder);
        } catch (GridAccessException | ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return stack;
        }
    }

    /**
     * 从 AE 气体网络提取气体。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param request 请求提取的气体栈
     * @param action AE 提取动作，模拟或真实执行
     * @return 实际提取到的气体，失败时返回 null
     */
    @Nullable
    public static GasStack extract(AEUpgradeNode node, GasStack request, Actionable action) {
        if (request == null || request.getGas() == null || request.amount <= 0 || !node.canUseNetwork()) {
            return null;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null) {
            return null;
        }
        try {
            Object aeStack = activeBridge.toAEGasStack.invoke(null, request);
            if (!(aeStack instanceof IAEStack)) {
                return null;
            }
            IAEStack extracted = (IAEStack) activeBridge.getInventory(node).extractItems((IAEStack) aeStack, action, node.getActionSource());
            return activeBridge.toGasStack(extracted);
        } catch (GridAccessException | ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean hasAvailable(AEUpgradeNode node, GasStack request) {
        if (request == null || request.getGas() == null || request.amount <= 0 || !node.canUseNetwork()) {
            return false;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null) {
            return false;
        }
        try {
            Object aeStack = activeBridge.toAEGasStack.invoke(null, request);
            if (!(aeStack instanceof IAEStack requested)) {
                return false;
            }
            IMEInventory inventory = activeBridge.getInventory(node);
            if (inventory instanceof IMEMonitor monitor) {
                IAEStack available = (IAEStack) monitor.getStorageList().findPrecise(requested);
                return available != null && available.getStackSize() >= request.amount;
            }
            IAEStack extracted = (IAEStack) inventory.extractItems(requested, Actionable.SIMULATE, node.getActionSource());
            return extracted != null && extracted.getStackSize() >= request.amount;
        } catch (GridAccessException | ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    public static Object createAvailabilityRequest(GasStack request) {
        if (request == null || request.getGas() == null || request.amount <= 0) {
            return null;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null) {
            return null;
        }
        try {
            Object aeStack = activeBridge.toAEGasStack.invoke(null, request);
            return aeStack instanceof IAEStack ? aeStack : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean hasAvailable(AEUpgradeNode node, @Nullable Object availabilityRequest) {
        if (!(availabilityRequest instanceof IAEStack requested) || requested.getStackSize() <= 0 || !node.canUseNetwork()) {
            return false;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null) {
            return false;
        }
        try {
            IMEInventory inventory = activeBridge.getInventory(node);
            if (inventory instanceof IMEMonitor monitor) {
                IAEStack available = (IAEStack) monitor.getStorageList().findPrecise(requested);
                return available != null && available.getStackSize() >= requested.getStackSize();
            }
            IAEStack extracted = (IAEStack) inventory.extractItems(requested.copy(), Actionable.SIMULATE, node.getActionSource());
            return extracted != null && extracted.getStackSize() >= requested.getStackSize();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * 懒加载反射桥接信息。
     *
     * @return 成功绑定时返回桥接对象，否则返回 null
     */
    @Nullable
    private static Bridge getBridge() {
        if (bridge != null) {
            return bridge;
        }
        if (attempted || !Loader.isModLoaded(MEK_ENG_MODID)) {
            return null;
        }
        synchronized (AEUpgradeGasBridge.class) {
            if (bridge != null) {
                return bridge;
            }
            if (attempted || !Loader.isModLoaded(MEK_ENG_MODID)) {
                return null;
            }
            attempted = true;
            try {
                ClassLoader loader = AEUpgradeGasBridge.class.getClassLoader();
                Class<?> gasStorageChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel", false, loader);
                Class<?> aeGasStackClass = Class.forName("com.mekeng.github.common.me.data.impl.AEGasStack", false, loader);
                Class<?> aeGasStackInterface = Class.forName("com.mekeng.github.common.me.data.IAEGasStack", false, loader);
                IStorageChannel channel = AEApi.instance().storage().getStorageChannel((Class) gasStorageChannelClass);
                if (channel == null) {
                    return null;
                }
                bridge = new Bridge(channel, aeGasStackClass.getMethod("of", GasStack.class),
                      aeGasStackInterface.getMethod("getGasStack"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                bridge = null;
            }
            return bridge;
        }
    }

    /**
     * Mekanism Energistics 气体 API 的反射绑定集合。
     */
    private static final class Bridge {

        private final IStorageChannel gasStorageChannel;
        private final Method toAEGasStack;
        private final Method toGasStack;

        /**
         * @param gasStorageChannel 气体存储频道
         * @param toAEGasStack GasStack 转 AEGasStack 的静态方法
         * @param toGasStack AEGasStack 转 GasStack 的实例方法
         */
        private Bridge(IStorageChannel gasStorageChannel, Method toAEGasStack, Method toGasStack) {
            this.gasStorageChannel = gasStorageChannel;
            this.toAEGasStack = toAEGasStack;
            this.toGasStack = toGasStack;
        }

        /**
         * 获取 AE 网络中的气体库存。
         *
         * @param node 负责访问 AE 网络的升级节点
         * @return 气体频道库存
         * @throws GridAccessException AE 网络不可访问时抛出
         */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private IMEInventory getInventory(AEUpgradeNode node) throws GridAccessException {
            return node.getInventory(gasStorageChannel);
        }

        /**
         * 将 AE 气体栈反射转换回 Mekanism 气体栈。
         *
         * @param stack AE 气体栈
         * @return 转换出的 Mekanism 气体栈，空栈返回 null
         * @throws ReflectiveOperationException 反射调用失败时抛出
         */
        @Nullable
        private GasStack toGasStack(@Nullable IAEStack stack) throws ReflectiveOperationException {
            if (stack == null || stack.getStackSize() <= 0) {
                return null;
            }
            Object gasStack = toGasStack.invoke(stack);
            return gasStack instanceof GasStack gas ? gas : null;
        }
    }
}
