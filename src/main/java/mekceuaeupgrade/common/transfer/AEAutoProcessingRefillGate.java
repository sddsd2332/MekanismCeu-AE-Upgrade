package mekceuaeupgrade.common.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.IContentsListenerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

/** Non-owning admission hint for one item input group and one enabled recipe. Never pauses output/recovery. */
public final class AEAutoProcessingRefillGate implements AutoCloseable {
    private static final int LOW_WATERMARK_OPERATIONS = 4;
    private final List<IInventorySlot> slots;
    private final ItemStack input;
    private final List<IContentsListenerRegistry> observed = new ArrayList<>();
    private final AtomicLong generation = new AtomicLong();
    private final IContentsListener listener = generation::incrementAndGet;
    private long checkedGeneration = -1, lastCheck = Long.MIN_VALUE;
    private boolean waiting;
    private boolean empty;
    private boolean lowWatermark;

    /** Largest batches are chosen in complete lane rounds when the whole group is empty. */
    public int batchMultiple() {return empty ? slots.size() : 1;}

    private AEAutoProcessingRefillGate(List<IInventorySlot> slots, ItemStack input) {
        this.slots = new ArrayList<>(slots); this.input = input.copy();
    }

    public static AEAutoProcessingRefillGate create(List<IInventorySlot> slots, ItemStack input) {
        if (slots.isEmpty() || input.isEmpty()) return null;
        AEAutoProcessingRefillGate gate = new AEAutoProcessingRefillGate(slots, input);
        for (IInventorySlot slot : slots) {
            if (!(slot instanceof IContentsListenerRegistry registry) || !registry.addContentsListener(gate.listener)) {
                gate.close(); return null;
            }
            gate.observed.add(registry);
        }
        return gate;
    }

    public boolean shouldWait(long tick, Function<Supplier<Boolean>, Boolean> transaction) {
        if (needsCheck(tick)) transaction.apply(() -> { refreshInsideTransaction(tick); return waiting; });
        return waiting;
    }

    boolean needsCheck(long tick) {
        return generation.get() != checkedGeneration || tick == Long.MIN_VALUE || lastCheck == Long.MIN_VALUE ||
              tick < lastCheck || tick - lastCheck >= 20;
    }

    /** Called only while the host container transaction is held, including by the output gate. */
    void refreshInsideTransaction(long tick) {
        long before = generation.get();
        if (before != checkedGeneration || tick == Long.MIN_VALUE || lastCheck == Long.MIN_VALUE ||
              tick < lastCheck || tick - lastCheck >= 20) {
            waiting = hasWholeBatchRemaining();
            checkedGeneration = before;
            lastCheck = tick;
        }
    }

    boolean usesInputs(List<IInventorySlot> other) {
        if (slots.size() != other.size()) return false;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i) != other.get(i)) return false;
        return true;
    }

    boolean inputsEmpty() { return empty; }

    /** Valid only until a listener change or the existing fallback inspection deadline. */
    public boolean canKeepWaiting(long tick) {
        return waiting && generation.get() == checkedGeneration && tick != Long.MIN_VALUE &&
              lastCheck != Long.MIN_VALUE && tick >= lastCheck && tick - lastCheck < 20;
    }

    private boolean hasWholeBatchRemaining() {
        empty = false;
        lowWatermark = false;
        boolean occupied = false;
        for (IInventorySlot slot : slots) {
            ItemStack current = slot.getStack();
            if (current.isEmpty()) continue;
            // Foreign materials or a stranded partial recipe must keep the original admission path.
            if (!ItemHandlerHelper.canItemStacksStack(current, input) || current.getCount() % input.getCount() != 0) return false;
            occupied = true;
            if (current.getCount() <= input.getCount() * LOW_WATERMARK_OPERATIONS) {
                lowWatermark = true;
            }
        }
        empty = !occupied;
        return occupied && !lowWatermark;
    }

    @Override public void close() {
        for (IContentsListenerRegistry registry : observed) registry.removeContentsListener(listener);
        observed.clear();
    }
}
