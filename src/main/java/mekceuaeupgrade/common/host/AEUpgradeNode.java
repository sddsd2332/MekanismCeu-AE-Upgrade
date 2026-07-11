package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.config.AERecipeConfigType;
import mekceuaeupgrade.common.config.AERecipeProfile;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import mekceuaeupgrade.common.transfer.AEAutoProcessingController;
import mekceuaeupgrade.common.transfer.AEUpgradeFluidBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeGasBridge;
import mekceuaeupgrade.common.transfer.AEUpgradeInputInjector;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.IWirelessAccessPoint;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.util.AECableType;
import ae2.me.helpers.MachineSource;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.IContainerTransaction;
import mekanism.api.gas.GasStack;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class AEUpgradeNode {

    private enum ExposureMode {
        NONE,
        WIRED_CRAFTING,
        WIRED_AUTO_PROCESSING,
        WIRED_OUTPUT,
        WIRELESS_CRAFTING,
        WIRELESS_AUTO_PROCESSING,
        WIRELESS_OUTPUT
    }

    private static final String PROFILE_OWNER_TAG = "aeRecipeProfileOwner";
    private static final String PROFILE_INDIVIDUAL_TAG = "aeRecipeProfileIndividual";
    private static final String PROFILE_BLACKLIST_INDIVIDUAL_INITIALIZED_TAG = "aeRecipeProfileBlacklistIndividualInitialized";
    private static final String PROFILE_WHITELIST_INDIVIDUAL_INITIALIZED_TAG = "aeRecipeProfileWhitelistIndividualInitialized";
    private static final String PROFILE_INSTANCE_TAG = "aeRecipeProfileInstance";
    private static final String PROFILE_GLOBAL_SLOT_TAG = "aeRecipeProfileGlobalSlot";
    private static final String PROFILE_FILTER_MODE_TAG = "aeRecipeProfileFilterMode";
    private static final String AUTO_PROFILE_INDIVIDUAL_TAG = "aeAutoProcessingRecipeProfileIndividual";
    private static final String AUTO_PROFILE_INDIVIDUAL_INITIALIZED_TAG = "aeAutoProcessingRecipeProfileIndividualInitialized";
    private static final String AUTO_PROFILE_INSTANCE_TAG = "aeAutoProcessingRecipeProfileInstance";
    private static final String AUTO_PROFILE_GLOBAL_SLOT_TAG = "aeAutoProcessingRecipeProfileGlobalSlot";
    private static final String CONNECTION_SIDE_TAG = "aeConnectionSide";
    private static final String WIRELESS_CRAFTING_LINK_TAG = "aeWirelessCraftingLink";
    private static final String WIRELESS_AUTO_PROCESSING_LINK_TAG = "aeWirelessAutoProcessingLink";
    private static final String WIRELESS_OUTPUT_LINK_TAG = "aeWirelessOutputLink";
    private static final String GRID_NODE_TAG = "aeUpgrade";

    private static final int PATTERN_CHANGE_RETRY_TICKS = 5;
    private static final int WIRED_CONNECTION_RETRY_TICKS = 5;
    private static final int WIRED_CONNECTION_REFRESH_TICKS = 20;
    private static final int WIRELESS_TARGET_RETRY_TICKS = 5;
    private static final int WIRELESS_TARGET_REFRESH_TICKS = 20;
    private static final int AUTO_PROCESSING_INTERVAL_TICKS = 5;
    private static final int AUTO_PROCESSING_RETRY_INITIAL_TICKS = 20;
    private static final int AUTO_PROCESSING_RETRY_MAX_TICKS = 100;
    private static final int OUTPUT_DRAIN_RETRY_INITIAL_TICKS = 5;
    private static final int OUTPUT_DRAIN_RETRY_MAX_TICKS = 40;

    public static final int MIN_GLOBAL_PROFILE_SLOT = 1;
    public static final int MAX_GLOBAL_PROFILE_SLOT = 10;

    private final IAEUpgradeHost host;
    private final Object machineAccessMonitor;
    private final AEUpgradeRecipeCache recipeCache;
    private final MachineSource source;
    private final IContentsListener outputContentsListener = this::requestOutputDrain;
    private final IContentsListener inputContentsListener = this::requestAutoProcessing;
    private final Map<IContentsListenerRegistry, Boolean> observedOutputContainers = new IdentityHashMap<>();
    private final Map<IContentsListenerRegistry, Boolean> observedInputContainers = new IdentityHashMap<>();

    @Nullable
    private IManagedGridNode managedNode;
    private NBTTagCompound gridNodeData = new NBTTagCompound();
    @Nullable
    private EnumFacing connectionSide;
    private long nextConnectionRefreshTick = Long.MIN_VALUE;

    @Nullable
    private AEWirelessLink wirelessCraftingLink;
    @Nullable
    private AEWirelessLink wirelessAutoProcessingLink;
    @Nullable
    private AEWirelessLink wirelessOutputLink;
    @Nullable
    private AEWirelessLink resolvedWirelessLink;
    @Nullable
    private IWirelessAccessPoint resolvedWirelessAccessPoint;
    @Nullable
    private IGrid resolvedWirelessGrid;
    @Nullable
    private IGridNode resolvedWirelessActionableNode;
    private long nextWirelessTargetRefreshTick = Long.MIN_VALUE;

    @Nullable
    private ICraftingService registeredWirelessCraftingService;
    @Nullable
    private IGrid registeredWirelessCraftingGrid;

    @Nullable
    private UUID recipeProfileOwner;
    @Nullable
    private UUID recipeProfileInstance;
    @Nullable
    private UUID autoProcessingRecipeProfileInstance;
    private int recipeProfileGlobalSlot = MIN_GLOBAL_PROFILE_SLOT;
    private int autoProcessingRecipeProfileGlobalSlot = MIN_GLOBAL_PROFILE_SLOT;
    private AERecipeProfile.RouteFilterMode recipeProfileFilterMode = AERecipeProfile.RouteFilterMode.BLACKLIST;
    private boolean recipeProfileIndividual;
    private boolean recipeProfileBlacklistIndividualInitialized;
    private boolean recipeProfileWhitelistIndividualInitialized;
    private boolean autoProcessingRecipeProfileIndividual;
    private boolean autoProcessingRecipeProfileIndividualInitialized;

    private ExposureMode activeMode = ExposureMode.NONE;
    private boolean ready;
    private boolean lastNetworkUsable;
    private int pendingPatternChangeTicks;
    @Nullable
    private Object lastRecipeSourceKey;

    private long networkCacheTick = Long.MIN_VALUE;
    private boolean cachedNetworkUsable;
    @Nullable
    private IGrid cachedGrid;
    @Nullable
    private IGridNode cachedActionableNode;
    @Nullable
    private MEStorage cachedStorage;

    private final int autoProcessingTickOffset;
    private volatile boolean autoProcessingRequested = true;
    private long nextAutoProcessingTick = Long.MIN_VALUE;
    private int autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;

    private volatile boolean outputDrainRequested = true;
    private boolean outputDrainRetryPending;
    private boolean outputBlocked;
    private boolean outputPollingRequired;
    private long nextOutputDrainTick = Long.MIN_VALUE;
    private int outputDrainRetryInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;

    private long lastBusyDebugTick = Long.MIN_VALUE;

    public AEUpgradeNode(IAEUpgradeHost host, Object machineAccessMonitor) {
        this.host = host;
        this.machineAccessMonitor = Objects.requireNonNull(machineAccessMonitor);
        recipeCache = new AEUpgradeRecipeCache(host);
        source = new MachineSource(host);
        autoProcessingTickOffset = Math.floorMod(System.identityHashCode(host), AUTO_PROCESSING_INTERVAL_TICKS);
    }

    @Nullable
    public IManagedGridNode getManagedNode() {
        return managedNode;
    }

    public IActionSource getActionSource() {
        return source;
    }

    public void read(NBTTagCompound nbtTags) {
        gridNodeData = new NBTTagCompound();
        if (nbtTags.hasKey(GRID_NODE_TAG, Constants.NBT.TAG_COMPOUND)) {
            gridNodeData.setTag(GRID_NODE_TAG, nbtTags.getCompoundTag(GRID_NODE_TAG).copy());
        }
        if (managedNode != null && !managedNode.isReady()) {
            managedNode.loadFromNBT(gridNodeData);
        }
        recipeProfileOwner = readUuid(nbtTags, PROFILE_OWNER_TAG);
        recipeProfileInstance = readUuid(nbtTags, PROFILE_INSTANCE_TAG);
        autoProcessingRecipeProfileInstance = readUuid(nbtTags, AUTO_PROFILE_INSTANCE_TAG);
        recipeProfileGlobalSlot = nbtTags.hasKey(PROFILE_GLOBAL_SLOT_TAG)
              ? clampGlobalProfileSlot(nbtTags.getInteger(PROFILE_GLOBAL_SLOT_TAG)) : MIN_GLOBAL_PROFILE_SLOT;
        autoProcessingRecipeProfileGlobalSlot = nbtTags.hasKey(AUTO_PROFILE_GLOBAL_SLOT_TAG)
              ? clampGlobalProfileSlot(nbtTags.getInteger(AUTO_PROFILE_GLOBAL_SLOT_TAG)) : MIN_GLOBAL_PROFILE_SLOT;
        recipeProfileIndividual = nbtTags.getBoolean(PROFILE_INDIVIDUAL_TAG);
        recipeProfileFilterMode = nbtTags.hasKey(PROFILE_FILTER_MODE_TAG)
              ? AERecipeProfile.RouteFilterMode.fromName(nbtTags.getString(PROFILE_FILTER_MODE_TAG))
              : AERecipeProfile.RouteFilterMode.BLACKLIST;
        recipeProfileBlacklistIndividualInitialized = nbtTags.getBoolean(PROFILE_BLACKLIST_INDIVIDUAL_INITIALIZED_TAG);
        recipeProfileWhitelistIndividualInitialized = nbtTags.getBoolean(PROFILE_WHITELIST_INDIVIDUAL_INITIALIZED_TAG);
        autoProcessingRecipeProfileIndividual = nbtTags.getBoolean(AUTO_PROFILE_INDIVIDUAL_TAG);
        autoProcessingRecipeProfileIndividualInitialized = nbtTags.getBoolean(AUTO_PROFILE_INDIVIDUAL_INITIALIZED_TAG);
        connectionSide = readConnectionSide(nbtTags);
        wirelessCraftingLink = AEWirelessLink.read(nbtTags, WIRELESS_CRAFTING_LINK_TAG);
        wirelessAutoProcessingLink = AEWirelessLink.read(nbtTags, WIRELESS_AUTO_PROCESSING_LINK_TAG);
        wirelessOutputLink = AEWirelessLink.read(nbtTags, WIRELESS_OUTPUT_LINK_TAG);
        invalidateWirelessTarget();
        invalidateNetworkCache();
        updateExposedSides();
        queuePatternChange();
    }

    public void write(NBTTagCompound nbtTags) {
        if (managedNode != null) {
            managedNode.saveToNBT(gridNodeData);
        }
        if (gridNodeData.hasKey(GRID_NODE_TAG, Constants.NBT.TAG_COMPOUND)) {
            nbtTags.setTag(GRID_NODE_TAG, gridNodeData.getTag(GRID_NODE_TAG).copy());
        } else {
            nbtTags.removeTag(GRID_NODE_TAG);
        }
        writeUuid(nbtTags, PROFILE_OWNER_TAG, recipeProfileOwner);
        writeUuid(nbtTags, PROFILE_INSTANCE_TAG, recipeProfileInstance);
        writeUuid(nbtTags, AUTO_PROFILE_INSTANCE_TAG, autoProcessingRecipeProfileInstance);
        nbtTags.setInteger(PROFILE_GLOBAL_SLOT_TAG, recipeProfileGlobalSlot);
        nbtTags.setInteger(AUTO_PROFILE_GLOBAL_SLOT_TAG, autoProcessingRecipeProfileGlobalSlot);
        nbtTags.setBoolean(PROFILE_INDIVIDUAL_TAG, recipeProfileIndividual);
        nbtTags.setString(PROFILE_FILTER_MODE_TAG, recipeProfileFilterMode.name());
        nbtTags.setBoolean(PROFILE_BLACKLIST_INDIVIDUAL_INITIALIZED_TAG, recipeProfileBlacklistIndividualInitialized);
        nbtTags.setBoolean(PROFILE_WHITELIST_INDIVIDUAL_INITIALIZED_TAG, recipeProfileWhitelistIndividualInitialized);
        nbtTags.setBoolean(AUTO_PROFILE_INDIVIDUAL_TAG, autoProcessingRecipeProfileIndividual);
        nbtTags.setBoolean(AUTO_PROFILE_INDIVIDUAL_INITIALIZED_TAG, autoProcessingRecipeProfileIndividualInitialized);
        if (connectionSide == null) {
            nbtTags.removeTag(CONNECTION_SIDE_TAG);
        } else {
            nbtTags.setInteger(CONNECTION_SIDE_TAG, connectionSide.ordinal());
        }
        writeWirelessLink(nbtTags, WIRELESS_CRAFTING_LINK_TAG, wirelessCraftingLink);
        writeWirelessLink(nbtTags, WIRELESS_AUTO_PROCESSING_LINK_TAG, wirelessAutoProcessingLink);
        writeWirelessLink(nbtTags, WIRELESS_OUTPUT_LINK_TAG, wirelessOutputLink);
    }

    @Nullable
    public AEWirelessLink getWirelessCraftingLink() {
        return wirelessCraftingLink;
    }

    public void setWirelessCraftingLink(@Nullable AEWirelessLink link) {
        if (!Objects.equals(wirelessCraftingLink, link)) {
            wirelessCraftingLink = link;
            onWirelessLinkChanged();
        }
    }

    @Nullable
    public AEWirelessLink getWirelessAutoProcessingLink() {
        return wirelessAutoProcessingLink;
    }

    public void setWirelessAutoProcessingLink(@Nullable AEWirelessLink link) {
        if (!Objects.equals(wirelessAutoProcessingLink, link)) {
            wirelessAutoProcessingLink = link;
            onWirelessLinkChanged();
        }
    }

    @Nullable
    public AEWirelessLink getWirelessOutputLink() {
        return wirelessOutputLink;
    }

    public void setWirelessOutputLink(@Nullable AEWirelessLink link) {
        if (!Objects.equals(wirelessOutputLink, link)) {
            wirelessOutputLink = link;
            onWirelessLinkChanged();
        }
    }

    private void onWirelessLinkChanged() {
        unregisterWirelessCraftingProvider();
        invalidateWirelessTarget();
        invalidateNetworkCache();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        queuePatternChange();
        markHostDirty();
    }

    @Nullable
    public UUID getRecipeProfileOwner() {
        return recipeProfileOwner;
    }

    public void setRecipeProfileOwner(@Nullable UUID owner) {
        if (!Objects.equals(recipeProfileOwner, owner)) {
            recipeProfileOwner = owner;
            markHostDirty();
            invalidateRecipeCache();
        }
    }

    public boolean isRecipeProfileIndividual() {
        return isRecipeProfileIndividual(AERecipeConfigType.CRAFTING);
    }

    public boolean isRecipeProfileIndividualInitialized() {
        return isRecipeProfileIndividualInitialized(AERecipeConfigType.CRAFTING);
    }

    public int getRecipeProfileGlobalSlot() {
        return getRecipeProfileGlobalSlot(AERecipeConfigType.CRAFTING);
    }

    public boolean cycleRecipeProfileGlobalSlot() {
        return cycleRecipeProfileGlobalSlot(AERecipeConfigType.CRAFTING, 1);
    }

    public boolean cycleRecipeProfileGlobalSlot(int direction) {
        return cycleRecipeProfileGlobalSlot(AERecipeConfigType.CRAFTING, direction);
    }

    public boolean setRecipeProfileGlobalSlot(int slot) {
        return setRecipeProfileGlobalSlot(AERecipeConfigType.CRAFTING, slot);
    }

    public boolean setRecipeProfileIndividual(boolean individual) {
        return setRecipeProfileIndividual(AERecipeConfigType.CRAFTING, individual);
    }

    public void markRecipeProfileIndividualInitialized() {
        markRecipeProfileIndividualInitialized(AERecipeConfigType.CRAFTING);
    }

    public UUID getOrCreateRecipeProfileInstance() {
        return getOrCreateRecipeProfileInstance(AERecipeConfigType.CRAFTING);
    }

    public boolean isRecipeProfileIndividual(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING
              ? autoProcessingRecipeProfileIndividual : recipeProfileIndividual;
    }

    public boolean isRecipeProfileIndividualInitialized(AERecipeConfigType type) {
        return isRecipeProfileIndividualInitialized(type, getRecipeProfileFilterMode(type));
    }

    public boolean isRecipeProfileIndividualInitialized(AERecipeConfigType type, AERecipeProfile.RouteFilterMode filterMode) {
        if (type == AERecipeConfigType.AUTO_PROCESSING) {
            return autoProcessingRecipeProfileIndividualInitialized;
        }
        return filterMode == AERecipeProfile.RouteFilterMode.WHITELIST
              ? recipeProfileWhitelistIndividualInitialized : recipeProfileBlacklistIndividualInitialized;
    }

    public int getRecipeProfileGlobalSlot(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING
              ? autoProcessingRecipeProfileGlobalSlot : recipeProfileGlobalSlot;
    }

    public AERecipeProfile.RouteFilterMode getRecipeProfileFilterMode(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING
              ? AERecipeProfile.RouteFilterMode.WHITELIST : recipeProfileFilterMode;
    }

    public boolean setRecipeProfileFilterMode(AERecipeConfigType type, AERecipeProfile.RouteFilterMode filterMode) {
        if (type != AERecipeConfigType.CRAFTING) {
            return false;
        }
        AERecipeProfile.RouteFilterMode normalized = filterMode == null
              ? AERecipeProfile.RouteFilterMode.BLACKLIST : filterMode;
        if (recipeProfileFilterMode == normalized) {
            return false;
        }
        recipeProfileFilterMode = normalized;
        markHostDirty();
        invalidateRecipeCache();
        return true;
    }

    public boolean cycleRecipeProfileGlobalSlot(AERecipeConfigType type, int direction) {
        int current = getRecipeProfileGlobalSlot(type);
        int next = direction < 0
              ? current <= MIN_GLOBAL_PROFILE_SLOT ? MAX_GLOBAL_PROFILE_SLOT : current - 1
              : current >= MAX_GLOBAL_PROFILE_SLOT ? MIN_GLOBAL_PROFILE_SLOT : current + 1;
        return setRecipeProfileGlobalSlot(type, next);
    }

    public boolean setRecipeProfileGlobalSlot(AERecipeConfigType type, int slot) {
        slot = clampGlobalProfileSlot(slot);
        if (type == AERecipeConfigType.AUTO_PROCESSING) {
            if (autoProcessingRecipeProfileGlobalSlot == slot) {
                return false;
            }
            autoProcessingRecipeProfileGlobalSlot = slot;
        } else {
            if (recipeProfileGlobalSlot == slot) {
                return false;
            }
            recipeProfileGlobalSlot = slot;
        }
        markHostDirty();
        invalidateRecipeCache();
        return true;
    }

    public boolean setRecipeProfileIndividual(AERecipeConfigType type, boolean individual) {
        if (type == AERecipeConfigType.AUTO_PROCESSING) {
            if (autoProcessingRecipeProfileIndividual == individual) {
                return false;
            }
            autoProcessingRecipeProfileIndividual = individual;
        } else {
            if (recipeProfileIndividual == individual) {
                return false;
            }
            recipeProfileIndividual = individual;
        }
        markHostDirty();
        invalidateRecipeCache();
        return true;
    }

    public void markRecipeProfileIndividualInitialized(AERecipeConfigType type) {
        markRecipeProfileIndividualInitialized(type, getRecipeProfileFilterMode(type));
    }

    public void markRecipeProfileIndividualInitialized(AERecipeConfigType type, AERecipeProfile.RouteFilterMode filterMode) {
        if (isRecipeProfileIndividualInitialized(type, filterMode)) {
            return;
        }
        if (type == AERecipeConfigType.AUTO_PROCESSING) {
            autoProcessingRecipeProfileIndividualInitialized = true;
        } else if (filterMode == AERecipeProfile.RouteFilterMode.WHITELIST) {
            recipeProfileWhitelistIndividualInitialized = true;
        } else {
            recipeProfileBlacklistIndividualInitialized = true;
        }
        markHostDirty();
    }

    public UUID getOrCreateRecipeProfileInstance(AERecipeConfigType type) {
        if (type == AERecipeConfigType.AUTO_PROCESSING) {
            if (autoProcessingRecipeProfileInstance == null) {
                autoProcessingRecipeProfileInstance = UUID.randomUUID();
                markHostDirty();
            }
            return autoProcessingRecipeProfileInstance;
        }
        if (recipeProfileInstance == null) {
            recipeProfileInstance = UUID.randomUUID();
            markHostDirty();
        }
        return recipeProfileInstance;
    }

    public void onLoad() {
        synchronizeMode();
    }

    public void validate() {
        synchronizeMode();
    }

    public void invalidate() {
        stopRuntime();
        activeMode = ExposureMode.NONE;
    }

    public void onChunkUnload() {
        stopRuntime();
        activeMode = ExposureMode.NONE;
    }

    public void tickServer() {
        synchronizeMode();
        if (activeMode == ExposureMode.NONE || !ready) {
            return;
        }

        boolean connectionChanged = usesWiredNetwork(activeMode) && refreshConnectionIfNeeded();
        boolean networkUsable = canUseNetwork();
        updateWirelessCraftingProviderRegistration(networkUsable);
        boolean networkRestored = networkUsable && !lastNetworkUsable;

        if (networkRestored || connectionChanged) {
            resetOutputDrainSchedule();
            resetAutoProcessingSchedule();
            queuePatternChange();
        }
        if (networkUsable && (isCraftingMode(activeMode) || isAutoProcessingMode(activeMode))
              && refreshRecipeSourceKey()) {
            if (isCraftingMode(activeMode)) {
                queuePatternChange();
            } else {
                resetAutoProcessingSchedule();
            }
        }
        flushPendingPatternChange(networkUsable);
        lastNetworkUsable = networkUsable;

        if (networkUsable && supportsOutputDrain() && shouldDrainOutputsThisTick()) {
            outputDrainRequested = false;
            drainOutputs();
            scheduleNextOutputDrain();
        }
        if (networkUsable && isAutoProcessingMode(activeMode) && host instanceof IAEItemRecipeHost itemHost
              && shouldRunAutoProcessingThisTick()) {
            autoProcessingRequested = false;
            itemHost.observeAEInputContainers(this::observeInputContainer);
            boolean processed = AEAutoProcessingController.process(this, itemHost,
                  recipeCache.getAutoProcessingRecipes());
            scheduleNextAutoProcessing(processed);
            if (processed) {
                resetOutputDrainSchedule();
            }
        }
    }

    public void activate() {
        synchronizeMode();
    }

    public void deactivate() {
        stopRuntime();
        activeMode = ExposureMode.NONE;
    }

    public void onNeighborChanged() {
        if (usesWiredNetwork(activeMode) && refreshConnection(true)) {
            invalidateNetworkCache();
            queuePatternChange();
        }
    }

    public void onUpgradeConfigurationChanged() {
        ExposureMode desiredMode = resolveExposureMode();
        if (desiredMode != activeMode) {
            switchMode(desiredMode);
        } else {
            invalidateWirelessTarget();
            invalidateNetworkCache();
            resetOutputDrainSchedule();
            resetAutoProcessingSchedule();
            queuePatternChange();
        }
        markHostDirty();
    }

    private void synchronizeMode() {
        ExposureMode desiredMode = resolveExposureMode();
        if (desiredMode != activeMode) {
            switchMode(desiredMode);
        } else if (desiredMode != ExposureMode.NONE && !ready) {
            startRuntime();
        }
    }

    private void switchMode(ExposureMode mode) {
        stopRuntime();
        activeMode = mode;
        if (mode != ExposureMode.NONE) {
            startRuntime();
        }
    }

    private void startRuntime() {
        if (activeMode == ExposureMode.NONE || !isHostAvailable()) {
            return;
        }
        if (usesWiredNetwork(activeMode)) {
            refreshConnection(true);
            createManagedNodeWhenReady();
        } else {
            EnumFacing previousSide = connectionSide;
            connectionSide = null;
            updateExposedSides();
            notifyConnectionSide(previousSide);
            destroyManagedNode();
        }
        AERecipeProfileManager.registerHost(host);
        ready = true;
        lastNetworkUsable = false;
        invalidateWirelessTarget();
        invalidateNetworkCache();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        queuePatternChange();
    }

    private void stopRuntime() {
        clearObservedContainers();
        AERecipeProfileManager.unregisterHost(host);
        unregisterWirelessCraftingProvider();
        EnumFacing previousSide = connectionSide;
        connectionSide = null;
        updateExposedSides();
        destroyManagedNode();
        notifyConnectionSide(previousSide);
        invalidateWirelessTarget();
        invalidateNetworkCache();
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        pendingPatternChangeTicks = 0;
        nextConnectionRefreshTick = Long.MIN_VALUE;
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
    }

    @Nullable
    public IGridNode getGridNode(@Nonnull EnumFacing dir) {
        return usesWiredNetwork(activeMode) && connectionSide == dir ? getGridNode() : null;
    }

    @Nullable
    public IGridNode getGridNode() {
        return managedNode == null ? null : managedNode.getNode();
    }

    @Nullable
    public IGridNode getActionableNode() {
        refreshNetworkCache();
        return cachedActionableNode;
    }

    @Nonnull
    public AECableType getCableConnectionType(@Nonnull EnumFacing dir) {
        return usesWiredNetwork(activeMode) && connectionSide == dir ? AECableType.COVERED : AECableType.NONE;
    }

    public boolean isBusy() {
        if (!isCraftingMode(activeMode)) {
            return true;
        }
        if (!canUseNetwork()) {
            debugBusy("busy: network unavailable mode={} side={}", activeMode, connectionSide);
            return true;
        }
        if (host instanceof IAEItemRecipeHost itemHost) {
            return callMachineContainerTransaction(() -> {
                if (itemHost.canAcceptAnyAEItemInput()) {
                    return false;
                }
                debugBusy("busy: machine cannot currently accept any AE item input");
                return true;
            });
        }
        return false;
    }

    public List<AEExposedRecipe> getAvailablePatterns() {
        if (!isCraftingMode(activeMode) || !canUseNetwork()) {
            return List.of();
        }
        return recipeCache.getRecipes();
    }

    public boolean isWirelessCraftingProviderFor(IGrid grid) {
        return grid != null && activeMode == ExposureMode.WIRELESS_CRAFTING
              && registeredWirelessCraftingGrid == grid;
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        if (!isCraftingMode(activeMode) || multiplier != 1 || !canUseNetwork()
              || !(host instanceof IAEItemRecipeHost itemHost)) {
            return false;
        }
        AEExposedRecipe recipe = recipeCache.find(exposed -> exposed.matches(patternDetails));
        if (recipe == null) {
            return false;
        }
        List<ItemStack> inputs = toItemInputs(inputHolder, recipe);
        if (inputs == null) {
            return false;
        }
        if (!callMachineContainerTransaction(() -> AEUpgradeInputInjector.push(itemHost, recipe, inputs))) {
            return false;
        }
        resetOutputDrainSchedule();
        AEUpgradeDebug.log(host, "pushPattern accepted inputs={} outputs={}",
              AEUpgradeDebug.inputStacks(recipe), AEUpgradeDebug.outputStacks(recipe));
        return true;
    }

    public <T> T callMachineContainerTransaction(Supplier<T> action) {
        Objects.requireNonNull(action, "Machine container transaction action cannot be null");
        if (host instanceof IContainerTransaction transaction) {
            return transaction.callContainerTransaction(action);
        }
        synchronized (machineAccessMonitor) {
            return action.get();
        }
    }

    public boolean canUseNetwork() {
        refreshNetworkCache();
        return cachedNetworkUsable;
    }

    @Nullable
    public MEStorage getNetworkStorage() {
        refreshNetworkCache();
        return cachedNetworkUsable ? cachedStorage : null;
    }

    public ItemStack injectItem(ItemStack stack, Actionable action) {
        if (stack.isEmpty()) {
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

    public ItemStack extractItem(ItemStack request, Actionable action) {
        if (request.isEmpty()) {
            return ItemStack.EMPTY;
        }
        MEStorage storage = getNetworkStorage();
        AEItemKey key = AEItemKey.of(request);
        if (storage == null || key == null) {
            return ItemStack.EMPTY;
        }
        long extracted = storage.extract(key, request.getCount(), action, source);
        return extracted <= 0 ? ItemStack.EMPTY : key.toStack((int) Math.min(extracted, Integer.MAX_VALUE));
    }

    public boolean hasAvailableItem(ItemStack request) {
        return !request.isEmpty() && extractItem(request, Actionable.SIMULATE).getCount() >= request.getCount();
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
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        queuePatternChange();
    }

    public void onRecipePortsChanged() {
        clearObservedContainers();
        invalidateRecipeCache();
    }

    public void observeOutputContainer(Object container) {
        if (container != null && !observeContainer(container, outputContentsListener, observedOutputContainers)) {
            outputPollingRequired = true;
        }
    }

    public void observeInputContainer(Object container) {
        if (container != null) {
            observeContainer(container, inputContentsListener, observedInputContainers);
        }
    }

    public void markOutputBlocked() {
        outputBlocked = true;
    }

    private void refreshNetworkCache() {
        long tick = getWorldTime();
        if (networkCacheTick == tick && tick != Long.MIN_VALUE) {
            return;
        }
        networkCacheTick = tick;
        cachedNetworkUsable = false;
        cachedGrid = null;
        cachedActionableNode = null;
        cachedStorage = null;

        if (usesWiredNetwork(activeMode)) {
            IGridNode node = getGridNode();
            if (managedNode == null || !managedNode.isReady() || !managedNode.isActive() || !managedNode.isPowered()
                  || node == null) {
                return;
            }
            try {
                cachedGrid = node.grid();
                cachedActionableNode = node;
            } catch (IllegalStateException ignored) {
                return;
            }
        } else if (usesWirelessNetwork(activeMode)) {
            refreshWirelessTarget();
            cachedGrid = resolvedWirelessGrid;
            cachedActionableNode = resolvedWirelessActionableNode;
            if (resolvedWirelessAccessPoint == null || !resolvedWirelessAccessPoint.isActive()
                  || cachedGrid == null || cachedActionableNode == null || !cachedActionableNode.isActive()) {
                return;
            }
        } else {
            return;
        }

        try {
            cachedStorage = cachedGrid.getStorageService().getInventory();
            cachedNetworkUsable = cachedStorage != null;
        } catch (RuntimeException | LinkageError ignored) {
            cachedStorage = null;
        }
    }

    private void invalidateNetworkCache() {
        networkCacheTick = Long.MIN_VALUE;
        cachedNetworkUsable = false;
        cachedGrid = null;
        cachedActionableNode = null;
        cachedStorage = null;
    }

    private void refreshWirelessTarget() {
        AEWirelessLink link = getActiveWirelessLink();
        if (link == null) {
            invalidateWirelessTarget();
            return;
        }
        long tick = getWorldTime();
        if (!Objects.equals(link, resolvedWirelessLink)) {
            invalidateWirelessTarget();
            resolvedWirelessLink = link;
        }
        if (resolvedWirelessAccessPoint != null && tick != Long.MIN_VALUE
              && nextWirelessTargetRefreshTick != Long.MIN_VALUE && tick < nextWirelessTargetRefreshTick) {
            try {
                IGrid grid = resolvedWirelessAccessPoint.getGrid();
                IGridNode node = resolvedWirelessAccessPoint.getActionableNode();
                if (resolvedWirelessAccessPoint.isActive() && grid != null && node != null) {
                    resolvedWirelessGrid = grid;
                    resolvedWirelessActionableNode = node;
                    return;
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }

        resolvedWirelessAccessPoint = null;
        resolvedWirelessGrid = null;
        resolvedWirelessActionableNode = null;
        World targetWorld = getLinkedWorld(link);
        if (targetWorld != null && targetWorld.isBlockLoaded(link.position(), false)) {
            TileEntity tile = targetWorld.getTileEntity(link.position());
            if (tile instanceof IWirelessAccessPoint accessPoint) {
                try {
                    IGrid grid = accessPoint.getGrid();
                    IGridNode node = accessPoint.getActionableNode();
                    if (accessPoint.isActive() && grid != null && node != null && node.isActive()) {
                        resolvedWirelessAccessPoint = accessPoint;
                        resolvedWirelessGrid = grid;
                        resolvedWirelessActionableNode = node;
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
        if (tick != Long.MIN_VALUE) {
            nextWirelessTargetRefreshTick = tick + (resolvedWirelessGrid == null
                  ? WIRELESS_TARGET_RETRY_TICKS : WIRELESS_TARGET_REFRESH_TICKS);
        }
    }

    @Nullable
    private World getLinkedWorld(AEWirelessLink link) {
        if (!(host instanceof TileEntity tile) || tile.getWorld() == null) {
            return null;
        }
        if (tile.getWorld().provider.getDimension() == link.dimension()) {
            return tile.getWorld();
        }
        return DimensionManager.getWorld(link.dimension());
    }

    private void invalidateWirelessTarget() {
        resolvedWirelessLink = null;
        resolvedWirelessAccessPoint = null;
        resolvedWirelessGrid = null;
        resolvedWirelessActionableNode = null;
        nextWirelessTargetRefreshTick = Long.MIN_VALUE;
    }

    @Nullable
    private AEWirelessLink getActiveWirelessLink() {
        return switch (activeMode) {
            case WIRELESS_CRAFTING -> wirelessCraftingLink;
            case WIRELESS_AUTO_PROCESSING -> wirelessAutoProcessingLink;
            case WIRELESS_OUTPUT -> wirelessOutputLink;
            default -> null;
        };
    }

    private void updateWirelessCraftingProviderRegistration(boolean networkUsable) {
        ICraftingService desiredService = null;
        IGrid desiredGrid = null;
        if (activeMode == ExposureMode.WIRELESS_CRAFTING && networkUsable) {
            refreshNetworkCache();
            desiredGrid = cachedGrid;
            if (desiredGrid != null) {
                desiredService = desiredGrid.getCraftingService();
            }
        }
        if (desiredService == registeredWirelessCraftingService && desiredGrid == registeredWirelessCraftingGrid) {
            return;
        }
        unregisterWirelessCraftingProvider();
        if (desiredService != null) {
            try {
                desiredService.addGlobalCraftingProvider(host);
                registeredWirelessCraftingService = desiredService;
                registeredWirelessCraftingGrid = desiredGrid;
            } catch (RuntimeException | LinkageError ignored) {
                registeredWirelessCraftingService = null;
                registeredWirelessCraftingGrid = null;
            }
        }
    }

    private void unregisterWirelessCraftingProvider() {
        if (registeredWirelessCraftingService != null) {
            try {
                registeredWirelessCraftingService.removeGlobalCraftingProvider(host);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        registeredWirelessCraftingService = null;
        registeredWirelessCraftingGrid = null;
    }

    private void createManagedNodeWhenReady() {
        if (managedNode != null || !(host instanceof TileEntity tile) || tile.getWorld() == null) {
            return;
        }
        ExposureMode mode = activeMode;
        IManagedGridNode node = createManagedNode(mode);
        managedNode = node;
        GridHelper.onFirstTick(tile, ignored -> {
            if (managedNode == node && activeMode == mode && usesWiredNetwork(mode) && !node.isReady()
                  && tile.getWorld() != null && !tile.isInvalid()) {
                node.create(tile.getWorld(), tile.getPos());
                invalidateNetworkCache();
                resetOutputDrainSchedule();
                resetAutoProcessingSchedule();
                queuePatternChange();
            }
        });
    }

    private IManagedGridNode createManagedNode(ExposureMode mode) {
        IManagedGridNode node = GridHelper.createManagedNode(host, new IGridNodeListener<IAEUpgradeHost>() {
                  @Override
                  public void onSaveChanges(IAEUpgradeHost owner, IGridNode gridNode) {
                      markHostDirty();
                  }

                  @Override
                  public void onInWorldConnectionChanged(IAEUpgradeHost owner, IGridNode gridNode) {
                      notifyConnectionSide(connectionSide);
                  }

                  @Override
                  public void onGridChanged(IAEUpgradeHost owner, IGridNode gridNode) {
                      invalidateNetworkCache();
                      resetOutputDrainSchedule();
                      resetAutoProcessingSchedule();
                      queuePatternChange();
                  }

                  @Override
                  public void onStateChanged(IAEUpgradeHost owner, IGridNode gridNode, State state) {
                      invalidateNetworkCache();
                      resetOutputDrainSchedule();
                      resetAutoProcessingSchedule();
                      queuePatternChange();
                  }
              })
              .setInWorldNode(true)
              .setTagName(GRID_NODE_TAG)
              .setFlags(GridFlags.REQUIRE_CHANNEL)
              .setIdlePowerUsage(0.0)
              .setVisualRepresentation(getVisualRepresentation(mode));
        if (mode == ExposureMode.WIRED_CRAFTING) {
            node.addService(ICraftingProvider.class, host);
        }
        node.loadFromNBT(gridNodeData);
        node.setExposedOnSides(connectionSide == null ? Set.of() : EnumSet.of(connectionSide));
        return node;
    }

    private ItemStack getVisualRepresentation(ExposureMode mode) {
        return switch (mode) {
            case WIRED_AUTO_PROCESSING -> new ItemStack(MEKCeuAEUpgradeItems.AEAutoProcessingUpgrade);
            case WIRED_OUTPUT -> new ItemStack(MEKCeuAEUpgradeItems.AEOutputUpgrade);
            default -> new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade);
        };
    }

    private void destroyManagedNode() {
        if (managedNode == null) {
            return;
        }
        managedNode.saveToNBT(gridNodeData);
        managedNode.destroy();
        managedNode = null;
    }

    private boolean refreshConnectionIfNeeded() {
        long tick = getWorldTime();
        return tick == Long.MIN_VALUE || nextConnectionRefreshTick == Long.MIN_VALUE
              || tick >= nextConnectionRefreshTick ? refreshConnection(false) : false;
    }

    private boolean refreshConnection(boolean force) {
        if (!usesWiredNetwork(activeMode)) {
            return false;
        }
        if (force) {
            nextConnectionRefreshTick = Long.MIN_VALUE;
        }
        EnumFacing previous = connectionSide;
        connectionSide = chooseConnectionSide();
        updateExposedSides();
        long tick = getWorldTime();
        if (tick != Long.MIN_VALUE) {
            nextConnectionRefreshTick = tick + (connectionSide == null
                  ? WIRED_CONNECTION_RETRY_TICKS : WIRED_CONNECTION_REFRESH_TICKS);
        }
        if (previous == connectionSide) {
            return false;
        }
        notifyConnectionSide(previous);
        notifyConnectionSide(connectionSide);
        invalidateNetworkCache();
        markHostDirty();
        return true;
    }

    @Nullable
    private EnumFacing chooseConnectionSide() {
        if (!(host instanceof TileEntity tile) || tile.getWorld() == null) {
            return null;
        }
        boolean currentSideUnloaded = false;
        if (connectionSide != null) {
            BlockPos neighborPos = tile.getPos().offset(connectionSide);
            if (!tile.getWorld().isBlockLoaded(neighborPos, false)) {
                currentSideUnloaded = true;
            } else if (isConnectableNeighbor(tile, neighborPos, connectionSide)) {
                return connectionSide;
            }
        }
        for (EnumFacing side : EnumFacing.VALUES) {
            if (side == connectionSide) {
                continue;
            }
            BlockPos neighborPos = tile.getPos().offset(side);
            if (tile.getWorld().isBlockLoaded(neighborPos, false) && isConnectableNeighbor(tile, neighborPos, side)) {
                return side;
            }
        }
        return currentSideUnloaded ? connectionSide : null;
    }

    private static boolean isConnectableNeighbor(TileEntity hostTile, BlockPos neighborPos, EnumFacing side) {
        TileEntity neighbor = hostTile.getWorld().getTileEntity(neighborPos);
        return !(neighbor instanceof IAEUpgradeHost)
              && GridHelper.getExposedNode(hostTile.getWorld(), neighborPos, side.getOpposite()) != null;
    }

    private void updateExposedSides() {
        if (managedNode != null) {
            managedNode.setExposedOnSides(connectionSide == null ? Set.of() : EnumSet.of(connectionSide));
        }
    }

    private boolean refreshRecipeSourceKey() {
        Object sourceKey = host instanceof IAEItemRecipeHost itemHost ? itemHost.getAERecipeSourceKey() : null;
        if (Objects.equals(lastRecipeSourceKey, sourceKey)) {
            return false;
        }
        lastRecipeSourceKey = sourceKey;
        recipeCache.invalidate();
        return true;
    }

    private boolean supportsOutputDrain() {
        if (host instanceof IAEItemRecipeHost) {
            return isCraftingMode(activeMode) || isAutoProcessingMode(activeMode);
        }
        return host instanceof IAEOutputHost && isOutputMode(activeMode);
    }

    private boolean drainOutputs() {
        outputBlocked = false;
        if (host instanceof IAEItemRecipeHost itemHost) {
            return itemHost.drainAEItemOutputs(this);
        }
        return host instanceof IAEOutputHost outputHost && outputHost.drainAEOutputs(this);
    }

    private boolean shouldDrainOutputsThisTick() {
        long tick = getWorldTime();
        return outputDrainRequested || outputDrainRetryPending
              && (tick == Long.MIN_VALUE || nextOutputDrainTick == Long.MIN_VALUE || tick >= nextOutputDrainTick);
    }

    private void scheduleNextOutputDrain() {
        outputDrainRetryPending = outputBlocked || outputPollingRequired;
        if (!outputDrainRetryPending) {
            outputDrainRetryInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
            nextOutputDrainTick = Long.MIN_VALUE;
            return;
        }
        long tick = getWorldTime();
        nextOutputDrainTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + outputDrainRetryInterval;
        outputDrainRetryInterval = Math.min(OUTPUT_DRAIN_RETRY_MAX_TICKS, outputDrainRetryInterval << 1);
    }

    private void resetOutputDrainSchedule() {
        outputDrainRequested = true;
        outputDrainRetryPending = false;
        outputDrainRetryInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
        nextOutputDrainTick = Long.MIN_VALUE;
    }

    private void requestOutputDrain() {
        outputDrainRequested = true;
        autoProcessingRequested = true;
    }

    private boolean shouldRunAutoProcessingThisTick() {
        long tick = getWorldTime();
        return autoProcessingRequested || tick == Long.MIN_VALUE || nextAutoProcessingTick == Long.MIN_VALUE
              || tick >= nextAutoProcessingTick;
    }

    private void scheduleNextAutoProcessing(boolean processed) {
        long tick = getWorldTime();
        if (processed) {
            autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
            nextAutoProcessingTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + AUTO_PROCESSING_INTERVAL_TICKS;
        } else {
            nextAutoProcessingTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE
                  : tick + autoProcessingRetryInterval + autoProcessingTickOffset;
            autoProcessingRetryInterval = Math.min(AUTO_PROCESSING_RETRY_MAX_TICKS,
                  autoProcessingRetryInterval << 1);
        }
    }

    private void resetAutoProcessingSchedule() {
        autoProcessingRequested = true;
        autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
        nextAutoProcessingTick = Long.MIN_VALUE;
    }

    private void requestAutoProcessing() {
        autoProcessingRequested = true;
    }

    private static boolean observeContainer(Object container, IContentsListener listener,
          Map<IContentsListenerRegistry, Boolean> observedContainers) {
        if (!(container instanceof IContentsListenerRegistry registry)) {
            return false;
        }
        if (observedContainers.containsKey(registry)) {
            return true;
        }
        if (registry.addContentsListener(listener)) {
            observedContainers.put(registry, Boolean.TRUE);
            return true;
        }
        return false;
    }

    private void clearObservedContainers() {
        removeObservedContainers(observedOutputContainers, outputContentsListener);
        removeObservedContainers(observedInputContainers, inputContentsListener);
        outputPollingRequired = false;
    }

    private static void removeObservedContainers(Map<IContentsListenerRegistry, Boolean> containers,
          IContentsListener listener) {
        for (IContentsListenerRegistry container : containers.keySet()) {
            container.removeContentsListener(listener);
        }
        containers.clear();
    }

    private void queuePatternChange() {
        pendingPatternChangeTicks = isCraftingMode(activeMode) ? PATTERN_CHANGE_RETRY_TICKS : 0;
    }

    private void flushPendingPatternChange(boolean networkUsable) {
        if (pendingPatternChangeTicks <= 0 || !networkUsable || !isCraftingMode(activeMode)) {
            return;
        }
        try {
            if (activeMode == ExposureMode.WIRED_CRAFTING && managedNode != null) {
                ICraftingProvider.requestUpdate(managedNode);
                pendingPatternChangeTicks = 0;
            } else if (activeMode == ExposureMode.WIRELESS_CRAFTING
                  && registeredWirelessCraftingService != null) {
                registeredWirelessCraftingService.refreshGlobalCraftingProvider(host);
                pendingPatternChangeTicks = 0;
            }
        } catch (RuntimeException | LinkageError ignored) {
            pendingPatternChangeTicks--;
        }
    }

    private ExposureMode resolveExposureMode() {
        if (!isHostAvailable()) {
            return ExposureMode.NONE;
        }
        if (host.supportsAEWiredCraftingUpgrade() && host.hasAEWiredCraftingUpgrade()) {
            return ExposureMode.WIRED_CRAFTING;
        }
        if (host.supportsAEWirelessCraftingUpgrade() && host.hasAEWirelessCraftingUpgrade()) {
            return ExposureMode.WIRELESS_CRAFTING;
        }
        if (host.supportsAEWiredAutoProcessingUpgrade() && host.hasAEWiredAutoProcessingUpgrade()) {
            return ExposureMode.WIRED_AUTO_PROCESSING;
        }
        if (host.supportsAEWirelessAutoProcessingUpgrade() && host.hasAEWirelessAutoProcessingUpgrade()) {
            return ExposureMode.WIRELESS_AUTO_PROCESSING;
        }
        if (host.supportsAEWiredOutputUpgrade() && host.hasAEWiredOutputUpgrade()) {
            return ExposureMode.WIRED_OUTPUT;
        }
        if (host.supportsAEWirelessOutputUpgrade() && host.hasAEWirelessOutputUpgrade()) {
            return ExposureMode.WIRELESS_OUTPUT;
        }
        return ExposureMode.NONE;
    }

    private boolean isHostAvailable() {
        return host instanceof TileEntity tile && tile.getWorld() != null && !tile.getWorld().isRemote
              && !tile.isInvalid();
    }

    private static boolean usesWiredNetwork(ExposureMode mode) {
        return mode == ExposureMode.WIRED_CRAFTING || mode == ExposureMode.WIRED_AUTO_PROCESSING
              || mode == ExposureMode.WIRED_OUTPUT;
    }

    private static boolean usesWirelessNetwork(ExposureMode mode) {
        return mode == ExposureMode.WIRELESS_CRAFTING || mode == ExposureMode.WIRELESS_AUTO_PROCESSING
              || mode == ExposureMode.WIRELESS_OUTPUT;
    }

    private static boolean isCraftingMode(ExposureMode mode) {
        return mode == ExposureMode.WIRED_CRAFTING || mode == ExposureMode.WIRELESS_CRAFTING;
    }

    private static boolean isAutoProcessingMode(ExposureMode mode) {
        return mode == ExposureMode.WIRED_AUTO_PROCESSING || mode == ExposureMode.WIRELESS_AUTO_PROCESSING;
    }

    private static boolean isOutputMode(ExposureMode mode) {
        return mode == ExposureMode.WIRED_OUTPUT || mode == ExposureMode.WIRELESS_OUTPUT;
    }

    @Nullable
    private static List<ItemStack> toItemInputs(KeyCounter[] inputHolder, AEExposedRecipe recipe) {
        if (inputHolder == null || recipe == null || inputHolder.length != recipe.getInputs().length) {
            return null;
        }
        Map<AEKey, Long> supplied = new LinkedHashMap<>();
        for (KeyCounter counter : inputHolder) {
            if (counter == null) {
                return null;
            }
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                long current = supplied.getOrDefault(key, 0L);
                if (current > Long.MAX_VALUE - amount) {
                    return null;
                }
                supplied.put(key, current + amount);
            }
        }

        List<ItemStack> expectedInputs = recipe.getInputStacks();
        List<ItemStack> inputs = new ArrayList<>(expectedInputs.size());
        for (ItemStack expectedInput : expectedInputs) {
            GenericStack expected = GenericStack.fromItemStack(expectedInput);
            if (expected == null || expected.amount() <= 0) {
                return null;
            }
            AEKey key = expected.what();
            long available = supplied.getOrDefault(key, 0L);
            if (available < expected.amount()) {
                return null;
            }
            ItemStack input = key instanceof AEItemKey itemKey
                  ? expected.amount() <= Integer.MAX_VALUE ? itemKey.toStack((int) expected.amount()) : ItemStack.EMPTY
                  : GenericStack.wrapInItemStack(key, expected.amount());
            if (input.isEmpty()) {
                return null;
            }
            inputs.add(input);
            long remaining = available - expected.amount();
            if (remaining == 0) {
                supplied.remove(key);
            } else {
                supplied.put(key, remaining);
            }
        }
        return supplied.isEmpty() ? inputs : null;
    }

    private void notifyConnectionSide(@Nullable EnumFacing side) {
        if (side == null || !(host instanceof TileEntity tile) || tile.getWorld() == null
              || tile.getWorld().isRemote) {
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

    @Nullable
    private EnumFacing readConnectionSide(NBTTagCompound nbtTags) {
        if (!nbtTags.hasKey(CONNECTION_SIDE_TAG)) {
            return null;
        }
        int side = nbtTags.getInteger(CONNECTION_SIDE_TAG);
        return side >= 0 && side < EnumFacing.VALUES.length ? EnumFacing.VALUES[side] : null;
    }

    @Nullable
    private static UUID readUuid(NBTTagCompound nbtTags, String key) {
        if (!nbtTags.hasKey(key)) {
            return null;
        }
        try {
            return UUID.fromString(nbtTags.getString(key));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void writeUuid(NBTTagCompound nbtTags, String key, @Nullable UUID value) {
        if (value == null) {
            nbtTags.removeTag(key);
        } else {
            nbtTags.setString(key, value.toString());
        }
    }

    private static void writeWirelessLink(NBTTagCompound nbtTags, String key, @Nullable AEWirelessLink link) {
        if (link == null) {
            nbtTags.removeTag(key);
        } else {
            link.write(nbtTags, key);
        }
    }

    private static int clampGlobalProfileSlot(int slot) {
        return Math.max(MIN_GLOBAL_PROFILE_SLOT, Math.min(MAX_GLOBAL_PROFILE_SLOT, slot));
    }
}
