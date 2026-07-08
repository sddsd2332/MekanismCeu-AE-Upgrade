package mekceuaeupgrade.common.registries;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.common.item.ItemUpgrade;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.registries.IForgeRegistry;

@ObjectHolder(MEKCeuAEUpgrade.MODID)
public class MEKCeuAEUpgradeItems {

    public static final Item AECraftingUpgrade = new ItemUpgrade(AEUpgrade.AE_CRAFTING).setCreativeTab(MEKCeuAEUpgrade.tabMEKCeuAEUpgrade);

    public static void registerItems(IForgeRegistry<Item> registry) {
        registry.register(init(AECraftingUpgrade, "AECraftingUpgrade"));
    }

    private static Item init(Item item, String name) {
        return item.setTranslationKey(name).setRegistryName(new ResourceLocation(MEKCeuAEUpgrade.MODID, name.toLowerCase()));
    }
}
