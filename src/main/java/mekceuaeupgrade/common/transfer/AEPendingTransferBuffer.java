package mekceuaeupgrade.common.transfer;

import appeng.api.config.Actionable;
import mekanism.api.gas.GasStack;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.function.Function;

/**
 * Persistent ownership for resources that have left AE or a machine but have not reached their destination yet.
 *
 * <p>All transfers in this project are synchronous server-thread operations. PREPARED therefore owns no resource and
 * is not persisted. Once an extraction returns, the entry becomes HELD before any delivery is attempted. DELIVERING
 * entries recover as UNCERTAIN: without an acknowledgement, neither retry nor return-to-source is safe.</p>
 */
public final class AEPendingTransferBuffer {

    private static final int FORMAT_VERSION = 1;
    private static final String VERSION_TAG = "version";
    private static final String ENTRIES_TAG = "entries";
    private static final String ID_TAG = "id";
    private static final String KIND_TAG = "kind";
    private static final String PHASE_TAG = "phase";
    private static final String RESOURCE_TAG = "resource";
    private static final String AMOUNT_TAG = "amount";

    enum Kind {
        ITEM,
        GAS,
        FLUID
    }

    enum Phase {
        PREPARED,
        HELD,
        DELIVERING,
        UNCERTAIN
    }

    private final Runnable changeListener;
    private final List<Entry> entries = new ArrayList<>();
    @Nullable
    private NBTTagCompound quarantinedData;

    public AEPendingTransferBuffer(Runnable changeListener) {
        this.changeListener = Objects.requireNonNull(changeListener, "Pending transfer change listener cannot be null");
    }

    Entry prepareItem(ItemStack expected) {
        if (expected == null || expected.isEmpty()) {
            throw new IllegalArgumentException("Prepared item transfer requires a non-empty stack");
        }
        return prepare(Entry.item(newTransferId(), Phase.PREPARED, expected));
    }

    Entry prepareGas(GasStack expected) {
        if (!isValid(expected)) {
            throw new IllegalArgumentException("Prepared gas transfer requires a positive stack");
        }
        return prepare(Entry.gas(newTransferId(), Phase.PREPARED, expected));
    }

    Entry prepareFluid(FluidStack expected) {
        if (!isValid(expected)) {
            throw new IllegalArgumentException("Prepared fluid transfer requires a positive stack");
        }
        return prepare(Entry.fluid(newTransferId(), Phase.PREPARED, expected));
    }

    /**
     * Transfer IDs are persisted only as local ownership/debug identifiers; they are not security
     * tokens and never cross an AE trust boundary. Keep the UUID wire format and collision checks,
     * but avoid SecureRandom on every short-lived extraction.
     */
    private static UUID newTransferId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long most = random.nextLong();
        long least = random.nextLong();
        // Preserve the conventional RFC 4122 version-4/variant bits for diagnostics and tooling.
        most = (most & 0xffffffffffff0fffL) | 0x0000000000004000L;
        least = (least & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(most, least);
    }

    private Entry prepare(Entry entry) {
        if (quarantinedData != null) {
            throw new IllegalStateException("Cannot start a transfer while persisted pending data is quarantined");
        }
        entries.add(entry);
        changed();
        return entry;
    }

    void holdItem(Entry entry, ItemStack extracted) {
        requirePrepared(entry, Kind.ITEM);
        if (extracted == null || extracted.isEmpty()) {
            throw new IllegalArgumentException("Held item transfer requires a non-empty stack");
        }
        entry.item = extracted.copy();
        entry.phase = Phase.HELD;
        changed();
    }

    void holdGas(Entry entry, GasStack extracted) {
        requirePrepared(entry, Kind.GAS);
        if (!isValid(extracted)) {
            throw new IllegalArgumentException("Held gas transfer requires a positive stack");
        }
        entry.gas = extracted.copy();
        entry.phase = Phase.HELD;
        changed();
    }

    void holdFluid(Entry entry, FluidStack extracted) {
        requirePrepared(entry, Kind.FLUID);
        if (!isValid(extracted)) {
            throw new IllegalArgumentException("Held fluid transfer requires a positive stack");
        }
        entry.fluid = extracted.copy();
        entry.phase = Phase.HELD;
        changed();
    }

    void cancelPrepared(Entry entry) {
        requirePrepared(entry, entry.kind);
        remove(entry);
    }

    void complete(Entry entry) {
        requireOwned(entry);
        remove(entry);
    }

    boolean contains(Entry entry) {
        return entries.contains(entry);
    }

    boolean isPrepared(Entry entry) { return entries.contains(entry) && entry.phase == Phase.PREPARED; }

    boolean deliverToNetwork(Entry entry, AEUpgradeNode node) {
        Objects.requireNonNull(node, "Pending transfer AE node cannot be null");
        switch (entry.kind) {
            case ITEM:
                return deliverItem(entry, node::deliverOwnedItem);
            case GAS:
                return deliverGas(entry, node::deliverOwnedGas);
            case FLUID:
                return deliverFluid(entry, node::deliverOwnedFluid);
            default:
                return false;
        }
    }

    boolean deliverItem(Entry entry, Function<ItemStack, ItemStack> target) {
        requireOwned(entry, Kind.ITEM);
        if (entry.phase == Phase.UNCERTAIN) return false;
        Objects.requireNonNull(target, "Pending item transfer target cannot be null");
        ItemStack sent = entry.item.copy();
        beginDelivery(entry);
        ItemStack remainder;
        try {
            remainder = target.apply(sent.copy());
        } catch (UncertainNetworkDeliveryException e) {
            markUncertain(entry);
            return false;
        } catch (RuntimeException | LinkageError e) {
            restoreHeld(entry);
            throw e;
        }
        if (!isValidRemainder(sent, remainder)) {
            markUncertain(entry);
            return false;
        }
        if (remainder == null || remainder.isEmpty()) {
            remove(entry);
            return true;
        }
        entry.item = remainder.copy();
        restoreHeld(entry);
        return false;
    }

    boolean deliverGas(Entry entry, Function<GasStack, GasStack> target) {
        requireOwned(entry, Kind.GAS);
        if (entry.phase == Phase.UNCERTAIN) return false;
        Objects.requireNonNull(target, "Pending gas transfer target cannot be null");
        GasStack sent = entry.gas.copy();
        beginDelivery(entry);
        GasStack remainder;
        try {
            remainder = target.apply(sent.copy());
        } catch (UncertainNetworkDeliveryException e) {
            markUncertain(entry);
            return false;
        } catch (RuntimeException | LinkageError e) {
            restoreHeld(entry);
            throw e;
        }
        if (!isValidRemainder(sent, remainder)) {
            markUncertain(entry);
            return false;
        }
        if (!isValid(remainder)) {
            remove(entry);
            return true;
        }
        entry.gas = remainder.copy();
        restoreHeld(entry);
        return false;
    }

    boolean deliverFluid(Entry entry, Function<FluidStack, FluidStack> target) {
        requireOwned(entry, Kind.FLUID);
        if (entry.phase == Phase.UNCERTAIN) return false;
        Objects.requireNonNull(target, "Pending fluid transfer target cannot be null");
        FluidStack sent = entry.fluid.copy();
        beginDelivery(entry);
        FluidStack remainder;
        try {
            remainder = target.apply(sent.copy());
        } catch (UncertainNetworkDeliveryException e) {
            markUncertain(entry);
            return false;
        } catch (RuntimeException | LinkageError e) {
            restoreHeld(entry);
            throw e;
        }
        if (!isValidRemainder(sent, remainder)) {
            markUncertain(entry);
            return false;
        }
        if (!isValid(remainder)) {
            remove(entry);
            return true;
        }
        entry.fluid = remainder.copy();
        restoreHeld(entry);
        return false;
    }

    public boolean retryNetwork(AEUpgradeNode node) {
        if (node == null || quarantinedData != null || !hasOwnedResources()) {
            return !hasOwnedResources();
        }
        for (Entry entry : new ArrayList<>(entries)) {
            if (contains(entry) && entry.phase != Phase.PREPARED) {
                deliverToNetwork(entry, node);
            }
        }
        return !hasOwnedResources();
    }

    /** Return held resources to accepting machine containers after the upgrade is removed.
     * Each individual delivery checkpoints its remainder; full or incompatible containers keep the buffer intact.
     * The caller owns the machine transaction lock. No network calls or new extraction occur here.
     */
    public boolean retryMachine(mekanism.common.tile.prefab.TileEntityContainerBlock tile) {
        if (quarantinedData != null || !hasOwnedResources()) return !hasOwnedResources();
        for (Entry entry : new ArrayList<>(entries)) {
            if (entry.phase == Phase.PREPARED) continue;
            switch (entry.kind) {
                case ITEM:
                    for (mekanism.api.inventory.IInventorySlot slot : tile.getInventorySlots(null)) {
                        if (!contains(entry)) break;
                        deliverItem(entry, stack -> slot.insertItem(stack, mekanism.api.Action.EXECUTE,
                              mekanism.api.AutomationType.EXTERNAL));
                    }
                    break;
                case GAS:
                    for (mekanism.api.gas.IGasTank tank : tile.getGasTanks(null)) {
                        if (!contains(entry)) break;
                        if (tank instanceof mekanism.api.gas.IExtendedGasTank extended) {
                            deliverGas(entry, stack -> extended.insert(stack, mekanism.api.Action.EXECUTE,
                                  mekanism.api.AutomationType.EXTERNAL));
                        }
                    }
                    break;
                case FLUID:
                    for (net.minecraftforge.fluids.IFluidTank tank : tile.getFluidTanks(null)) {
                        if (!contains(entry)) break;
                        if (tank instanceof mekanism.api.fluid.IExtendedFluidTank extended) {
                            deliverFluid(entry, stack -> extended.insert(stack, mekanism.api.Action.EXECUTE,
                                  mekanism.api.AutomationType.EXTERNAL));
                        }
                    }
                    break;
            }
        }
        return !hasOwnedResources();
    }

    public boolean hasOwnedResources() {
        if (quarantinedData != null) {
            return true;
        }
        for (Entry entry : entries) {
            if (entry.phase != Phase.PREPARED) {
                return true;
            }
        }
        return false;
    }

    int ownedEntryCount() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.phase != Phase.PREPARED) {
                count++;
            }
        }
        return count;
    }

    public NBTTagCompound write() {
        if (quarantinedData != null) {
            return quarantinedData.copy();
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(VERSION_TAG, FORMAT_VERSION);
        NBTTagList serializedEntries = new NBTTagList();
        for (Entry entry : entries) {
            if (entry.phase != Phase.PREPARED) {
                serializedEntries.appendTag(entry.write());
            }
        }
        data.setTag(ENTRIES_TAG, serializedEntries);
        return data;
    }

    public void read(@Nullable NBTTagCompound data) {
        entries.clear();
        quarantinedData = null;
        if (data == null || data.isEmpty()) {
            return;
        }
        try {
            int version = data.getInteger(VERSION_TAG);
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported AE pending transfer buffer version: " + version);
            }
            NBTTagList serializedEntries = data.getTagList(ENTRIES_TAG, NBT.TAG_COMPOUND);
            Set<UUID> ids = new HashSet<>();
            List<Entry> restored = new ArrayList<>(serializedEntries.tagCount());
            for (int i = 0; i < serializedEntries.tagCount(); i++) {
                Entry entry = Entry.read(serializedEntries.getCompoundTagAt(i));
                if (!ids.add(entry.id)) {
                    throw new IllegalArgumentException("Duplicate AE pending transfer id: " + entry.id);
                }
                restored.add(entry);
            }
            entries.addAll(restored);
        } catch (RuntimeException failure) {
            entries.clear();
            quarantinedData = data.copy();
        }
    }

    /**
     * Invalid or newer persisted data is retained byte-for-byte and blocks new transfers until a compatible version
     * can recover it. This keeps a bad entry from crashing chunk loading without silently discarding owned resources.
     */
    public boolean hasQuarantinedData() {
        if (quarantinedData != null) return true;
        for (Entry entry : entries) if (entry.phase == Phase.UNCERTAIN) return true;
        return false;
    }

    void beginDelivery(Entry entry) {
        requireOwned(entry);
        entry.phase = Phase.DELIVERING;
        changed();
    }

    private void restoreHeld(Entry entry) {
        requirePresent(entry);
        entry.phase = Phase.HELD;
        changed();
    }

    void markUncertain(Entry entry) {
        requireOwned(entry);
        boolean newlyUncertain = entry.phase != Phase.UNCERTAIN;
        entry.phase = Phase.UNCERTAIN;
        changed();
        if (newlyUncertain) mekceuaeupgrade.common.core.MEKCeuAEUpgrade.logger.error(
              "AE transfer {} has an uncertain delivery outcome; preserved for inspection, automatic replay and return are blocked", entry.id);
    }

    private void remove(Entry entry) {
        requirePresent(entry);
        entries.remove(entry);
        changed();
    }

    private void requirePrepared(Entry entry, Kind kind) {
        requirePresent(entry);
        if (entry.kind != kind || entry.phase != Phase.PREPARED) {
            throw new IllegalStateException("Pending transfer is not a prepared " + kind + " entry");
        }
    }

    private void requireOwned(Entry entry) {
        requirePresent(entry);
        if (entry.phase == Phase.PREPARED) {
            throw new IllegalStateException("Prepared pending transfer does not own a resource");
        }
    }

    private void requireOwned(Entry entry, Kind kind) {
        requireOwned(entry);
        if (entry.kind != kind) {
            throw new IllegalStateException("Pending transfer is not a held " + kind + " entry");
        }
    }

    private void requirePresent(Entry entry) {
        if (entry == null || !entries.contains(entry)) {
            throw new IllegalStateException("Pending transfer entry does not belong to this buffer");
        }
    }

    private void changed() {
        changeListener.run();
    }

    private static boolean isValid(@Nullable GasStack stack) {
        return stack != null && stack.getGas() != null && stack.amount > 0;
    }

    private static boolean isValid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() != null && stack.amount > 0;
    }

    private static boolean isValidRemainder(ItemStack sent, @Nullable ItemStack remainder) {
        return remainder != null && remainder.getCount() >= 0 && (remainder.isEmpty() ||
              remainder.getCount() <= sent.getCount() && ItemHandlerHelper.canItemStacksStack(sent, remainder));
    }

    private static boolean isValidRemainder(GasStack sent, @Nullable GasStack remainder) {
        return remainder == null || remainder.amount == 0 || remainder.amount > 0 &&
              remainder.getGas() != null && remainder.amount <= sent.amount && sent.isGasEqual(remainder);
    }

    private static boolean isValidRemainder(FluidStack sent, @Nullable FluidStack remainder) {
        return remainder == null || remainder.amount == 0 || remainder.amount > 0 &&
              remainder.getFluid() != null && remainder.amount <= sent.amount && sent.isFluidEqual(remainder);
    }

    static final class Entry {

        private final UUID id;
        private final Kind kind;
        private Phase phase;
        private ItemStack item = ItemStack.EMPTY;
        @Nullable
        private GasStack gas;
        @Nullable
        private FluidStack fluid;

        private Entry(UUID id, Kind kind, Phase phase) {
            this.id = Objects.requireNonNull(id, "Pending transfer id cannot be null");
            this.kind = Objects.requireNonNull(kind, "Pending transfer kind cannot be null");
            this.phase = Objects.requireNonNull(phase, "Pending transfer phase cannot be null");
        }

        private static Entry item(UUID id, Phase phase, ItemStack stack) {
            Entry entry = new Entry(id, Kind.ITEM, phase);
            entry.item = stack.copy();
            return entry;
        }

        private static Entry gas(UUID id, Phase phase, GasStack stack) {
            Entry entry = new Entry(id, Kind.GAS, phase);
            entry.gas = stack.copy();
            return entry;
        }

        private static Entry fluid(UUID id, Phase phase, FluidStack stack) {
            Entry entry = new Entry(id, Kind.FLUID, phase);
            entry.fluid = stack.copy();
            return entry;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString(ID_TAG, id.toString());
            data.setString(KIND_TAG, kind.name());
            data.setString(PHASE_TAG, phase.name());
            NBTTagCompound resource = new NBTTagCompound();
            switch (kind) {
                case ITEM:
                    item.writeToNBT(resource);
                    data.setInteger(AMOUNT_TAG, item.getCount());
                    break;
                case GAS:
                    gas.write(resource);
                    data.setInteger(AMOUNT_TAG, gas.amount);
                    break;
                case FLUID:
                    fluid.writeToNBT(resource);
                    data.setInteger(AMOUNT_TAG, fluid.amount);
                    break;
            }
            data.setTag(RESOURCE_TAG, resource);
            return data;
        }

        private static Entry read(NBTTagCompound data) {
            try {
                UUID id = UUID.fromString(data.getString(ID_TAG));
                Kind kind = Kind.valueOf(data.getString(KIND_TAG));
                Phase serializedPhase = Phase.valueOf(data.getString(PHASE_TAG));
                if (serializedPhase == Phase.PREPARED || !data.hasKey(RESOURCE_TAG, NBT.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Persisted pending transfer must own a resource");
                }
                Phase recoveredPhase = serializedPhase == Phase.DELIVERING || serializedPhase == Phase.UNCERTAIN
                      ? Phase.UNCERTAIN : Phase.HELD;
                NBTTagCompound resource = data.getCompoundTag(RESOURCE_TAG);
                int amount = data.getInteger(AMOUNT_TAG);
                if (amount <= 0) {
                    throw new IllegalArgumentException("Persisted pending transfer has no positive amount");
                }
                switch (kind) {
                    case ITEM:
                        resource.setByte("Count", (byte) 1);
                        ItemStack item = new ItemStack(resource);
                        if (item.isEmpty()) {
                            throw new IllegalArgumentException("Persisted pending item is empty");
                        }
                        item.setCount(amount);
                        return item(id, recoveredPhase, item);
                    case GAS:
                        GasStack gas = GasStack.readFromNBT(resource);
                        if (!isValid(gas)) {
                            throw new IllegalArgumentException("Persisted pending gas is empty");
                        }
                        gas.amount = amount;
                        return gas(id, recoveredPhase, gas);
                    case FLUID:
                        FluidStack fluid = FluidStack.loadFluidStackFromNBT(resource);
                        if (!isValid(fluid)) {
                            throw new IllegalArgumentException("Persisted pending fluid is empty");
                        }
                        fluid.amount = amount;
                        return fluid(id, recoveredPhase, fluid);
                    default:
                        throw new IllegalArgumentException("Unknown pending transfer kind");
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid AE pending transfer entry", e);
            }
        }
    }
}
