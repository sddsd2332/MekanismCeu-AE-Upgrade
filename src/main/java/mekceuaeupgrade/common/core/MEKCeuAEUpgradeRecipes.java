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
            Optional<ItemStack> wirelessReceiver = materialsApi.wirelessReceiver().maybeStack(1);

            if (fluixCrystal.isPresent() && logicProcessor.isPresent()) {
                GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_crafting_upgrade"), null, new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade),
                      " F ",
                      "LAL",
                      " C ",
                      'F', fluixCrystal.get().copy(),
                      'L', logicProcessor.get().copy(),
                      'A', new ItemStack(MekanismItems.EnrichedAlloy),
                      'C', new ItemStack(MekanismItems.ControlCircuit, 1, 0));

                GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_output_upgrade"), null, new ItemStack(MEKCeuAEUpgradeItems.AEOutputUpgrade),
                      " C ",
                      "FAF",
                      " L ",
                      'F', fluixCrystal.get().copy(),
                      'L', logicProcessor.get().copy(),
                      'A', new ItemStack(MekanismItems.EnrichedAlloy),
                      'C', new ItemStack(MekanismItems.ControlCircuit, 1, 1));

                GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_auto_processing_upgrade"), null,
                      new ItemStack(MEKCeuAEUpgradeItems.AEAutoProcessingUpgrade),
                      " L ",
                      "CAC",
                      " F ",
                      'F', fluixCrystal.get().copy(),
                      'L', logicProcessor.get().copy(),
                      'A', new ItemStack(MekanismItems.EnrichedAlloy),
                      'C', new ItemStack(MekanismItems.ControlCircuit, 1, 3));

                if (wirelessReceiver.isPresent()) {
                    GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_wireless_crafting_upgrade"), null,
                          new ItemStack(MEKCeuAEUpgradeItems.AEWirelessCraftingUpgrade),
                          " W ",
                          "LAL",
                          " C ",
                          'W', wirelessReceiver.get().copy(),
                          'L', logicProcessor.get().copy(),
                          'A', new ItemStack(MekanismItems.EnrichedAlloy),
                          'C', new ItemStack(MekanismItems.ControlCircuit, 1, 0));

                    GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_wireless_auto_processing_upgrade"), null,
                          new ItemStack(MEKCeuAEUpgradeItems.AEWirelessAutoProcessingUpgrade),
                          " W ",
                          "CAC",
                          " L ",
                          'W', wirelessReceiver.get().copy(),
                          'L', logicProcessor.get().copy(),
                          'A', new ItemStack(MekanismItems.EnrichedAlloy),
                          'C', new ItemStack(MekanismItems.ControlCircuit, 1, 3));

                    GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_wireless_output_upgrade"), null,
                          new ItemStack(MEKCeuAEUpgradeItems.AEWirelessOutputUpgrade),
                          " W ",
                          "FAF",
                          " L ",
                          'W', wirelessReceiver.get().copy(),
                          'F', fluixCrystal.get().copy(),
                          'L', logicProcessor.get().copy(),
                          'A', new ItemStack(MekanismItems.EnrichedAlloy));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            MEKCeuAEUpgrade.logger.error("Failed to register AE Upgrade recipes", e);
        }
    }
}
