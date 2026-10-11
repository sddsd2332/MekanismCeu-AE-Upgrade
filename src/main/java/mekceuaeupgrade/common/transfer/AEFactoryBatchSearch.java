package mekceuaeupgrade.common.transfer;

import java.util.function.IntFunction;

/** Searches the same downward-closed admission predicate as the original factory binary search.
 * A capacity hint is only a starting point: it never authorizes delivery or caps the search. */
final class AEFactoryBatchSearch {
    private AEFactoryBatchSearch() {}

    static <T> T find(int maximum, int hint, IntFunction<T> prepare) {
        if (maximum < 1) return null;
        int pivot = Math.max(1, Math.min(maximum, hint));
        T best = prepare.apply(pivot);
        int low;
        int high;
        if (best == null) {
            if (pivot == 1) return null;
            best = prepare.apply(1);
            if (best == null) return null;
            low = 2;
            high = pivot - 1;
        } else {
            if (pivot == maximum) return best;
            T next = prepare.apply(pivot + 1);
            if (next == null) return best;
            best = next;
            if (pivot + 1 == maximum) return best;
            T full = prepare.apply(maximum);
            if (full != null) return full;
            low = pivot + 2;
            high = maximum - 1;
        }
        while (low <= high) {
            int amount = low + (high - low) / 2;
            T candidate = prepare.apply(amount);
            if (candidate == null) high = amount - 1;
            else { best = candidate; low = amount + 1; }
        }
        return best;
    }
}
