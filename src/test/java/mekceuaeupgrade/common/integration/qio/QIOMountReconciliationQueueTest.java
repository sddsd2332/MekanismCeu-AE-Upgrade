package mekceuaeupgrade.common.integration.qio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QIOMountReconciliationQueueTest {

    @Test
    void flushesInactiveMountsBeforeActiveMounts() {
        QIOMountReconciliationQueue<Mount> queue = new QIOMountReconciliationQueue<>();
        Mount active = new Mount("active");
        Mount inactive = new Mount("inactive");
        queue.request(active, false);
        queue.request(inactive, true);
        queue.request(active, false);

        List<String> reconciled = new ArrayList<>();
        queue.flush(mount -> reconciled.add(mount.name));

        assertEquals(Arrays.asList("inactive", "active"), reconciled);
    }

    @Test
    void defersRequestsMadeDuringFlush() {
        QIOMountReconciliationQueue<Mount> queue = new QIOMountReconciliationQueue<>();
        Mount first = new Mount("first");
        Mount next = new Mount("next");
        queue.request(first, true);

        List<String> reconciled = new ArrayList<>();
        queue.flush(mount -> {
            reconciled.add(mount.name);
            queue.request(next, true);
        });
        assertEquals(Arrays.asList("first"), reconciled);

        queue.flush(mount -> reconciled.add(mount.name));
        assertEquals(Arrays.asList("first", "next"), reconciled);
    }

    @Test
    void keepsRemovalPriorityAfterReactivation() {
        QIOMountReconciliationQueue<Mount> queue = new QIOMountReconciliationQueue<>();
        Mount rebound = new Mount("rebound");
        Mount replacement = new Mount("replacement");
        queue.request(rebound, true);
        queue.request(rebound, false);
        queue.request(replacement, false);

        List<String> reconciled = new ArrayList<>();
        queue.flush(mount -> reconciled.add(mount.name));

        assertEquals(Arrays.asList("rebound", "replacement"), reconciled);
    }

    private static final class Mount {

        private final String name;

        private Mount(String name) {
            this.name = name;
        }
    }
}
