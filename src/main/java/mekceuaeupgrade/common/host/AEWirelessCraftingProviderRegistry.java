package mekceuaeupgrade.common.host;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProviderHelper;

import java.util.*;

public final class AEWirelessCraftingProviderRegistry {

    private static final Map<IGrid, Set<AEUpgradeNode>> PROVIDERS_BY_GRID = new IdentityHashMap<>();
    private static final Map<AEUpgradeNode, IGrid> GRID_BY_PROVIDER = new IdentityHashMap<>();

    private AEWirelessCraftingProviderRegistry() {
    }

    public static synchronized void register(AEUpgradeNode node, IGrid grid) {
        if (node == null || grid == null) {
            return;
        }
        IGrid previousGrid = GRID_BY_PROVIDER.get(node);
        if (previousGrid != null && previousGrid != grid) {
            removeFromGrid(node, previousGrid);
            node.onWirelessCraftingProviderEvicted(previousGrid);
        }
        // A stale set entry must not be able to evict a provider after it has moved to a new
        // grid. The reverse map is authoritative, but clean all old memberships as well.
        removeFromOtherGrids(node, grid);
        GRID_BY_PROVIDER.put(node, grid);
        PROVIDERS_BY_GRID.computeIfAbsent(grid, ignored -> new LinkedHashSet<>()).add(node);
    }

    public static synchronized void unregister(AEUpgradeNode node) {
        if (node == null) {
            return;
        }
        IGrid grid = GRID_BY_PROVIDER.remove(node);
        if (grid != null) {
            removeFromGrid(node, grid);
        }
        removeFromOtherGrids(node, null);
    }

    public static synchronized boolean isRegistered(AEUpgradeNode node) {
        return node != null && GRID_BY_PROVIDER.containsKey(node);
    }

    public static synchronized boolean isRegistered(AEUpgradeNode node, IGrid grid) {
        return node != null && grid != null && GRID_BY_PROVIDER.get(node) == grid;
    }

    public static synchronized void provideCrafting(IGrid grid, ICraftingProviderHelper helper) {
        if (grid == null || helper == null || PROVIDERS_BY_GRID.isEmpty()) {
            return;
        }
        Set<AEUpgradeNode> providers = PROVIDERS_BY_GRID.get(grid);
        if (providers == null || providers.isEmpty()) {
            return;
        }
        Iterator<AEUpgradeNode> iterator = providers.iterator();
        while (iterator.hasNext()) {
            AEUpgradeNode node = iterator.next();
            IGrid registeredGrid = GRID_BY_PROVIDER.get(node);
            if (registeredGrid != grid || !node.isWirelessCraftingProviderValid() || !node.isWirelessTargetGrid(grid)) {
                iterator.remove();
                // A stale membership from an old grid must not remove the current registration.
                if (registeredGrid == grid) {
                    GRID_BY_PROVIDER.remove(node);
                }
                node.onWirelessCraftingProviderEvicted(grid);
                continue;
            }
            node.provideCrafting(helper);
        }
        if (providers.isEmpty()) {
            PROVIDERS_BY_GRID.remove(grid);
        }
    }

    private static void removeFromGrid(AEUpgradeNode node, IGrid grid) {
        Set<AEUpgradeNode> providers = PROVIDERS_BY_GRID.get(grid);
        if (providers == null) {
            return;
        }
        providers.remove(node);
        if (providers.isEmpty()) {
            PROVIDERS_BY_GRID.remove(grid);
        }
    }

    private static void removeFromOtherGrids(AEUpgradeNode node, IGrid keepGrid) {
        Iterator<Map.Entry<IGrid, Set<AEUpgradeNode>>> iterator = PROVIDERS_BY_GRID.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<IGrid, Set<AEUpgradeNode>> entry = iterator.next();
            if (entry.getKey() == keepGrid) {
                continue;
            }
            Set<AEUpgradeNode> providers = entry.getValue();
            providers.remove(node);
            if (providers.isEmpty()) {
                iterator.remove();
            }
        }
    }
}
