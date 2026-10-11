package mekceuaeupgrade.common.item;

import mekanism.api.EnumColor;
import mekanism.common.Upgrade;
import mekanism.common.recipe.cache.IRecipeLookupHandler;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.host.IAEOutputHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Comparator;

public final class AEUpgrade {

    public static final Upgrade AE_CRAFTING = Upgrade.builder(MEKCeuAEUpgrade.MODID, "ae_crafting")
          .deterministicSecondaryUsage()
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
          .deterministicSecondaryUsage()
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
          .deterministicSecondaryUsage()
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
          .deterministicSecondaryUsage()
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
          .deterministicSecondaryUsage()
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
          .deterministicSecondaryUsage()
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

    private static final boolean CLASS_SUPPORT_API = findClassSupportApi();

    public static boolean hasClassSupportApi() { return CLASS_SUPPORT_API; }

    private static boolean findClassSupportApi() {
        try {
            ExternalUpgradeSupportRegistry.class.getMethod("registerClassSupport", net.minecraft.util.ResourceLocation.class,
                  java.util.function.Predicate.class, mekanism.common.Upgrade[].class);
            ExternalUpgradeSupportRegistry.class.getMethod("isSupportStable", mekanism.common.Upgrade.class);
            return true;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return false;
        }
    }

    private static void registerTypeSupport(net.minecraft.util.ResourceLocation id,
          java.util.function.Predicate<Class<?>> predicate, mekanism.common.Upgrade... upgrades) {
        if (CLASS_SUPPORT_API) ExternalUpgradeSupportRegistry.registerClassSupport(id, predicate, upgrades);
        else ExternalUpgradeSupportRegistry.register(id, tile -> predicate.test(tile.getClass()), upgrades);
    }

    public static void registerExternalSupport() {
        registerTypeSupport(MEKCeuAEUpgrade.rl("crafting_upgrade_support"),
              type -> IRecipeLookupHandler.class.isAssignableFrom(type) && IAEUpgradeHost.class.isAssignableFrom(type),
              AE_CRAFTING, AE_WIRELESS_CRAFTING);
        registerTypeSupport(MEKCeuAEUpgrade.rl("auto_processing_upgrade_support"),
              type -> IAEItemRecipeHost.class.isAssignableFrom(type),
              AE_AUTO_PROCESSING, AE_WIRELESS_AUTO_PROCESSING);
        registerTypeSupport(MEKCeuAEUpgrade.rl("output_upgrade_support"),
              type -> !(IRecipeLookupHandler.class.isAssignableFrom(type) && IAEUpgradeHost.class.isAssignableFrom(type)) &&
                      !IAEItemRecipeHost.class.isAssignableFrom(type) && IAEOutputHost.class.isAssignableFrom(type),
              AE_OUTPUT, AE_WIRELESS_OUTPUT);
    }

    private static void handleAEUpgradeChanged(IAEUpgradeHost host, mekanism.common.Upgrade upgrade, int previousAmount, int amount) {
        if (host instanceof mekceuaeupgrade.common.host.IAEUpgradeHostBridge bridge) {
            bridge.mekceuaeupgrade$getAEUpgradeDelegate().onUpgradeCommitted(upgrade, previousAmount, amount);
        }
    }

    public static void setRecipePriorityComparator(@Nullable Comparator<AEExposedRecipe> comparator) {
        AEUpgradeRecipeCache.setRecipePriorityComparator(comparator);
    }
}
