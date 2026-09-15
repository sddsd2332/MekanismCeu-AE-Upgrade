package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.OxidationRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.machine.TileEntityChemicalOxidizer;
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

@Mixin(value = TileEntityChemicalOxidizer.class, remap = false)
public abstract class MixinTileEntityChemicalOxidizer extends TileEntityBasicMachine<ItemStackInput, GasOutput, OxidationRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicGasTank gasTank;
    @Shadow
    private InputInventorySlot inputSlot;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityChemicalOxidizer(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<ItemStackInput, OxidationRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEGasItemRecipeAdapters.itemToGas(this::getRecipes, () -> inputSlot, () -> gasTank,
                  this::refreshRecipeLookupCache);
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
