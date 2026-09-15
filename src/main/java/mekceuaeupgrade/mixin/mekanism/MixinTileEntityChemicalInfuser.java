package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.machine.TileEntityChemicalInfuser;
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

@Mixin(value = TileEntityChemicalInfuser.class, remap = false)
public abstract class MixinTileEntityChemicalInfuser extends TileEntityBasicMachine<ChemicalPairInput, GasOutput, ChemicalInfuserRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicGasTank leftTank;
    @Shadow
    public BasicGasTank rightTank;
    @Shadow
    public BasicGasTank centerTank;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityChemicalInfuser(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<ChemicalPairInput, ChemicalInfuserRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEGasItemRecipeAdapters.gasPairToGas(this::getRecipes, () -> leftTank, () -> rightTank,
                  () -> centerTank, this::refreshRecipeLookupCache, "chemical infuser");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
