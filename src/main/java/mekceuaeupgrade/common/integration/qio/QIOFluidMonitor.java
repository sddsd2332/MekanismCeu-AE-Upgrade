package mekceuaeupgrade.common.integration.qio;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;
import mekanism.api.Action;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageResourceKind;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

final class QIOFluidMonitor extends AbstractQIOMEMonitor<IAEFluidStack> {

    QIOFluidMonitor(LegacyQIOStorageMonitorable owner, IStorageChannel<IAEFluidStack> channel) {
        super(owner, channel, QIOStorageResourceKind.FLUID);
    }

    @Override
    protected boolean isValidInput(IAEFluidStack stack) {
        return stack != null && stack.getFluid() != null;
    }

    @Nullable
    @Override
    protected IAEFluidStack toAEStack(QIOStorageEntry entry, long amount) {
        FluidStack template = entry.getFluid();
        IAEFluidStack stack = template == null ? null : AEFluidStack.fromFluidStack(template);
        return stack == null ? null : stack.setStackSize(amount);
    }

    @Override
    protected long insert(IQIOStorageView view, IAEFluidStack stack, long amount, Action action) {
        FluidStack template = stack.getFluidStack();
        if (template == null) {
            return 0;
        }
        template.amount = 1;
        return view.insert(template, amount, action);
    }

    @Override
    protected long extract(IQIOStorageView view, IAEFluidStack stack, long amount, Action action) {
        FluidStack template = stack.getFluidStack();
        if (template == null) {
            return 0;
        }
        template.amount = 1;
        return view.extract(template, amount, action);
    }
}
