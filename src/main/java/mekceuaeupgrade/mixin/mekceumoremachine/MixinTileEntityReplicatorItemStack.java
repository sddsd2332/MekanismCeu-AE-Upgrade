package mekceuaeupgrade.mixin.mekceumoremachine;

import mekceuaeupgrade.common.adapter.AEMoreMachineRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.machines.ReplicatorItemStackRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekceumoremachine.common.capability.ResizableGasTank;
import mekceumoremachine.common.tile.machine.replicator.TileEntityReplicatorItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityReplicatorItemStack.class, remap = false)
public abstract class MixinTileEntityReplicatorItemStack extends TileEntityBasicMachine<NucleosynthesizerInput, ItemStackOutput, ReplicatorItemStackRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public ResizableGasTank inputGasTank;
    @Shadow
    private BasicInventorySlot inputSlot;
    @Shadow
    private OutputInventorySlot outputSlot;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityReplicatorItemStack(String soundPath, String name, double energyStorage, double energyUsage, int upgradeSlot,
          int baseTicksRequired) {
        super(soundPath, name, energyStorage, energyUsage, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<NucleosynthesizerInput, ReplicatorItemStackRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEMoreMachineRecipeAdapters.replicatorItemTemplateToItem(this::getRecipes,
                  () -> inputSlot, () -> inputGasTank, () -> outputSlot, gas -> true, this::refreshRecipeLookupCache,
                  "replicator item stack");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
