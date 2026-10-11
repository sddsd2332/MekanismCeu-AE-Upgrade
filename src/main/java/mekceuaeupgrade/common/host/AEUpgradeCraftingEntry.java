package mekceuaeupgrade.common.host;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import mekceuaeupgrade.common.integration.appeng.IAEIncrementalCraftingGridCache;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.transfer.AEUpgradeInputInjector;
import mekceuaeupgrade.common.util.AEUpgradeDebug;
import net.minecraft.inventory.InventoryCrafting;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Single-mode responsibility; shares the node network and pending ownership. */
public final class AEUpgradeCraftingEntry {
    private final AEUpgradeNode owner;
    private long lastBusyDebugTick = Long.MIN_VALUE;
    private final AtomicLong busyCacheGeneration = new AtomicLong();
    private volatile long busyCacheReadGeneration = Long.MIN_VALUE;
    private volatile boolean busyCacheValue;

    AEUpgradeCraftingEntry(AEUpgradeNode owner) { this.owner = owner; }

    public boolean isBusy() {
        // Preserve custom host gates; the normal host path uses the versioned installed-state cache.
        if (owner.legacyExposureCheck) {
            if (!owner.host.shouldExposeAECrafting()) return true;
        }
        if (!owner.isCraftingMode(owner.getExposureMode())) {
            debugBusy("busy: AE upgrade is not installed or host is not exposing AE");
            return true;
        }
        // Host gates that do not participate in invalidation must be read on every query, hit or
        // miss: the generation can be unchanged while a custom host flips its own admission state.
        if (owner.admissionBlockedCheck.getAsBoolean()) return true;
        // Extension nodes may override the network probe and can have state which is not
        // represented by this node's invalidation callbacks. Keep that contract live.
        if (owner.customNetworkCheck) {
            if (!owner.canUseNetwork()) {
                return true;
            }
        }
        long generation = busyCacheGeneration.get();
        if (busyCacheReadGeneration == generation) {
            return busyCacheValue;
        }
        return recomputeBusy(generation);
    }

    private boolean recomputeBusy(long generation) {
        if (!owner.customNetworkCheck && !owner.canUseNetwork()) {
            if (AEUpgradeDebug.enabled()) {
                debugBusy("busy: network unavailable node={} active={} powered={} side={}",
                      owner.proxy.getNode() != null, owner.proxy.isActive(), owner.proxy.isPowered(), owner.connectionSide);
            }
            return true;
        }
        if (owner.pendingTransfers.hasOwnedResources()) {
            debugBusy("busy: pending AE transfer buffer still owns resources");
            return true;
        }
        if (owner.host instanceof IAEItemRecipeHost itemHost) {
            boolean busy = owner.callMachineContainerTransaction(() -> {
                itemHost.observeAEInputContainers(owner::observeInputContainer);
                itemHost.observeAEOutputContainers(owner::observeOutputContainer);
                if (itemHost.canAttemptAEItemInput()) {
                    return false;
                }
                debugBusy("busy: machine admission is blocked");
                return true;
            });
            // Only cache when output capacity is observable. Legacy adapters without output
            // listeners must retain the live check so silent capacity changes remain visible.
            if (generation == busyCacheGeneration.get() && !owner.observedOutputContainers.isEmpty()) {
                busyCacheValue = busy;
                busyCacheReadGeneration = generation;
            }
            return busy;
        }
        return false;
    }

    public void invalidateBusyCache() {
        invalidateBusyCache("unspecified");
    }

    public void invalidateBusyCache(String reason) {
        busyCacheGeneration.incrementAndGet();
        busyCacheReadGeneration = Long.MIN_VALUE;
    }

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        boolean exposesCrafting = owner.isCraftingMode(owner.getExposureMode());
        boolean networkUsable = owner.canUseNetwork();
        if (!exposesCrafting || !networkUsable) {
            // AE can invoke this callback during a native cache rebuild while the node is
            // already disconnected. Drop any sidecar state instead of leaving an unreachable
            // provider visible through the query mixins.
            if (craftingTracker instanceof IAEIncrementalCraftingGridCache incrementalCache) {
                if (owner.incrementalCraftingCache == incrementalCache) {
                    owner.unregisterIncrementalCraftingProvider();
                }
            } else {
                owner.unregisterIncrementalCraftingProvider();
            }
            AEUpgradeDebug.log(owner.host, "skipping crafting provider expose={} networkUsable={}", exposesCrafting, networkUsable);
            return;
        }
        List<AEExposedRecipe> recipes = owner.recipeCache.getRecipes();
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(owner.host, "providing {} AE processing recipes", recipes.size());
        }
        if (craftingTracker instanceof IAEIncrementalCraftingGridCache incrementalCache) {
            IGrid providerGrid = incrementalCache.mekceuaeupgrade$getGrid();
            IGrid activeGrid = owner.getActiveGrid();
            if (providerGrid != activeGrid) {
                // A callback from an old cache can race a split/join. Never associate that cache
                // with the newly resolved grid; the next ready tick will publish to the new one.
                if (owner.incrementalCraftingCache == incrementalCache && owner.incrementalCraftingGrid == providerGrid) {
                    owner.unregisterIncrementalCraftingProvider();
                }
                return;
            }
            owner.publishIncrementalCraftingProvider(incrementalCache, providerGrid, recipes);
            return;
        }
        owner.unregisterIncrementalCraftingProvider();
        for (AEExposedRecipe recipe : recipes) {
            craftingTracker.addCraftingOption(owner.host, recipe);
        }
        owner.incrementalCraftingRefreshRequired = false;
    }

    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (!owner.isCraftingMode(owner.getExposureMode())) {
            AEUpgradeDebug.log(owner.host, "pushPattern rejected: AE upgrade is not installed or host is not exposing AE");
            return false;
        }
        if (!owner.canUseNetwork()) {
            AEUpgradeDebug.log(owner.host, "pushPattern rejected: network unavailable node={} active={} powered={} side={}",
                  owner.proxy.getNode() != null, owner.proxy.isActive(), owner.proxy.isPowered(), owner.connectionSide);
            return false;
        }
        if (owner.pendingTransfers.hasOwnedResources()) {
            AEUpgradeDebug.log(owner.host, "pushPattern rejected: pending AE transfer buffer still owns resources");
            return false;
        }
        if (!(owner.host instanceof IAEItemRecipeHost itemHost)) {
            AEUpgradeDebug.log(owner.host, "pushPattern rejected: host does not implement item recipe owner.host");
            return false;
        }
        if (itemHost.isAEAdmissionBlocked()) {
            return false;
        }
        AEExposedRecipe recipe = owner.recipeCache.find(exposed -> exposed.matches(patternDetails));
        if (recipe == null) {
            AEUpgradeDebug.log(owner.host, "pushPattern rejected: no matching exposed recipe for AE pattern");
            return false;
        }
        if (!owner.callMachineContainerTransaction(() -> AEUpgradeInputInjector.push(itemHost, recipe, table))) {
            return false;
        }
        owner.outputEntry.request();
        AEUpgradeDebug.log(owner.host, "pushPattern accepted inputs={} outputs={}",
              AEUpgradeDebug.inputStacks(recipe), AEUpgradeDebug.outputStacks(recipe));
        return true;
    }

    private void debugBusy(String message, Object... args) {
        if (!AEUpgradeDebug.enabled()) {
            return;
        }
        long now = owner.getWorldTime();
        if (now != Long.MIN_VALUE && now - lastBusyDebugTick < 20) {
            return;
        }
        lastBusyDebugTick = now;
        AEUpgradeDebug.log(owner.host, message, args);
    }
}
