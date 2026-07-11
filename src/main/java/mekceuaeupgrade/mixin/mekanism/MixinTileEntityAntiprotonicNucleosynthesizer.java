package mekceuaeupgrade.mixin.mekanism;

import mekanism.api.gas.Gas;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import mekceuaeupgrade.common.adapter.AEHybridRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityAntiprotonicNucleosynthesizer.class, remap = false)
public abstract class MixinTileEntityAntiprotonicNucleosynthesizer
      extends TileEntityUpgradeableMachine<NucleosynthesizerInput, ItemStackOutput, NucleosynthesizerRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicGasTank inputGasTank;
    @Shadow
    private GasInventorySlot gasInputSlot;
    @Shadow
    private InputInventorySlot inputSlot;
    @Shadow
    private OutputInventorySlot outputSlot;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityAntiprotonicNucleosynthesizer(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<NucleosynthesizerInput, NucleosynthesizerRecipe> getRecipes();

    @Shadow
    private boolean isValidGas(Gas gas) {
        return false;
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
            mekceuaeupgrade$aeRecipeAdapter = AEHybridRecipeAdapters.nucleosynthesizerItemGasToItem(this::getRecipes,
                  () -> inputSlot, () -> gasInputSlot, () -> outputSlot, () -> inputGasTank, this::isValidGas, this::refreshRecipeLookupCache);
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
