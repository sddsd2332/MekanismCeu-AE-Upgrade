package mekceuaeupgrade.mixin.multiblockmachine;

import mekceuaeupgrade.common.adapter.AEGasItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = TileEntityLargeElectrolyticSeparator.class, remap = false)
public abstract class MixinTileEntityLargeElectrolyticSeparator extends TileEntityBasicMachine<FluidInput, ChemicalPairOutput, SeparatorRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicFluidTank fluidTank;
    @Shadow
    public BasicGasTank leftTank;
    @Shadow
    public BasicGasTank rightTank;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityLargeElectrolyticSeparator(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<FluidInput, SeparatorRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEGasItemRecipeAdapters.fluidToGasPair(this::getRecipes, () -> fluidTank, () -> leftTank,
                  () -> rightTank, this::refreshRecipeLookupCache, "large electrolytic separator");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

    @Inject(method = "getConfigurationData", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$getConfigurationData(NBTTagCompound nbtTags, CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.setReturnValue(AERecipeProfileManager.writeConfigCardData((net.minecraft.tileentity.TileEntity) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "setConfigurationData", at = @At("TAIL"))
    private void mekceuaeupgrade$setConfigurationData(NBTTagCompound nbtTags, CallbackInfo ci) {
        AERecipeProfileManager.readConfigCardData((net.minecraft.tileentity.TileEntity) (Object) this, nbtTags);
    }
}
