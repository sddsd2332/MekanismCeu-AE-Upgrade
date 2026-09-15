package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.outputs.PressurizedOutput;
import mekanism.common.tile.machine.TileEntityPRC;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import mekceuaeupgrade.common.adapter.AEHybridRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = TileEntityPRC.class, remap = false)
public abstract class MixinTileEntityPRC extends TileEntityUpgradeableMachine<PressurizedInput, PressurizedOutput, PressurizedRecipe>
      implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicFluidTank inputFluidTank;
    @Shadow
    public BasicGasTank inputGasTank;
    @Shadow
    public BasicGasTank outputGasTank;
    @Shadow
    private InputInventorySlot inputSlot;
    @Shadow
    private OutputInventorySlot outputSlot;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityPRC(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    @Shadow
    public abstract Map<PressurizedInput, PressurizedRecipe> getRecipes();

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
            mekceuaeupgrade$aeRecipeAdapter = AEHybridRecipeAdapters.pressurizedItemFluidGas(this::getRecipes, () -> inputSlot,
                  () -> inputFluidTank, () -> inputGasTank, () -> outputSlot, () -> outputGasTank, this::refreshRecipeLookupCache,
                  "pressurized reaction chamber");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

}
