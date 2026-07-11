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
        }
        GRID_BY_PROVIDER.put(node, grid);
        PROVIDERS_BY_GRID.computeIfAbsent(grid, ignored -> new LinkedHashSet<>()).add(node);
    }

    public static synchronized void unregister(AEUpgradeNode node) {
        IGrid grid = GRID_BY_PROVIDER.remove(node);
        if (grid != null) {
            removeFromGrid(node, grid);
        }
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
            if (!node.isWirelessCraftingProviderValid() || !node.isWirelessTargetGrid(grid)) {
                iterator.remove();
                GRID_BY_PROVIDER.remove(node);
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
}
