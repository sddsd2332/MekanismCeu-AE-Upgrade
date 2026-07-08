package mekceuaeupgrade.common.item;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;

import mekanism.api.EnumColor;
import mekanism.common.Upgrade;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Comparator;

public final class AEUpgrade {

    public static final Upgrade AE_CRAFTING = Upgrade.builder(MEKCeuAEUpgrade.MODID, "ae_crafting")
          .maxInstalled(1)
          .maxItemStackSize(1)
          .color(EnumColor.AQUA)
          .stack(count -> new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade, count))
          .onChanged((upgrade, tile, previousAmount, amount) -> {
              if (tile instanceof IAEUpgradeHost host) {
                  if (amount > 0) {
                      host.onAEUpgradeInstalled();
                  } else {
                      host.onAEUpgradeRemoved();
                  }
              }
          })
          .register();

    private AEUpgrade() {
    }

    public static void setRecipePriorityComparator(@Nullable Comparator<AEExposedRecipe> comparator) {
        AEUpgradeRecipeCache.setRecipePriorityComparator(comparator);
    }
}
