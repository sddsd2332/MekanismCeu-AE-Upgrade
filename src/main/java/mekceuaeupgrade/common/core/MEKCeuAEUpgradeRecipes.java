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
        } catch (RuntimeException | LinkageError e) {
            MEKCeuAEUpgrade.logger.error("Failed to register AE Crafting Upgrade recipe", e);
        }
    }
}
