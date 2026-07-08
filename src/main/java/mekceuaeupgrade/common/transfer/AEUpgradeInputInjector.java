package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * AE crafting CPU 向机器推送输入的统一入口。
 *
 * <p>该类负责把 AE 交付的物品输入和 generic wrapper 输入交给 host 做匹配和接收。</p>
 */
public final class AEUpgradeInputInjector {

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeInputInjector() {
    }

    /**
     * 推送已经从 AE key 请求转换出的输入列表。
     *
     * @param host 当前机器的 AE 配方 host
     * @param recipe AE 选中的暴露配方
     * @param inputs AE crafting CPU 请求的输入，流体和气体会以 Supergiant generic wrapper 物品承载
     * @return 输入被机器接受时返回 true
     */
    public static boolean push(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> inputs) {
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

}
