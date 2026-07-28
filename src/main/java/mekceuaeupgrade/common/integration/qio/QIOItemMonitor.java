package mekceuaeupgrade.common.integration.qio;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import mekanism.api.Action;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageResourceKind;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

final class QIOItemMonitor extends AbstractQIOMEMonitor<IAEItemStack> {

    QIOItemMonitor(LegacyQIOStorageMonitorable owner, IStorageChannel<IAEItemStack> channel) {
        super(owner, channel, QIOStorageResourceKind.ITEM);
    }

    @Override
    protected boolean isValidInput(IAEItemStack stack) {
        return stack != null && !stack.getDefinition().isEmpty();
    }

    @Nullable
    @Override
    protected IAEItemStack toAEStack(QIOStorageEntry entry, long amount) {
        ItemStack template = entry.getItem();
        IAEItemStack stack = AEItemStack.fromItemStack(template);
        return stack == null ? null : stack.setStackSize(amount);
    }

    @Override
    protected long insert(IQIOStorageView view, IAEItemStack stack, long amount, Action action) {
        ItemStack template = stack.getDefinition().copy();
        template.setCount(1);
        return view.insert(template, amount, action);
    }

    @Override
    protected long extract(IQIOStorageView view, IAEItemStack stack, long amount, Action action) {
        ItemStack template = stack.getDefinition().copy();
        template.setCount(1);
        return view.extract(template, amount, action);
    }
}
