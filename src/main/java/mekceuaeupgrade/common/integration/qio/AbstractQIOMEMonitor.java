package mekceuaeupgrade.common.integration.qio;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import mekanism.api.Action;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOStorageChange;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageResourceKind;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

abstract class AbstractQIOMEMonitor<T extends IAEStack<T>> implements IMEMonitor<T> {

    private final LegacyQIOStorageMonitorable owner;
    private final IStorageChannel<T> channel;
    private final QIOStorageResourceKind kind;
    private final Map<IMEMonitorHandlerReceiver<T>, Object> listeners = new IdentityHashMap<>();
    private final Map<UUID, Long> reportedAmounts = new LinkedHashMap<>();
    private final Map<UUID, QIOStorageEntry> reportedEntries = new HashMap<>();
    private boolean reportedInitialized;
    private boolean mountActive;
    private boolean closed;

    AbstractQIOMEMonitor(LegacyQIOStorageMonitorable owner, IStorageChannel<T> channel,
          QIOStorageResourceKind kind) {
        this.owner = owner;
        this.channel = channel;
        this.kind = kind;
    }

    LegacyQIOStorageMonitorable getOwner() {
        return owner;
    }

    @Override
    public IStorageChannel<T> getChannel() {
        return channel;
    }

    @Override
    public T injectItems(T input, Actionable mode, IActionSource source) {
        if (input == null || input.getStackSize() <= 0 || !canAccept(input)) {
            return input;
        }
        IQIOStorageView view = owner.getView();
        if (view == null) {
            return input;
        }
        long requested = input.getStackSize();
        long inserted = insert(view, input, requested, toMekanismAction(mode));
        if (inserted <= 0) {
            return input;
        }
        if (inserted >= requested) {
            return null;
        }
        T remainder = input.copy();
        remainder.setStackSize(requested - inserted);
        return remainder;
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource source) {
        if (request == null || request.getStackSize() <= 0 || !mountActive || !owner.hasUsableView()) {
            return null;
        }
        IQIOStorageView view = owner.getView();
        if (view == null) {
            return null;
        }
        long extracted = extract(view, request, request.getStackSize(), toMekanismAction(mode));
        if (extracted <= 0) {
            return null;
        }
        T result = request.copy();
        result.setStackSize(extracted);
        return result;
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        Map<UUID, QIOStorageEntry> entries = getVisibleEntries();
        for (QIOStorageEntry entry : entries.values()) {
            T stack = toAEStack(entry, entry.getAmountClamped());
            if (stack != null && stack.getStackSize() > 0) {
                out.addStorage(stack);
            }
        }
        if (!listeners.isEmpty()) {
            rememberReportedState(entries);
        }
        return out;
    }

    @Override
    public IItemList<T> getStorageList() {
        return getAvailableItems(channel.createList());
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<T> listener, Object verificationToken) {
        if (closed || listener == null) {
            return;
        }
        boolean wasEmpty = listeners.isEmpty();
        listeners.put(listener, verificationToken);
        if (wasEmpty) {
            onBindingChanged();
        }
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<T> listener) {
        if (listener == null || !listeners.containsKey(listener)) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            LegacyQIOMountRegistry.unregister(this);
            clearReportedState();
        }
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public boolean canAccept(T input) {
        return input != null && input.getStackSize() > 0 && mountActive && owner.hasUsableView() && isValidInput(input);
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return true;
    }

    void onStorageChanged(QIOStorageChangeBatch changes) {
        if (closed || listeners.isEmpty() || !reportedInitialized) {
            return;
        }
        if (changes.isInvalidated() || changes.isFullRescanRequired() || !mountActive || !owner.hasUsableView()) {
            reconcileCurrentState();
            return;
        }
        List<T> deltas = new ArrayList<>();
        for (QIOStorageChange change : changes.getChanges()) {
            QIOStorageEntry entry = change.getResource();
            if (entry.getKind() != kind) {
                continue;
            }
            UUID resource = entry.getResourceUUID();
            long oldReported = reportedAmounts.getOrDefault(resource, 0L);
            long newAmount = change.getNewAmountClamped();
            long delta = newAmount - oldReported;
            if (delta != 0) {
                T stack = toAEStack(entry, delta);
                if (stack == null) {
                    reconcileCurrentState();
                    return;
                }
                deltas.add(stack);
            }
            updateReportedResource(resource, entry, newAmount);
        }
        dispatchChanges(deltas);
    }

    void onBindingChanging() {
        LegacyQIOMountRegistry.unregister(this);
    }

    void onBindingChanged() {
        if (!closed && !listeners.isEmpty() && owner.hasUsableView()) {
            LegacyQIOMountRegistry.register(this);
        } else {
            setMountActive(false);
            LegacyQIOMountRegistry.requestReconcile(this);
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

    void reconcileCurrentState() {
        if (closed || !reportedInitialized || listeners.isEmpty()) {
            return;
        }
        Map<UUID, QIOStorageEntry> replacement = getVisibleEntries();
        List<T> deltas = new ArrayList<>();
        java.util.Set<UUID> resources = new java.util.LinkedHashSet<>(reportedAmounts.keySet());
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
            T stack = template == null ? null : toAEStack(template, delta);
            if (stack != null) {
                deltas.add(stack);
            } else {
                notifyListUpdate();
                rememberReportedState(replacement);
                return;
            }
        }
        rememberReportedState(replacement);
        dispatchChanges(deltas);
    }

    void close() {
        if (closed) {
            return;
        }
        LegacyQIOMountRegistry.unregister(this);
        closed = true;
        listeners.clear();
        clearReportedState();
    }

    protected abstract boolean isValidInput(T stack);

    @Nullable
    protected abstract T toAEStack(QIOStorageEntry entry, long amount);

    protected abstract long insert(IQIOStorageView view, T stack, long amount, Action action);

    protected abstract long extract(IQIOStorageView view, T stack, long amount, Action action);

    private Map<UUID, QIOStorageEntry> getVisibleEntries() {
        if (!mountActive || !owner.hasUsableView()) {
            return Collections.emptyMap();
        }
        QIOStorageSnapshot snapshot = owner.getSnapshot();
        if (snapshot == null) {
            return Collections.emptyMap();
        }
        Map<UUID, QIOStorageEntry> result = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            if (entry.getKind() == kind && entry.getAmountClamped() > 0) {
                result.put(entry.getResourceUUID(), entry);
            }
        }
        return result;
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

    private void dispatchChanges(List<T> changes) {
        if (changes.isEmpty()) {
            return;
        }
        for (Map.Entry<IMEMonitorHandlerReceiver<T>, Object> registration :
              new ArrayList<>(listeners.entrySet())) {
            IMEMonitorHandlerReceiver<T> receiver = registration.getKey();
            if (listeners.get(receiver) != registration.getValue()) {
                continue;
            }
            if (!receiver.isValid(registration.getValue())) {
                listeners.remove(receiver);
                continue;
            }
            List<T> copies = new ArrayList<>(changes.size());
            for (T change : changes) {
                copies.add(change.copy());
            }
            try {
                receiver.postChange(this, copies, owner.getActionSource());
            } catch (RuntimeException e) {
                listeners.remove(receiver);
                MEKCeuAEUpgrade.logger.error("Removing a failing AE listener from a QIO storage monitor", e);
            }
        }
        if (listeners.isEmpty()) {
            LegacyQIOMountRegistry.unregister(this);
            clearReportedState();
        }
    }

    private void notifyListUpdate() {
        for (Map.Entry<IMEMonitorHandlerReceiver<T>, Object> registration :
              new ArrayList<>(listeners.entrySet())) {
            IMEMonitorHandlerReceiver<T> receiver = registration.getKey();
            if (!receiver.isValid(registration.getValue())) {
                listeners.remove(receiver);
                continue;
            }
            try {
                receiver.onListUpdate();
            } catch (RuntimeException e) {
                listeners.remove(receiver);
                MEKCeuAEUpgrade.logger.error("Removing a failing AE listener from a QIO storage monitor", e);
            }
        }
        if (listeners.isEmpty()) {
            LegacyQIOMountRegistry.unregister(this);
            clearReportedState();
        }
    }

    private static Action toMekanismAction(Actionable actionable) {
        return actionable == Actionable.MODULATE ? Action.EXECUTE : Action.SIMULATE;
    }
}
