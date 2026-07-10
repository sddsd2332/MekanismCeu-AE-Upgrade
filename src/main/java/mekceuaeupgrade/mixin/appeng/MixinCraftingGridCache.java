package mekceuaeupgrade.mixin.appeng;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.me.cache.CraftingGridCache;
import mekceuaeupgrade.common.host.AEWirelessCraftingProviderRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class MixinCraftingGridCache implements ICraftingProviderHelper {

    @Shadow
    @Final
    private IGrid grid;

    @Inject(
          method = "recalculateCraftingPatterns",
          at = @At(
                value = "NEW",
                target = "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap",
                ordinal = 1,
                shift = At.Shift.BEFORE
          )
    )
    private void mekceuaeupgrade$provideWirelessCrafting(CallbackInfo ci) {
        AEWirelessCraftingProviderRegistry.provideCrafting(grid, this);
    }

    @ModifyArg(
          method = "recalculateCraftingPatterns",
          at = @At(
                value = "INVOKE",
                target = "Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;containsKey(Ljava/lang/Object;)Z",
                ordinal = 1
          ),
          index = 0
    )
    private Object mekceuaeupgrade$useCraftableStackAsLookupKey(Object key) {
        return key instanceof Map.Entry ? ((Map.Entry<?, ?>) key).getKey() : key;
    }
}
