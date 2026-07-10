package mekceuaeupgrade.mixin.mekceumoremachine;

import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEOutputHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeOutputDrainer;

import mekceumoremachine.common.capability.ResizableGasTank;
import mekceumoremachine.common.tile.machine.TileEntityTierAmbientAccumulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TileEntityTierAmbientAccumulator.class, remap = false)
public abstract class MixinTileEntityTierAmbientAccumulator implements IAEOutputHost, IAEUpgradeHostBridge {

    @Shadow
    public ResizableGasTank outputTank;

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
