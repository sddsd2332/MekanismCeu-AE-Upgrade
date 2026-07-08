package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import net.minecraft.nbt.NBTTagCompound;

public final class AEUpgradeHostDelegate {

    private final AEUpgradeNode node;

    public AEUpgradeHostDelegate(IAEUpgradeHost host) {
        node = new AEUpgradeNode(host);
    }

    public AEUpgradeNode getNode() {
        return node;
    }

    public void read(NBTTagCompound nbtTags) {
        node.read(nbtTags);
    }

    public void write(NBTTagCompound nbtTags) {
        node.write(nbtTags);
    }

    public void tickServer() {
        node.tickServer();
    }

    public void onLoad() {
        node.onLoad();
    }

    public void validate() {
        node.validate();
    }

    public void invalidate() {
        node.invalidate();
    }

    public void onChunkUnload() {
        node.onChunkUnload();
    }

    public void onNeighborChanged() {
        node.onNeighborChanged();
    }

    public void invalidateRecipeCache() {
        node.invalidateRecipeCache();
    }
}
