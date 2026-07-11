package mekceuaeupgrade.client;

import mekceuaeupgrade.common.core.CommonProxy;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import mekanism.client.render.MekanismRenderer;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.item.Item;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void registerItemRenders() {
        registerItemRender(MEKCeuAEUpgradeItems.AECraftingUpgrade);
        registerItemRender(MEKCeuAEUpgradeItems.AEOutputUpgrade);
        registerItemRender(MEKCeuAEUpgradeItems.AEAutoProcessingUpgrade);
        registerItemRender(MEKCeuAEUpgradeItems.AEWirelessCraftingUpgrade);
        registerItemRender(MEKCeuAEUpgradeItems.AEWirelessAutoProcessingUpgrade);
        registerItemRender(MEKCeuAEUpgradeItems.AEWirelessOutputUpgrade);
    }

    private void registerItemRender(Item item) {
        MekanismRenderer.registerItemRender(MEKCeuAEUpgrade.MODID, item);
    }
}
