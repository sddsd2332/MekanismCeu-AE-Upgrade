package mekceuaeupgrade.common.core;

import ae2.core.definitions.AEItems;
import mekanism.common.MekanismItems;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;

public final class MEKCeuAEUpgradeRecipes {

    private MEKCeuAEUpgradeRecipes() {
    }

    public static void addRecipes() {
        try {
            GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_crafting_upgrade"), null, new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade),
                  " F ",
                  "LAL",
                  " C ",
                  'F', AEItems.FLUIX_CRYSTAL.stack(1),
                  'L', AEItems.LOGIC_PROCESSOR.stack(1),
                  'A', new ItemStack(MekanismItems.EnrichedAlloy),
                  'C', new ItemStack(MekanismItems.ControlCircuit, 1, 0));

            GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_output_upgrade"), null,
                  new ItemStack(MEKCeuAEUpgradeItems.AEOutputUpgrade),
                  " C ",
                  "FAF",
                  " L ",
                  'F', AEItems.FLUIX_CRYSTAL.stack(1),
                  'L', AEItems.LOGIC_PROCESSOR.stack(1),
                  'A', new ItemStack(MekanismItems.EnrichedAlloy),
                  'C', new ItemStack(MekanismItems.ControlCircuit, 1, 1));

            GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_auto_processing_upgrade"), null,
                  new ItemStack(MEKCeuAEUpgradeItems.AEAutoProcessingUpgrade),
                  " L ",
                  "CAC",
                  " F ",
                  'F', AEItems.FLUIX_CRYSTAL.stack(1),
                  'L', AEItems.LOGIC_PROCESSOR.stack(1),
                  'A', new ItemStack(MekanismItems.EnrichedAlloy),
                  'C', new ItemStack(MekanismItems.ControlCircuit, 1, 3));

            GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_wireless_crafting_upgrade"), null,
                  new ItemStack(MEKCeuAEUpgradeItems.AEWirelessCraftingUpgrade),
                  " W ",
                  "LAL",
                  " C ",
                  'W', AEItems.WIRELESS_RECEIVER.stack(1),
                  'L', AEItems.LOGIC_PROCESSOR.stack(1),
                  'A', new ItemStack(MekanismItems.EnrichedAlloy),
                  'C', new ItemStack(MekanismItems.ControlCircuit, 1, 0));

            GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_wireless_auto_processing_upgrade"), null,
                  new ItemStack(MEKCeuAEUpgradeItems.AEWirelessAutoProcessingUpgrade),
                  " W ",
                  "CAC",
                  " L ",
                  'W', AEItems.WIRELESS_RECEIVER.stack(1),
                  'L', AEItems.LOGIC_PROCESSOR.stack(1),
                  'A', new ItemStack(MekanismItems.EnrichedAlloy),
                  'C', new ItemStack(MekanismItems.ControlCircuit, 1, 3));

            GameRegistry.addShapedRecipe(MEKCeuAEUpgrade.rl("ae_wireless_output_upgrade"), null,
                  new ItemStack(MEKCeuAEUpgradeItems.AEWirelessOutputUpgrade),
                  " W ",
                  "FAF",
                  " L ",
                  'W', AEItems.WIRELESS_RECEIVER.stack(1),
                  'F', AEItems.FLUIX_CRYSTAL.stack(1),
                  'L', AEItems.LOGIC_PROCESSOR.stack(1),
                  'A', new ItemStack(MekanismItems.EnrichedAlloy));
        } catch (RuntimeException | LinkageError e) {
            MEKCeuAEUpgrade.logger.error("Failed to register AE Upgrade recipes", e);
        }
    }
}
