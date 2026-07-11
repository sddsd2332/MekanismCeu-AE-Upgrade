package mekceuaeupgrade.common.ui;

import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import net.minecraft.util.ResourceLocation;

public final class AEUpgradeWindowTypes {

    public static final WindowType AE_RECIPE_CONFIG = WindowType.register(new ResourceLocation(MEKCeuAEUpgrade.MODID, "ae_recipe_config"), "ae_recipe_config", true);
    public static final WindowType AE_AUTO_PROCESSING_RECIPE_CONFIG = WindowType.register(
          new ResourceLocation(MEKCeuAEUpgrade.MODID, "ae_auto_processing_recipe_config"), "ae_auto_processing_recipe_config", true);

    private AEUpgradeWindowTypes() {
    }

    public static void init() {
        MekanismConfig.local().client.registerWindowType(AE_RECIPE_CONFIG);
        MekanismConfig.local().client.registerWindowType(AE_AUTO_PROCESSING_RECIPE_CONFIG);
    }
}
