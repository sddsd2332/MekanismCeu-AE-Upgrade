package mekceuaeupgrade.mixin.appeng;

import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.storage.ICellContainer;
import appeng.api.util.IReadOnlyCollection;
import appeng.me.Grid;
import appeng.me.GridCacheWrapper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cache.EnergyGridCache;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.P2PCache;
import appeng.me.cache.PathGridCache;
import appeng.me.cache.SecurityCache;
import appeng.me.cache.SpatialPylonCache;
import appeng.me.cache.TickManagerCache;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.integration.appeng.IAEGridCacheTickGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Stops idle cache dispatch only for the private singleton grid of a wireless upgrade. */
@Mixin(value = Grid.class, remap = false)
public abstract class MixinGrid {
    @Shadow public abstract IGridNode getPivot();
    @Shadow @Final private Map<Class<? extends IGridCache>, GridCacheWrapper> caches;
    @Shadow public abstract IReadOnlyCollection<IGridNode> getNodes();
    @Shadow public abstract <C extends IGridCache> C getCache(Class<? extends IGridCache> iface);

    @Unique private boolean mekceuaeupgrade$knownCaches;
    @Unique private IAEGridCacheTickGate mekceuaeupgrade$craftingTickGate;
    @Unique private boolean mekceuaeupgrade$topologyDirty = true;
    @Unique private int mekceuaeupgrade$topologyMutationDepth;
    @Unique private long mekceuaeupgrade$topologyGeneration;
    @Unique private boolean mekceuaeupgrade$resolvingCandidate;
    @Unique private IGridNode mekceuaeupgrade$candidateNode;
    @Unique private AEUpgradeNode mekceuaeupgrade$candidateUpgrade;

    /** Invalidate before callbacks can re-enter update on a partly changed grid. */
    @Inject(method = {"add", "remove", "setPivot"}, at = @At("HEAD"), require = 3)
    private void mekceuaeupgrade$beginTopologyChange(CallbackInfo ci) {
        mekceuaeupgrade$topologyMutationDepth++;
        mekceuaeupgrade$invalidateCandidate();
    }

    /** Coalesce all membership changes until the next normal update. Never count invocations. */
    @Inject(method = {"add", "remove", "setPivot"}, at = @At("RETURN"), require = 3)
    private void mekceuaeupgrade$finishTopologyChange(CallbackInfo ci) {
        if (mekceuaeupgrade$topologyMutationDepth > 0) mekceuaeupgrade$topologyMutationDepth--;
        mekceuaeupgrade$invalidateCandidate();
    }

    @Unique
    private void mekceuaeupgrade$invalidateCandidate() {
        mekceuaeupgrade$topologyGeneration++;
        mekceuaeupgrade$topologyDirty = true;
        mekceuaeupgrade$candidateNode = null;
        mekceuaeupgrade$candidateUpgrade = null;
    }

    /** Cache instances are fixed at Grid construction; unknown extension caches keep their ticks. */
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void mekceuaeupgrade$checkNativeCaches(CallbackInfo ci) {
        mekceuaeupgrade$invalidateCandidate();
        mekceuaeupgrade$knownCaches = false;
        mekceuaeupgrade$craftingTickGate = null;
        if (caches.size() != 8) return;
        Set<Class<?>> expected = new HashSet<>();
        expected.add(CraftingGridCache.class);
        expected.add(EnergyGridCache.class);
        expected.add(GridStorageCache.class);
        expected.add(P2PCache.class);
        expected.add(PathGridCache.class);
        expected.add(SecurityCache.class);
        expected.add(SpatialPylonCache.class);
        expected.add(TickManagerCache.class);
        for (Class<? extends IGridCache> key : caches.keySet()) {
            IGridCache cache = getCache(key);
            if (cache == null || !expected.remove(cache.getClass())) return;
            if (cache instanceof CraftingGridCache && cache instanceof IAEGridCacheTickGate gate) {
                mekceuaeupgrade$craftingTickGate = gate;
            }
        }
        mekceuaeupgrade$knownCaches = expected.isEmpty();
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 1)
    private void mekceuaeupgrade$skipIsolatedWirelessUpgrade(CallbackInfo ci) {
        if (!mekceuaeupgrade$knownCaches || caches.size() != 8 ||
              mekceuaeupgrade$craftingTickGate == null ||
              mekceuaeupgrade$topologyMutationDepth != 0 || mekceuaeupgrade$resolvingCandidate) return;
        if (mekceuaeupgrade$topologyDirty) mekceuaeupgrade$refreshCandidate();
        IGridNode node = mekceuaeupgrade$candidateNode;
        AEUpgradeNode upgrade = mekceuaeupgrade$candidateUpgrade;
        if (node == null || upgrade == null) return;
        // Membership can change while the Grid object survives. Never reuse a candidate elsewhere.
        if (getPivot() != node || (Object) node.getGrid() != this) {
            mekceuaeupgrade$invalidateCandidate();
            return;
        }
        // Connections and upgrade modes remain live; topology caching does not cache permission to skip.
        if (!node.getConnections().isEmpty() || !node.getGridBlock().getConnectableSides().isEmpty()) return;
        // The local crafting cache may need one-time rebuilds or deferred publication even when
        // the actual wireless provider belongs to another grid. Its event-driven wake must win.
        if (upgrade.canSkipIsolatedGridTick(node) && !mekceuaeupgrade$craftingTickGate.mekceuaeupgrade$isTickRequired() &&
              !mekceuaeupgrade$topologyDirty &&
              mekceuaeupgrade$topologyMutationDepth == 0) ci.cancel();
    }

    @Unique
    private void mekceuaeupgrade$refreshCandidate() {
        long generation = mekceuaeupgrade$topologyGeneration;
        mekceuaeupgrade$resolvingCandidate = true;
        try {
            mekceuaeupgrade$candidateNode = null;
            mekceuaeupgrade$candidateUpgrade = null;
            IGridNode node = getPivot();
            if (node == null || !(node.getMachine() instanceof IAEUpgradeHost host)) return;
            Iterator<IGridNode> nodes = getNodes().iterator();
            if (!nodes.hasNext() || nodes.next() != node || nodes.hasNext()) return;
            // Other integrations may add ticking/storage roles to a machine. They must retain AE service.
            if (host instanceof IGridTickable || host instanceof ICellContainer ||
                  host instanceof IStackWatcherHost || host instanceof ICraftingRequester) return;
            AEUpgradeNode upgrade = host.getAEUpgradeNode();
            // A lazy delegate may not exist yet; retry discovery rather than caching a permanent miss.
            if (upgrade == null) {
                mekceuaeupgrade$invalidateCandidate();
                return;
            }
            if (generation == mekceuaeupgrade$topologyGeneration && mekceuaeupgrade$topologyMutationDepth == 0) {
                mekceuaeupgrade$candidateNode = node;
                mekceuaeupgrade$candidateUpgrade = upgrade;
            }
        } finally {
            mekceuaeupgrade$resolvingCandidate = false;
            mekceuaeupgrade$topologyDirty = generation != mekceuaeupgrade$topologyGeneration;
        }
    }
}
