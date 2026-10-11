package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.adapter.AEProviderBackedRecipeAdapter;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.transfer.AEAutoProcessingController;
import mekceuaeupgrade.common.transfer.AEAutoProcessingProgram;
import mekceuaeupgrade.common.transfer.AEAutoProcessingRefillGate;

import java.util.*;

/** Single-mode responsibility; shares the node network and pending ownership. */
public final class AEUpgradeProcessingEntry {
    private final AEUpgradeNode owner;
    private static final int AUTO_PROCESSING_INTERVAL_TICKS = 5;
    private static final int AUTO_PROCESSING_RETRY_INITIAL_TICKS = 20;
    private static final int AUTO_PROCESSING_RETRY_MAX_TICKS = 100;
    private static final int RECIPE_IDENTITY_CHECK_INTERVAL_TICKS = 20;
    private AEAutoProcessingRefillGate refillGate;
    private List<AEExposedRecipe> refillRecipes;
    private AEAutoProcessingProgram autoProcessingProgram;
    private int autoProcessingTickOffset;
    private long nextAutoProcessingTick = Long.MIN_VALUE;
    private int autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
    private volatile boolean autoProcessingRequested = true;
    private long nextRecipeIdentityCheckTick = Long.MIN_VALUE;

    AEUpgradeProcessingEntry(AEUpgradeNode owner) { this.owner = owner; autoProcessingTickOffset = Math.floorMod(System.identityHashCode(owner.host), AUTO_PROCESSING_INTERVAL_TICKS); }

    boolean shouldRunAutoProcessingThisTick() {
        long time = owner.getWorldTime();
        return autoProcessingRequested || time == Long.MIN_VALUE || nextAutoProcessingTick == Long.MIN_VALUE || time >= nextAutoProcessingTick;
    }

    /** A waiting hint never consumes a request or touches machine containers. */
    boolean needsServerTick() {
        long time = owner.getWorldTime();
        if (refillRecipes != null && (time == Long.MIN_VALUE || nextRecipeIdentityCheckTick == Long.MIN_VALUE ||
              time >= nextRecipeIdentityCheckTick)) return true;
        return shouldRunAutoProcessingThisTick() && (refillGate == null || !refillGate.canKeepWaiting(time));
    }

    /** Local scheduling inspection only; AE admission and transfer still happen in the full context. */
    void prepareLocalState() {
        if (refillGate != null && shouldRunAutoProcessingThisTick()) {
            refillGate.shouldWait(owner.getWorldTime(), owner::callMachineContainerTransaction);
        }
    }

    void scheduleNextAutoProcessing(boolean processed) {
        long tick = owner.getWorldTime();
        if (processed) {
            autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
            nextAutoProcessingTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + AUTO_PROCESSING_INTERVAL_TICKS;
        } else {
            nextAutoProcessingTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + autoProcessingRetryInterval + autoProcessingTickOffset;
            autoProcessingRetryInterval = Math.min(AUTO_PROCESSING_RETRY_MAX_TICKS, autoProcessingRetryInterval << 1);
        }
    }

    void resetAutoProcessingSchedule() {
        clearRefillGate();
        autoProcessingRequested = true;
        autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
        nextAutoProcessingTick = Long.MIN_VALUE;
        nextRecipeIdentityCheckTick = Long.MIN_VALUE;
    }

    void clearRefillGate() {
        owner.outputEntry.clearBatchGate();
        if (refillGate != null) refillGate.close();
        refillGate = null;
        refillRecipes = null;
        if (autoProcessingProgram != null && owner.getProviderContextCache() != null) {
            owner.getProviderContextCache().setAutoProcessingRecipes(null);
        }
        autoProcessingProgram = null;
        nextRecipeIdentityCheckTick = Long.MIN_VALUE;
    }

    public int getAutoProcessingBatchMultiple() {
        return refillGate == null ? 1 : refillGate.batchMultiple();
    }

    void request() { autoProcessingRequested = true; }

    void validateRecipes() {
        if (refillRecipes == null) {
            return;
        }
        long time = owner.getWorldTime();
        if (time != Long.MIN_VALUE && nextRecipeIdentityCheckTick != Long.MIN_VALUE &&
              time < nextRecipeIdentityCheckTick) {
            return;
        }
        nextRecipeIdentityCheckTick = time == Long.MIN_VALUE ? Long.MIN_VALUE :
              time + RECIPE_IDENTITY_CHECK_INTERVAL_TICKS;
        if (!owner.recipeCache.isAutoProcessingViewCurrent(refillRecipes)) {
            clearRefillGate();
            owner.resetOutputDrainSchedule();
            request();
        }
    }

    void tick(IAEItemRecipeHost itemHost) {
        if (!shouldRunAutoProcessingThisTick()) return;
        autoProcessingRequested = false;
        if (refillGate != null && refillGate.shouldWait(owner.getWorldTime(), owner::callMachineContainerTransaction)) return;
        List<AEExposedRecipe> recipes = owner.recipeCache.getAutoProcessingRecipes();
        if (recipes != refillRecipes) {
            clearRefillGate();
            refillRecipes = recipes;
            autoProcessingProgram = new AEAutoProcessingProgram(recipes);
            owner.callMachineContainerTransaction(() -> {
                if (itemHost instanceof IAERecipeMachineHost providerHost) {
                    refillGate = AEProviderBackedRecipeAdapter.createRefillGate(providerHost, recipes);
                    owner.outputEntry.setBatchGate(AEProviderBackedRecipeAdapter.createOutputBatchGate(providerHost, recipes, refillGate));
                }
                itemHost.observeAEInputContainers(owner::observeInputContainer);
                return null;
            });
        }
        if (refillGate != null && refillGate.shouldWait(owner.getWorldTime(), owner::callMachineContainerTransaction)) return;
        if (owner.callMachineContainerTransaction(owner.admissionBlockedCheck::getAsBoolean)) {
            scheduleNextAutoProcessing(false);
            return;
        }
        boolean processed = AEAutoProcessingController.process(owner, itemHost, autoProcessingProgram);
        scheduleNextAutoProcessing(processed);
        if (processed) owner.outputEntry.request();
    }
}
