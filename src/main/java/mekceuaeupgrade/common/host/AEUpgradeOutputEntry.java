package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.adapter.AEProviderBackedRecipeAdapter;
import mekceuaeupgrade.common.transfer.AEAutoOutputBatchGate;

import java.util.*;

import mekceuaeupgrade.common.host.AEUpgradeNode.ExposureMode;

/** Single-mode responsibility; shares the node network and pending ownership. */
public final class AEUpgradeOutputEntry {
    private final AEUpgradeNode owner;
    private static final int OUTPUT_DRAIN_RETRY_INITIAL_TICKS = 5;
    private static final int OUTPUT_DRAIN_MAX_INTERVAL_TICKS = 40;
    private AEAutoOutputBatchGate outputBatchGate;
    private long nextOutputDrainTick = Long.MIN_VALUE;
    private int outputDrainInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
    private volatile boolean outputDrainRequested = true;
    private boolean outputDrainRetryPending;
    private boolean outputBlocked;
    boolean outputPollingRequired;

    AEUpgradeOutputEntry(AEUpgradeNode owner) { this.owner = owner; }

    boolean supportsOutputDrain(ExposureMode mode) {
        return owner.host instanceof IAEItemRecipeHost && mode != ExposureMode.NONE ||
              !(owner.host instanceof IAEItemRecipeHost) && owner.host instanceof IAEOutputHost && owner.isOutputMode(mode);
    }

    boolean drainOutputs() {
        outputBlocked = false;
        return AEProviderBackedRecipeAdapter.drainOutputs(owner.host, owner, () -> {
            if (owner.host instanceof IAEItemRecipeHost itemHost) {
                return itemHost.drainAEItemOutputs(owner);
            }
            return owner.host instanceof IAEOutputHost outputHost && outputHost.drainAEOutputs(owner);
        });
    }

    boolean shouldDrainOutputsThisTick() {
        long tick = owner.getWorldTime();
        if (outputBatchGate != null && !outputBlocked && !outputPollingRequired) {
            return outputBatchGate.shouldDrain(tick, owner::callMachineContainerTransaction);
        }
        return outputDrainRequested || outputDrainRetryPending &&
              (tick == Long.MIN_VALUE || nextOutputDrainTick == Long.MIN_VALUE || tick >= nextOutputDrainTick);
    }

    /** Cheap host-scheduler gate; does not inspect output containers or execute a transaction. */
    boolean needsServerTick(ExposureMode mode, long tick) {
        if (!supportsOutputDrain(mode)) {
            return false;
        }
        if (outputBatchGate != null && !outputBlocked && !outputPollingRequired) {
            return outputBatchGate.needsCheck(tick);
        }
        if (outputDrainRequested) {
            return true;
        }
        if (outputDrainRetryPending &&
              (tick == Long.MIN_VALUE || nextOutputDrainTick == Long.MIN_VALUE || tick >= nextOutputDrainTick)) {
            return true;
        }
        return outputBatchGate != null && outputBatchGate.needsCheck(tick);
    }

    /** A shared input snapshot is refreshed in the same transaction as dirty output state. */
    void prepareLocalState(long tick) {
        if (outputBatchGate != null && !outputBlocked && !outputPollingRequired) {
            outputBatchGate.shouldDrain(tick, owner::callMachineContainerTransaction);
        }
    }

    void scheduleNextOutputDrain() {
        outputDrainRetryPending = outputBlocked || outputPollingRequired;
        if (!outputDrainRetryPending) {
            outputDrainInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
            nextOutputDrainTick = Long.MIN_VALUE;
            return;
        }
        long tick = owner.getWorldTime();
        nextOutputDrainTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + outputDrainInterval;
        outputDrainInterval = Math.min(OUTPUT_DRAIN_MAX_INTERVAL_TICKS, outputDrainInterval << 1);
    }

    void resetOutputDrainSchedule() {
        outputDrainRequested = true;
        outputDrainRetryPending = false;
        outputDrainInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
        nextOutputDrainTick = Long.MIN_VALUE;
    }

    boolean tick(ExposureMode mode) {
        if (!supportsOutputDrain(mode) || !shouldDrainOutputsThisTick()) return false;
        outputDrainRequested = false;
        drainOutputs();
        if (outputBatchGate != null) outputBatchGate.afterDrain(owner.getWorldTime());
        scheduleNextOutputDrain();
        return true;
    }

    void request() {
        // Inventory/input activity must not turn a full-network retry into polling
        // every tick. Explicit network/mode resets still use resetOutputDrainSchedule.
        if (!outputDrainRetryPending) outputDrainRequested = true;
    }
    void markBlocked() { outputBlocked = true; }
    void setBatchGate(AEAutoOutputBatchGate gate) { clearBatchGate(); outputBatchGate = gate; }
    void clearBatchGate() {
        if (outputBatchGate != null) outputBatchGate.close();
        outputBatchGate = null;
    }
}
