package mekceuaeupgrade.common.item;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.INetworkEncodable;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;
import mekanism.api.EnumColor;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.item.ItemUpgrade;
import mekanism.common.util.LangUtils;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemAEWirelessUpgrade extends ItemUpgrade implements INetworkEncodable {

    public static final String ENCRYPTION_KEY_TAG = "encryptionKey";
    private static final String NAME_TAG = "name";

    public ItemAEWirelessUpgrade(Upgrade type) {
        super(type);
        setCreativeTab(MEKCeuAEUpgrade.tabMEKCeuAEUpgrade);
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        NBTTagCompound tag = Platform.openNbtData(item);
        return tag.getString(ENCRYPTION_KEY_TAG);
    }

    private static String readEncryptionKey(ItemStack item) {
        NBTTagCompound tag = item.getTagCompound();
        return tag == null ? "" : tag.getString(ENCRYPTION_KEY_TAG);
    }

    private static boolean clearEncryptionKey(ItemStack item) {
        NBTTagCompound tag = item.getTagCompound();
        if (tag == null || tag.getString(ENCRYPTION_KEY_TAG).trim().isEmpty()) {
            return false;
        }
        tag.removeTag(ENCRYPTION_KEY_TAG);
        tag.removeTag(NAME_TAG);
        if (tag.isEmpty()) {
            item.setTagCompound(null);
        }
        return true;
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        NBTTagCompound tag = Platform.openNbtData(item);
        tag.setString(ENCRYPTION_KEY_TAG, encKey == null ? "" : encKey);
        tag.setString(NAME_TAG, name == null ? "" : name);
    }

    @Override
    public boolean canInstallUpgrade(ItemStack stack, IUpgradeTile tile) {
        return !readEncryptionKey(stack).trim().isEmpty();
    }

    @Nullable
    @Override
    public ITextComponent getInstallFailureMessage(ItemStack stack, IUpgradeTile tile) {
        return canInstallUpgrade(stack, tile) ? null :
              new TextComponentString(EnumColor.DARK_RED + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_install_unlinked"));
    }

    @Override
    public void onInstalled(ItemStack stack, IUpgradeTile tile, int amount) {
        if (amount <= 0 || !(tile instanceof IAEUpgradeHost host)) {
            return;
        }
        setHostWirelessKey(host, getUpgradeType(stack), readEncryptionKey(stack));
    }

    @Override
    public ItemStack getUninstalledStack(IUpgradeTile tile, Upgrade upgrade, int amount, ItemStack defaultStack) {
        if (defaultStack.isEmpty() || !(tile instanceof IAEUpgradeHost host)) {
            return defaultStack;
        }
        String key = getHostWirelessKey(host, upgrade);
        if (key != null && !key.trim().isEmpty()) {
            setEncryptionKey(defaultStack, key, "");
        }
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
            if (clearEncryptionKey(stack)) {
                player.sendMessage(new TextComponentString(EnumColor.GREY + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_cleared")));
                if (player instanceof EntityPlayerMP serverPlayer) {
                    serverPlayer.sendContainerToPlayer(player.openContainer);
                }
            } else {
                player.sendMessage(new TextComponentString(EnumColor.GREY + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_already_unlinked")));
            }
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        String key = readEncryptionKey(stack);
        if (key == null || key.trim().isEmpty()) {
            tooltip.add(EnumColor.DARK_RED + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_unlinked"));
            return;
        }
        DimensionalCoord location = getLinkedLocation(key);
        if (location == null) {
            tooltip.add(EnumColor.YELLOW + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_linked"));
            tooltip.add(EnumColor.GREY + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_location_unavailable"));
        } else {
            tooltip.add(EnumColor.DARK_GREEN + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_linked"));
            tooltip.add(EnumColor.GREY + LangUtils.localize("tooltip.mekceuaeupgrade.wireless_location") + ": " + EnumColor.AQUA + location);
        }
    }

    @Nullable
    private DimensionalCoord getLinkedLocation(String key) {
        try {
            long parsedKey = Long.parseLong(key);
            ILocatable locatable = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);
            if (locatable instanceof TileEntity tile) {
                return new DimensionalCoord(tile);
            }
            if (locatable instanceof IGridProxyable proxyable) {
                return proxyable.getLocation();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return null;
    }

    private static void setHostWirelessKey(IAEUpgradeHost host, Upgrade upgrade, String key) {
        if (upgrade == AEUpgrade.AE_WIRELESS_CRAFTING) {
            host.getAEUpgradeNode().setWirelessCraftingKey(key);
        } else if (upgrade == AEUpgrade.AE_WIRELESS_AUTO_PROCESSING) {
            host.getAEUpgradeNode().setWirelessAutoProcessingKey(key);
        } else if (upgrade == AEUpgrade.AE_WIRELESS_OUTPUT) {
            host.getAEUpgradeNode().setWirelessOutputKey(key);
        }
    }

    @Nullable
    private static String getHostWirelessKey(IAEUpgradeHost host, Upgrade upgrade) {
        if (upgrade == AEUpgrade.AE_WIRELESS_CRAFTING) {
            return host.getAEUpgradeNode().getWirelessCraftingKey();
        }
        if (upgrade == AEUpgrade.AE_WIRELESS_AUTO_PROCESSING) {
            return host.getAEUpgradeNode().getWirelessAutoProcessingKey();
        }
        if (upgrade == AEUpgrade.AE_WIRELESS_OUTPUT) {
            return host.getAEUpgradeNode().getWirelessOutputKey();
        }
        return null;
    }
}
