package mekceuaeupgrade.common.host;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.LongFunction;

/** Shared location/topology only. Activity and all transfer permissions remain live. */
public final class AEWirelessTargetCache {
    private static final Map<World, Targets> WORLDS = new IdentityHashMap<>();
    private static final Map<World, Map<Long, RevisionToken>> REVISIONS = new IdentityHashMap<>();
    private static volatile long clearGeneration;
    private AEWirelessTargetCache() {}

    static Target resolve(World world, long serial, long tick) {
        return WORLDS.computeIfAbsent(world, ignored -> new Targets(AEWirelessTargetCache::find))
              .resolve(serial, tick);
    }

    /**
     * Returns the stable token for a world/serial pair. Nodes keep this token after
     * resolving their target, so the hot canUseNetwork path only reads two fields
     * instead of looking up the world and serial in nested maps every time.
     */
    static RevisionToken token(World world, long serial) {
        return REVISIONS.computeIfAbsent(world, ignored -> new HashMap<>())
              .computeIfAbsent(serial, ignored -> new RevisionToken());
    }

    static long generation() {
        return clearGeneration;
    }

    public static void invalidate(long serial) {
        for (Targets targets : WORLDS.values()) {
            targets.entries.remove(serial);
        }
        for (Map<Long, RevisionToken> revisions : REVISIONS.values()) {
            RevisionToken token = revisions.get(serial);
            if (token != null) token.revision++;
        }
    }

    public static void clear() {
        WORLDS.clear();
        REVISIONS.clear();
        clearGeneration++;
    }

    @Nullable
    private static IGridNode find(long serial) {
        ILocatable locatable = AEApi.instance().registries().locatable().getLocatableBy(serial);
        return locatable instanceof IActionHost host ? host.getActionableNode() : null;
    }

    // Package visibility lets tests exercise target replacement and world-clock rollback
    // without booting the global AE registry.
    static final class Targets {
        private final LongFunction<IGridNode> resolver;
        private final Map<Long, Target> entries = new LinkedHashMap<>(256, 0.75f, true);
        Targets(LongFunction<IGridNode> resolver) { this.resolver = resolver; }
        Target resolve(long serial, long tick) {
            Target target = entries.get(serial);
            if (target == null) {
                target = new Target();
                entries.put(serial, target);
                if (entries.size() > 2048) entries.remove(entries.keySet().iterator().next());
            }
            try {
                if (tick == Long.MIN_VALUE || target.lastResolve == Long.MIN_VALUE ||
                      tick < target.lastResolve || tick >= target.nextResolve) {
                    target.node = resolver.apply(serial);
                    target.lastResolve = tick;
                    target.nextResolve = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + 20;
                }
                // Do not share an activity decision across machines or synchronous AE callbacks.
                if (target.node == null || !target.node.isActive()) {
                    if (tick != Long.MIN_VALUE) target.nextResolve = Math.min(target.nextResolve, tick + 5);
                    target.grid = null;
                    target.storage = null;
                    return target;
                }
                IGrid grid = target.node.getGrid();
                if (target.grid != grid || target.storage == null) {
                    target.grid = grid;
                    target.storage = grid == null ? null : grid.getCache(IStorageGrid.class);
                }
            } catch (RuntimeException | LinkageError unavailable) {
                target.node = null; target.grid = null; target.storage = null;
                target.nextResolve = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + 5;
            }
            return target;
        }
    }

    static final class Target {
        IGridNode node;
        IGrid grid;
        IStorageGrid storage;
        long lastResolve = Long.MIN_VALUE, nextResolve = Long.MIN_VALUE;
    }

    static final class RevisionToken {
        private volatile long revision;

        long revision() { return revision; }
    }
}
