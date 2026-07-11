package mekceuaeupgrade.mixin.mekanism;

import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEOutputHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeOutputDrainer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = TileEntityDigitalMiner.class, remap = false)
public abstract class MixinTileEntityDigitalMiner implements IAEOutputHost, IAEUpgradeHostBridge {

    @Shadow
    private List<IInventorySlot> mainSlots;

    @Shadow
    public abstract boolean isReplaceStack(ItemStack stack);

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
        if (mainSlots == null || mainSlots.isEmpty()) {
            return false;
        }
        boolean drained = false;
        for (IInventorySlot slot : mainSlots) {
            node.observeOutputContainer(slot);
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || isReplaceStack(stack)) {
                continue;
            }
            drained |= AEUpgradeOutputDrainer.drainItemSlot(node, slot, AutomationType.EXTERNAL);
        }
        return drained;
    }
}
