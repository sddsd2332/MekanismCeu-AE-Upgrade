package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHost;

import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(value = TileEntityBasicMachine.class, remap = false)
public abstract class MixinTileEntityBasicMachine implements ISpecialConfigData {

    @Inject(method = "hasCapability", at = @At("HEAD"), cancellable = true)
    private void mekceuaeupgrade$hasCapability(Capability<?> capability, EnumFacing side, CallbackInfoReturnable<Boolean> cir) {
        if (!mekceuaeupgrade$self().isCapabilityDisabled(capability, side) && capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private <T> void mekceuaeupgrade$getCapability(Capability<T> capability, EnumFacing side, CallbackInfoReturnable<T> cir) {
        if (!mekceuaeupgrade$self().isCapabilityDisabled(capability, side) && capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            cir.setReturnValue((T) (Object) this);
        }
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        IAEUpgradeHost host = mekceuaeupgrade$getAEHost();
        if (host != null) {
            return AERecipeProfileManager.writeConfigCardData(mekceuaeupgrade$self(), nbtTags);
        }
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        IAEUpgradeHost host = mekceuaeupgrade$getAEHost();
        if (host != null) {
            AERecipeProfileManager.readConfigCardData(mekceuaeupgrade$self(), nbtTags);
        }
    }

    @Override
    public String getDataType() {
        return mekceuaeupgrade$self().getName();
    }

    @Inject(method = "onRecipeCacheInvalidated", at = @At("TAIL"))
    private void mekceuaeupgrade$onRecipeCacheInvalidated(int cacheIndex, CallbackInfo ci) {
        IAEUpgradeHost host = mekceuaeupgrade$getAEHost();
        if (host != null) {
            host.getAEUpgradeNode().invalidateRecipeCache();
        }
    }

    @Unique
    @Nullable
    private IAEUpgradeHost mekceuaeupgrade$getAEHost() {
        Object self = this;
        return self instanceof IAEUpgradeHost ? (IAEUpgradeHost) self : null;
    }

    @Unique
    private TileEntityBasicMachine mekceuaeupgrade$self() {
        return (TileEntityBasicMachine) (Object) this;
    }
}
