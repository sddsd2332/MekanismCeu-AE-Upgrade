package mekceuaeupgrade.common.registries;

import mekanism.common.item.ItemUpgrade;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.item.AEUpgrade;
import mekceuaeupgrade.common.item.ItemAEWirelessUpgrade;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.registries.IForgeRegistry;

@ObjectHolder(MEKCeuAEUpgrade.MODID)
public class MEKCeuAEUpgradeItems {

    public static final Item AECraftingUpgrade = new ItemUpgrade(AEUpgrade.AE_CRAFTING).setCreativeTab(MEKCeuAEUpgrade.tabMEKCeuAEUpgrade);
    public static final Item AEOutputUpgrade = new ItemUpgrade(AEUpgrade.AE_OUTPUT).setCreativeTab(MEKCeuAEUpgrade.tabMEKCeuAEUpgrade);
    public static final Item AEAutoProcessingUpgrade = new ItemUpgrade(AEUpgrade.AE_AUTO_PROCESSING).setCreativeTab(MEKCeuAEUpgrade.tabMEKCeuAEUpgrade);
    public static final Item AEWirelessCraftingUpgrade = new ItemAEWirelessUpgrade(AEUpgrade.AE_WIRELESS_CRAFTING);
    public static final Item AEWirelessAutoProcessingUpgrade = new ItemAEWirelessUpgrade(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING);
    public static final Item AEWirelessOutputUpgrade = new ItemAEWirelessUpgrade(AEUpgrade.AE_WIRELESS_OUTPUT);

    public static void registerItems(IForgeRegistry<Item> registry) {
        registry.register(init(AECraftingUpgrade, "AECraftingUpgrade"));
        registry.register(init(AEOutputUpgrade, "AEOutputUpgrade"));
        registry.register(init(AEAutoProcessingUpgrade, "AEAutoProcessingUpgrade"));
        registry.register(init(AEWirelessCraftingUpgrade, "AEWirelessCraftingUpgrade"));
        registry.register(init(AEWirelessAutoProcessingUpgrade, "AEWirelessAutoProcessingUpgrade"));
        registry.register(init(AEWirelessOutputUpgrade, "AEWirelessOutputUpgrade"));
    }

    private static Item init(Item item, String name) {
        return item.setTranslationKey(name).setRegistryName(new ResourceLocation(MEKCeuAEUpgrade.MODID, name.toLowerCase()));
    }
}
