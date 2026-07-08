package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.adapter.AEGasItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.api.gas.Gas;
import mekanism.api.math.MathUtils;
import mekanism.common.Upgrade;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = TileEntityFarmMachine.class, remap = false)
public abstract class MixinTileEntityFarmMachine<RECIPE extends FarmMachineRecipe<RECIPE>>
      extends TileEntityUpgradeableMachine<AdvancedMachineInput, ChanceOutput, RECIPE> implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public double secondaryEnergyPerTick;
    @Shadow
    public BasicGasTank gasTank;
    @Shadow
    protected InputInventorySlot inputSlot;
    @Shadow
    protected GasInventorySlot gasSlot;
    @Shadow
    protected OutputInventorySlot outputSlot;
    @Shadow
    protected OutputInventorySlot secondaryOutputSlot;
    @Shadow
    private long baseTotalUsage;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityFarmMachine(String soundPath, MachineType type, int ticksRequired, int secondaryPerTick) {
        super(soundPath, type, 5, ticksRequired);
    }

    @Shadow
    public abstract Map<AdvancedMachineInput, RECIPE> getRecipes();

    @Shadow
    public abstract boolean isValidGas(Gas gas);

    @Shadow
    public abstract boolean useStatisticalMechanics();

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
            mekceuaeupgrade$aeRecipeAdapter = AEGasItemRecipeAdapters.farmItemGasToItem(this::getRecipes, () -> inputSlot,
                  () -> gasSlot, () -> outputSlot, () -> secondaryOutputSlot, () -> gasTank, this::isValidGas,
                  this::mekceuaeupgrade$supportsAEFarmGasItemRecipes, this::mekceuaeupgrade$getAEGasUsagePerOperation,
                  this::refreshRecipeLookupCache);
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

    @Inject(method = "recalculateUpgradables", at = @At("TAIL"))
    private void mekceuaeupgrade$recalculateUpgradables(Upgrade upgrade, CallbackInfo ci) {
        if (upgrade == Upgrade.SPEED || upgrade == Upgrade.GAS) {
            mekceuaeupgrade$invalidateAERecipeCache();
        }
    }

    @Unique
    private boolean mekceuaeupgrade$supportsAEFarmGasItemRecipes() {
        return mekceuaeupgrade$getAEGasUsagePerOperation() > 0;
    }

    @Unique
    private int mekceuaeupgrade$getAEGasUsagePerOperation() {
        long usage = useStatisticalMechanics() ? mekceuaeupgrade$getAEStatisticalGasUsageLimit() : baseTotalUsage;
        return Math.max(1, MathUtils.clampToInt(usage));
    }

    @Unique
    private long mekceuaeupgrade$getAEStatisticalGasUsageLimit() {
        int ticks = Math.max(1, getTicksRequired());
        long maxPerTick = 3L * Math.max(1, (long) Math.ceil(Math.max(secondaryEnergyPerTick, 0)));
        return maxPerTick * ticks;
    }
}
