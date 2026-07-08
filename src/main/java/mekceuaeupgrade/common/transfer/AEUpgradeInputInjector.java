package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * AE crafting CPU 向机器推送输入的统一入口。
 *
 * <p>该类负责从 AE 的 crafting table 中取出旧物品输入，交给 host 做匹配和接收。
 * 下面保留的 execute*Atomically 方法是早期 adapter 的兼容入口，内部已经统一委托 {@link AERecipeTransferPlan}。</p>
 */
public final class AEUpgradeInputInjector {

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeInputInjector() {
    }

    /**
     * 从 AE crafting table 读取输入并推送给机器 host。
     *
     * @param host 当前机器的 AE 配方 host
     * @param recipe AE 选中的暴露配方
     * @param table AE crafting CPU 传入的临时合成表
     * @return 输入被机器接受时返回 true
     */
    public static boolean push(IAEItemRecipeHost host, AEExposedRecipe recipe, InventoryCrafting table) {
        List<ItemStack> inputs = getInputs(table);
        if (inputs.isEmpty()) {
            AEUpgradeDebug.log(host, "push rejected: AE supplied no inputs for recipe inputs={} outputs={}",
                  AEUpgradeDebug.stacks(recipe.getInputStacks()), AEUpgradeDebug.stacks(recipe.getOutputStacks()));
            return false;
        }
        if (!recipe.matchesInputs(inputs)) {
            AEUpgradeDebug.log(host, "push rejected: AE supplied {} but recipe expects {}",
                  AEUpgradeDebug.stacks(inputs), AEUpgradeDebug.stacks(recipe.getInputStacks()));
            return false;
        }
        if (!host.canAcceptAEItemInputs(recipe, inputs)) {
            AEUpgradeDebug.log(host, "push rejected: machine cannot accept {}", AEUpgradeDebug.stacks(inputs));
            return false;
        }
        if (!host.acceptAEItemInputs(recipe, inputs)) {
            AEUpgradeDebug.log(host, "push rejected: machine rejected {} during execute", AEUpgradeDebug.stacks(inputs));
            return false;
        }
        return true;
    }

    /**
     * 提取 crafting table 中的非空输入。
     *
     * @param table AE crafting CPU 传入的临时合成表
     * @return 按槽位顺序复制出的输入列表
     */
    private static List<ItemStack> getInputs(InventoryCrafting table) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            ItemStack stack = table.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                inputs.add(stack.copy());
            }
        }
        return inputs;
    }

    /**
     * 兼容入口：原子写入多个物品槽位。
     *
     * @param slots 目标物品槽位列表
     * @param stacks 与槽位一一对应的物品输入列表
     * @return 全部写入成功时返回 true，失败会回滚
     */
    public static boolean executeItemInsertsAtomically(List<? extends IInventorySlot> slots, List<ItemStack> stacks) {
        if (slots.size() != stacks.size()) {
            return false;
        }
        AERecipeTransferPlan plan = AERecipeTransferPlan.create();
        for (int i = 0; i < slots.size(); i++) {
            String portId = "item_" + i;
            plan.add(AERecipePort.item(portId, slots.get(i)), AERecipeRouteStack.item(portId, stacks.get(i)));
        }
        return plan.execute();
    }

    /**
     * 兼容入口：原子写入多个气体储罐。
     *
     * @param tanks 目标气体储罐列表
     * @param stacks 与储罐一一对应的气体输入列表
     * @return 全部写入成功时返回 true，失败会回滚
     */
    public static boolean executeGasInsertsAtomically(List<? extends IExtendedGasTank> tanks, List<GasStack> stacks) {
        if (tanks.size() != stacks.size()) {
            return false;
        }
        AERecipeTransferPlan plan = AERecipeTransferPlan.create();
        for (int i = 0; i < tanks.size(); i++) {
            String portId = "gas_" + i;
            plan.add(AERecipePort.gas(portId, tanks.get(i)), AERecipeRouteStack.gas(portId, stacks.get(i)));
        }
        return plan.execute();
    }

    /**
     * 兼容入口：原子写入物品、流体和气体输入。
     *
     * @param itemSlot 目标物品槽位
     * @param itemStack 物品输入
     * @param fluidTank 目标流体储罐
     * @param fluidStack 流体输入
     * @param gasTank 目标气体储罐
     * @param gasStack 气体输入
     * @return 全部写入成功时返回 true，失败会回滚
     */
    public static boolean executeItemFluidGasInsertsAtomically(IInventorySlot itemSlot, ItemStack itemStack, IExtendedFluidTank fluidTank,
          FluidStack fluidStack, IExtendedGasTank gasTank, GasStack gasStack) {
        return AERecipeTransferPlan.create()
              .add(AERecipePort.item("item", itemSlot), AERecipeRouteStack.item("item", itemStack))
              .add(AERecipePort.fluid("fluid", fluidTank), AERecipeRouteStack.fluid("fluid", fluidStack))
              .add(AERecipePort.gas("gas", gasTank), AERecipeRouteStack.gas("gas", gasStack))
              .execute();
    }

    /**
     * 兼容入口：原子写入物品和气体输入。
     *
     * @param itemSlot 目标物品槽位
     * @param itemStack 物品输入
     * @param gasTank 目标气体储罐
     * @param gasStack 气体输入
     * @return 全部写入成功时返回 true，失败会回滚
     */
    public static boolean executeItemGasInsertsAtomically(IInventorySlot itemSlot, ItemStack itemStack, IExtendedGasTank gasTank, GasStack gasStack) {
        return AERecipeTransferPlan.create()
              .add(AERecipePort.item("item", itemSlot), AERecipeRouteStack.item("item", itemStack))
              .add(AERecipePort.gas("gas", gasTank), AERecipeRouteStack.gas("gas", gasStack))
              .execute();
    }

    /**
     * 兼容入口：原子写入气体和流体输入。
     *
     * @param gasTank 目标气体储罐
     * @param gasStack 气体输入
     * @param fluidTank 目标流体储罐
     * @param fluidStack 流体输入
     * @return 全部写入成功时返回 true，失败会回滚
     */
    public static boolean executeGasFluidInsertsAtomically(IExtendedGasTank gasTank, GasStack gasStack, IExtendedFluidTank fluidTank,
          FluidStack fluidStack) {
        return AERecipeTransferPlan.create()
              .add(AERecipePort.gas("gas", gasTank), AERecipeRouteStack.gas("gas", gasStack))
              .add(AERecipePort.fluid("fluid", fluidTank), AERecipeRouteStack.fluid("fluid", fluidStack))
              .execute();
    }

}
