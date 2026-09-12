package mekceuaeupgrade.mixin.mekceumoremachine;

import mekanism.common.Upgrade;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmRecipe;
import mekceuaeupgrade.common.adapter.AEFarmRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceumoremachine.common.inventory.slot.FarmOutputInventorySlot;
import mekceumoremachine.common.tile.machine.TierOrganicFarm.TileEntityTierOrganicFarm;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Mixin(value = TileEntityTierOrganicFarm.class, remap = false)
public abstract class MixinTileEntityTierOrganicFarm implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    @Final
    public InputInventorySlot[] inputSlots;
    @Shadow
    @Final
    public List<FarmOutputInventorySlot> outputSlots;
    @Shadow
    @Final
    public int threadCount;
    @Shadow
    public MergedTank mergedTank;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    @Shadow
    public abstract Map<FarmInput, FarmRecipe> getRecipes();

    @Shadow
    public abstract int getRecipeGasUsagePerOperation();

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
            mekceuaeupgrade$aeRecipeAdapter = AEFarmRecipeAdapters.itemMediumToItems(this::getRecipes,
                  () -> Arrays.asList(inputSlots), () -> mergedTank.getGasTank(), () -> mergedTank.getFluidTank(),
                  () -> outputSlots, () -> threadCount, this::getRecipeGasUsagePerOperation, () -> {
                  }, "tier organic farm");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

    @Inject(method = "onRecipeCacheInvalidated", at = @At("TAIL"))
    private void mekceuaeupgrade$onRecipeCacheInvalidated(int cacheIndex, CallbackInfo ci) {
        mekceuaeupgrade$invalidateAERecipeCache();
    }

    @Inject(method = "recalculateUpgradables", at = @At("TAIL"))
    private void mekceuaeupgrade$recalculateUpgradables(Upgrade upgrade, CallbackInfo ci) {
        if (upgrade == Upgrade.SPEED || upgrade == Upgrade.GAS) {
            mekceuaeupgrade$invalidateAERecipeCache();
        }
    }

    @Inject(method = "getConfigurationData", at = @At("RETURN"), cancellable = true, require = 1)
    private void mekceuaeupgrade$getConfigurationData(NBTTagCompound nbtTags, CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.setReturnValue(AERecipeProfileManager.writeConfigCardData(
              (net.minecraft.tileentity.TileEntity) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "setConfigurationData", at = @At("TAIL"), require = 1)
    private void mekceuaeupgrade$setConfigurationData(NBTTagCompound nbtTags, CallbackInfo ci) {
        AERecipeProfileManager.readConfigCardData((net.minecraft.tileentity.TileEntity) (Object) this, nbtTags);
    }
}
