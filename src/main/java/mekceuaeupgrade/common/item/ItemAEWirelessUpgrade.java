package mekceuaeupgrade.common.item;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEWirelessLink;
import mekceuaeupgrade.common.host.IAEUpgradeHost;

import ae2.api.features.GridLinkables;
import ae2.api.features.IGridLinkableHandler;
import mekanism.api.EnumColor;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.item.ItemUpgrade;
import mekanism.common.util.LangUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemAEWirelessUpgrade extends ItemUpgrade {

    private static final String LINK_TAG = "aeWirelessLink";

    private static final IGridLinkableHandler LINKABLE_HANDLER = new IGridLinkableHandler() {
        @Override
        public boolean canLink(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ItemAEWirelessUpgrade;
        }

        @Override
        public void link(ItemStack stack, World world, BlockPos pos) {
            writeLink(stack, new AEWirelessLink(world.provider.getDimension(), pos));
        }

        @Override
        public void unlink(ItemStack stack) {
            writeLink(stack, null);
        }
    };

    public ItemAEWirelessUpgrade(Upgrade type) {
        super(type);
        setCreativeTab(MEKCeuAEUpgrade.tabMEKCeuAEUpgrade);
        GridLinkables.register(this, LINKABLE_HANDLER);
    }

    @Override
    public boolean canInstallUpgrade(ItemStack stack, IUpgradeTile tile) {
        return readLink(stack) != null;
    }

    @Nullable
    @Override
    public ITextComponent getInstallFailureMessage(ItemStack stack, IUpgradeTile tile) {
        return canInstallUpgrade(stack, tile) ? null : new TextComponentString(
              EnumColor.DARK_RED + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_install_unlinked"));
    }

    @Override
    public void onInstalled(ItemStack stack, IUpgradeTile tile, int amount) {
        if (amount <= 0 || !(tile instanceof IAEUpgradeHost host)) {
            return;
        }
        setHostLink(host, getUpgradeType(stack), readLink(stack));
    }

    @Override
    public ItemStack getUninstalledStack(IUpgradeTile tile, Upgrade upgrade, int amount, ItemStack defaultStack) {
        if (defaultStack.isEmpty() || !(tile instanceof IAEUpgradeHost host)) {
            return defaultStack;
        }
        writeLink(defaultStack, getHostLink(host, upgrade));
        return defaultStack;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.isSneaking()) {
            return ActionResult.newResult(EnumActionResult.PASS, stack);
        }
        if (!world.isRemote) {
            if (readLink(stack) != null) {
                writeLink(stack, null);
                player.sendMessage(new TextComponentString(
                      EnumColor.GREY + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_cleared")));
                if (player instanceof EntityPlayerMP serverPlayer) {
                    serverPlayer.sendContainerToPlayer(player.openContainer);
                }
            } else {
                player.sendMessage(new TextComponentString(
                      EnumColor.GREY + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_already_unlinked")));
            }
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        AEWirelessLink link = readLink(stack);
        if (link == null) {
            tooltip.add(EnumColor.DARK_RED + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_unlinked"));
            return;
        }
        tooltip.add(EnumColor.DARK_GREEN + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_linked"));
        tooltip.add(EnumColor.GREY + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_location") + ": "
              + EnumColor.AQUA + link.formatLocation());
    }

    @Nullable
    private static AEWirelessLink readLink(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? null : AEWirelessLink.read(tag, LINK_TAG);
    }

    private static void writeLink(ItemStack stack, @Nullable AEWirelessLink link) {
        NBTTagCompound tag = stack.getTagCompound();
        if (link == null) {
            if (tag == null) {
                return;
            }
            tag.removeTag(LINK_TAG);
            if (tag.isEmpty()) {
                stack.setTagCompound(null);
            }
            return;
        }
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        link.write(tag, LINK_TAG);
    }

    private static void setHostLink(IAEUpgradeHost host, Upgrade upgrade, @Nullable AEWirelessLink link) {
        if (upgrade == AEUpgrade.AE_WIRELESS_CRAFTING) {
            host.getAEUpgradeNode().setWirelessCraftingLink(link);
        } else if (upgrade == AEUpgrade.AE_WIRELESS_AUTO_PROCESSING) {
            host.getAEUpgradeNode().setWirelessAutoProcessingLink(link);
        } else if (upgrade == AEUpgrade.AE_WIRELESS_OUTPUT) {
            host.getAEUpgradeNode().setWirelessOutputLink(link);
        }
    }

    @Nullable
    private static AEWirelessLink getHostLink(IAEUpgradeHost host, Upgrade upgrade) {
        if (upgrade == AEUpgrade.AE_WIRELESS_CRAFTING) {
            return host.getAEUpgradeNode().getWirelessCraftingLink();
        }
        if (upgrade == AEUpgrade.AE_WIRELESS_AUTO_PROCESSING) {
            return host.getAEUpgradeNode().getWirelessAutoProcessingLink();
        }
        if (upgrade == AEUpgrade.AE_WIRELESS_OUTPUT) {
            return host.getAEUpgradeNode().getWirelessOutputLink();
        }
        return null;
    }
}
