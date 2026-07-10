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
          .maxItemStackSize(64)
          .color(EnumColor.AQUA)
          .stack(count -> new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade, count))
          .onChanged((upgrade, tile, previousAmount, amount) -> {
              if (tile instanceof IAEUpgradeHost host) {
                  handleAEUpgradeChanged(host, upgrade, previousAmount, amount);
              }
          })
          .register();

    public static final Upgrade AE_OUTPUT = Upgrade.builder(MEKCeuAEUpgrade.MODID, "ae_output")
          .maxInstalled(1)
          .maxItemStackSize(64)
          .color(EnumColor.DARK_AQUA)
          .stack(count -> new ItemStack(MEKCeuAEUpgradeItems.AEOutputUpgrade, count))
          .conflictsWith(AE_CRAFTING)
          .onChanged((upgrade, tile, previousAmount, amount) -> {
              if (tile instanceof IAEUpgradeHost host) {
                  handleAEUpgradeChanged(host, upgrade, previousAmount, amount);
              }
          })
          .register();

    public static final Upgrade AE_AUTO_PROCESSING = Upgrade.builder(MEKCeuAEUpgrade.MODID, "ae_auto_processing")
          .maxInstalled(1)
          .maxItemStackSize(64)
          .color(EnumColor.PURPLE)
          .stack(count -> new ItemStack(MEKCeuAEUpgradeItems.AEAutoProcessingUpgrade, count))
          .conflictsWith(AE_CRAFTING, AE_OUTPUT)
          .onChanged((upgrade, tile, previousAmount, amount) -> {
              if (tile instanceof IAEUpgradeHost host) {
                  handleAEUpgradeChanged(host, upgrade, previousAmount, amount);
              }
          })
          .register();

    public static final Upgrade AE_WIRELESS_CRAFTING = Upgrade.builder(MEKCeuAEUpgrade.MODID, "ae_wireless_crafting")
          .maxInstalled(1)
          .maxItemStackSize(64)
          .color(EnumColor.BRIGHT_GREEN)
          .stack(count -> new ItemStack(MEKCeuAEUpgradeItems.AEWirelessCraftingUpgrade, count))
          .conflictsWith(AE_CRAFTING, AE_OUTPUT, AE_AUTO_PROCESSING)
          .onChanged((upgrade, tile, previousAmount, amount) -> {
              if (tile instanceof IAEUpgradeHost host) {
                  handleAEUpgradeChanged(host, upgrade, previousAmount, amount);
              }
          })
          .register();

    public static final Upgrade AE_WIRELESS_AUTO_PROCESSING = Upgrade.builder(MEKCeuAEUpgrade.MODID, "ae_wireless_auto_processing")
          .maxInstalled(1)
          .maxItemStackSize(64)
          .color(EnumColor.PINK)
          .stack(count -> new ItemStack(MEKCeuAEUpgradeItems.AEWirelessAutoProcessingUpgrade, count))
          .conflictsWith(AE_CRAFTING, AE_OUTPUT, AE_AUTO_PROCESSING, AE_WIRELESS_CRAFTING)
          .onChanged((upgrade, tile, previousAmount, amount) -> {
              if (tile instanceof IAEUpgradeHost host) {
                  handleAEUpgradeChanged(host, upgrade, previousAmount, amount);
              }
          })
          .register();

    public static final Upgrade AE_WIRELESS_OUTPUT = Upgrade.builder(MEKCeuAEUpgrade.MODID, "ae_wireless_output")
          .maxInstalled(1)
          .maxItemStackSize(64)
          .color(EnumColor.DARK_GREEN)
          .stack(count -> new ItemStack(MEKCeuAEUpgradeItems.AEWirelessOutputUpgrade, count))
          .conflictsWith(AE_CRAFTING, AE_OUTPUT, AE_AUTO_PROCESSING, AE_WIRELESS_CRAFTING, AE_WIRELESS_AUTO_PROCESSING)
          .onChanged((upgrade, tile, previousAmount, amount) -> {
              if (tile instanceof IAEUpgradeHost host) {
                  handleAEUpgradeChanged(host, upgrade, previousAmount, amount);
              }
          })
          .register();

    private AEUpgrade() {
    }

    private static void handleAEUpgradeChanged(IAEUpgradeHost host, mekanism.common.Upgrade upgrade, int previousAmount, int amount) {
        host.getAEUpgradeNode().onUpgradeConfigurationChanged();
        if (amount <= 0 && previousAmount > 0) {
            if (upgrade == AE_WIRELESS_CRAFTING) {
                host.getAEUpgradeNode().setWirelessCraftingKey(null);
            } else if (upgrade == AE_WIRELESS_AUTO_PROCESSING) {
                host.getAEUpgradeNode().setWirelessAutoProcessingKey(null);
            } else if (upgrade == AE_WIRELESS_OUTPUT) {
                host.getAEUpgradeNode().setWirelessOutputKey(null);
            }
        }
        if (amount > 0 || host.hasAEUpgrade()) {
            host.onAEUpgradeInstalled();
        } else {
            host.onAEUpgradeRemoved();
        }
    }

    public static void setRecipePriorityComparator(@Nullable Comparator<AEExposedRecipe> comparator) {
        AEUpgradeRecipeCache.setRecipePriorityComparator(comparator);
    }
}
