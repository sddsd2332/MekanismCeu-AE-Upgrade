package mekceuaeupgrade.common.integration.qio;

import ae2.api.AECapabilities;
import mekanism.api.qio.external.QIOCapabilities;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

final class SupergiantQIOCapabilityProvider implements ICapabilityProvider {

    private final TileEntityQIODashboard dashboard;
    private final Map<EnumFacing, SupergiantQIOStorageMonitor> monitors = new EnumMap<>(EnumFacing.class);
    private boolean closed;

    SupergiantQIOCapabilityProvider(TileEntityQIODashboard dashboard) {
        this.dashboard = dashboard;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (closed || capability != AECapabilities.ME_STORAGE || facing == null ||
              !dashboard.hasCapability(QIOCapabilities.STORAGE_ACCESSOR, facing)) {
            return false;
        }
        World world = dashboard.getWorld();
        if (world != null && !world.isRemote) {
            if (!isDashboardLoaded()) {
                return false;
            }
            QIOAEStorageBridge.activate(this);
        }
        return true;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (!hasCapability(capability, facing)) {
            return null;
        }
        var monitor = monitors.computeIfAbsent(facing, side -> new SupergiantQIOStorageMonitor(dashboard, side));
        monitor.refreshBinding();
        return AECapabilities.ME_STORAGE.cast(monitor);
    }

    void tick() {
        if (closed) {
            return;
        }
        if (!isDashboardLoaded()) {
            close();
            return;
        }
        for (var monitor : monitors.values()) {
            monitor.refreshBinding();
        }
    }

    boolean isForWorld(World world) {
        return dashboard.getWorld() == world;
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        QIOAEStorageBridge.deactivate(this);
        for (var monitor : monitors.values()) {
            monitor.close();
        }
        monitors.clear();
    }

    private boolean isDashboardLoaded() {
        World world = dashboard.getWorld();
        return world != null && !world.isRemote && !dashboard.isInvalid() &&
              world.isBlockLoaded(dashboard.getPos()) && world.getTileEntity(dashboard.getPos()) == dashboard;
    }
}
