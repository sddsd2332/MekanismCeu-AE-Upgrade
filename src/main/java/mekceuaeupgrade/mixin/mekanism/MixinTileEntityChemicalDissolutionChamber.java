package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.adapter.AEHybridRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.api.gas.Gas;
import mekanism.api.math.MathUtils;
import mekanism.common.MekanismFluids;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.DissolutionRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityChemicalDissolutionChamber.class, remap = false)
public abstract class MixinTileEntityChemicalDissolutionChamber extends TileEntityBasicMachine<ItemStackInput, GasOutput, DissolutionRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicGasTank injectTank;
    @Shadow
    public BasicGasTank outputTank;
    @Shadow
    private InputInventorySlot inputSlot;
    @Shadow
    private long baseTotalUsage;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityChemicalDissolutionChamber(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<ItemStackInput, DissolutionRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEHybridRecipeAdapters.itemGasToGas(this::getRecipes, () -> inputSlot,
                  () -> injectTank, () -> outputTank, () -> MekanismFluids.SulfuricAcid, this::mekceuaeupgrade$isValidGas,
                  this::mekceuaeupgrade$getAEGasUsagePerOperation, this::refreshRecipeLookupCache, "chemical dissolution chamber");
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
}
