package mekceuaeupgrade.common.config;

import com.github.bsideup.jabel.Desugar;
import mekanism.api.Coord4D;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class AERecipeConfigClientCache {

    private static final Map<Key, AERecipeConfigSnapshot> snapshots = new HashMap<>();

    private AERecipeConfigClientCache() {
    }

    public static void setSnapshot(@Nullable Coord4D coord, AERecipeConfigSnapshot snapshot) {
        setSnapshot(coord, AERecipeConfigType.CRAFTING, snapshot);
    }

    public static void setSnapshot(@Nullable Coord4D coord, AERecipeConfigType type, AERecipeConfigSnapshot snapshot) {
        if (coord != null) {
            snapshots.put(new Key(coord, type), snapshot);
        }
    }

    @Nullable
    public static AERecipeConfigSnapshot getSnapshot(@Nullable Coord4D coord) {
        return getSnapshot(coord, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    public static AERecipeConfigSnapshot getSnapshot(@Nullable Coord4D coord, AERecipeConfigType type) {
        return coord == null ? null : snapshots.get(new Key(coord, type));
    }

    public static void clear(@Nullable Coord4D coord) {
        clear(coord, AERecipeConfigType.CRAFTING);
    }

    public static void clear(@Nullable Coord4D coord, AERecipeConfigType type) {
        if (coord != null) {
            snapshots.remove(new Key(coord, type));
        }
    }

    @Desugar
    private record Key(Coord4D coord, AERecipeConfigType type) {
    }
}
