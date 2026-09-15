package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityContainerBlock.class, remap = false)
public abstract class MixinTileEntityContainerBlock {

    @Inject(method = "onContentsChanged", at = @At("TAIL"), require = 1)
    private void mekceuaeupgrade$invalidateAEBusyCache(CallbackInfo ci) {
        if ((Object) this instanceof IAEUpgradeHostBridge bridge) {
            bridge.mekceuaeupgrade$invalidateAEBusyCache();
        }
    }
}
