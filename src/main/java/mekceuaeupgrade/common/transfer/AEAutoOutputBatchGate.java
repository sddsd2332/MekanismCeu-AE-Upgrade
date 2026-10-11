package mekceuaeupgrade.common.transfer;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.gas.GasStack;
import mekanism.api.processing.MachineResourceStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

/** Only schedules source extraction. Network ownership, rollback and pending retries remain in the drainer. */
public final class AEAutoOutputBatchGate implements AutoCloseable {
    public static final int MAX_HOLD_TICKS = 100;
    private final List<IInventorySlot> inputs;
    private final List<Output> outputs;
    private final AEAutoProcessingRefillGate sharedInputs;
    private final List<IContentsListenerRegistry> observed = new ArrayList<>();
    private final AtomicLong generation = new AtomicLong();
    private final IContentsListener listener = generation::incrementAndGet;
    private long checked = -1, lastCheck = Long.MIN_VALUE, heldSince = Long.MIN_VALUE;
    private boolean hasOutput, inputsEmpty, blocksNext;

    public static final class Output {
        private final Object container;
        private final ItemStack item;
        private final GasStack gas;
        private final FluidStack fluid;
        private final long nextAmount;
        public Output(Object container, MachineResourceStack resource, int simultaneousOperations) {
            this.container = container;
            item = resource.kind() == mekanism.api.processing.MachineResourceKind.ITEM ? resource.itemStack() : ItemStack.EMPTY;
            gas = resource.kind() == mekanism.api.processing.MachineResourceKind.GAS ? resource.gasStack() : null;
            fluid = resource.kind() == mekanism.api.processing.MachineResourceKind.FLUID ? resource.fluidStack() : null;
            nextAmount = Math.multiplyExact(resource.amount(), simultaneousOperations);
        }
        boolean hasContents() {
            if (container instanceof IInventorySlot slot) return !slot.isEmpty();
            if (container instanceof IExtendedGasTank tank) return tank.getGasAmount() > 0;
            if (container instanceof IExtendedFluidTank tank) return tank.getFluidAmount() > 0;
            return true;
        }
        boolean blocksNext() {
            if (container instanceof IInventorySlot slot && !item.isEmpty()) {
                ItemStack stored = slot.getStack();
                return !stored.isEmpty() && (!ItemHandlerHelper.canItemStacksStack(stored, item) ||
                      (long) slot.getLimit(item) - stored.getCount() < nextAmount);
            }
            if (container instanceof IExtendedGasTank tank && gas != null) {
                GasStack stored = tank.getGas();
                return stored != null && stored.amount > 0 && (!stored.isGasEqual(gas) ||
                      (long) tank.getCapacity() - tank.getGasAmount() < nextAmount);
            }
            if (container instanceof IExtendedFluidTank tank && fluid != null) {
                FluidStack stored = tank.getFluid();
                return stored != null && stored.amount > 0 && (!stored.isFluidEqual(fluid) ||
                      (long) tank.getCapacity() - tank.getFluidAmount() < nextAmount);
            }
            return true;
        }
    }

    private AEAutoOutputBatchGate(List<IInventorySlot> inputs, List<Output> outputs,
          AEAutoProcessingRefillGate sharedInputs) {
        this.inputs = new ArrayList<>(inputs); this.outputs = new ArrayList<>(outputs);
        this.sharedInputs = sharedInputs;
    }
    public static AEAutoOutputBatchGate create(List<IInventorySlot> inputs, List<Output> outputs) {
        return create(inputs, outputs, null);
    }

    /** The processing entry owns the shared input gate and closes this view before closing it. */
    public static AEAutoOutputBatchGate create(List<IInventorySlot> inputs, List<Output> outputs,
          AEAutoProcessingRefillGate refillGate) {
        if (inputs.isEmpty() || outputs.isEmpty()) return null;
        AEAutoProcessingRefillGate shared = refillGate != null && refillGate.usesInputs(inputs) ? refillGate : null;
        AEAutoOutputBatchGate gate = new AEAutoOutputBatchGate(inputs, outputs, shared);
        Set<Object> containers = Collections.newSetFromMap(new IdentityHashMap<>());
        if (shared == null) containers.addAll(inputs);
        for (Output output : outputs) containers.add(output.container);
        for (Object container : containers) {
            if (!(container instanceof IContentsListenerRegistry registry) || !registry.addContentsListener(gate.listener)) {
                gate.close(); return null;
            }
            gate.observed.add(registry);
        }
        return gate;
    }
    public boolean shouldDrain(long tick, Function<Supplier<Boolean>, Boolean> transaction) {
        long before = generation.get();
        boolean checkOutputs = needsOwnCheck(tick, before);
        if (checkOutputs || sharedInputs != null && sharedInputs.needsCheck(tick)) {
            transaction.apply(() -> {
                if (sharedInputs != null) sharedInputs.refreshInsideTransaction(tick);
                else {
                    inputsEmpty = true;
                    for (IInventorySlot input : inputs) if (!input.isEmpty()) {inputsEmpty = false; break;}
                }
                if (checkOutputs) {
                    hasOutput = false; blocksNext = false;
                    for (Output output : outputs) {hasOutput |= output.hasContents(); blocksNext |= output.blocksNext();}
                }
                return true;
            });
            if (checkOutputs) {checked = before; lastCheck = tick;}
        }
        if (!hasOutput) {heldSince = Long.MIN_VALUE; return false;}
        if (tick == Long.MIN_VALUE) return true;
        if (heldSince == Long.MIN_VALUE || tick < heldSince) heldSince = tick;
        return isUrgent() || tick - heldSince >= MAX_HOLD_TICKS;
    }

    private boolean isUrgent() {
        return blocksNext || (sharedInputs == null ? inputsEmpty : sharedInputs.inputsEmpty());
    }

    private boolean needsOwnCheck(long tick, long current) {
        return current != checked || lastCheck == Long.MIN_VALUE || tick == Long.MIN_VALUE ||
              tick < lastCheck || tick - lastCheck >= 20;
    }

    /**
     * Cheap scheduler-side check. It does not inspect machine containers; it only reports when
     * the listener generation or the bounded polling interval requires {@link #shouldDrain}.
     */
    public boolean needsCheck(long tick) {
        return needsOwnCheck(tick, generation.get()) || sharedInputs != null && sharedInputs.needsCheck(tick) || hasOutput &&
              (isUrgent() || heldSince == Long.MIN_VALUE || tick < heldSince || tick - heldSince >= MAX_HOLD_TICKS);
    }
    public void afterDrain(long tick) {
        // Retry policy handles a blocked drain; subsequent accumulation gets its own bounded window.
        heldSince = tick; checked = -1;
    }
    @Override public void close() {
        for (IContentsListenerRegistry registry : observed) registry.removeContentsListener(listener);
        observed.clear();
    }
}
