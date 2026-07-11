package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.adapter.AEGasItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.tile.machine.TileEntitySolarNeutronActivator;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntitySolarNeutronActivator.class, remap = false)
public abstract class MixinTileEntitySolarNeutronActivator implements IAERecipeMachineHost, IAEUpgradeHostBridge, ISpecialConfigData {

    @Shadow
    public BasicGasTank inputTank;
    @Shadow
    public BasicGasTank outputTank;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    @Shadow
    private void refreshRecipeLookupCache() {
        throw new AssertionError();
    }

    @Override
    public AEUpgradeHostDelegate mekceuaeupgrade$getAEUpgradeDelegate() {
        if (mekceuaeupgrade$aeUpgrade == null) {
            mekceuaeupgrade$aeUpgrade = new AEUpgradeHostDelegate(this);
        }
        return mekceuaeupgrade$aeUpgrade;
    }

    @Override
    public IAERecipeMachineAdapter getAERecipeMachineAdapter() {
        if (mekceuaeupgrade$aeRecipeAdapter == null) {
            mekceuaeupgrade$aeRecipeAdapter = AEGasItemRecipeAdapters.gasToGas(() -> RecipeHandler.Recipe.SOLAR_NEUTRON_ACTIVATOR.get(),
                  () -> inputTank, () -> outputTank, this::refreshRecipeLookupCache, "solar neutron");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

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

    @Inject(method = "onRecipeCacheInvalidated", at = @At("TAIL"))
    private void mekceuaeupgrade$onRecipeCacheInvalidated(int cacheIndex, CallbackInfo ci) {
        mekceuaeupgrade$invalidateAERecipeCache();
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        return AERecipeProfileManager.writeConfigCardData(mekceuaeupgrade$self(), nbtTags);
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        AERecipeProfileManager.readConfigCardData(mekceuaeupgrade$self(), nbtTags);
    }

    @Override
    public String getDataType() {
        return mekceuaeupgrade$self().getName();
    }

    @Unique
    private TileEntitySolarNeutronActivator mekceuaeupgrade$self() {
        return (TileEntitySolarNeutronActivator) (Object) this;
    }
}
