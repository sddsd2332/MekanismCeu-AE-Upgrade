package mekceuaeupgrade.common.integration.qio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class QIOMountReconciliationQueue<T> {

    private final Map<T, Boolean> pending = new LinkedHashMap<>();

    void request(T value, boolean removalFirst) {
        pending.merge(value, removalFirst, (previous, requested) -> previous || requested);
    }

    void flush(Consumer<T> reconcile) {
        if (pending.isEmpty()) {
            return;
        }
        List<Map.Entry<T, Boolean>> batch = new ArrayList<>(pending.entrySet());
        pending.clear();
        batch.sort((left, right) -> Boolean.compare(right.getValue(), left.getValue()));
        for (Map.Entry<T, Boolean> entry : batch) {
            reconcile.accept(entry.getKey());
        }
    }
}
