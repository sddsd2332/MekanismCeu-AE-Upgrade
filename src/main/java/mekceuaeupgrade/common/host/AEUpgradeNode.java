package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.transfer.AEUpgradeFluidBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeGasBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeInputInjector;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.util.AECableType;
import ae2.me.helpers.MachineSource;
import mekanism.api.gas.GasStack;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekanism.common.util.MekanismUtils;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class AEUpgradeNode {

    private static final String PROFILE_OWNER_TAG = "aeRecipeProfileOwner";
    private static final String PROFILE_INDIVIDUAL_TAG = "aeRecipeProfileIndividual";
    private static final String PROFILE_INDIVIDUAL_INITIALIZED_TAG = "aeRecipeProfileIndividualInitialized";
    private static final String PROFILE_INSTANCE_TAG = "aeRecipeProfileInstance";
    private static final String PROFILE_GLOBAL_SLOT_TAG = "aeRecipeProfileGlobalSlot";
    private static final String CONNECTION_SIDE_TAG = "aeConnectionSide";
    private static final String GRID_NODE_TAG = "aeUpgrade";
    private static final int PATTERN_CHANGE_RETRY_TICKS = 5;
    public static final int MIN_GLOBAL_PROFILE_SLOT = 1;
    public static final int MAX_GLOBAL_PROFILE_SLOT = 10;

    private final IAEUpgradeHost host;
    private final AEUpgradeRecipeCache recipeCache;
    private final MachineSource source;
    @Nullable
    private IManagedGridNode managedNode;
    private NBTTagCompound gridNodeData = new NBTTagCompound();
    @Nullable
    private EnumFacing connectionSide;
    @Nullable
    private UUID recipeProfileOwner;
    @Nullable
    private UUID recipeProfileInstance;
    private int recipeProfileGlobalSlot = MIN_GLOBAL_PROFILE_SLOT;
    private boolean recipeProfileIndividual;
    private boolean recipeProfileIndividualInitialized;
    private boolean ready;
    private boolean lastNetworkUsable;
    private int pendingPatternChangeTicks;
    private long lastBusyDebugTick = Long.MIN_VALUE;
    @Nullable
    private Object lastRecipeSourceKey;

    public AEUpgradeNode(IAEUpgradeHost host) {
        this.host = host;
        recipeCache = new AEUpgradeRecipeCache(host);
        source = new MachineSource(host);
    }

    @Nullable
    public IManagedGridNode getManagedNode() {
        return managedNode;
    }

    public IActionSource getActionSource() {
        return source;
    }

    public void read(NBTTagCompound nbtTags) {
        gridNodeData = nbtTags.copy();
        if (managedNode != null) {
            managedNode.loadFromNBT(gridNodeData);
        }
        if (nbtTags.hasKey(PROFILE_OWNER_TAG)) {
            try {
                recipeProfileOwner = UUID.fromString(nbtTags.getString(PROFILE_OWNER_TAG));
            } catch (IllegalArgumentException ignored) {
                recipeProfileOwner = null;
            }
        }
        if (nbtTags.hasKey(PROFILE_INSTANCE_TAG)) {
            try {
                recipeProfileInstance = UUID.fromString(nbtTags.getString(PROFILE_INSTANCE_TAG));
            } catch (IllegalArgumentException ignored) {
                recipeProfileInstance = null;
            }
        }
        if (nbtTags.hasKey(PROFILE_GLOBAL_SLOT_TAG)) {
            recipeProfileGlobalSlot = clampGlobalProfileSlot(nbtTags.getInteger(PROFILE_GLOBAL_SLOT_TAG));
        } else {
            recipeProfileGlobalSlot = MIN_GLOBAL_PROFILE_SLOT;
        }
        recipeProfileIndividual = nbtTags.getBoolean(PROFILE_INDIVIDUAL_TAG);
        recipeProfileIndividualInitialized = nbtTags.getBoolean(PROFILE_INDIVIDUAL_INITIALIZED_TAG);
        connectionSide = readConnectionSide(nbtTags);
        updateExposedSides();
        queuePatternChange();
    }

    public void write(NBTTagCompound nbtTags) {
        if (managedNode != null) {
            managedNode.saveToNBT(gridNodeData);
        }
        if (gridNodeData.hasKey(GRID_NODE_TAG)) {
            nbtTags.setTag(GRID_NODE_TAG, gridNodeData.getTag(GRID_NODE_TAG).copy());
        }
        if (recipeProfileOwner != null) {
            nbtTags.setString(PROFILE_OWNER_TAG, recipeProfileOwner.toString());
        }
        if (recipeProfileInstance != null) {
            nbtTags.setString(PROFILE_INSTANCE_TAG, recipeProfileInstance.toString());
        }
        nbtTags.setInteger(PROFILE_GLOBAL_SLOT_TAG, recipeProfileGlobalSlot);
        nbtTags.setBoolean(PROFILE_INDIVIDUAL_TAG, recipeProfileIndividual);
        nbtTags.setBoolean(PROFILE_INDIVIDUAL_INITIALIZED_TAG, recipeProfileIndividualInitialized);
        if (connectionSide == null) {
            nbtTags.removeTag(CONNECTION_SIDE_TAG);
        } else {
            nbtTags.setInteger(CONNECTION_SIDE_TAG, connectionSide.ordinal());
        }
    }

    @Nullable
    public UUID getRecipeProfileOwner() {
        return recipeProfileOwner;
    }

    public void setRecipeProfileOwner(@Nullable UUID owner) {
        if (owner == null ? recipeProfileOwner == null : owner.equals(recipeProfileOwner)) {
            return;
        }
        recipeProfileOwner = owner;
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
        invalidateRecipeCache();
    }

    public boolean isRecipeProfileIndividual() {
        return recipeProfileIndividual;
    }

    public boolean isRecipeProfileIndividualInitialized() {
        return recipeProfileIndividualInitialized;
    }

    public int getRecipeProfileGlobalSlot() {
        return recipeProfileGlobalSlot;
    }

    public boolean cycleRecipeProfileGlobalSlot() {
        return cycleRecipeProfileGlobalSlot(1);
    }

    public boolean cycleRecipeProfileGlobalSlot(int direction) {
        int nextSlot;
        if (direction < 0) {
            nextSlot = recipeProfileGlobalSlot <= MIN_GLOBAL_PROFILE_SLOT ? MAX_GLOBAL_PROFILE_SLOT : recipeProfileGlobalSlot - 1;
        } else {
            nextSlot = recipeProfileGlobalSlot >= MAX_GLOBAL_PROFILE_SLOT ? MIN_GLOBAL_PROFILE_SLOT : recipeProfileGlobalSlot + 1;
        }
        return setRecipeProfileGlobalSlot(nextSlot);
    }

    public boolean setRecipeProfileGlobalSlot(int slot) {
        slot = clampGlobalProfileSlot(slot);
        if (recipeProfileGlobalSlot == slot) {
            return false;
        }
        recipeProfileGlobalSlot = slot;
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
        invalidateRecipeCache();
        return true;
    }

    public boolean setRecipeProfileIndividual(boolean individual) {
        if (recipeProfileIndividual == individual) {
            return false;
        }
        recipeProfileIndividual = individual;
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
        invalidateRecipeCache();
        return true;
    }

    public void markRecipeProfileIndividualInitialized() {
        if (recipeProfileIndividualInitialized) {
            return;
        }
        recipeProfileIndividualInitialized = true;
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
    }

    public UUID getOrCreateRecipeProfileInstance() {
        if (recipeProfileInstance == null) {
            recipeProfileInstance = UUID.randomUUID();
            if (host instanceof TileEntity tile) {
                tile.markDirty();
            }
        }
        return recipeProfileInstance;
    }

    private static int clampGlobalProfileSlot(int slot) {
        return Math.max(MIN_GLOBAL_PROFILE_SLOT, Math.min(MAX_GLOBAL_PROFILE_SLOT, slot));
    }

    public void onLoad() {
        ensureActive();
    }

    public void validate() {
        ensureActive();
    }

    public void invalidate() {
        AERecipeProfileManager.unregisterHost(host);
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        pendingPatternChangeTicks = 0;
        destroyManagedNode();
    }

    public void onChunkUnload() {
        AERecipeProfileManager.unregisterHost(host);
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        pendingPatternChangeTicks = 0;
        destroyManagedNode();
    }

    public void tickServer() {
        if (!host.shouldExposeAE()) {
            if (ready) {
                deactivate();
            }
            lastNetworkUsable = false;
            pendingPatternChangeTicks = 0;
            return;
        }
        boolean connectionChanged;
        if (!ready || managedNode == null) {
            activate();
            connectionChanged = true;
        } else {
            connectionChanged = refreshConnection();
        }
        boolean networkUsable = canUseNetwork();
        if (networkUsable && (!lastNetworkUsable || connectionChanged)) {
            queuePatternChange();
        }
        if (networkUsable && refreshRecipeSourceKey()) {
            queuePatternChange();
        }
        flushPendingPatternChange(networkUsable);
        lastNetworkUsable = networkUsable;
        if (host instanceof IAEItemRecipeHost itemHost) {
            itemHost.drainAEItemOutputs(this);
        }
    }

    public void activate() {
        if (!host.shouldExposeAE()) {
            return;
        }
        refreshConnection();
        if (managedNode == null) {
            IManagedGridNode node = createManagedNode();
            managedNode = node;
            if (host instanceof TileEntity tile) {
                GridHelper.onFirstTick(tile, ignored -> {
                    if (managedNode == node && host.shouldExposeAE() && !node.isReady()) {
                        node.create(tile.getWorld(), tile.getPos());
                        queuePatternChange();
                        flushPendingPatternChange(canUseNetwork());
                    }
                });
            }
        }
        AERecipeProfileManager.registerHost(host);
        ready = true;
        lastNetworkUsable = false;
        notifyConnectionSide(connectionSide);
        queuePatternChange();
        flushPendingPatternChange(canUseNetwork());
    }

    public void deactivate() {
        AERecipeProfileManager.unregisterHost(host);
        EnumFacing previousSide = connectionSide;
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        connectionSide = null;
        pendingPatternChangeTicks = 0;
        updateExposedSides();
        destroyManagedNode();
        notifyConnectionSide(previousSide);
        markHostDirty();
    }

    public void onNeighborChanged() {
        if (host.shouldExposeAE()) {
            if (refreshConnection()) {
                queuePatternChange();
            }
            flushPendingPatternChange(canUseNetwork());
        }
    }

    @Nullable
    public IGridNode getGridNode(@Nonnull EnumFacing dir) {
        if (!host.shouldExposeAE() || connectionSide == null) {
            return null;
        }
        if (dir != connectionSide) {
            return null;
        }
        return getGridNode();
    }

    @Nullable
    public IGridNode getGridNode() {
        return managedNode == null ? null : managedNode.getNode();
    }

    @Nonnull
    public AECableType getCableConnectionType(@Nonnull EnumFacing dir) {
        return host.supportsAEUpgrade() && host.hasAEUpgrade() && dir == connectionSide ? AECableType.COVERED : AECableType.NONE;
    }

    public boolean isBusy() {
        if (!host.shouldExposeAE()) {
            debugBusy("busy: AE upgrade is not installed or host is not exposing AE");
            return true;
        }
        if (!canUseNetwork()) {
            debugBusy("busy: network unavailable node={} active={} powered={} side={}",
                  getGridNode() != null, managedNode != null && managedNode.isActive(), managedNode != null && managedNode.isPowered(), connectionSide);
            return true;
        }
        if (host instanceof IAEItemRecipeHost itemHost && !itemHost.canAcceptAnyAEItemInput()) {
            debugBusy("busy: machine cannot currently accept any AE item input");
            return true;
        }
        return false;
    }

    public List<AEExposedRecipe> getAvailablePatterns() {
        if (!host.shouldExposeAE() || !canUseNetwork()) {
            AEUpgradeDebug.log(host, "skipping crafting provider expose={} networkUsable={}", host.shouldExposeAE(), canUseNetwork());
            return List.of();
        }
        List<AEExposedRecipe> recipes = recipeCache.getRecipes();
        if (AEUpgradeDebug.enabled()) {
            int count = recipes.size();
            AEUpgradeDebug.log(host, "providing {} AE processing recipes", count);
        }
        return recipes;
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        if (!host.shouldExposeAE()) {
            AEUpgradeDebug.log(host, "pushPattern rejected: AE upgrade is not installed or host is not exposing AE");
            return false;
        }
        if (multiplier != 1) {
            AEUpgradeDebug.log(host, "pushPattern rejected: merged pattern pushes are not supported multiplier={}", multiplier);
            return false;
        }
        if (!canUseNetwork()) {
            AEUpgradeDebug.log(host, "pushPattern rejected: network unavailable node={} active={} powered={} side={}",
                  getGridNode() != null, managedNode != null && managedNode.isActive(), managedNode != null && managedNode.isPowered(), connectionSide);
            return false;
        }
        if (!(host instanceof IAEItemRecipeHost itemHost)) {
            AEUpgradeDebug.log(host, "pushPattern rejected: host does not implement item recipe host");
            return false;
        }
        AEExposedRecipe recipe = recipeCache.find(exposed -> exposed.matches(patternDetails));
        if (recipe == null) {
            AEUpgradeDebug.log(host, "pushPattern rejected: no matching exposed recipe for AE pattern");
            return false;
        }
        List<ItemStack> inputs = toItemInputs(inputHolder, recipe.getInputStacks().size());
        if (inputs == null) {
            AEUpgradeDebug.log(host, "pushPattern rejected: AE supplied malformed inputs for recipe inputs={} outputs={}",
                  AEUpgradeDebug.stacks(recipe.getInputStacks()), AEUpgradeDebug.stacks(recipe.getOutputStacks()));
            return false;
        }
        boolean pushed = AEUpgradeInputInjector.push(itemHost, recipe, inputs);
        if (pushed) {
            AEUpgradeDebug.log(host, "pushPattern accepted inputs={} outputs={}",
                  AEUpgradeDebug.stacks(recipe.getInputStacks()), AEUpgradeDebug.stacks(recipe.getOutputStacks()));
        }
        return pushed;
    }

    public boolean canUseNetwork() {
        return managedNode != null && managedNode.isReady() && managedNode.isActive() && managedNode.isPowered();
    }

    public ItemStack injectItem(ItemStack stack, Actionable action) {
        if (stack.isEmpty() || !canUseNetwork()) {
            return stack;
        }
        MEStorage storage = getNetworkStorage();
        AEItemKey key = AEItemKey.of(stack);
        if (storage == null || key == null) {
            return stack;
        }
        long inserted = storage.insert(key, stack.getCount(), action, source);
        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount((int) Math.max(0, stack.getCount() - inserted));
        return remainder;
    }

    public GasStack injectGas(GasStack stack, Actionable action) {
        return AEUpgradeGasBridge.inject(this, stack, action);
    }

    public GasStack extractGas(GasStack request, Actionable action) {
        return AEUpgradeGasBridge.extract(this, request, action);
    }

    public FluidStack injectFluid(FluidStack stack, Actionable action) {
        return AEUpgradeFluidBridge.inject(this, stack, action);
    }

    public FluidStack extractFluid(FluidStack request, Actionable action) {
        return AEUpgradeFluidBridge.extract(this, request, action);
    }

    @Nullable
    public MEStorage getNetworkStorage() {
        IGridNode node = getGridNode();
        if (node == null) {
            return null;
        }
        return node.grid().getStorageService().getInventory();
    }

    public void invalidateRecipeCache() {
        recipeCache.invalidate();
        queuePatternChange();
        flushPendingPatternChange(canUseNetwork());
    }

    private boolean refreshRecipeSourceKey() {
        Object currentRecipeSourceKey = host instanceof IAEItemRecipeHost itemHost ? itemHost.getAERecipeSourceKey() : null;
        if (Objects.equals(lastRecipeSourceKey, currentRecipeSourceKey)) {
            return false;
        }
        lastRecipeSourceKey = currentRecipeSourceKey;
        recipeCache.invalidate();
        return true;
    }

    private IManagedGridNode createManagedNode() {
        IManagedGridNode node = GridHelper.createManagedNode(host, new IGridNodeListener<IAEUpgradeHost>() {
                  @Override
                  public void onSaveChanges(IAEUpgradeHost nodeOwner, IGridNode gridNode) {
                      markHostDirty();
                  }

                  @Override
                  public void onInWorldConnectionChanged(IAEUpgradeHost nodeOwner, IGridNode gridNode) {
                      notifyConnectionSide(connectionSide);
                  }

                  @Override
                  public void onStateChanged(IAEUpgradeHost nodeOwner, IGridNode gridNode, State state) {
                      queuePatternChange();
                  }
              })
              .setInWorldNode(true)
              .setTagName(GRID_NODE_TAG)
              .setFlags(GridFlags.REQUIRE_CHANNEL)
              .setIdlePowerUsage(0.0)
              .setVisualRepresentation(new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade))
              .addService(ICraftingProvider.class, host);
        node.loadFromNBT(gridNodeData);
        node.setExposedOnSides(connectionSide == null ? Set.of() : EnumSet.of(connectionSide));
        return node;
    }

    private void destroyManagedNode() {
        if (managedNode == null) {
            return;
        }
        managedNode.saveToNBT(gridNodeData);
        managedNode.destroy();
        managedNode = null;
    }

    @Nullable
    private static List<ItemStack> toItemInputs(KeyCounter[] inputHolder, int expectedSlots) {
        if (inputHolder == null || inputHolder.length != expectedSlots) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (KeyCounter counter : inputHolder) {
            if (counter == null || counter.isEmpty()) {
                return null;
            }
            ItemStack slotInput = ItemStack.EMPTY;
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                if (!slotInput.isEmpty()) {
                    return null;
                }
                if (key instanceof AEItemKey itemKey) {
                    slotInput = itemKey.toStack((int) Math.min(amount, Integer.MAX_VALUE));
                } else {
                    slotInput = GenericStack.wrapInItemStack(key, amount);
                }
            }
            if (slotInput.isEmpty()) {
                return null;
            }
            inputs.add(slotInput);
        }
        return inputs;
    }

    private boolean refreshConnection() {
        EnumFacing previousSide = connectionSide;
        EnumFacing side = chooseConnectionSide();
        if (side != connectionSide) {
            connectionSide = side;
            updateExposedSides();
            notifyConnectionSide(previousSide);
            notifyConnectionSide(side);
            markHostDirty();
            return true;
        }
        return false;
    }

    @Nullable
    private EnumFacing chooseConnectionSide() {
        if (!(host instanceof TileEntity tile) || tile.getWorld() == null) {
            return null;
        }
        boolean currentSideUnloaded = false;
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos neighborPos = tile.getPos().offset(side);
            if (!tile.getWorld().isBlockLoaded(neighborPos, false)) {
                if (side == connectionSide) {
                    currentSideUnloaded = true;
                }
                continue;
            }
            TileEntity neighbor = tile.getWorld().getTileEntity(neighborPos);
            if (neighbor instanceof IAEUpgradeHost) {
                continue;
            }
            IGridNode neighborNode = GridHelper.getExposedNode(tile.getWorld(), neighborPos, side.getOpposite());
            if (neighborNode != null) {
                return side;
            }
        }
        return currentSideUnloaded ? connectionSide : null;
    }

    private void ensureActive() {
        if (!host.shouldExposeAE()) {
            return;
        }
        if (!ready || managedNode == null) {
            activate();
        } else {
            refreshConnection();
            queuePatternChange();
            flushPendingPatternChange(canUseNetwork());
        }
    }

    @Nullable
    private EnumFacing readConnectionSide(NBTTagCompound nbtTags) {
        if (!nbtTags.hasKey(CONNECTION_SIDE_TAG)) {
            return null;
        }
        int side = nbtTags.getInteger(CONNECTION_SIDE_TAG);
        return side >= 0 && side < EnumFacing.VALUES.length ? EnumFacing.VALUES[side] : null;
    }

    private void updateExposedSides() {
        if (managedNode != null) {
            managedNode.setExposedOnSides(connectionSide == null ? Set.of() : EnumSet.of(connectionSide));
        }
    }

    private void queuePatternChange() {
        pendingPatternChangeTicks = Math.max(pendingPatternChangeTicks, PATTERN_CHANGE_RETRY_TICKS);
    }

    private void flushPendingPatternChange(boolean networkUsable) {
        if (pendingPatternChangeTicks <= 0 || !networkUsable) {
            return;
        }
        if (managedNode != null) {
            ICraftingProvider.requestUpdate(managedNode);
            pendingPatternChangeTicks--;
        }
    }

    private void notifyConnectionSide(@Nullable EnumFacing side) {
        if (side == null || !(host instanceof TileEntity tile) || tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        BlockPos neighborPos = tile.getPos().offset(side);
        if (tile.getWorld().isBlockLoaded(neighborPos, false)) {
            MekanismUtils.notifyNeighborOfChange(tile.getWorld(), side, tile.getPos());
        }
    }

    private void markHostDirty() {
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
    }

    private void debugBusy(String message, Object... args) {
        if (!AEUpgradeDebug.enabled()) {
            return;
        }
        long now = getWorldTime();
        if (now != Long.MIN_VALUE && now - lastBusyDebugTick < 20) {
            return;
        }
        lastBusyDebugTick = now;
        AEUpgradeDebug.log(host, message, args);
    }

    private long getWorldTime() {
        if (host instanceof TileEntity tile && tile.getWorld() != null) {
            return tile.getWorld().getTotalWorldTime();
        }
        return Long.MIN_VALUE;
    }
}
