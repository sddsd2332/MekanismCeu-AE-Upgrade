package mekceuaeupgrade.common.integration.qio;

import appeng.api.storage.IStorageChannel;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageResourceKind;

import javax.annotation.Nullable;

final class QIOGasMonitor extends AbstractQIOMEMonitor<IAEGasStack> {

    QIOGasMonitor(LegacyQIOStorageMonitorable owner, IStorageChannel<IAEGasStack> channel) {
        super(owner, channel, QIOStorageResourceKind.GAS);
    }

    @Override
    protected boolean isValidInput(IAEGasStack stack) {
        return stack != null && stack.getGas() != null;
    }

    @Nullable
    @Override
    protected IAEGasStack toAEStack(QIOStorageEntry entry, long amount) {
        GasStack template = entry.getGas();
        IAEGasStack stack = template == null ? null : AEGasStack.of(template);
        return stack == null ? null : stack.setStackSize(amount);
    }

    @Override
    protected long insert(IQIOStorageView view, IAEGasStack stack, long amount, Action action) {
        GasStack template = stack.getGasStack();
        if (template == null) {
            return 0;
        }
        template.amount = 1;
        return view.insert(template, amount, action);
    }

    @Override
    protected long extract(IQIOStorageView view, IAEGasStack stack, long amount, Action action) {
        GasStack template = stack.getGasStack();
        if (template == null) {
            return 0;
        }
        template.amount = 1;
        return view.extract(template, amount, action);
    }
}
