package mekceuaeupgrade.mixin.mekceumoremachine;

import mekanism.api.inventory.IInventorySlot;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEOutputHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeOutputDrainer;
import mekceumoremachine.common.tile.machine.TileEntityVoidMineralGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = TileEntityVoidMineralGenerator.class, remap = false)
public abstract class MixinTileEntityVoidMineralGenerator implements IAEOutputHost, IAEUpgradeHostBridge {

    @Shadow
    private List<IInventorySlot> outputSlots;

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
        if (outputSlots == null || outputSlots.isEmpty()) {
            return false;
        }
        boolean drained = false;
        for (IInventorySlot slot : outputSlots) {
            drained |= AEUpgradeOutputDrainer.drainItemSlot(node, slot);
        }
        return drained;
    }
}
