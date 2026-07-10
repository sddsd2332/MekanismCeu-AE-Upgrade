package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import net.minecraft.nbt.NBTTagCompound;

public interface IAEUpgradeHostBridge extends IAEUpgradeHost {

    AEUpgradeHostDelegate mekceuaeupgrade$getAEUpgradeDelegate();

    @Override
    default AEUpgradeNode getAEUpgradeNode() {
        return mekceuaeupgrade$getAEUpgradeDelegate().getNode();
    }

    default void mekceuaeupgrade$readAEUpgrade(NBTTagCompound nbtTags) {
        mekceuaeupgrade$getAEUpgradeDelegate().read(nbtTags);
    }

    default void mekceuaeupgrade$writeAEUpgrade(NBTTagCompound nbtTags) {
        mekceuaeupgrade$getAEUpgradeDelegate().write(nbtTags);
    }

    default void mekceuaeupgrade$tickAEUpgradeServer() {
        mekceuaeupgrade$getAEUpgradeDelegate().tickServer();
    }

    default void mekceuaeupgrade$onAEUpgradeLoad() {
        mekceuaeupgrade$getAEUpgradeDelegate().onLoad();
    }

    default void mekceuaeupgrade$validateAEUpgrade() {
        mekceuaeupgrade$getAEUpgradeDelegate().validate();
    }

    default void mekceuaeupgrade$invalidateAEUpgrade() {
        mekceuaeupgrade$getAEUpgradeDelegate().invalidate();
    }

    default void mekceuaeupgrade$onAEUpgradeChunkUnload() {
        mekceuaeupgrade$getAEUpgradeDelegate().onChunkUnload();
    }

    default void mekceuaeupgrade$onAENeighborChanged() {
        mekceuaeupgrade$getAEUpgradeDelegate().onNeighborChanged();
    }

    default void mekceuaeupgrade$invalidateAERecipeCache() {
        mekceuaeupgrade$getAEUpgradeDelegate().invalidateRecipeCache();
    }

    default void mekceuaeupgrade$onAERecipePortsChanged() {
        mekceuaeupgrade$getAEUpgradeDelegate().onRecipePortsChanged();
    }
}
