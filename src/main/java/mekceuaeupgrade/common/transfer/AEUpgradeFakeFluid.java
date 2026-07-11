package mekceuaeupgrade.common.transfer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * AE2FC fake fluid 物品与 Forge 流体栈之间的互转工具。
 *
 * <p>AE 1.12 pattern 只能表达物品，因此流体输入输出需要包装成 AE2FC 的 fake item。
 * 这里通过反射访问 AE2FC，避免未安装 AE2FC 时硬依赖崩溃。</p>
 */
public final class AEUpgradeFakeFluid {

    private static final String AE2FC_MODID = "ae2fc";
    private static volatile boolean attempted;
    @Nullable
    private static volatile Bridge bridge;

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeFakeFluid() {
    }

    /**
     * @return fake fluid 物品桥接可用时返回 true
     */
    public static boolean isAvailable() {
        return getBridge() != null;
    }

    /**
     * 将真实流体栈包装为 AE pattern 可使用的 fake fluid 物品。
     *
     * @param stack 真实流体栈
     * @return fake fluid 物品，无法包装时返回空栈
     */
    public static ItemStack packOutput(FluidStack stack) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0) {
            return ItemStack.EMPTY;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object packed = activeBridge.packFluidToDrops.invoke(null, stack.copy());
            return packed instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 从 AE pattern 交付的 fake fluid 物品中解码真实流体。
     *
     * @param stack AE 交付的旧物品栈
     * @return 解码出的流体栈，失败时返回 null
     */
    @Nullable
    public static FluidStack unpackInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Bridge activeBridge = getBridge();
        if (activeBridge == null) {
            return null;
        }
        try {
            Object unpacked = activeBridge.getFakeStack.invoke(null, stack);
            return unpacked instanceof FluidStack fluidStack && fluidStack.getFluid() != null && fluidStack.amount > 0 ? fluidStack : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * 判断 supplied fake fluid 是否包含 expected fake fluid。
     *
     * @param supplied AE 实际交付的 fake fluid 物品
     * @param expected 配方期望的 fake fluid 物品
     * @return 流体类型相同且数量足够时返回 true
     */
    public static boolean inputContains(ItemStack supplied, ItemStack expected) {
        FluidStack suppliedFluid = unpackInput(supplied);
        FluidStack expectedFluid = unpackInput(expected);
        return suppliedFluid != null && expectedFluid != null && suppliedFluid.isFluidEqual(expectedFluid) &&
              suppliedFluid.amount >= expectedFluid.amount;
    }

    /**
     * 判断真实流体输出是否匹配 AE 期望的 fake fluid 物品输出。
     *
     * @param expected AE pattern 中期望的 fake fluid 物品
     * @param actual 机器实际产出的真实流体
     * @return 包装后物品相同且数量相同时返回 true
     */
    public static boolean outputMatches(ItemStack expected, FluidStack actual) {
        ItemStack packed = packOutput(actual);
        return !expected.isEmpty() && !packed.isEmpty() && expected.getCount() == packed.getCount() &&
              ItemHandlerHelper.canItemStacksStack(expected, packed);
    }

    /**
     * 判断按 craftAmount 放大后的真实流体输出是否匹配 AE 期望输出。
     *
     * @param expected AE pattern 中期望的 fake fluid 物品
     * @param actual 单次机器配方产出的真实流体
     * @param multiplier 配方批量倍数
     * @return 放大后匹配时返回 true
     */
    public static boolean outputMatches(ItemStack expected, FluidStack actual, int multiplier) {
        if (actual == null || actual.getFluid() == null) {
            return false;
        }
        long amount = (long) actual.amount * Math.max(1, multiplier);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return false;
        }
        FluidStack scaled = actual.copy();
        scaled.amount = (int) amount;
        return outputMatches(expected, scaled);
    }

    /**
     * 懒加载 AE2FC fake fluid 反射桥接信息。
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
        synchronized (AEUpgradeFakeFluid.class) {
            if (bridge != null) {
                return bridge;
            }
            if (attempted || !Loader.isModLoaded(AE2FC_MODID)) {
                return null;
            }
            attempted = true;
            try {
                ClassLoader loader = AEUpgradeFakeFluid.class.getClassLoader();
                Class<?> fakeFluidsClass = Class.forName("com.glodblock.github.common.item.fake.FakeFluids", false, loader);
                Class<?> fakeItemRegisterClass = Class.forName("com.glodblock.github.common.item.fake.FakeItemRegister", false, loader);
                bridge = new Bridge(fakeFluidsClass.getMethod("packFluid2Drops", FluidStack.class),
                      fakeItemRegisterClass.getMethod("getStack", ItemStack.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                bridge = null;
            }
            return bridge;
        }
    }

    /**
     * AE2FC fake fluid API 的反射绑定集合。
     */
    private static final class Bridge {

        private final Method packFluidToDrops;
        private final Method getFakeStack;

        /**
         * @param packFluidToDrops FluidStack 包装成 fake item 的方法
         * @param getFakeStack fake item 解码成 FluidStack 的方法
         */
        private Bridge(Method packFluidToDrops, Method getFakeStack) {
            this.packFluidToDrops = packFluidToDrops;
            this.getFakeStack = getFakeStack;
        }
    }
}
