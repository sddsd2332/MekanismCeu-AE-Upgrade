package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;

import mekanism.common.Mekanism;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = TileEntityElectricBlock.class, remap = false)
public abstract class MixinTileEntityElectricBlock {

    @Inject(method = "onAsyncUpdateServer", at = @At("TAIL"))
    private void mekceuaeupgrade$onAsyncUpdateServer(CallbackInfo ci) {
        Mekanism.EXECUTE_MANAGER.addSyncTask(this::mekceuaeupgrade$tickAEUpgradeAfterTileSync);
    }

    @Unique
    private void mekceuaeupgrade$tickAEUpgradeAfterTileSync() {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$tickAEUpgradeServer();
        }
    }

    @Unique
    @Nullable
    private IAEUpgradeHostBridge mekceuaeupgrade$getAEBridge() {
        Object self = this;
        return self instanceof IAEUpgradeHostBridge ? (IAEUpgradeHostBridge) self : null;
    }
}
