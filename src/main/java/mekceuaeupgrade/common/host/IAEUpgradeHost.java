package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.item.AEUpgrade;

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
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IAEUpgradeHost extends IGridProxyable, IActionHost, ICraftingProvider {

    AEUpgradeNode getAEUpgradeNode();

    default boolean supportsAEUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_CRAFTING);
    }

    default boolean hasAEUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_CRAFTING);
    }

    default boolean shouldExposeAE() {
        if (!(this instanceof TileEntity tile)) {
            return false;
        }
        return supportsAEUpgrade() && hasAEUpgrade() && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid();
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
        return getAEUpgradeNode().getProxy().getNode();
    }

    @Override
    default boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        return getAEUpgradeNode().pushPattern(patternDetails, table);
    }

    @Override
    default boolean isBusy() {
        return getAEUpgradeNode().isBusy();
    }

    @Override
    default void provideCrafting(ICraftingProviderHelper craftingTracker) {
        getAEUpgradeNode().provideCrafting(craftingTracker);
    }
}
