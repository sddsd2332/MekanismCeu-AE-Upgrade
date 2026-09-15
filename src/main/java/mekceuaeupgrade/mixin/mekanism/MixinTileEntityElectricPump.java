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
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;

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
    public boolean drainAEOutputs(AEUpgradeNode node) {
        return AEUpgradeOutputDrainer.drainFluidTank(node, fluidTank);
    }
}
