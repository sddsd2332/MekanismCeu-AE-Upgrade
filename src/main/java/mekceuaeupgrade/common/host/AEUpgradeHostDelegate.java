package mekceuaeupgrade.common.host;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.base.IUpgradeTileComponent;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.Upgrade;
import mekceuaeupgrade.common.item.AEUpgrade;

public final class AEUpgradeHostDelegate implements IUpgradeTileComponent {

    private final IAEUpgradeHost host;
    private final Object machineAccessMonitor = new Object();
    private final AEUpgradeNode node;
    private long lastServerTick = Long.MIN_VALUE;
    private net.minecraft.world.World lastServerWorld;
    private volatile boolean enabled;
    private int removedWirelessKeys;
    private int installedWirelessKeys;

    @Override
    public int getInstalledCount(TileComponentUpgrade upgrades) {
        return upgrades.getUpgrades(AEUpgrade.AE_CRAFTING) + upgrades.getUpgrades(AEUpgrade.AE_OUTPUT)
              + upgrades.getUpgrades(AEUpgrade.AE_AUTO_PROCESSING) + upgrades.getUpgrades(AEUpgrade.AE_WIRELESS_CRAFTING)
              + upgrades.getUpgrades(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING) + upgrades.getUpgrades(AEUpgrade.AE_WIRELESS_OUTPUT);
    }

    @Override public boolean isEnabled() { return enabled; }

    @Override
    public void onUpgradesChanged(TileComponentUpgrade upgrades) {
        enabled = getInstalledCount(upgrades) > 0;
        installedWirelessKeys = (upgrades.getUpgrades(AEUpgrade.AE_WIRELESS_CRAFTING) > 0 ? 1 : 0)
              | (upgrades.getUpgrades(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING) > 0 ? 2 : 0)
              | (upgrades.getUpgrades(AEUpgrade.AE_WIRELESS_OUTPUT) > 0 ? 4 : 0);
    }

    /** Upgrade-specific metadata is committed here; network work waits for the component phase. */
    public void onUpgradeCommitted(Upgrade upgrade, int previous, int amount) {
        int bit = upgrade == AEUpgrade.AE_WIRELESS_CRAFTING ? 1
              : upgrade == AEUpgrade.AE_WIRELESS_AUTO_PROCESSING ? 2
              : upgrade == AEUpgrade.AE_WIRELESS_OUTPUT ? 4 : 0;
        if (previous > 0 && amount == 0) removedWirelessKeys |= bit;
        // A subsequent install supplies a new key through the item's onInstalled callback.
        if (amount > 0) removedWirelessKeys &= ~bit;
    }

    @Override
    public void onServerStateChanged() {
        // Also handles save/unload between physical removal and deferred lifecycle delivery.
        removedWirelessKeys |= ~installedWirelessKeys & 7;
        if ((removedWirelessKeys & 1) != 0) node.setWirelessCraftingKey(null);
        if ((removedWirelessKeys & 2) != 0) node.setWirelessAutoProcessingKey(null);
        if ((removedWirelessKeys & 4) != 0) node.setWirelessOutputKey(null);
        removedWirelessKeys = 0;
        node.onUpgradeConfigurationChanged();
        if (enabled) node.onLoad();
        else node.deactivate();
    }

    @Override
    public void onServerReady() {
        lastServerWorld = null;
        lastServerTick = Long.MIN_VALUE;
    }

    @Override public boolean needsRecoveryTick() { return node.getPendingTransferBuffer().hasOwnedResources(); }
    @Override public void tickRecovery() { node.retryDisabledTransfers(); }
    @Override public void onContentsChanged() { if (enabled) node.invalidateBusyCache("component_contents"); }
    @Override public void read(ByteBuf data) {}
    @Override public void write(TileNetworkList data) {}

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
        removedWirelessKeys = 0;
        lastServerWorld = null;
        lastServerTick = Long.MIN_VALUE;
        node.read(nbtTags);
    }

    public void write(NBTTagCompound nbtTags) {
        node.write(nbtTags);
    }

    public void tickServer() {
        if (host instanceof TileEntity tile && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid()) {
            long worldTick = tile.getWorld().getTotalWorldTime();
            if (lastServerWorld == tile.getWorld() && lastServerTick == worldTick) {
                return;
            }
            lastServerWorld = tile.getWorld();
            lastServerTick = worldTick;
            node.tickServer();
        }
    }

    public void invalidate() {
        lastServerWorld = null;
        lastServerTick = Long.MIN_VALUE;
        node.invalidate();
    }

    public void onChunkUnload() {
        lastServerWorld = null;
        lastServerTick = Long.MIN_VALUE;
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

    public void invalidateBusyCache() {
        node.invalidateBusyCache();
    }

    public void invalidateBusyCache(String reason) {
        node.invalidateBusyCache(reason);
    }
}
