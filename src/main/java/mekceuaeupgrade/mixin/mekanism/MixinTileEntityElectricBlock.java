package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityElectricBlock.class, remap = false)
public abstract class MixinTileEntityElectricBlock {

    @Inject(method = "validate", at = @At("TAIL"), require = 1)
    private void mekceuaeupgrade$validate(CallbackInfo ci) {
        if ((Object) this instanceof IAEUpgradeHostBridge bridge) {
            bridge.mekceuaeupgrade$validateAEUpgrade();
        }
    }

    @Inject(method = "onChunkUnload", at = @At("TAIL"), require = 1)
    private void mekceuaeupgrade$onChunkUnload(CallbackInfo ci) {
        if ((Object) this instanceof IAEUpgradeHostBridge bridge) {
            bridge.mekceuaeupgrade$onAEUpgradeChunkUnload();
        }
    }
}
