package mekceuaeupgrade.common.recipe.route;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;

import mekanism.api.gas.GasStack;
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
 * @param carrierItemStack AE pattern 中用于代表该 typed 栈的兼容物品栈
 */
public record AERecipeRouteStack(
      AERecipeStackKind kind,
      String portId,
      int order,
      ItemStack itemStack,
      @Nullable GasStack gasStack,
      @Nullable FluidStack fluidStack,
      ItemStack carrierItemStack) {

    /**
     * 复制传入栈，避免 route 保存外部可变引用。
     */
    public AERecipeRouteStack {
        Objects.requireNonNull(kind, "kind");
        portId = portId == null ? "" : portId;
        itemStack = copy(itemStack);
        gasStack = copy(gasStack);
        fluidStack = copy(fluidStack);
        carrierItemStack = copy(carrierItemStack);
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
     * 创建没有指定兼容物品代表的气体 route 栈。
     *
     * @param portId 绑定机器端口的 ID
     * @param stack 真实气体输入或输出
     * @return 气体 route 栈
     */
    public static AERecipeRouteStack gas(String portId, GasStack stack) {
        return gas(portId, stack, ItemStack.EMPTY);
    }

    /**
     * 创建气体 route 栈，并指定 AE pattern 中使用的兼容物品代表。
     *
     * @param portId 绑定机器端口的 ID
     * @param stack 真实气体输入或输出
     * @param carrierItemStack AE pattern 中代表该气体的兼容物品
     * @return 气体 route 栈
     */
    public static AERecipeRouteStack gas(String portId, GasStack stack, ItemStack carrierItemStack) {
        return new AERecipeRouteStack(AERecipeStackKind.GAS, portId, 0, ItemStack.EMPTY, stack, null, carrierItemStack);
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
        return new AERecipeRouteStack(kind, portId, order, itemStack, gasStack, fluidStack, carrierItemStack);
    }

    /**
     * @return 是否有显式兼容物品代表该 typed 栈
     */
    public boolean hasCarrierStack() {
        return !carrierItemStack.isEmpty();
    }

    /**
     * 将 typed 栈转换成 AE pattern 可识别的输入/输出载体。
     *
     * @return 可写入 AE pattern 的载体栈，无法转换时返回空栈
     */
    public ItemStack toCarrierStack() {
        switch (kind) {
            case ITEM:
                return copy(itemStack);
            case GAS:
                return hasCarrierStack() ? copy(carrierItemStack) : AEUpgradeFakeGas.packOutput(gasStack);
            case FLUID:
                return AEUpgradeFakeFluid.packOutput(fluidStack);
            default:
                return ItemStack.EMPTY;
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
