package mekceuaeupgrade.common.integration.qio;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import mekanism.api.qio.external.IQIOStorageListener;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.UUID;

final class LegacyQIOStorageMonitorable implements IStorageMonitorable, IQIOStorageListener {

    private final TileEntityQIODashboard dashboard;
    private final EnumFacing side;
    private final QIOItemMonitor itemMonitor;
    private final QIOFluidMonitor fluidMonitor;
    private final QIOGasMonitor gasMonitor;
    private IActionSource actionSource;
    @Nullable
    private UUID requester;
    private IGrid grid;
    @Nullable
    private IQIOStorageView view;
    private boolean closed;

    LegacyQIOStorageMonitorable(TileEntityQIODashboard dashboard, EnumFacing side, IActionSource actionSource,
          @Nullable UUID requester, IGrid grid, IQIOStorageView view) {
        this.dashboard = dashboard;
        this.side = side;
        this.actionSource = actionSource;
        this.requester = requester;
        this.grid = grid;
        this.itemMonitor = new QIOItemMonitor(this,
              AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        this.fluidMonitor = new QIOFluidMonitor(this,
              AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        this.gasMonitor = new QIOGasMonitor(this,
              AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));
        bindView(view);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T> channel) {
        if (channel == itemMonitor.getChannel()) {
            return (IMEMonitor<T>) itemMonitor;
        }
        if (channel == fluidMonitor.getChannel()) {
            return (IMEMonitor<T>) fluidMonitor;
        }
        if (channel == gasMonitor.getChannel()) {
            return (IMEMonitor<T>) gasMonitor;
        }
        return null;
    }

    boolean matchesBinding(@Nullable UUID requester, @Nullable IGrid grid) {
        return !closed && this.grid == grid && java.util.Objects.equals(this.requester, requester);
    }

    void setActionSource(IActionSource actionSource) {
        this.actionSource = actionSource;
    }

    void rebind(IActionSource actionSource, @Nullable UUID requester, IGrid grid, IQIOStorageView newView) {
        if (closed) {
            newView.close();
            return;
        }
        prepareBindingChange();
        this.actionSource = actionSource;
        this.requester = requester;
        this.grid = grid;
        bindView(newView);
        finishBindingChange();
    }

    void unbind() {
        if (closed || view == null) {
            return;
        }
        prepareBindingChange();
        releaseView();
        finishBindingChange();
    }

    void close() {
        if (closed) {
            return;
        }
        prepareBindingChange();
        closed = true;
        releaseView();
        itemMonitor.close();
        fluidMonitor.close();
        gasMonitor.close();
    }

    boolean hasUsableView() {
        return !closed && view != null && view.isValid();
    }

    @Nullable
    QIOStorageSnapshot getSnapshot() {
        if (!hasUsableView()) {
            return null;
        }
        try {
            return view.getSnapshot();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @Nullable
    IQIOStorageView getView() {
        return hasUsableView() ? view : null;
    }

    IActionSource getActionSource() {
        return actionSource;
    }

    IGrid getGrid() {
        return grid;
    }

    @Nullable
    UUID getFrequencyUUID() {
        return hasUsableView() ? view.getFrequencyUUID() : null;
    }

    int getDimension() {
        return dashboard.getWorld() == null ? 0 : dashboard.getWorld().provider.getDimension();
    }

    int getX() {
        return dashboard.getPos().getX();
    }

    int getY() {
        return dashboard.getPos().getY();
    }

    int getZ() {
        return dashboard.getPos().getZ();
    }

    int getSideOrdinal() {
        return side.ordinal();
    }

    @Override
    public void onQIOStorageChanged(QIOStorageChangeBatch changes) {
        itemMonitor.onStorageChanged(changes);
        fluidMonitor.onStorageChanged(changes);
        gasMonitor.onStorageChanged(changes);
    }

    private void prepareBindingChange() {
        itemMonitor.onBindingChanging();
        fluidMonitor.onBindingChanging();
        gasMonitor.onBindingChanging();
        releaseView();
    }

    private void bindView(IQIOStorageView newView) {
        this.view = newView;
        if (!newView.addListener(this)) {
            newView.close();
            this.view = null;
        }
    }

    private void finishBindingChange() {
        itemMonitor.onBindingChanged();
        fluidMonitor.onBindingChanged();
        gasMonitor.onBindingChanged();
    }

    private void releaseView() {
        IQIOStorageView oldView = view;
        view = null;
        if (oldView != null) {
            oldView.removeListener(this);
            oldView.close();
        }
    }
}
