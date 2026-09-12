package mekceuaeupgrade.mixin.appeng;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerMEMonitorable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerMEMonitorable.class, remap = false)
public abstract class MixinContainerMEMonitorable {
    @Shadow
    @Final
    public IItemList<IAEItemStack> items;

    @Inject(method = "postChange", at = @At("TAIL"), require = 1)
    private void mekceuaeupgrade$queueRemovedCraftables(IBaseMonitor<IAEItemStack> monitor,
          Iterable<IAEItemStack> changes, IActionSource source, CallbackInfo ci) {
        for (IAEItemStack change : changes) {
            if (change == null || change.isMeaningful()) continue;
            // Only mark the terminal's dirty key. AE serializes the actual monitor value,
            // or a zero stack when absent; a zero dirty key would be removed by its iterator.
            IAEItemStack pending = items.findPrecise(change);
            if (pending == null) {
                pending = change.copy();
                pending.reset();
                pending.setStackSize(1);
                items.add(pending);
            } else {
                pending.reset();
                pending.setStackSize(1);
            }
        }
    }
}
