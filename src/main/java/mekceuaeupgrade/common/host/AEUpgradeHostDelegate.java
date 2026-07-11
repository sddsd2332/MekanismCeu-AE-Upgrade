package mekceuaeupgrade.common.host;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public final class AEUpgradeHostDelegate {

    private final IAEUpgradeHost host;
    private final Object machineAccessMonitor = new Object();
    private final AEUpgradeNode node;
    private long lastServerTick = Long.MIN_VALUE;

    public AEUpgradeHostDelegate(IAEUpgradeHost host) {
        this.host = host;
        node = new AEUpgradeNode(host, machineAccessMonitor);
    }

    public AEUpgradeNode getNode() {
        return node;
    }

    public Object getMachineAccessMonitor() {
        return machineAccessMonitor;
    }

    public void read(NBTTagCompound nbtTags) {
        node.read(nbtTags);
    }

    public void write(NBTTagCompound nbtTags) {
        node.write(nbtTags);
    }

    public void tickServer() {
        if (host instanceof TileEntity tile && tile.getWorld() != null) {
            long worldTick = tile.getWorld().getTotalWorldTime();
            if (lastServerTick == worldTick) {
                return;
            }
            lastServerTick = worldTick;
        }
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

    public void onRecipePortsChanged() {
        node.onRecipePortsChanged();
    }
}
