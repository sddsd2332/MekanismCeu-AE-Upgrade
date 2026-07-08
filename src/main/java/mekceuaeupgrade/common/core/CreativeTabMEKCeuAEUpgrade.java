package mekceuaeupgrade.common.core;

import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class CreativeTabMEKCeuAEUpgrade extends CreativeTabs {

    public CreativeTabMEKCeuAEUpgrade() {
        super("tabmekceuaeupgrade");
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade);
    }
}
