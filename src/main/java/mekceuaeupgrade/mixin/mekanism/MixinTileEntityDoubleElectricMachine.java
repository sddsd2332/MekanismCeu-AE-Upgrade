package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.prefab.TileEntityDoubleElectricMachine;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import mekceuaeupgrade.common.adapter.AEItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TileEntityDoubleElectricMachine.class, remap = false)
public abstract class MixinTileEntityDoubleElectricMachine<RECIPE extends DoubleMachineRecipe<RECIPE>>
      extends TileEntityUpgradeableMachine<DoubleMachineInput, ItemStackOutput, RECIPE> implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    protected InputInventorySlot inputSlot;
    @Shadow
    protected InputInventorySlot extraSlot;
    @Shadow
    protected OutputInventorySlot outputSlot;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityDoubleElectricMachine(String soundPath, MachineType type, int ticksRequired) {
        super(soundPath, type, 4, ticksRequired);
    }

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
            mekceuaeupgrade$aeRecipeAdapter = AEItemRecipeAdapters.doubleItemToItem(this::getRecipes, () -> inputSlot, () -> extraSlot,
                  () -> outputSlot, this::refreshRecipeLookupCache);
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
