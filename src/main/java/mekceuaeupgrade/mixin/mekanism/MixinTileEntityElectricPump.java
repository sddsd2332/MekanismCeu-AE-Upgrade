package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEOutputHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeOutputDrainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TileEntityElectricPump.class, remap = false)
public abstract class MixinTileEntityElectricPump implements IAEOutputHost, IAEUpgradeHostBridge {

    @Shadow
    public BasicFluidTank fluidTank;

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
        return AEUpgradeOutputDrainer.drainFluidTank(node, fluidTank);
    }
}
