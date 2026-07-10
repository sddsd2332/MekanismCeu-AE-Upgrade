package mekceuaeupgrade.mixin.appeng;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.NetworkMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class MixinNetworkMonitor<T extends IAEStack<T>> {

    @Inject(method = "updateCraftables", at = @At("TAIL"))
    private void mekceuaeupgrade$notifyCraftableListeners(Iterable<T> changes, IActionSource source, CallbackInfo ci) {
        mekceuaeupgrade$invokeNotifyListenersOfChange(changes, source);
    }

    @Invoker("notifyListenersOfChange")
    protected abstract void mekceuaeupgrade$invokeNotifyListenersOfChange(Iterable<T> changes, IActionSource source);
}
