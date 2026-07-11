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
