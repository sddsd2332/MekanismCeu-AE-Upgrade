package mekceuaeupgrade.common.integration.qio;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.capabilities.Capabilities;
import mekanism.api.qio.external.IQIOStorageAccessor;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOCapabilities;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekceuaeupgrade.common.integration.appeng.AEPlayerUUIDResolver;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class LegacyQIOCapabilityProvider implements ICapabilityProvider {

    private final TileEntityQIODashboard dashboard;
    private final Map<EnumFacing, FaceAccessor> accessors = new EnumMap<>(EnumFacing.class);
    private boolean closed;

    LegacyQIOCapabilityProvider(TileEntityQIODashboard dashboard) {
        this.dashboard = dashboard;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (closed || capability != Capabilities.STORAGE_MONITORABLE_ACCESSOR || facing == null ||
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
        FaceAccessor accessor = accessors.computeIfAbsent(facing, side -> new FaceAccessor(dashboard, side));
        return Capabilities.STORAGE_MONITORABLE_ACCESSOR.cast(accessor);
    }

    void tick() {
        if (closed) {
            return;
        }
        if (!isDashboardLoaded()) {
            close();
            return;
        }
        for (FaceAccessor accessor : accessors.values()) {
            accessor.tick();
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
        for (FaceAccessor accessor : accessors.values()) {
            accessor.close();
        }
        accessors.clear();
    }

    private boolean isDashboardLoaded() {
        World world = dashboard.getWorld();
        return world != null && !world.isRemote && !dashboard.isInvalid() &&
              world.isBlockLoaded(dashboard.getPos()) && world.getTileEntity(dashboard.getPos()) == dashboard;
    }

    private static final class FaceAccessor implements IStorageMonitorableAccessor {

        private final TileEntityQIODashboard dashboard;
        private final EnumFacing side;
        @Nullable
        private IActionSource source;
        @Nullable
        private LegacyQIOStorageMonitorable monitorable;
        private boolean closed;

        private FaceAccessor(TileEntityQIODashboard dashboard, EnumFacing side) {
            this.dashboard = dashboard;
            this.side = side;
        }

        @Nullable
        @Override
        public IStorageMonitorable getInventory(IActionSource source) {
            if (closed || source == null) {
                return null;
            }
            this.source = source;
            refreshBinding();
            return monitorable != null && monitorable.hasUsableView() ? monitorable : null;
        }

        private void tick() {
            if (!closed && source != null) {
                refreshBinding();
            }
        }

        private void refreshBinding() {
            IActionSource currentSource = source;
            if (currentSource == null || dashboard.isInvalid() || dashboard.getWorld() == null ||
                  dashboard.getWorld().isRemote) {
                unbind();
                return;
            }
            if (!isSourceStillAdjacent(currentSource)) {
                source = null;
                releaseMonitorable();
                return;
            }
            UUID requester = AEPlayerUUIDResolver.resolve(currentSource);
            IGrid grid = resolveGrid(currentSource);
            if (monitorable != null && monitorable.matchesBinding(requester, grid) && monitorable.hasUsableView()) {
                monitorable.setActionSource(currentSource);
                return;
            }
            IQIOStorageAccessor coreAccessor = dashboard.getCapability(QIOCapabilities.STORAGE_ACCESSOR, side);
            IQIOStorageView view = coreAccessor == null || grid == null ? null : coreAccessor.open(requester);
            if (view == null) {
                unbind();
                return;
            }
            if (monitorable == null) {
                monitorable = new LegacyQIOStorageMonitorable(dashboard, side, currentSource, requester, grid, view);
            } else {
                monitorable.rebind(currentSource, requester, grid, view);
            }
        }

        private void unbind() {
            if (monitorable != null) {
                monitorable.unbind();
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            source = null;
            releaseMonitorable();
        }

        private void releaseMonitorable() {
            if (monitorable != null) {
                monitorable.close();
                monitorable = null;
            }
        }

        @Nullable
        private static IGrid resolveGrid(IActionSource source) {
            Optional<IActionHost> machine = source.machine();
            if (!machine.isPresent()) {
                return null;
            }
            IGridNode node = machine.get().getActionableNode();
            return node == null ? null : node.getGrid();
        }

        private boolean isSourceStillAdjacent(IActionSource currentSource) {
            Optional<IActionHost> machine = currentSource.machine();
            if (!machine.isPresent() || !(machine.get() instanceof IPart)) {
                return false;
            }
            World world = dashboard.getWorld();
            BlockPos adjacent = dashboard.getPos().offset(side);
            if (world == null || !world.isBlockLoaded(adjacent)) {
                return false;
            }
            net.minecraft.tileentity.TileEntity adjacentTile = world.getTileEntity(adjacent);
            return adjacentTile instanceof IPartHost &&
                  ((IPartHost) adjacentTile).getPart(side.getOpposite()) == machine.get();
        }
    }
}
