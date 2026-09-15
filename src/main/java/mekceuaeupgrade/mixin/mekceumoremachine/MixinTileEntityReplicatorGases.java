package mekceuaeupgrade.mixin.mekceumoremachine;

import mekanism.common.recipe.inputs.ChemicalGasInput;
import mekanism.common.recipe.machines.ReplicatorGasStackRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekceuaeupgrade.common.adapter.AEMoreMachineRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceumoremachine.common.capability.ResizableGasTank;
import mekceumoremachine.common.tile.machine.replicator.TileEntityReplicatorGases;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityReplicatorGases.class, remap = false)
public abstract class MixinTileEntityReplicatorGases extends TileEntityBasicMachine<ChemicalGasInput, GasOutput, ReplicatorGasStackRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public ResizableGasTank inputTank;
    @Shadow
    public ResizableGasTank uuTank;
    @Shadow
    public ResizableGasTank outputTank;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityReplicatorGases(String soundPath, String name, double energyStorage, double energyUsage, int upgradeSlot,
          int baseTicksRequired) {
        super(soundPath, name, energyStorage, energyUsage, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<ChemicalGasInput, ReplicatorGasStackRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEMoreMachineRecipeAdapters.replicatorGasTemplateToGas(this::getRecipes,
                  () -> inputTank, () -> uuTank, () -> outputTank, this::refreshRecipeLookupCache, "replicator gases");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
