package mekceuaeupgrade.mixin.multiblockmachine;

import mekceuaeupgrade.common.adapter.AEGasItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.recipe.RecipeHandler;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
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

@Mixin(value = TileEntityLargeSolarNeutronActivator.class, remap = false)
public abstract class MixinTileEntityLargeSolarNeutronActivator implements IAERecipeMachineHost, IAEUpgradeHostBridge {

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
                  () -> inputTank, () -> outputTank, this::refreshRecipeLookupCache, "large solar neutron activator");
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

    @Inject(method = "getConfigurationData", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$getConfigurationData(NBTTagCompound nbtTags, CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.setReturnValue(AERecipeProfileManager.writeConfigCardData(mekceuaeupgrade$self(), cir.getReturnValue()));
    }

    @Inject(method = "setConfigurationData", at = @At("TAIL"))
    private void mekceuaeupgrade$setConfigurationData(NBTTagCompound nbtTags, CallbackInfo ci) {
        AERecipeProfileManager.readConfigCardData(mekceuaeupgrade$self(), nbtTags);
    }

    @Unique
    private TileEntityLargeSolarNeutronActivator mekceuaeupgrade$self() {
        return (TileEntityLargeSolarNeutronActivator) (Object) this;
    }
}
