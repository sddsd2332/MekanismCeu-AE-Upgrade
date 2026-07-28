package mekceuaeupgrade.common.integration.appeng;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.core.worlddata.IWorldData;
import appeng.core.worlddata.IWorldPlayerData;
import appeng.core.worlddata.WorldData;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/** Resolves AE2's persistent player IDs without requiring the owner to be online. */
public final class AEPlayerUUIDResolver {

    private AEPlayerUUIDResolver() {
    }

    @Nullable
    public static UUID resolve(@Nullable IActionSource source) {
        if (source == null) {
            return null;
        }
        Optional<EntityPlayer> player = source.player();
        if (player.isPresent()) {
            return player.get().getUniqueID();
        }
        Optional<IActionHost> machine = source.machine();
        return machine.isPresent() ? resolve(machine.get().getActionableNode()) : null;
    }

    @Nullable
    public static UUID resolve(@Nullable IGridNode node) {
        return node == null ? null : resolve(node.getPlayerID());
    }

    @Nullable
    public static UUID resolve(int playerID) {
        if (playerID < 0) {
            return null;
        }
        IWorldData worldData = WorldData.instance();
        if (worldData == null) {
            return null;
        }
        IWorldPlayerData playerData = worldData.playerData();
        if (playerData instanceof IAEPlayerDataUUIDResolver resolver) {
            return resolver.mekceuaeupgrade$getPlayerUUID(playerID);
        }
        return null;
    }
}
