package mekceuaeupgrade.mixin.mekceumoremachine;

import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.machines.ReplicatorFluidStackRecipe;
import mekanism.common.recipe.outputs.FluidOutput;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekceuaeupgrade.common.adapter.AEMoreMachineRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceumoremachine.common.capability.ResizableFluidTank;
import mekceumoremachine.common.capability.ResizableGasTank;
import mekceumoremachine.common.tile.machine.replicator.TileEntityReplicatorFluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityReplicatorFluidStack.class, remap = false)
public abstract class MixinTileEntityReplicatorFluidStack extends TileEntityBasicMachine<GasAndFluidInput, FluidOutput, ReplicatorFluidStackRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public ResizableFluidTank inputTank;
    @Shadow
    public ResizableGasTank uuTank;
    @Shadow
    public ResizableFluidTank outputTank;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityReplicatorFluidStack(String soundPath, String name, double energyStorage, double energyUsage, int upgradeSlot,
          int baseTicksRequired) {
        super(soundPath, name, energyStorage, energyUsage, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<GasAndFluidInput, ReplicatorFluidStackRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEMoreMachineRecipeAdapters.replicatorFluidTemplateToFluid(this::getRecipes,
                  () -> inputTank, () -> uuTank, () -> outputTank, this::refreshRecipeLookupCache, "replicator fluid stack");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
