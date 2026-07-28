package mekceuaeupgrade.common.integration.qio;

import ae2.api.networking.IGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SupergiantQIOMountRegistry {

    private static final Map<IGrid, Map<UUID, List<SupergiantQIOStorageMonitor>>> MOUNTS = new IdentityHashMap<>();
    private static final QIOMountReconciliationQueue<SupergiantQIOStorageMonitor> PENDING =
          new QIOMountReconciliationQueue<>();
    private static final Comparator<SupergiantQIOStorageMonitor> LOCATION_ORDER = Comparator
          .comparingInt(SupergiantQIOStorageMonitor::getDimension)
          .thenComparingInt(SupergiantQIOStorageMonitor::getX)
          .thenComparingInt(SupergiantQIOStorageMonitor::getY)
          .thenComparingInt(SupergiantQIOStorageMonitor::getZ)
          .thenComparingInt(SupergiantQIOStorageMonitor::getSideOrdinal)
          .thenComparingInt(System::identityHashCode);

    private SupergiantQIOMountRegistry() {
    }

    static void register(SupergiantQIOStorageMonitor monitor) {
        var frequency = monitor.getFrequencyUUID();
        var grid = monitor.getGrid();
        if (frequency == null || grid == null) {
            monitor.setMountActive(false);
            requestReconcile(monitor);
            return;
        }
        var candidates = MOUNTS.computeIfAbsent(grid, ignored -> new HashMap<>())
              .computeIfAbsent(frequency, ignored -> new ArrayList<>());
        if (!candidates.contains(monitor)) {
            candidates.add(monitor);
        }
        elect(candidates);
        requestReconcile(monitor);
    }

    static void unregister(SupergiantQIOStorageMonitor monitor) {
        var gridIterator = MOUNTS.entrySet().iterator();
        while (gridIterator.hasNext()) {
            var byFrequency = gridIterator.next().getValue();
            var frequencyIterator = byFrequency.entrySet().iterator();
            while (frequencyIterator.hasNext()) {
                var candidates = frequencyIterator.next().getValue();
                if (candidates.remove(monitor)) {
                    elect(candidates);
                }
                if (candidates.isEmpty()) {
                    frequencyIterator.remove();
                }
            }
            if (byFrequency.isEmpty()) {
                gridIterator.remove();
            }
        }
        monitor.setMountActive(false);
        requestReconcile(monitor);
    }

    static void requestReconcile(SupergiantQIOStorageMonitor monitor) {
        PENDING.request(monitor, !monitor.isMountActive());
    }

    static void flushPendingChanges() {
        PENDING.flush(SupergiantQIOStorageMonitor::reconcileCurrentState);
    }

    private static void elect(List<SupergiantQIOStorageMonitor> candidates) {
        candidates.sort(LOCATION_ORDER);
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            if (candidate.setMountActive(i == 0)) {
                requestReconcile(candidate);
            }
        }
    }
}
