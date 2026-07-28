package mekceuaeupgrade.mixin.appeng;

import appeng.core.worlddata.IWorldPlayerMapping;
import mekceuaeupgrade.common.integration.appeng.IAEPlayerDataUUIDResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(targets = "appeng.core.worlddata.PlayerData", remap = false)
public abstract class MixinPlayerData implements IAEPlayerDataUUIDResolver {

    @Shadow
    @Final
    private IWorldPlayerMapping playerMapping;

    @Nullable
    @Override
    public UUID mekceuaeupgrade$getPlayerUUID(int playerID) {
        return playerMapping.get(playerID).orElse(null);
    }
}
