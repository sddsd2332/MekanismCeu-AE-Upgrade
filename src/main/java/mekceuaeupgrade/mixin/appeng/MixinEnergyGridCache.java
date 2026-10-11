package mekceuaeupgrade.mixin.appeng;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridHost;
import appeng.me.cache.EnergyGridCache;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mekanism AE upgrade nodes have zero AE idle power and do not provide or consume AE power.
 * An isolated grid containing only those nodes has no EnergyGridCache work to perform.
 * Ordinary AE grids are never skipped.
 */
@Mixin(value = EnergyGridCache.class, remap = false)
public abstract class MixinEnergyGridCache {
    @Shadow @Final private appeng.api.networking.IGrid myGrid;
    @Unique private boolean mekceuaeupgrade$zeroPowerUpgradeGrid;

    @Inject(method = "addNode", at = @At("RETURN"))
    private void mekceuaeupgrade$refreshAfterAdd(IGridNode node, IGridHost machine, CallbackInfo ci) {
        mekceuaeupgrade$refreshZeroPowerState();
    }

    @Inject(method = "removeNode", at = @At("RETURN"))
    private void mekceuaeupgrade$refreshAfterRemove(IGridNode node, IGridHost machine, CallbackInfo ci) {
        mekceuaeupgrade$refreshZeroPowerState();
    }

    @Inject(method = "onUpdateTick", at = @At("HEAD"), cancellable = true)
    private void mekceuaeupgrade$skipZeroPowerUpgradeGrid(CallbackInfo ci) {
        if (mekceuaeupgrade$zeroPowerUpgradeGrid) ci.cancel();
    }

    @Unique
    private void mekceuaeupgrade$refreshZeroPowerState() {
        boolean hasNode = false;
        boolean onlyUpgradeHosts = true;
        for (IGridNode node : myGrid.getNodes()) {
            hasNode = true;
            if (!(node.getMachine() instanceof IAEUpgradeHost)) {
                onlyUpgradeHosts = false;
                break;
            }
        }
        mekceuaeupgrade$zeroPowerUpgradeGrid = hasNode && onlyUpgradeHosts;
    }
}
