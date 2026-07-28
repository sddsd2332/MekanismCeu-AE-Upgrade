package mekceuaeupgrade.common.integration.qio;

import appeng.api.networking.IGrid;
import appeng.api.storage.IStorageChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class LegacyQIOMountRegistry {

    private static final Map<IGrid, Map<MountKey, List<AbstractQIOMEMonitor<?>>>> MOUNTS = new IdentityHashMap<>();
    private static final QIOMountReconciliationQueue<AbstractQIOMEMonitor<?>> PENDING =
          new QIOMountReconciliationQueue<>();
    private static final Comparator<AbstractQIOMEMonitor<?>> LOCATION_ORDER = Comparator
          .comparingInt((AbstractQIOMEMonitor<?> monitor) -> monitor.getOwner().getDimension())
          .thenComparingInt(monitor -> monitor.getOwner().getX())
          .thenComparingInt(monitor -> monitor.getOwner().getY())
          .thenComparingInt(monitor -> monitor.getOwner().getZ())
          .thenComparingInt(monitor -> monitor.getOwner().getSideOrdinal())
          .thenComparingInt(System::identityHashCode);

    private LegacyQIOMountRegistry() {
    }

    static void register(AbstractQIOMEMonitor<?> monitor) {
        UUID frequency = monitor.getOwner().getFrequencyUUID();
        IGrid grid = monitor.getOwner().getGrid();
        if (frequency == null || grid == null) {
            monitor.setMountActive(false);
            requestReconcile(monitor);
            return;
        }
        MountKey key = new MountKey(frequency, monitor.getChannel());
        List<AbstractQIOMEMonitor<?>> candidates = MOUNTS
              .computeIfAbsent(grid, ignored -> new java.util.HashMap<>())
              .computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!candidates.contains(monitor)) {
            candidates.add(monitor);
        }
        elect(candidates);
        requestReconcile(monitor);
    }

    static void unregister(AbstractQIOMEMonitor<?> monitor) {
        for (java.util.Iterator<Map.Entry<IGrid, Map<MountKey, List<AbstractQIOMEMonitor<?>>>>> gridIt =
              MOUNTS.entrySet().iterator(); gridIt.hasNext();) {
            Map<MountKey, List<AbstractQIOMEMonitor<?>>> byKey = gridIt.next().getValue();
            for (java.util.Iterator<Map.Entry<MountKey, List<AbstractQIOMEMonitor<?>>>> keyIt =
                  byKey.entrySet().iterator(); keyIt.hasNext();) {
                List<AbstractQIOMEMonitor<?>> candidates = keyIt.next().getValue();
                if (candidates.remove(monitor)) {
                    elect(candidates);
                }
                if (candidates.isEmpty()) {
                    keyIt.remove();
                }
            }
            if (byKey.isEmpty()) {
                gridIt.remove();
            }
        }
        monitor.setMountActive(false);
        requestReconcile(monitor);
    }

    static void requestReconcile(AbstractQIOMEMonitor<?> monitor) {
        PENDING.request(monitor, !monitor.isMountActive());
    }

    static void flushPendingChanges() {
        PENDING.flush(AbstractQIOMEMonitor::reconcileCurrentState);
    }

    private static void elect(List<AbstractQIOMEMonitor<?>> candidates) {
        candidates.sort(LOCATION_ORDER);
        for (int i = 0; i < candidates.size(); i++) {
            AbstractQIOMEMonitor<?> candidate = candidates.get(i);
            if (candidate.setMountActive(i == 0)) {
                requestReconcile(candidate);
            }
        }
    }

    private static final class MountKey {

        private final UUID frequency;
        private final IStorageChannel<?> channel;

        private MountKey(UUID frequency, IStorageChannel<?> channel) {
            this.frequency = frequency;
            this.channel = channel;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MountKey key)) {
                return false;
            }
            return frequency.equals(key.frequency) && channel == key.channel;
        }

        @Override
        public int hashCode() {
            return 31 * frequency.hashCode() + System.identityHashCode(channel);
        }
    }
}
