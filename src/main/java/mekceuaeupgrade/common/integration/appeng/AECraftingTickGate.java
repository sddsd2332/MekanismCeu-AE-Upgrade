package mekceuaeupgrade.common.integration.appeng;

/** Event-maintained idle state. A wake arriving during publication must survive the end-of-tick check. */
public final class AECraftingTickGate {
    private volatile boolean required = true;
    private long generation;

    public boolean isRequired() { return required; }

    public synchronized void wake() {
        generation++;
        required = true;
    }

    public synchronized long begin() { return generation; }

    public synchronized void finish(long startedGeneration, boolean hasWork) {
        required = hasWork || generation != startedGeneration;
    }
}
