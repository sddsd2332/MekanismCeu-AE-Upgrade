package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.item.AEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.KeyCounter;
import ae2.api.util.AECableType;
import mekanism.common.base.IUpgradeTile;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IAEUpgradeHost extends IInWorldGridNodeHost, IActionHost, ICraftingProvider {

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
    @Nullable
    default IGridNode getGridNode(@Nonnull EnumFacing dir) {
        return getAEUpgradeNode().getGridNode(dir);
    }

    @Override
    @Nonnull
    default AECableType getCableConnectionType(@Nonnull EnumFacing dir) {
        return getAEUpgradeNode().getCableConnectionType(dir);
    }

    default void securityBreak() {
        if (this instanceof TileEntity tile && tile.getWorld() != null) {
            tile.getWorld().destroyBlock(tile.getPos(), true);
        }
    }

    @Override
    @Nullable
    default IGridNode getActionableNode() {
        return getAEUpgradeNode().getGridNode();
    }

    @Override
    default List<AEExposedRecipe> getAvailablePatterns() {
        return getAEUpgradeNode().getAvailablePatterns();
    }

    @Override
    default boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        return getAEUpgradeNode().pushPattern(patternDetails, inputHolder, multiplier);
    }

    @Override
    default boolean canMergePatternPush(IPatternDetails patternDetails) {
        return false;
    }

    @Override
    default int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int maxMultiplier) {
        return 1;
    }

    @Override
    default boolean isBusy() {
        return getAEUpgradeNode().isBusy();
    }
}
