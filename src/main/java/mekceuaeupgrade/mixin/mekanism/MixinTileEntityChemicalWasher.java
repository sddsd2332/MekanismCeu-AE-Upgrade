package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.machines.WasherRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.machine.TileEntityChemicalWasher;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekceuaeupgrade.common.adapter.AEGasItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityChemicalWasher.class, remap = false)
public abstract class MixinTileEntityChemicalWasher extends TileEntityBasicMachine<GasAndFluidInput, GasOutput, WasherRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicFluidTank fluidTank;
    @Shadow
    public BasicGasTank inputTank;
    @Shadow
    public BasicGasTank outputTank;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityChemicalWasher(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<GasAndFluidInput, WasherRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEGasItemRecipeAdapters.gasFluidToGas(this::getRecipes, () -> inputTank,
                  () -> fluidTank, () -> outputTank, this::refreshRecipeLookupCache, "chemical washer");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
