package mekceuaeupgrade.common.recipe.route;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.inputs.MachineInput;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * route typed 栈与 AE 1.12 旧物品输入之间的转换和匹配工具。
 *
 * <p>AE pattern 在 1.12 中仍以物品栈表达输入输出；该类负责判断旧物品栈是否符合 route 类型，
 * 并把 fake gas、气体转换物品、fake fluid 解码回真实 Mekanism 栈。</p>
 */
public final class AERecipeRouteLegacyIO {

    /**
     * 工具类不允许实例化。
     */
    private AERecipeRouteLegacyIO() {
    }

    /**
     * 检查旧物品输入列表是否匹配指定 route。
     *
     * @param route 待匹配的 route
     * @param stacks AE 实际交付的旧物品输入列表
     * @return 所有输入都匹配 route 时返回 true
     */
    public static boolean matchesInputs(AERecipeRoute route, List<ItemStack> stacks) {
        if (route == null || stacks == null || route.inputs().size() != stacks.size()) {
            return false;
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (!matchesInput(route.inputs().get(i), stacks.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查旧物品输入列表是否匹配暴露配方及其 route 类型。
     *
     * @param recipe 暴露给 AE 的配方
     * @param stacks AE 实际交付的旧物品输入列表
     * @return 输入数量、物品内容和 route 类型都匹配时返回 true
     */
    public static boolean matchesInputs(AEExposedRecipe recipe, List<ItemStack> stacks) {
        if (recipe == null || stacks == null || !recipe.matchesInputs(stacks)) {
            return false;
        }
        AERecipeRoute route = recipe.getRecipeRoute();
        return route == null || matchesKinds(route.inputs(), stacks);
    }

    /**
     * 检查单个旧物品输入是否匹配暴露配方及其 route 类型。
     *
     * @param recipe 暴露给 AE 的配方
     * @param stack AE 实际交付的旧物品输入
     * @return 输入匹配时返回 true
     */
    public static boolean matchesInput(AEExposedRecipe recipe, ItemStack stack) {
        if (recipe == null || stack == null || !recipe.matchesInput(stack)) {
            return false;
        }
        AERecipeRoute route = recipe.getRecipeRoute();
        if (route == null || route.inputs().size() != 1) {
            return true;
        }
        return matchesKind(route.inputs().get(0), stack);
    }

    /**
     * 检查单个旧物品输入是否匹配指定 route 栈。
     *
     * @param routeStack route 中的 typed 输入栈
     * @param supplied AE 实际交付的旧物品输入
     * @return 类型和内容都匹配时返回 true
     */
    public static boolean matchesInput(AERecipeRouteStack routeStack, ItemStack supplied) {
        if (routeStack == null || supplied == null || supplied.isEmpty()) {
            return false;
        }
        switch (routeStack.kind()) {
            case ITEM:
                return MachineInput.inputContains(supplied, routeStack.itemStack());
            case GAS:
                return getGasFromSource(supplied, gas -> routeStack.gasStack() != null && gas == routeStack.gasStack().getGas()) != null;
            case FLUID:
                FluidStack fluid = AEUpgradeFakeFluid.unpackInput(supplied);
                FluidStack expected = routeStack.fluidStack();
                return fluid != null && expected != null && fluid.isFluidEqual(expected);
            default:
                return false;
        }
    }

    /**
     * 从多输入配方的指定位置解码气体输入。
     *
     * @param recipe 暴露给 AE 的配方
     * @param stacks AE 实际交付的旧物品输入列表
     * @param index 要解码的输入下标
     * @param fallbackPredicate 没有 route 限定时用于判断气体类型是否有效的谓词
     * @return 解码出的气体栈，失败时返回 null
     */
    @Nullable
    public static GasStack getGasInput(AEExposedRecipe recipe, List<ItemStack> stacks, int index, Predicate<Gas> fallbackPredicate) {
        if (stacks == null || index < 0 || index >= stacks.size()) {
            return null;
        }
        Predicate<Gas> predicate = fallbackPredicate;
        AERecipeRouteStack routeStack = getInputRouteStack(recipe, index);
        if (routeStack != null && routeStack.kind() != AERecipeStackKind.GAS) {
            return null;
        }
        if (routeStack != null && routeStack.gasStack() != null) {
            Gas expected = routeStack.gasStack().getGas();
            predicate = gas -> gas == expected;
        }
        return getGasFromSource(stacks.get(index), predicate);
    }

    /**
     * 从单输入配方中解码气体输入。
     *
     * @param recipe 暴露给 AE 的配方
     * @param stack AE 实际交付的旧物品输入
     * @param fallbackPredicate 没有 route 限定时用于判断气体类型是否有效的谓词
     * @return 解码出的气体栈，失败时返回 null
     */
    @Nullable
    public static GasStack getGasInput(AEExposedRecipe recipe, ItemStack stack, Predicate<Gas> fallbackPredicate) {
        Predicate<Gas> predicate = fallbackPredicate;
        AERecipeRouteStack routeStack = getInputRouteStack(recipe, 0);
        if (routeStack != null && routeStack.kind() != AERecipeStackKind.GAS) {
            return null;
        }
        if (routeStack != null && routeStack.gasStack() != null) {
            Gas expected = routeStack.gasStack().getGas();
            predicate = gas -> gas == expected;
        }
        return getGasFromSource(stack, predicate);
    }

    /**
     * 从多输入配方的指定位置解码流体输入。
     *
     * @param recipe 暴露给 AE 的配方
     * @param stacks AE 实际交付的旧物品输入列表
     * @param index 要解码的输入下标
     * @return 解码出的流体栈，失败时返回 null
     */
    @Nullable
    public static FluidStack getFluidInput(AEExposedRecipe recipe, List<ItemStack> stacks, int index) {
        if (stacks == null || index < 0 || index >= stacks.size()) {
            return null;
        }
        return getFluidInput(recipe, stacks.get(index), index);
    }

    /**
     * 从单个旧物品输入中解码流体输入。
     *
     * @param recipe 暴露给 AE 的配方
     * @param stack AE 实际交付的旧物品输入
     * @param index 该输入在 route 中的下标
     * @return 解码出的流体栈，失败时返回 null
     */
    @Nullable
    public static FluidStack getFluidInput(AEExposedRecipe recipe, ItemStack stack, int index) {
        AERecipeRouteStack routeStack = getInputRouteStack(recipe, index);
        if (routeStack != null && routeStack.kind() != AERecipeStackKind.FLUID) {
            return null;
        }
        return AEUpgradeFakeFluid.unpackInput(stack);
    }

    /**
     * 从旧物品输入中解析气体来源。
     *
     * <p>优先解析 AE2FC fake gas；如果不是气体容器，则尝试 Mekanism 的物品转气体表。</p>
     *
     * @param stack AE 实际交付的旧物品输入
     * @param isValidGas 判断气体类型是否可用于当前机器的谓词
     * @return 解码出的气体栈，失败时返回 null
     */
    @Nullable
    public static GasStack getGasFromSource(ItemStack stack, Predicate<Gas> isValidGas) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        GasStack fakeGasInput = AEUpgradeFakeGas.unpackInput(stack);
        if (fakeGasInput != null && fakeGasInput.getGas() != null && isValidGas.test(fakeGasInput.getGas())) {
            return fakeGasInput.copy();
        }
        if (GasInventorySlot.isGasContainerItem(stack)) {
            return null;
        }
        GasStack perItem = GasConversionHandler.getConversionGas(stack, isValidGas);
        if (perItem == null || perItem.amount <= 0 || perItem.getGas() == null) {
            return null;
        }
        long amount = (long) perItem.amount * stack.getCount();
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        return new GasStack(perItem.getGas(), (int) amount);
    }

    /**
     * 仅检查输入列表中的 typed 状态是否和 route 一致。
     *
     * @param routeInputs route 输入栈列表
     * @param stacks AE 实际交付的旧物品输入列表
     * @return typed 状态全部匹配时返回 true
     */
    private static boolean matchesKinds(List<AERecipeRouteStack> routeInputs, List<ItemStack> stacks) {
        if (routeInputs.size() != stacks.size()) {
            return false;
        }
        for (int i = 0; i < routeInputs.size(); i++) {
            if (!matchesKind(routeInputs.get(i), stacks.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查单个旧物品输入是否能作为 route 栈对应的状态。
     *
     * @param routeStack route 中的 typed 输入栈
     * @param supplied AE 实际交付的旧物品输入
     * @return 状态匹配时返回 true
     */
    private static boolean matchesKind(AERecipeRouteStack routeStack, ItemStack supplied) {
        switch (routeStack.kind()) {
            case ITEM:
                return !supplied.isEmpty();
            case GAS:
                return getGasInputFromRoute(routeStack, supplied) != null;
            case FLUID:
                return AEUpgradeFakeFluid.unpackInput(supplied) != null;
            default:
                return false;
        }
    }

    /**
     * 按 route 限定的气体类型解码旧物品输入。
     *
     * @param routeStack route 中的气体输入栈
     * @param supplied AE 实际交付的旧物品输入
     * @return 解码出的气体栈，失败时返回 null
     */
    @Nullable
    private static GasStack getGasInputFromRoute(AERecipeRouteStack routeStack, ItemStack supplied) {
        GasStack expected = routeStack.gasStack();
        if (expected == null || expected.getGas() == null) {
            return null;
        }
        return getGasFromSource(supplied, gas -> gas == expected.getGas());
    }

    /**
     * 读取配方 route 中指定位置的输入栈。
     *
     * @param recipe 暴露给 AE 的配方
     * @param index 输入下标
     * @return route 输入栈，无法读取时返回 null
     */
    @Nullable
    private static AERecipeRouteStack getInputRouteStack(AEExposedRecipe recipe, int index) {
        if (recipe == null) {
            return null;
        }
        AERecipeRoute route = recipe.getRecipeRoute();
        if (route == null || index < 0 || index >= route.inputs().size()) {
            return null;
        }
        return route.inputs().get(index);
    }
}
