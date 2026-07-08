package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * AE2FC fake gas 物品与 Mekanism 气体栈之间的互转工具。
 *
 * <p>AE 1.12 pattern 只能表达物品，因此气体输入输出需要包装成 AE2FC 的 fake item。
 * 这里通过反射访问 AE2FC，避免未安装 AE2FC 时硬依赖崩溃。</p>
 */
public final class AEUpgradeFakeGas {

    private static final String AE2FC_MODID = "ae2fc";
    private static volatile boolean attempted;
    @Nullable
    private static volatile Bridge bridge;

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeFakeGas() {
    }

    /**
     * @return fake gas 物品桥接和 AE 气体频道都可用时返回 true
     */
    public static boolean isAvailable() {
        return getBridge() != null && AEUpgradeGasBridge.isAvailable();
    }

    /**
     * 将真实气体栈包装为 AE pattern 可使用的 fake gas 物品。
     *
     * @param stack 真实气体栈
     * @return fake gas 物品，无法包装时返回空栈
     */
    public static ItemStack packOutput(GasStack stack) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0) {
            return ItemStack.EMPTY;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null || !AEUpgradeGasBridge.isAvailable()) {
            return ItemStack.EMPTY;
        }
        try {
            Object packed = activeBridge.packGasToDrops.invoke(null, stack.copy());
            return packed instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 从 AE pattern 交付的 fake gas 物品中解码真实气体。
     *
     * @param stack AE 交付的旧物品栈
     * @return 解码出的气体栈，失败时返回 null
     */
    @Nullable
    public static GasStack unpackInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null || !AEUpgradeGasBridge.isAvailable()) {
            return null;
        }
        try {
            Object unpacked = activeBridge.getFakeStack.invoke(null, stack);
            return unpacked instanceof GasStack gasStack && gasStack.getGas() != null && gasStack.amount > 0 ? gasStack : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * 判断 supplied fake gas 是否包含 expected fake gas。
     *
     * @param supplied AE 实际交付的 fake gas 物品
     * @param expected 配方期望的 fake gas 物品
     * @return 气体类型相同且数量足够时返回 true
     */
    public static boolean inputContains(ItemStack supplied, ItemStack expected) {
        GasStack suppliedGas = unpackInput(supplied);
        GasStack expectedGas = unpackInput(expected);
        return suppliedGas != null && expectedGas != null && suppliedGas.isGasEqual(expectedGas) &&
              suppliedGas.amount >= expectedGas.amount;
    }

    /**
     * 判断真实气体输出是否匹配 AE 期望的 fake gas 物品输出。
     *
     * @param expected AE pattern 中期望的 fake gas 物品
     * @param actual 机器实际产出的真实气体
     * @return 包装后物品相同且数量相同时返回 true
     */
    public static boolean outputMatches(ItemStack expected, GasStack actual) {
        ItemStack packed = packOutput(actual);
        return !expected.isEmpty() && !packed.isEmpty() && expected.getCount() == packed.getCount() &&
              ItemHandlerHelper.canItemStacksStack(expected, packed);
    }

    /**
     * 判断按 craftAmount 放大后的真实气体输出是否匹配 AE 期望输出。
     *
     * @param expected AE pattern 中期望的 fake gas 物品
     * @param actual 单次机器配方产出的真实气体
     * @param multiplier 配方批量倍数
     * @return 放大后匹配时返回 true
     */
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

    /**
     * 懒加载 AE2FC fake gas 反射桥接信息。
     *
     * @return 成功绑定时返回桥接对象，否则返回 null
     */
    @Nullable
    private static Bridge getBridge() {
        if (bridge != null) {
            return bridge;
        }
        if (attempted || !Loader.isModLoaded(AE2FC_MODID)) {
            return null;
        }
        synchronized (AEUpgradeFakeGas.class) {
            if (bridge != null) {
                return bridge;
            }
            if (attempted || !Loader.isModLoaded(AE2FC_MODID)) {
                return null;
            }
            attempted = true;
            try {
                ClassLoader loader = AEUpgradeFakeGas.class.getClassLoader();
                Class<?> fakeGasesClass = Class.forName("com.glodblock.github.integration.mek.FakeGases", false, loader);
                Class<?> fakeItemRegisterClass = Class.forName("com.glodblock.github.common.item.fake.FakeItemRegister", false, loader);
                bridge = new Bridge(fakeGasesClass.getMethod("packGas2Drops", GasStack.class),
                      fakeItemRegisterClass.getMethod("getStack", ItemStack.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                bridge = null;
            }
            return bridge;
        }
    }

    /**
     * AE2FC fake gas API 的反射绑定集合。
     */
    private static final class Bridge {

        private final Method packGasToDrops;
        private final Method getFakeStack;

        /**
         * @param packGasToDrops GasStack 包装成 fake item 的方法
         * @param getFakeStack fake item 解码成 GasStack 的方法
         */
        private Bridge(Method packGasToDrops, Method getFakeStack) {
            this.packGasToDrops = packGasToDrops;
            this.getFakeStack = getFakeStack;
        }
    }
}
