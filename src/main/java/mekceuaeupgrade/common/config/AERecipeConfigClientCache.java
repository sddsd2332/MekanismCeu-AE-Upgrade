package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import mekanism.api.Coord4D;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class AERecipeConfigClientCache {

    private static final Map<Coord4D, AERecipeConfigSnapshot> snapshots = new HashMap<>();

    private AERecipeConfigClientCache() {
    }

    public static void setSnapshot(@Nullable Coord4D coord, AERecipeConfigSnapshot snapshot) {
        if (coord != null) {
            snapshots.put(coord, snapshot);
        }
    }

    @Nullable
    public static AERecipeConfigSnapshot getSnapshot(@Nullable Coord4D coord) {
        return coord == null ? null : snapshots.get(coord);
    }

    public static void clear(@Nullable Coord4D coord) {
        if (coord != null) {
            snapshots.remove(coord);
        }
    }
}
