package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.item.AEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.transfer.AEUpgradeFluidBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeGasBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeInputInjector;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import mekanism.api.gas.GasStack;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekanism.common.util.MekanismUtils;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

public class AEUpgradeNode {

    private static final String PROFILE_OWNER_TAG = "aeRecipeProfileOwner";
    private static final String PROFILE_INDIVIDUAL_TAG = "aeRecipeProfileIndividual";
    private static final String PROFILE_INDIVIDUAL_INITIALIZED_TAG = "aeRecipeProfileIndividualInitialized";
    private static final String PROFILE_INSTANCE_TAG = "aeRecipeProfileInstance";
    private static final String PROFILE_GLOBAL_SLOT_TAG = "aeRecipeProfileGlobalSlot";
    private static final String CONNECTION_SIDE_TAG = "aeConnectionSide";
    private static final int PATTERN_CHANGE_RETRY_TICKS = 5;
    public static final int MIN_GLOBAL_PROFILE_SLOT = 1;
    public static final int MAX_GLOBAL_PROFILE_SLOT = 10;

    private final IAEUpgradeHost host;
    private final AENetworkProxy proxy;
    private final AEUpgradeRecipeCache recipeCache;
    private final MachineSource source;
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
        proxy = new AENetworkProxy(host, "aeUpgrade", new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade), true);
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(0.0);
        proxy.setValidSides(EnumSet.noneOf(EnumFacing.class));
        recipeCache = new AEUpgradeRecipeCache(host);
        source = new MachineSource(host);
    }

    public AENetworkProxy getProxy() {
        return proxy;
    }

    public IActionSource getActionSource() {
        return source;
    }

    public void read(NBTTagCompound nbtTags) {
        proxy.readFromNBT(nbtTags);
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
        updateProxyValidSides();
        queuePatternChange();
    }

    public void write(NBTTagCompound nbtTags) {
        proxy.writeToNBT(nbtTags);
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
        proxy.invalidate();
    }

    public void onChunkUnload() {
        AERecipeProfileManager.unregisterHost(host);
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        pendingPatternChangeTicks = 0;
        proxy.onChunkUnload();
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
        if (!ready || proxy.getNode() == null) {
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
        proxy.validate();
        proxy.onReady();
        AERecipeProfileManager.registerHost(host);
        ready = true;
        lastNetworkUsable = false;
        notifyConnectionSide(connectionSide);
        refreshNeighborGridNode(connectionSide);
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
        updateProxyValidSides();
        proxy.invalidate();
        notifyConnectionSide(previousSide);
        refreshNeighborGridNode(previousSide);
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
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        if (!host.shouldExposeAE() || connectionSide == null) {
            return null;
        }
        EnumFacing side = dir.getFacing();
        if (side != null && side != connectionSide) {
            return null;
        }
        return proxy.getNode();
    }

    @Nonnull
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        EnumFacing side = dir.getFacing();
        return host.shouldExposeAE() && side != null && side == connectionSide ? AECableType.COVERED : AECableType.NONE;
    }

    public boolean isBusy() {
        if (!host.shouldExposeAE()) {
            debugBusy("busy: AE upgrade is not installed or host is not exposing AE");
            return true;
        }
        if (!canUseNetwork()) {
            debugBusy("busy: network unavailable node={} active={} powered={} side={}",
                  proxy.getNode() != null, proxy.isActive(), proxy.isPowered(), connectionSide);
            return true;
        }
        if (host instanceof IAEItemRecipeHost itemHost && !itemHost.canAcceptAnyAEItemInput()) {
            debugBusy("busy: machine cannot currently accept any AE item input");
            return true;
        }
        return false;
    }

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        if (!host.shouldExposeAE() || !canUseNetwork()) {
            AEUpgradeDebug.log(host, "skipping crafting provider expose={} networkUsable={}", host.shouldExposeAE(), canUseNetwork());
            return;
        }
        Iterable<AEExposedRecipe> recipes = recipeCache.getRecipes();
        if (AEUpgradeDebug.enabled()) {
            int count = recipeCache.getRecipes().size();
            AEUpgradeDebug.log(host, "providing {} AE processing recipes", count);
        }
        for (AEExposedRecipe recipe : recipes) {
            craftingTracker.addCraftingOption(host, recipe);
        }
    }

    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (!host.shouldExposeAE()) {
            AEUpgradeDebug.log(host, "pushPattern rejected: AE upgrade is not installed or host is not exposing AE");
            return false;
        }
        if (!canUseNetwork()) {
            AEUpgradeDebug.log(host, "pushPattern rejected: network unavailable node={} active={} powered={} side={}",
                  proxy.getNode() != null, proxy.isActive(), proxy.isPowered(), connectionSide);
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
        boolean pushed = AEUpgradeInputInjector.push(itemHost, recipe, table);
        if (pushed) {
            AEUpgradeDebug.log(host, "pushPattern accepted inputs={} outputs={}",
                  AEUpgradeDebug.stacks(recipe.getInputStacks()), AEUpgradeDebug.stacks(recipe.getOutputStacks()));
        }
        return pushed;
    }

    public boolean canUseNetwork() {
        return proxy.getNode() != null && proxy.isActive() && proxy.isPowered();
    }

    public ItemStack injectItem(ItemStack stack, Actionable action) {
        if (stack.isEmpty() || !canUseNetwork()) {
            return stack;
        }
        try {
            IMEInventory<IAEItemStack> inventory = proxy.getStorage().getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            IAEItemStack toInsert = AEItemStack.fromItemStack(stack);
            IAEItemStack remainder = inventory.injectItems(toInsert, action, source);
            return remainder == null ? ItemStack.EMPTY : remainder.createItemStack();
        } catch (GridAccessException ignored) {
            return stack;
        }
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

    private boolean refreshConnection() {
        EnumFacing previousSide = connectionSide;
        EnumFacing side = chooseConnectionSide();
        if (side != connectionSide) {
            connectionSide = side;
            updateProxyValidSides();
            notifyConnectionSide(previousSide);
            refreshNeighborGridNode(previousSide);
            notifyConnectionSide(side);
            refreshNeighborGridNode(side);
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
            if (neighbor instanceof IGridHost gridHost && gridHost.getCableConnectionType(AEPartLocation.fromFacing(side.getOpposite())).isValid()) {
                return side;
            }
        }
        return currentSideUnloaded ? connectionSide : null;
    }

    private void ensureActive() {
        if (!host.shouldExposeAE()) {
            return;
        }
        if (!ready || proxy.getNode() == null) {
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

    private void updateProxyValidSides() {
        proxy.setValidSides(connectionSide == null ? EnumSet.noneOf(EnumFacing.class) : EnumSet.of(connectionSide));
    }

    private void queuePatternChange() {
        pendingPatternChangeTicks = Math.max(pendingPatternChangeTicks, PATTERN_CHANGE_RETRY_TICKS);
    }

    private void flushPendingPatternChange(boolean networkUsable) {
        if (pendingPatternChangeTicks <= 0 || !networkUsable) {
            return;
        }
        if (postPatternChange()) {
            pendingPatternChangeTicks--;
        }
    }

    private boolean postPatternChange() {
        IGridNode node = proxy.getNode();
        if (node == null) {
            return false;
        }
        try {
            proxy.getGrid().postEvent(new MENetworkCraftingPatternChange(host, node));
            return true;
        } catch (GridAccessException ignored) {
            return false;
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

    private void refreshNeighborGridNode(@Nullable EnumFacing side) {
        if (side == null || !(host instanceof TileEntity tile) || tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        BlockPos neighborPos = tile.getPos().offset(side);
        if (!tile.getWorld().isBlockLoaded(neighborPos, false)) {
            return;
        }
        TileEntity neighbor = tile.getWorld().getTileEntity(neighborPos);
        if (neighbor instanceof IGridHost gridHost) {
            IGridNode neighborNode = gridHost.getGridNode(AEPartLocation.fromFacing(side.getOpposite()));
            if (neighborNode != null) {
                neighborNode.updateState();
            }
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
