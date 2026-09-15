package mekceuaeupgrade.mixin.mekceumoremachine;

import io.netty.buffer.ByteBuf;
import mekanism.common.recipe.RecipeHandler;
import mekceuaeupgrade.common.adapter.AEGasItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceumoremachine.common.capability.ResizableFluidTank;
import mekceumoremachine.common.capability.ResizableGasTank;
import mekceumoremachine.common.tile.machine.TileEntityTierRotaryCondensentrator;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityTierRotaryCondensentrator.class, remap = false)
public abstract class MixinTileEntityTierRotaryCondensentrator implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public ResizableGasTank gasTank;
    @Shadow
    public ResizableFluidTank fluidTank;
    @Shadow
    public boolean mode;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    @Override
    public AEUpgradeNode getAEUpgradeNode() {
        AEUpgradeNode node = mekceuaeupgrade$cachedNode;
        if (node == null) {
            node = mekceuaeupgrade$getAEUpgradeDelegate().getNode();
            mekceuaeupgrade$cachedNode = node;
        }
        return node;
    }

    @Override
    public AEUpgradeHostDelegate mekceuaeupgrade$getAEUpgradeDelegate() {
        AEUpgradeHostDelegate delegate = mekceuaeupgrade$aeUpgrade;
        if (delegate == null) {
            synchronized (this) {
                delegate = mekceuaeupgrade$aeUpgrade;
                if (delegate == null) {
                    delegate = new AEUpgradeHostDelegate(this);
                    mekceuaeupgrade$aeUpgrade = delegate;
                }
            }
        }
        return delegate;
    }

    @Override
    public IAERecipeMachineAdapter getAERecipeMachineAdapter() {
        if (mekceuaeupgrade$aeRecipeAdapter == null) {
            mekceuaeupgrade$aeRecipeAdapter = AEGasItemRecipeAdapters.rotaryGasFluid(
                  () -> RecipeHandler.Recipe.ROTARY_CONDENSENTRATOR.get(), () -> gasTank, () -> fluidTank,
                  () -> mode ? 0 : 1, "tier rotary condensentrator");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

    @Inject(method = "handlePacketData", at = @At(value = "INVOKE",
          target = "Lmekanism/common/recipe/cache/RecipeCacheLookupMonitor;onChange()V", shift = At.Shift.AFTER))
    private void mekceuaeupgrade$handleModePacketData(ByteBuf dataStream, CallbackInfo ci) {
        mekceuaeupgrade$onAERecipePortsChanged();
    }

    @Inject(method = "onRecipeCacheInvalidated", at = @At("TAIL"))
    private void mekceuaeupgrade$onRecipeCacheInvalidated(int cacheIndex, CallbackInfo ci) {
        mekceuaeupgrade$invalidateAERecipeCache();
    }

    @Inject(method = "getConfigurationData", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$getConfigurationData(NBTTagCompound nbtTags, CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.setReturnValue(AERecipeProfileManager.writeConfigCardData((net.minecraft.tileentity.TileEntity) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "setConfigurationData", at = @At("TAIL"))
    private void mekceuaeupgrade$setConfigurationData(NBTTagCompound nbtTags, CallbackInfo ci) {
        AERecipeProfileManager.readConfigCardData((net.minecraft.tileentity.TileEntity) (Object) this, nbtTags);
        mekceuaeupgrade$onAERecipePortsChanged();
    }
}
