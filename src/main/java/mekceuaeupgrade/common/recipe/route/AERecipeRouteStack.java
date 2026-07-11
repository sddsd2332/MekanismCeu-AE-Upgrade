package mekceuaeupgrade.common.recipe.route;

import com.github.bsideup.jabel.Desugar;
import mekanism.api.gas.GasStack;
import mekceuaeupgrade.common.recipe.AERecipeStacks;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * route 中的单个 typed 输入或输出栈。
 *
 * @param kind 栈状态类型，决定使用 item/gas/fluid 哪个字段
 * @param portId 绑定机器端口的稳定 ID
 * @param order 在 route 输入或输出列表中的顺序
 * @param itemStack 物品状态下的真实物品栈
 * @param gasStack 气体状态下的真实气体栈
 * @param fluidStack 流体状态下的真实流体栈
 * @param legacyItemStack AE 1.12 pattern 中用于代表该 typed 栈的物品栈
 */
@Desugar
public record AERecipeRouteStack(
      AERecipeStackKind kind,
      String portId,
      int order,
      ItemStack itemStack,
      @Nullable GasStack gasStack,
      @Nullable FluidStack fluidStack,
      ItemStack legacyItemStack) {

    /**
     * 复制传入栈，避免 route 保存外部可变引用。
     */
    public AERecipeRouteStack {
        Objects.requireNonNull(kind, "kind");
        portId = portId == null ? "" : portId;
        itemStack = copy(itemStack);
        gasStack = copy(gasStack);
        fluidStack = copy(fluidStack);
        legacyItemStack = copy(legacyItemStack);
    }

    /**
     * 创建物品 route 栈。
     *
     * @param portId 绑定机器端口的 ID
     * @param stack 真实物品输入或输出
     * @return 物品 route 栈
     */
    public static AERecipeRouteStack item(String portId, ItemStack stack) {
        return new AERecipeRouteStack(AERecipeStackKind.ITEM, portId, 0, stack, null, null, ItemStack.EMPTY);
    }

    /**
     * 创建没有指定旧物品代表的气体 route 栈。
     *
     * @param portId 绑定机器端口的 ID
     * @param stack 真实气体输入或输出
     * @return 气体 route 栈
     */
    public static AERecipeRouteStack gas(String portId, GasStack stack) {
        return gas(portId, stack, ItemStack.EMPTY);
    }

    /**
     * 创建气体 route 栈，并指定 AE pattern 中使用的旧物品代表。
     *
     * @param portId 绑定机器端口的 ID
     * @param stack 真实气体输入或输出
     * @param legacyItemStack AE 1.12 pattern 中代表该气体的物品
     * @return 气体 route 栈
     */
    public static AERecipeRouteStack gas(String portId, GasStack stack, ItemStack legacyItemStack) {
        return new AERecipeRouteStack(AERecipeStackKind.GAS, portId, 0, ItemStack.EMPTY, stack, null, legacyItemStack);
    }

    /**
     * 创建流体 route 栈。
     *
     * @param portId 绑定机器端口的 ID
     * @param stack 真实流体输入或输出
     * @return 流体 route 栈
     */
    public static AERecipeRouteStack fluid(String portId, FluidStack stack) {
        return new AERecipeRouteStack(AERecipeStackKind.FLUID, portId, 0, ItemStack.EMPTY, null, stack, ItemStack.EMPTY);
    }

    /**
     * 返回设置了 route 顺序的新栈。
     *
     * @param order route 输入或输出列表中的顺序
     * @return 带新顺序的 route 栈副本
     */
    public AERecipeRouteStack withOrder(int order) {
        return new AERecipeRouteStack(kind, portId, order, itemStack, gasStack, fluidStack, legacyItemStack);
    }

    /**
     * @return 是否有显式旧物品代表该 typed 栈
     */
    public boolean hasLegacyStack() {
        return !legacyItemStack.isEmpty();
    }

    /**
     * 将 typed 栈转换成 AE 1.12 pattern 可识别的物品栈。
     *
     * @return 可写入 AE pattern 的旧物品栈，无法转换时返回空栈
     */
    public ItemStack toLegacyStack() {
        return toLegacyStack(1);
    }

    /**
     * 将 typed 栈按批量数量转换成 AE 1.12 pattern 可识别的物品栈。
     *
     * <p>气体和流体 fake item 的真实数量通常保存在 NBT 中，因此批量化时需要先放大
     * typed 栈再重新包装，不能只修改 {@link ItemStack#getCount()}。</p>
     *
     * @param multiplier 批量倍数
     * @return 可写入 AE pattern 的旧物品栈，无法转换时返回空栈
     */
    public ItemStack toLegacyStack(int multiplier) {
        switch (kind) {
            case ITEM:
                return AERecipeStacks.scale(itemStack, multiplier);
            case GAS:
                return hasLegacyStack() ? AERecipeStacks.scale(legacyItemStack, multiplier) : AEUpgradeFakeGas.packOutput(scale(gasStack, multiplier));
            case FLUID:
                return AEUpgradeFakeFluid.packOutput(scale(fluidStack, multiplier));
            default:
                return ItemStack.EMPTY;
        }
    }

    /**
     * @return 该 typed 栈在不溢出的前提下允许的最大批量倍数
     */
    public int getMaxCraftAmount() {
        int max = Integer.MAX_VALUE;
        switch (kind) {
            case ITEM:
                return getMaxCraftAmount(itemStack);
            case GAS:
                if (hasLegacyStack()) {
                    max = Math.min(max, getMaxCraftAmount(legacyItemStack));
                }
                return Math.min(max, getMaxCraftAmount(gasStack));
            case FLUID:
                return getMaxCraftAmount(fluidStack);
            default:
                return max;
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
     * @param stack 需要检查的物品栈
     * @return 该物品栈计数不溢出时允许的最大批量倍数
     */
    private static int getMaxCraftAmount(ItemStack stack) {
        return stack == null || stack.isEmpty() || stack.getCount() <= 0 ? Integer.MAX_VALUE : Integer.MAX_VALUE / stack.getCount();
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
     * @param stack 需要放大的气体栈
     * @param multiplier 批量倍数
     * @return 放大后的气体栈，溢出或无效时返回 null
     */
    @Nullable
    private static GasStack scale(@Nullable GasStack stack, int multiplier) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0 || multiplier <= 0) {
            return null;
        }
        long amount = (long) stack.amount * multiplier;
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        return stack.copy().withAmount((int) amount);
    }

    /**
     * @param stack 需要检查的气体栈
     * @return 该气体数量不溢出时允许的最大批量倍数
     */
    private static int getMaxCraftAmount(@Nullable GasStack stack) {
        return stack == null || stack.getGas() == null || stack.amount <= 0 ? Integer.MAX_VALUE : Integer.MAX_VALUE / stack.amount;
    }

    /**
     * @param stack 需要复制的流体栈
     * @return 安全副本，空值保持为 null
     */
    @Nullable
    private static FluidStack copy(@Nullable FluidStack stack) {
        return stack == null ? null : stack.copy();
    }

    /**
     * @param stack 需要放大的流体栈
     * @param multiplier 批量倍数
     * @return 放大后的流体栈，溢出或无效时返回 null
     */
    @Nullable
    private static FluidStack scale(@Nullable FluidStack stack, int multiplier) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0 || multiplier <= 0) {
            return null;
        }
        long amount = (long) stack.amount * multiplier;
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        FluidStack scaled = stack.copy();
        scaled.amount = (int) amount;
        return scaled;
    }

    /**
     * @param stack 需要检查的流体栈
     * @return 该流体数量不溢出时允许的最大批量倍数
     */
    private static int getMaxCraftAmount(@Nullable FluidStack stack) {
        return stack == null || stack.getFluid() == null || stack.amount <= 0 ? Integer.MAX_VALUE : Integer.MAX_VALUE / stack.amount;
    }
}
