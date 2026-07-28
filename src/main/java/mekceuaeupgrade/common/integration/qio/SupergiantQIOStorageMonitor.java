package mekceuaeupgrade.common.integration.qio;

import ae2.api.config.Actionable;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.parts.IPartHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.parts.storagebus.StorageBusPart;
import mekanism.api.Action;
import mekanism.api.qio.external.IQIOStorageAccessor;
import mekanism.api.qio.external.IQIOStorageListener;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOCapabilities;
import mekanism.api.qio.external.QIOStorageChange;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import me.ramidzkh.mekae2.ae2.AEGasKey;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class SupergiantQIOStorageMonitor implements MEStorageMonitor, IQIOStorageListener {

    private final TileEntityQIODashboard dashboard;
    private final EnumFacing side;
    private final Map<MEStorageChangeListener, Object> listeners = new IdentityHashMap<>();
    private final Map<UUID, Long> reportedAmounts = new LinkedHashMap<>();
    private final Map<UUID, QIOStorageEntry> reportedEntries = new HashMap<>();
    @Nullable
    private IGrid grid;
    @Nullable
    private UUID requester;
    @Nullable
    private IQIOStorageView view;
    private boolean reportedInitialized;
    private boolean mountActive;
    private boolean closed;

    SupergiantQIOStorageMonitor(TileEntityQIODashboard dashboard, EnumFacing side) {
        this.dashboard = dashboard;
        this.side = side;
    }

    void refreshBinding() {
        if (closed || dashboard.isInvalid() || dashboard.getWorld() == null || dashboard.getWorld().isRemote ||
              !dashboard.hasCapability(QIOCapabilities.STORAGE_ACCESSOR, side)) {
            unbind();
            return;
        }
        IGridNode node = findAdjacentNode();
        IGrid newGrid = getGrid(node);
        UUID newRequester = node == null ? null : node.getOwningPlayerProfileId();
        if (newGrid != null && newGrid == grid && Objects.equals(newRequester, requester) && hasUsableView()) {
            return;
        }
        IQIOStorageAccessor accessor = dashboard.getCapability(QIOCapabilities.STORAGE_ACCESSOR, side);
        IQIOStorageView newView = accessor == null || newGrid == null ? null : accessor.open(newRequester);
        changeBinding(newGrid, newRequester, newView);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0 || !mountActive || !hasUsableView()) {
            return 0;
        }
        Action action = toMekanismAction(mode);
        if (what instanceof AEItemKey itemKey) {
            return view.insert(itemKey.toStack(1), amount, action);
        }
        if (what instanceof AEFluidKey fluidKey) {
            return view.insert(fluidKey.toStack(1), amount, action);
        }
        if (what instanceof AEGasKey gasKey) {
            return view.insert(gasKey.toStack(1), amount, action);
        }
        return 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0 || !mountActive || !hasUsableView()) {
            return 0;
        }
        Action action = toMekanismAction(mode);
        if (what instanceof AEItemKey itemKey) {
            return view.extract(itemKey.toStack(1), amount, action);
        }
        if (what instanceof AEFluidKey fluidKey) {
            return view.extract(fluidKey.toStack(1), amount, action);
        }
        if (what instanceof AEGasKey gasKey) {
            return view.extract(gasKey.toStack(1), amount, action);
        }
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        Objects.requireNonNull(out, "out");
        Map<UUID, QIOStorageEntry> entries = getVisibleEntries();
        for (QIOStorageEntry entry : entries.values()) {
            AEKey key = toAEKey(entry);
            if (key != null && entry.getAmountClamped() > 0) {
                out.add(key, entry.getAmountClamped());
            }
        }
        if (!listeners.isEmpty()) {
            rememberReportedState(entries);
        }
    }

    @Override
    public ITextComponent getDescription() {
        String frequency = hasUsableView() ? view.getFrequencyName() : "";
        return new TextComponentTranslation("gui.mekceuaeupgrade.qio_storage", frequency);
    }

    @Override
    public void addListener(MEStorageChangeListener listener, Object verificationToken) {
        Objects.requireNonNull(listener, "listener");
        if (listeners.containsKey(listener)) {
            throw new IllegalStateException("The QIO storage listener is already registered.");
        }
        listeners.put(listener, verificationToken);
        if (listeners.size() == 1 && hasUsableView()) {
            SupergiantQIOMountRegistry.register(this);
        }
    }

    @Override
    public void removeListener(MEStorageChangeListener listener) {
        if (listener == null || !listeners.containsKey(listener)) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            SupergiantQIOMountRegistry.unregister(this);
            clearReportedState();
        }
    }

    @Override
    public void onQIOStorageChanged(QIOStorageChangeBatch changes) {
        if (closed || listeners.isEmpty() || !reportedInitialized) {
            return;
        }
        if (changes.isInvalidated() || changes.isFullRescanRequired() || !mountActive || !hasUsableView()) {
            reconcileCurrentState();
            return;
        }
        for (QIOStorageChange change : changes.getChanges()) {
            QIOStorageEntry entry = change.getResource();
            UUID resource = entry.getResourceUUID();
            long oldReported = reportedAmounts.getOrDefault(resource, 0L);
            long newAmount = change.getNewAmountClamped();
            long delta = newAmount - oldReported;
            if (delta != 0) {
                AEKey key = toAEKey(entry);
                if (key == null) {
                    clearReportedState();
                    requestListUpdate();
                    return;
                }
                dispatchDelta(key, delta);
                if (listeners.isEmpty()) {
                    return;
                }
            }
            updateReportedResource(resource, entry, newAmount);
        }
    }

    void reconcileCurrentState() {
        if (closed || listeners.isEmpty() || !reportedInitialized) {
            return;
        }
        Map<UUID, QIOStorageEntry> replacement = getVisibleEntries();
        Set<UUID> resources = new LinkedHashSet<>(reportedAmounts.keySet());
        resources.addAll(replacement.keySet());
        for (UUID resource : resources) {
            long oldAmount = reportedAmounts.getOrDefault(resource, 0L);
            QIOStorageEntry newEntry = replacement.get(resource);
            long newAmount = newEntry == null ? 0 : newEntry.getAmountClamped();
            long delta = newAmount - oldAmount;
            if (delta == 0) {
                continue;
            }
            QIOStorageEntry template = newEntry == null ? reportedEntries.get(resource) : newEntry;
            AEKey key = template == null ? null : toAEKey(template);
            if (key == null) {
                clearReportedState();
                requestListUpdate();
                return;
            }
            dispatchDelta(key, delta);
            if (listeners.isEmpty()) {
                return;
            }
        }
        if (!listeners.isEmpty()) {
            rememberReportedState(replacement);
        }
    }

    boolean setMountActive(boolean active) {
        if (mountActive == active) {
            return false;
        }
        mountActive = active;
        return true;
    }

    boolean isMountActive() {
        return mountActive;
    }

    @Nullable
    IGrid getGrid() {
        return grid;
    }

    @Nullable
    UUID getFrequencyUUID() {
        return hasUsableView() ? view.getFrequencyUUID() : null;
    }

    int getDimension() {
        return dashboard.getWorld() == null ? 0 : dashboard.getWorld().provider.getDimension();
    }

    int getX() {
        return dashboard.getPos().getX();
    }

    int getY() {
        return dashboard.getPos().getY();
    }

    int getZ() {
        return dashboard.getPos().getZ();
    }

    int getSideOrdinal() {
        return side.ordinal();
    }

    void close() {
        if (closed) {
            return;
        }
        SupergiantQIOMountRegistry.unregister(this);
        mountActive = false;
        reconcileCurrentState();
        closed = true;
        releaseView();
        listeners.clear();
        clearReportedState();
    }

    private void changeBinding(@Nullable IGrid newGrid, @Nullable UUID newRequester,
          @Nullable IQIOStorageView newView) {
        SupergiantQIOMountRegistry.unregister(this);
        releaseView();
        this.grid = newGrid;
        this.requester = newRequester;
        if (newView != null && newView.addListener(this)) {
            this.view = newView;
        } else if (newView != null) {
            newView.close();
        }
        if (!listeners.isEmpty() && hasUsableView()) {
            SupergiantQIOMountRegistry.register(this);
        } else {
            mountActive = false;
        }
        SupergiantQIOMountRegistry.requestReconcile(this);
    }

    private void unbind() {
        if (view == null && grid == null) {
            return;
        }
        changeBinding(null, null, null);
    }

    private void releaseView() {
        IQIOStorageView oldView = view;
        view = null;
        if (oldView != null) {
            oldView.removeListener(this);
            oldView.close();
        }
    }

    private boolean hasUsableView() {
        return !closed && view != null && view.isValid();
    }

    @Nullable
    private IGridNode findAdjacentNode() {
        BlockPos adjacent = dashboard.getPos().offset(side);
        if (!dashboard.getWorld().isBlockLoaded(adjacent)) {
            return null;
        }
        var adjacentTile = dashboard.getWorld().getTileEntity(adjacent);
        if (!(adjacentTile instanceof IPartHost host)) {
            return null;
        }
        var part = host.getPart(side.getOpposite());
        return part instanceof StorageBusPart ? part.getGridNode() : null;
    }

    @Nullable
    private static IGrid getGrid(@Nullable IGridNode node) {
        if (node == null) {
            return null;
        }
        try {
            return node.grid();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private Map<UUID, QIOStorageEntry> getVisibleEntries() {
        if (!mountActive || !hasUsableView()) {
            return java.util.Collections.emptyMap();
        }
        QIOStorageSnapshot snapshot;
        try {
            snapshot = view.getSnapshot();
        } catch (IllegalStateException ignored) {
            return java.util.Collections.emptyMap();
        }
        Map<UUID, QIOStorageEntry> result = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            if (entry.getAmountClamped() > 0 && toAEKey(entry) != null) {
                result.put(entry.getResourceUUID(), entry);
            }
        }
        return result;
    }

    @Nullable
    private static AEKey toAEKey(QIOStorageEntry entry) {
        return switch (entry.getKind()) {
            case ITEM -> AEItemKey.of(entry.getItem());
            case FLUID -> AEFluidKey.of(entry.getFluid());
            case GAS -> AEGasKey.of(entry.getGas());
        };
    }

    private void rememberReportedState(Map<UUID, QIOStorageEntry> entries) {
        reportedAmounts.clear();
        reportedEntries.clear();
        for (Map.Entry<UUID, QIOStorageEntry> entry : entries.entrySet()) {
            long amount = entry.getValue().getAmountClamped();
            if (amount > 0) {
                reportedAmounts.put(entry.getKey(), amount);
                reportedEntries.put(entry.getKey(), entry.getValue());
            }
        }
        reportedInitialized = true;
    }

    private void updateReportedResource(UUID resource, QIOStorageEntry entry, long amount) {
        if (amount <= 0) {
            reportedAmounts.remove(resource);
            reportedEntries.remove(resource);
        } else {
            reportedAmounts.put(resource, amount);
            reportedEntries.put(resource, entry);
        }
    }

    private void clearReportedState() {
        reportedInitialized = false;
        reportedAmounts.clear();
        reportedEntries.clear();
    }

    private void dispatchDelta(AEKey key, long delta) {
        if (delta == 0) {
            return;
        }
        for (Map.Entry<MEStorageChangeListener, Object> registration : new ArrayList<>(listeners.entrySet())) {
            MEStorageChangeListener listener = registration.getKey();
            if (!listeners.containsKey(listener) || listeners.get(listener) != registration.getValue()) {
                continue;
            }
            if (!listener.isValid(registration.getValue())) {
                listeners.remove(listener);
                continue;
            }
            try {
                listener.onStackChange(key, delta);
            } catch (RuntimeException e) {
                listeners.remove(listener);
                MEKCeuAEUpgrade.logger.error("Removing a failing AE listener from a QIO storage monitor", e);
            }
        }
        unregisterWhenUnused();
    }

    private void requestListUpdate() {
        for (Map.Entry<MEStorageChangeListener, Object> registration : new ArrayList<>(listeners.entrySet())) {
            MEStorageChangeListener listener = registration.getKey();
            if (!listener.isValid(registration.getValue())) {
                listeners.remove(listener);
                continue;
            }
            try {
                listener.onListUpdate();
            } catch (RuntimeException e) {
                listeners.remove(listener);
                MEKCeuAEUpgrade.logger.error("Removing a failing AE listener from a QIO storage monitor", e);
            }
        }
        unregisterWhenUnused();
    }

    private void unregisterWhenUnused() {
        if (listeners.isEmpty()) {
            SupergiantQIOMountRegistry.unregister(this);
            clearReportedState();
        }
    }

    private static Action toMekanismAction(Actionable actionable) {
        return actionable == Actionable.MODULATE ? Action.EXECUTE : Action.SIMULATE;
    }
}
