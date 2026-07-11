package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEOutputHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeOutputDrainer;

import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.tile.machine.TileEntityAmbientAccumulatorEnergy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TileEntityAmbientAccumulatorEnergy.class, remap = false)
public abstract class MixinTileEntityAmbientAccumulatorEnergy implements IAEOutputHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicGasTank outputTank;

    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Override
    public AEUpgradeHostDelegate mekceuaeupgrade$getAEUpgradeDelegate() {
        if (mekceuaeupgrade$aeUpgrade == null) {
            mekceuaeupgrade$aeUpgrade = new AEUpgradeHostDelegate(this);
        }
        return mekceuaeupgrade$aeUpgrade;
    }

    @Override
    public boolean drainAEOutputs(AEUpgradeNode node) {
        return AEUpgradeOutputDrainer.drainGasTank(node, outputTank);
    }
}
