package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.Upgrade;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import mekceuaeupgrade.common.adapter.AEFarmRecipeAdapters;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mixin(value = TileEntityFarmMachine.class, remap = false)
public abstract class MixinTileEntityFarmMachine<RECIPE extends FarmMachineRecipe<RECIPE>>
      extends TileEntityUpgradeableMachine<FarmInput, FarmOutput, RECIPE> implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    @Shadow
    protected InputInventorySlot inputSlot;
    @Shadow
    public MergedTank mergedTank;
    @Shadow
    @Final
    protected List<OutputInventorySlot> outputSlots;
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;
    @Unique
    private IAERecipeMachineAdapter mekceuaeupgrade$aeRecipeAdapter;

    protected MixinTileEntityFarmMachine(String soundPath, MachineType type, int ticksRequired, int secondaryPerTick) {
        super(soundPath, type, 5, ticksRequired);
    }

    @Shadow
    public abstract Map<FarmInput, RECIPE> getRecipes();

    @Shadow
    public abstract int getRecipeGasUsagePerOperation();

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
            mekceuaeupgrade$aeRecipeAdapter = AEFarmRecipeAdapters.itemMediumToItems(this::getRecipes,
                  () -> Collections.singletonList(inputSlot), () -> mergedTank.getGasTank(), () -> mergedTank.getFluidTank(),
                  () -> outputSlots, () -> 1, this::getRecipeGasUsagePerOperation, this::refreshRecipeLookupCache,
                  "organic farm");
        }
        return mekceuaeupgrade$aeRecipeAdapter;
    }

    @Inject(method = "recalculateUpgradables", at = @At("TAIL"))
    private void mekceuaeupgrade$recalculateUpgradables(Upgrade upgrade, CallbackInfo ci) {
        if (upgrade == Upgrade.SPEED || upgrade == Upgrade.GAS) {
            mekceuaeupgrade$invalidateAERecipeCache();
        }
    }
}
