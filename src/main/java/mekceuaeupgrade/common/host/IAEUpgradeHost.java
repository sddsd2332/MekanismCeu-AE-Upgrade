package mekceuaeupgrade.common.host;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import mekanism.common.base.IUpgradeTile;
import mekceuaeupgrade.common.item.AEUpgrade;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IAEUpgradeHost extends IGridProxyable, IActionHost, ICraftingProvider {

    AEUpgradeNode getAEUpgradeNode();

    default boolean supportsAEUpgrade() {
        return supportsAECraftingUpgrade() || supportsAEOutputUpgrade() || supportsAEAutoProcessingUpgrade();
    }

    default boolean hasAEUpgrade() {
        return hasAECraftingUpgrade() || hasAEOutputUpgrade() || hasAEAutoProcessingUpgrade();
    }

    default boolean supportsAECraftingUpgrade() {
        return supportsAEWiredCraftingUpgrade() || supportsAEWirelessCraftingUpgrade();
    }

    default boolean hasAECraftingUpgrade() {
        return hasAEWiredCraftingUpgrade() || hasAEWirelessCraftingUpgrade();
    }

    default boolean supportsAEWiredCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_CRAFTING);
    }

    default boolean hasAEWiredCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_CRAFTING);
    }

    default boolean supportsAEWirelessCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_WIRELESS_CRAFTING);
    }

    default boolean hasAEWirelessCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_WIRELESS_CRAFTING);
    }

    default boolean supportsAEOutputUpgrade() {
        return supportsAEWiredOutputUpgrade() || supportsAEWirelessOutputUpgrade();
    }

    default boolean hasAEOutputUpgrade() {
        return hasAEWiredOutputUpgrade() || hasAEWirelessOutputUpgrade();
    }

    default boolean supportsAEWiredOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_OUTPUT);
    }

    default boolean hasAEWiredOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_OUTPUT);
    }

    default boolean supportsAEWirelessOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_WIRELESS_OUTPUT);
    }

    default boolean hasAEWirelessOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_WIRELESS_OUTPUT);
    }

    default boolean supportsAEAutoProcessingUpgrade() {
        return supportsAEWiredAutoProcessingUpgrade() || supportsAEWirelessAutoProcessingUpgrade();
    }

    default boolean hasAEAutoProcessingUpgrade() {
        return hasAEWiredAutoProcessingUpgrade() || hasAEWirelessAutoProcessingUpgrade();
    }

    default boolean supportsAEWiredAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_AUTO_PROCESSING);
    }

    default boolean hasAEWiredAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_AUTO_PROCESSING);
    }

    default boolean supportsAEWirelessAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING);
    }

    default boolean hasAEWirelessAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING);
    }

    default boolean shouldExposeAE() {
        if (!(this instanceof TileEntity tile)) {
            return false;
        }
        return supportsAEUpgrade() && hasAEUpgrade() && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid();
    }

    default boolean shouldExposeAECrafting() {
        return shouldExposeAE() && supportsAECraftingUpgrade() && hasAECraftingUpgrade();
    }

    default boolean shouldExposeAEWiredCrafting() {
        return shouldExposeAE() && supportsAEWiredCraftingUpgrade() && hasAEWiredCraftingUpgrade();
    }

    default boolean shouldExposeAEWirelessCrafting() {
        return shouldExposeAE() && supportsAEWirelessCraftingUpgrade() && hasAEWirelessCraftingUpgrade();
    }

    default boolean shouldExposeAEOutput() {
        return shouldExposeAE() && supportsAEOutputUpgrade() && hasAEOutputUpgrade();
    }

    default boolean shouldExposeAEWiredOutput() {
        return shouldExposeAE() && supportsAEWiredOutputUpgrade() && hasAEWiredOutputUpgrade();
    }

    default boolean shouldExposeAEWirelessOutput() {
        return shouldExposeAE() && supportsAEWirelessOutputUpgrade() && hasAEWirelessOutputUpgrade();
    }

    default boolean shouldExposeAEAutoProcessing() {
        return shouldExposeAE() && supportsAEAutoProcessingUpgrade() && hasAEAutoProcessingUpgrade();
    }

    default boolean shouldExposeAEWiredAutoProcessing() {
        return shouldExposeAE() && supportsAEWiredAutoProcessingUpgrade() && hasAEWiredAutoProcessingUpgrade();
    }

    default boolean shouldExposeAEWirelessAutoProcessing() {
        return shouldExposeAE() && supportsAEWirelessAutoProcessingUpgrade() && hasAEWirelessAutoProcessingUpgrade();
    }

    default void onAEUpgradeInstalled() {
        getAEUpgradeNode().activate();
    }

    default void onAEUpgradeRemoved() {
        getAEUpgradeNode().deactivate();
    }

    @Override
    default AENetworkProxy getProxy() {
        return getAEUpgradeNode().getProxy();
    }

    @Override
    @Nullable
    default IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        return getAEUpgradeNode().getGridNode(dir);
    }

    @Override
    @Nonnull
    default AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return getAEUpgradeNode().getCableConnectionType(dir);
    }

    @Override
    default DimensionalCoord getLocation() {
        return new DimensionalCoord((TileEntity) this);
    }

    @Override
    default void gridChanged() {
        getAEUpgradeNode().onGridChanged();
    }

    @Override
    default void securityBreak() {
        if (this instanceof TileEntity tile && tile.getWorld() != null) {
            tile.getWorld().destroyBlock(tile.getPos(), true);
        }
    }

    @Override
    @Nonnull
    default IGridNode getActionableNode() {
        IGridNode node = getAEUpgradeNode().getActionableNode();
        return node == null ? getAEUpgradeNode().getProxy().getNode() : node;
    }

    @Override
    default boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        return shouldExposeAECrafting() && getAEUpgradeNode().pushPattern(patternDetails, table);
    }

    @Override
    default boolean isBusy() {
        return shouldExposeAECrafting() ? getAEUpgradeNode().isBusy() : true;
    }

    @Override
    default void provideCrafting(ICraftingProviderHelper craftingTracker) {
        if (shouldExposeAECrafting()) {
            getAEUpgradeNode().provideCrafting(craftingTracker);
        }
    }
}
