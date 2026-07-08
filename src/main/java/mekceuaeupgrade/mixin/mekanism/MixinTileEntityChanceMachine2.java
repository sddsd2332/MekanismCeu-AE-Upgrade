package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.adapter.AEItemRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.Chance2MachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput2;
import mekanism.common.tile.prefab.TileEntityChanceMachine2;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityChanceMachine2.class, remap = false)
public abstract class MixinTileEntityChanceMachine2<RECIPE extends Chance2MachineRecipe<RECIPE>>
      extends TileEntityUpgradeableMachine<ItemStackInput, ChanceOutput2, RECIPE> implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    protected InputInventorySlot inputSlot;
    @Shadow
    protected OutputInventorySlot outputSlot;
    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityChanceMachine2(String soundPath, MachineType type, int ticksRequired) {
        super(soundPath, type, 3, ticksRequired);
    }

    @Shadow
    public abstract Map<ItemStackInput, RECIPE> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEItemRecipeAdapters.guaranteedChance2ItemToItem(this::getRecipes, () -> inputSlot,
                  () -> outputSlot, this::refreshRecipeLookupCache);
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }
}
