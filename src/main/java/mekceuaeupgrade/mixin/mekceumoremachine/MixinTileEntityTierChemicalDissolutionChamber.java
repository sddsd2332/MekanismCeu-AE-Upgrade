package mekceuaeupgrade.mixin.mekceumoremachine;

import mekanism.api.gas.Gas;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.MathUtils;
import mekanism.common.MekanismFluids;
import mekanism.common.recipe.RecipeHandler;
import mekceuaeupgrade.common.adapter.AEMoreMachineRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceumoremachine.common.capability.ResizableGasTank;
import mekceumoremachine.common.tier.MachineTier;
import mekceumoremachine.common.tile.machine.TierDissolution.TileEntityTierChemicalDissolutionChamber;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TileEntityTierChemicalDissolutionChamber.class, remap = false)
public abstract class MixinTileEntityTierChemicalDissolutionChamber implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    @Final
    private List<IInventorySlot> inputSlots;
    @Shadow
    public ResizableGasTank injectTank;
    @Shadow
    public ResizableGasTank[] outPutTanks;
    @Shadow
    public MachineTier tier;
    @Shadow
    private long baseTotalUsage;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

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
            mekceuaeupgrade$aeRecipeAdapter = AEMoreMachineRecipeAdapters.multiItemGasToGas(
                  () -> RecipeHandler.Recipe.CHEMICAL_DISSOLUTION_CHAMBER.get(), () -> inputSlots, () -> injectTank,
                  () -> outPutTanks, () -> tier.processes, () -> MekanismFluids.SulfuricAcid,
                  this::mekceuaeupgrade$isValidGas, this::mekceuaeupgrade$getAEGasUsagePerOperation, () -> {
                  }, "tier chemical dissolution chamber");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

    @Unique
    private int mekceuaeupgrade$getAEGasUsagePerOperation() {
        return Math.max(1, MathUtils.clampToInt(baseTotalUsage));
    }

    @Unique
    private boolean mekceuaeupgrade$isValidGas(Gas gas) {
        return gas == MekanismFluids.SulfuricAcid;
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
    }
}
