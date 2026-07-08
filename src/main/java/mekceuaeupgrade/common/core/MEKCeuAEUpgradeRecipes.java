package mekceuaeupgrade.common.core;

import appeng.api.AEApi;
import appeng.api.definitions.IMaterials;
import mekanism.common.MekanismItems;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Optional;

public final class MEKCeuAEUpgradeRecipes {

    private MEKCeuAEUpgradeRecipes() {
    }

    public static void addRecipes() {
        try {
            IMaterials materialsApi = AEApi.instance().definitions().materials();

            Optional<ItemStack> fluixCrystal = materialsApi.fluixCrystal().maybeStack(1);
            Optional<ItemStack> logicProcessor = materialsApi.logicProcessor().maybeStack(1);

            if (fluixCrystal.isPresent() && logicProcessor.isPresent()) {
                GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_crafting_upgrade"), null, new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade),
                      " F ",
                      "LAL",
                      " C ",
                      'F', fluixCrystal.get().copy(),
                      'L', logicProcessor.get().copy(),
                      'A', new ItemStack(MekanismItems.EnrichedAlloy),
                      'C', new ItemStack(MekanismItems.ControlCircuit, 1, 0));
            }
        } catch (RuntimeException | LinkageError e) {
            MEKCeuAEUpgrade.logger.error("Failed to register AE Crafting Upgrade recipe", e);
        }
    }
}
