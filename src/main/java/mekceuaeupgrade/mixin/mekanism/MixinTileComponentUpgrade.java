package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.host.IAEOutputHost;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.common.Upgrade;
import mekanism.common.recipe.cache.IRecipeLookupHandler;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileComponentUpgrade.class, remap = false)
public abstract class MixinTileComponentUpgrade {

    @Shadow
    public abstract void setSupported(Upgrade upgrade);

    @Inject(method = "<init>(Lmekanism/common/tile/prefab/TileEntityContainerBlock;)V", at = @At("RETURN"))
    private void mekceuaeupgrade$addImplicitAEUpgrade(TileEntityContainerBlock tile, CallbackInfo ci) {
        mekceuaeupgrade$addImplicitAEUpgrade(tile);
    }

    @Inject(method = "<init>(Lmekanism/common/tile/prefab/TileEntityContainerBlock;Lmekanism/common/Upgrade;)V", at = @At("RETURN"))
    private void mekceuaeupgrade$addImplicitAEUpgrade(TileEntityContainerBlock tile, Upgrade upgrade, CallbackInfo ci) {
        mekceuaeupgrade$addImplicitAEUpgrade(tile);
    }

    @Unique
    private void mekceuaeupgrade$addImplicitAEUpgrade(TileEntityContainerBlock tile) {
        boolean supportsCrafting = tile instanceof IRecipeLookupHandler<?> && tile instanceof IAEUpgradeHost;
        boolean supportsAutoProcessing = tile instanceof IAEItemRecipeHost;
        if (supportsCrafting) {
            setSupported(AEUpgrade.AE_CRAFTING);
            setSupported(AEUpgrade.AE_WIRELESS_CRAFTING);
        }
        if (supportsAutoProcessing) {
            setSupported(AEUpgrade.AE_AUTO_PROCESSING);
            setSupported(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING);
        }
        if (!supportsCrafting && !supportsAutoProcessing && tile instanceof IAEOutputHost) {
            setSupported(AEUpgrade.AE_OUTPUT);
            setSupported(AEUpgrade.AE_WIRELESS_OUTPUT);
        }
    }
}
