package mekceuaeupgrade.common.host;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.ILocatable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.IContainerTransaction;
import mekanism.api.gas.GasStack;
import mekanism.common.util.MekanismUtils;
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
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
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
    private static final String LEGACY_PROFILE_INDIVIDUAL_INITIALIZED_TAG = "aeRecipeProfileIndividualInitialized";
    private static final String PROFILE_INSTANCE_TAG = "aeRecipeProfileInstance";
    private static final String PROFILE_GLOBAL_SLOT_TAG = "aeRecipeProfileGlobalSlot";
    private static final String PROFILE_FILTER_MODE_TAG = "aeRecipeProfileFilterMode";
    private static final String AUTO_PROFILE_INDIVIDUAL_TAG = "aeAutoProcessingRecipeProfileIndividual";
    private static final String AUTO_PROFILE_INDIVIDUAL_INITIALIZED_TAG = "aeAutoProcessingRecipeProfileIndividualInitialized";
    private static final String AUTO_PROFILE_INSTANCE_TAG = "aeAutoProcessingRecipeProfileInstance";
    private static final String AUTO_PROFILE_GLOBAL_SLOT_TAG = "aeAutoProcessingRecipeProfileGlobalSlot";
    private static final String CONNECTION_SIDE_TAG = "aeConnectionSide";
    private static final String WIRELESS_CRAFTING_KEY_TAG = "aeWirelessCraftingKey";
    private static final String WIRELESS_AUTO_PROCESSING_KEY_TAG = "aeWirelessAutoProcessingKey";
    private static final String WIRELESS_OUTPUT_KEY_TAG = "aeWirelessOutputKey";
    private static final int AUTO_PROCESSING_INTERVAL_TICKS = 5;
    private static final int AUTO_PROCESSING_RETRY_INITIAL_TICKS = 20;
    private static final int AUTO_PROCESSING_RETRY_MAX_TICKS = 100;
    private static final int WIRED_CONNECTION_RETRY_INTERVAL_TICKS = 5;
    private static final int WIRED_CONNECTION_REFRESH_INTERVAL_TICKS = 20;
    private static final int WIRELESS_TARGET_RETRY_INTERVAL_TICKS = 5;
    private static final int WIRELESS_TARGET_REFRESH_INTERVAL_TICKS = 20;
    private static final int NETWORK_TOPOLOGY_REFRESH_INTERVAL_TICKS = 20;
    private static final int OUTPUT_DRAIN_RETRY_INITIAL_TICKS = 5;
    private static final int OUTPUT_DRAIN_MAX_INTERVAL_TICKS = 40;
    public static final int MIN_GLOBAL_PROFILE_SLOT = 1;
    public static final int MAX_GLOBAL_PROFILE_SLOT = 10;

    private final IAEUpgradeHost host;
    private final Object machineAccessMonitor;
    private final AENetworkProxy proxy;
    private final AEUpgradeRecipeCache recipeCache;
    private final MachineSource source;
    @Nullable
    private EnumFacing connectionSide;
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
    private boolean ready;
    private boolean lastNetworkUsable;
    private boolean registeredWirelessCraftingProvider;
    @Nullable
    private IGrid lastWirelessCraftingGrid;
    @Nullable
    private IGridNode lastWirelessCraftingNode;
    private int pendingPatternChangeTicks;
    private int autoProcessingTickOffset;
    private long nextConnectionRefreshTick = Long.MIN_VALUE;
    private long nextOutputDrainTick = Long.MIN_VALUE;
    private int outputDrainInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
    private volatile boolean outputDrainRequested = true;
    private boolean outputDrainRetryPending;
    private boolean outputBlocked;
    private boolean outputPollingRequired;
    private long nextAutoProcessingTick = Long.MIN_VALUE;
    private int autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
    private volatile boolean autoProcessingRequested = true;
    private final IContentsListener outputContentsListener = this::requestOutputDrain;
    private final IContentsListener inputContentsListener = this::requestAutoProcessing;
    private final Map<IContentsListenerRegistry, Boolean> observedOutputContainers = new IdentityHashMap<>();
    private final Map<IContentsListenerRegistry, Boolean> observedInputContainers = new IdentityHashMap<>();
    private long lastBusyDebugTick = Long.MIN_VALUE;
    @Nullable
    private Object lastRecipeSourceKey;
    @Nullable
    private String wirelessCraftingKey;
    @Nullable
    private String wirelessAutoProcessingKey;
    @Nullable
    private String wirelessOutputKey;
    @Nullable
    private Long wirelessCraftingSerial;
    @Nullable
    private Long wirelessAutoProcessingSerial;
    @Nullable
    private Long wirelessOutputSerial;
    private boolean networkCacheValid;
    private long networkCacheTick = Long.MIN_VALUE;
    private boolean cachedNetworkUsable;
    @Nullable
    private IGridNode cachedActionableNode;
    @Nullable
    private IGrid cachedActiveGrid;
    @Nullable
    private IStorageGrid cachedStorage;
    @Nullable
    private IGridNode cachedTopologyNode;
    private long nextNetworkTopologyRefreshTick = Long.MIN_VALUE;
    private final Map<IStorageChannel<?>, IMEInventory<?>> cachedInventories = new IdentityHashMap<>();
    @Nullable
    private Long resolvedWirelessSerial;
    @Nullable
    private IGridNode resolvedWirelessNode;
    private long nextWirelessTargetRefreshTick = Long.MIN_VALUE;
    private ExposureMode exposureMode = ExposureMode.NONE;
    private boolean exposureModeValid;
    @Nullable
    private static IItemStorageChannel itemStorageChannel;

    public AEUpgradeNode(IAEUpgradeHost host, Object machineAccessMonitor) {
        this.host = host;
        this.machineAccessMonitor = Objects.requireNonNull(machineAccessMonitor);
        proxy = new AENetworkProxy(host, "aeUpgrade", new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade), true);
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(0.0);
        proxy.setValidSides(EnumSet.noneOf(EnumFacing.class));
        recipeCache = new AEUpgradeRecipeCache(host);
        source = new MachineSource(host);
        autoProcessingTickOffset = Math.floorMod(System.identityHashCode(host), AUTO_PROCESSING_INTERVAL_TICKS);
    }

    public AENetworkProxy getProxy() {
        return proxy;
    }

    public IActionSource getActionSource() {
        return source;
    }

    @Nullable
    public IGridNode getActionableNode() {
        refreshNetworkCache();
        return cachedActionableNode;
    }

    @Nullable
    public String getWirelessCraftingKey() {
        return wirelessCraftingKey;
    }

    public void setWirelessCraftingKey(@Nullable String key) {
        String cleaned = cleanWirelessKey(key);
        if (Objects.equals(wirelessCraftingKey, cleaned)) {
            return;
        }
        if (registeredWirelessCraftingProvider) {
            unregisterWirelessCraftingProvider();
        }
        wirelessCraftingKey = cleaned;
        wirelessCraftingSerial = parseWirelessSerial(cleaned);
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        markHostDirty();
        if (getExposureMode() == ExposureMode.WIRELESS_CRAFTING) {
            updateWirelessCraftingProviderRegistration();
            queuePatternChange();
            flushPendingPatternChange(canUseNetwork());
        }
    }

    @Nullable
    public String getWirelessAutoProcessingKey() {
        return wirelessAutoProcessingKey;
    }

    public void setWirelessAutoProcessingKey(@Nullable String key) {
        String cleaned = cleanWirelessKey(key);
        if (Objects.equals(wirelessAutoProcessingKey, cleaned)) {
            return;
        }
        wirelessAutoProcessingKey = cleaned;
        wirelessAutoProcessingSerial = parseWirelessSerial(cleaned);
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        markHostDirty();
    }

    @Nullable
    public String getWirelessOutputKey() {
        return wirelessOutputKey;
    }

    public void setWirelessOutputKey(@Nullable String key) {
        String cleaned = cleanWirelessKey(key);
        if (Objects.equals(wirelessOutputKey, cleaned)) {
            return;
        }
        wirelessOutputKey = cleaned;
        wirelessOutputSerial = parseWirelessSerial(cleaned);
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        markHostDirty();
    }

    public void read(NBTTagCompound nbtTags) {
        invalidateExposureModeCache();
        proxy.readFromNBT(nbtTags);
        recipeProfileOwner = readUuid(nbtTags, PROFILE_OWNER_TAG);
        recipeProfileInstance = readUuid(nbtTags, PROFILE_INSTANCE_TAG);
        if (nbtTags.hasKey(PROFILE_GLOBAL_SLOT_TAG)) {
            recipeProfileGlobalSlot = clampGlobalProfileSlot(nbtTags.getInteger(PROFILE_GLOBAL_SLOT_TAG));
        } else {
            recipeProfileGlobalSlot = MIN_GLOBAL_PROFILE_SLOT;
        }
        recipeProfileIndividual = nbtTags.getBoolean(PROFILE_INDIVIDUAL_TAG);
        recipeProfileFilterMode = nbtTags.hasKey(PROFILE_FILTER_MODE_TAG) ?
              AERecipeProfile.RouteFilterMode.fromName(nbtTags.getString(PROFILE_FILTER_MODE_TAG)) : AERecipeProfile.RouteFilterMode.BLACKLIST;
        recipeProfileBlacklistIndividualInitialized = nbtTags.getBoolean(PROFILE_BLACKLIST_INDIVIDUAL_INITIALIZED_TAG);
        recipeProfileWhitelistIndividualInitialized = nbtTags.getBoolean(PROFILE_WHITELIST_INDIVIDUAL_INITIALIZED_TAG);
        if (recipeProfileIndividual && !nbtTags.hasKey(PROFILE_BLACKLIST_INDIVIDUAL_INITIALIZED_TAG) &&
            !nbtTags.hasKey(PROFILE_WHITELIST_INDIVIDUAL_INITIALIZED_TAG)) {
            // 旧配置文件不迁移，但保留机器已处于单机模式这一状态，避免新配置随后被空全局配置覆盖。
            recipeProfileBlacklistIndividualInitialized = recipeProfileFilterMode == AERecipeProfile.RouteFilterMode.BLACKLIST;
            recipeProfileWhitelistIndividualInitialized = recipeProfileFilterMode == AERecipeProfile.RouteFilterMode.WHITELIST;
        }
        autoProcessingRecipeProfileInstance = readUuid(nbtTags, AUTO_PROFILE_INSTANCE_TAG);
        if (nbtTags.hasKey(AUTO_PROFILE_GLOBAL_SLOT_TAG)) {
            autoProcessingRecipeProfileGlobalSlot = clampGlobalProfileSlot(nbtTags.getInteger(AUTO_PROFILE_GLOBAL_SLOT_TAG));
        } else {
            autoProcessingRecipeProfileGlobalSlot = MIN_GLOBAL_PROFILE_SLOT;
        }
        autoProcessingRecipeProfileIndividual = nbtTags.getBoolean(AUTO_PROFILE_INDIVIDUAL_TAG);
        autoProcessingRecipeProfileIndividualInitialized = nbtTags.getBoolean(AUTO_PROFILE_INDIVIDUAL_INITIALIZED_TAG);
        wirelessCraftingKey = cleanWirelessKey(nbtTags.getString(WIRELESS_CRAFTING_KEY_TAG));
        wirelessAutoProcessingKey = cleanWirelessKey(nbtTags.getString(WIRELESS_AUTO_PROCESSING_KEY_TAG));
        wirelessOutputKey = cleanWirelessKey(nbtTags.getString(WIRELESS_OUTPUT_KEY_TAG));
        wirelessCraftingSerial = parseWirelessSerial(wirelessCraftingKey);
        wirelessAutoProcessingSerial = parseWirelessSerial(wirelessAutoProcessingKey);
        wirelessOutputSerial = parseWirelessSerial(wirelessOutputKey);
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        connectionSide = readConnectionSide(nbtTags);
        updateProxyValidSides();
        queuePatternChange();
    }

    public void write(NBTTagCompound nbtTags) {
        proxy.writeToNBT(nbtTags);
        if (recipeProfileOwner != null) {
            nbtTags.setString(PROFILE_OWNER_TAG, recipeProfileOwner.toString());
        } else {
            nbtTags.removeTag(PROFILE_OWNER_TAG);
        }
        if (recipeProfileInstance != null) {
            nbtTags.setString(PROFILE_INSTANCE_TAG, recipeProfileInstance.toString());
        } else {
            nbtTags.removeTag(PROFILE_INSTANCE_TAG);
        }
        nbtTags.setInteger(PROFILE_GLOBAL_SLOT_TAG, recipeProfileGlobalSlot);
        nbtTags.setBoolean(PROFILE_INDIVIDUAL_TAG, recipeProfileIndividual);
        nbtTags.setString(PROFILE_FILTER_MODE_TAG, recipeProfileFilterMode.name());
        nbtTags.setBoolean(PROFILE_BLACKLIST_INDIVIDUAL_INITIALIZED_TAG, recipeProfileBlacklistIndividualInitialized);
        nbtTags.setBoolean(PROFILE_WHITELIST_INDIVIDUAL_INITIALIZED_TAG, recipeProfileWhitelistIndividualInitialized);
        nbtTags.removeTag(LEGACY_PROFILE_INDIVIDUAL_INITIALIZED_TAG);
        if (autoProcessingRecipeProfileInstance != null) {
            nbtTags.setString(AUTO_PROFILE_INSTANCE_TAG, autoProcessingRecipeProfileInstance.toString());
        } else {
            nbtTags.removeTag(AUTO_PROFILE_INSTANCE_TAG);
        }
        nbtTags.setInteger(AUTO_PROFILE_GLOBAL_SLOT_TAG, autoProcessingRecipeProfileGlobalSlot);
        nbtTags.setBoolean(AUTO_PROFILE_INDIVIDUAL_TAG, autoProcessingRecipeProfileIndividual);
        nbtTags.setBoolean(AUTO_PROFILE_INDIVIDUAL_INITIALIZED_TAG, autoProcessingRecipeProfileIndividualInitialized);
        if (wirelessCraftingKey == null) {
            nbtTags.removeTag(WIRELESS_CRAFTING_KEY_TAG);
        } else {
            nbtTags.setString(WIRELESS_CRAFTING_KEY_TAG, wirelessCraftingKey);
        }
        if (wirelessAutoProcessingKey == null) {
            nbtTags.removeTag(WIRELESS_AUTO_PROCESSING_KEY_TAG);
        } else {
            nbtTags.setString(WIRELESS_AUTO_PROCESSING_KEY_TAG, wirelessAutoProcessingKey);
        }
        if (wirelessOutputKey == null) {
            nbtTags.removeTag(WIRELESS_OUTPUT_KEY_TAG);
        } else {
            nbtTags.setString(WIRELESS_OUTPUT_KEY_TAG, wirelessOutputKey);
        }
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
        return type == AERecipeConfigType.AUTO_PROCESSING ? autoProcessingRecipeProfileIndividual : recipeProfileIndividual;
    }

    public boolean isRecipeProfileIndividualInitialized(AERecipeConfigType type) {
        return isRecipeProfileIndividualInitialized(type, getRecipeProfileFilterMode(type));
    }

    /**
     * 判断指定配置类型和过滤模式的单机配置是否已经从全局配置初始化。
     */
    public boolean isRecipeProfileIndividualInitialized(AERecipeConfigType type, AERecipeProfile.RouteFilterMode filterMode) {
        if (type == AERecipeConfigType.AUTO_PROCESSING) {
            return autoProcessingRecipeProfileIndividualInitialized;
        }
        return filterMode == AERecipeProfile.RouteFilterMode.WHITELIST ? recipeProfileWhitelistIndividualInitialized :
              recipeProfileBlacklistIndividualInitialized;
    }

    public int getRecipeProfileGlobalSlot(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING ? autoProcessingRecipeProfileGlobalSlot : recipeProfileGlobalSlot;
    }

    /**
     * 获取指定配置类型当前选择的路线过滤模式。自动处理固定使用白名单语义。
     */
    public AERecipeProfile.RouteFilterMode getRecipeProfileFilterMode(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING ? AERecipeProfile.RouteFilterMode.WHITELIST : recipeProfileFilterMode;
    }

    /**
     * 切换合成配置当前使用的黑名单或白名单配置集。
     */
    public boolean setRecipeProfileFilterMode(AERecipeConfigType type, AERecipeProfile.RouteFilterMode filterMode) {
        if (type != AERecipeConfigType.CRAFTING) {
            return false;
        }
        AERecipeProfile.RouteFilterMode normalized = filterMode == null ? AERecipeProfile.RouteFilterMode.BLACKLIST : filterMode;
        if (recipeProfileFilterMode == normalized) {
            return false;
        }
        recipeProfileFilterMode = normalized;
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
        invalidateRecipeCache();
        return true;
    }

    public boolean cycleRecipeProfileGlobalSlot(AERecipeConfigType type, int direction) {
        int currentSlot = getRecipeProfileGlobalSlot(type);
        int nextSlot;
        if (direction < 0) {
            nextSlot = currentSlot <= MIN_GLOBAL_PROFILE_SLOT ? MAX_GLOBAL_PROFILE_SLOT : currentSlot - 1;
        } else {
            nextSlot = currentSlot >= MAX_GLOBAL_PROFILE_SLOT ? MIN_GLOBAL_PROFILE_SLOT : currentSlot + 1;
        }
        return setRecipeProfileGlobalSlot(type, nextSlot);
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
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
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
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
        invalidateRecipeCache();
        return true;
    }

    public void markRecipeProfileIndividualInitialized(AERecipeConfigType type) {
        markRecipeProfileIndividualInitialized(type, getRecipeProfileFilterMode(type));
    }

    /**
     * 标记指定配置类型和过滤模式的单机配置已经完成初始化。
     */
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
        if (host instanceof TileEntity tile) {
            tile.markDirty();
        }
    }

    public UUID getOrCreateRecipeProfileInstance(AERecipeConfigType type) {
        if (type == AERecipeConfigType.AUTO_PROCESSING) {
            if (autoProcessingRecipeProfileInstance == null) {
                autoProcessingRecipeProfileInstance = UUID.randomUUID();
                if (host instanceof TileEntity tile) {
                    tile.markDirty();
                }
            }
            return autoProcessingRecipeProfileInstance;
        }
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
        invalidateExposureModeCache();
        ensureActive();
    }

    public void validate() {
        invalidateExposureModeCache();
        ensureActive();
    }

    public void invalidate() {
        clearObservedContainers();
        AERecipeProfileManager.unregisterHost(host);
        unregisterWirelessCraftingProvider();
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        pendingPatternChangeTicks = 0;
        nextConnectionRefreshTick = Long.MIN_VALUE;
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        proxy.invalidate();
    }

    public void onChunkUnload() {
        clearObservedContainers();
        AERecipeProfileManager.unregisterHost(host);
        unregisterWirelessCraftingProvider();
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        pendingPatternChangeTicks = 0;
        nextConnectionRefreshTick = Long.MIN_VALUE;
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        proxy.onChunkUnload();
    }

    public void tickServer() {
        ExposureMode mode = getExposureMode();
        if (mode == ExposureMode.NONE) {
            clearObservedContainers();
            if (ready) {
                deactivate();
            }
            return;
        }
        if (mode == ExposureMode.WIRELESS_CRAFTING || registeredWirelessCraftingProvider) {
            updateWirelessCraftingProviderRegistration();
        }
        boolean connectionChanged;
        if (!ready || proxy.getNode() == null) {
            activate();
            connectionChanged = true;
        } else {
            connectionChanged = refreshConnectionIfNeeded();
        }
        boolean networkUsable = canUseNetwork();
        boolean networkRestored = networkUsable && !lastNetworkUsable;
        if (networkUsable && (networkRestored || connectionChanged)) {
            queuePatternChange();
        }
        if (networkRestored) {
            resetOutputDrainSchedule();
            resetAutoProcessingSchedule();
        }
        if (networkUsable && (isCraftingMode(mode) || isAutoProcessingMode(mode)) && refreshRecipeSourceKey()) {
            if (isCraftingMode(mode)) {
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
        if (networkUsable && isAutoProcessingMode(mode) && host instanceof IAEItemRecipeHost itemHost && shouldRunAutoProcessingThisTick()) {
            autoProcessingRequested = false;
            itemHost.observeAEInputContainers(this::observeInputContainer);
            boolean processed = AEAutoProcessingController.process(this, itemHost, recipeCache.getAutoProcessingRecipes());
            scheduleNextAutoProcessing(processed);
            if (processed) {
                resetOutputDrainSchedule();
            }
        }
    }

    public void activate() {
        invalidateExposureModeCache();
        if (getExposureMode() == ExposureMode.NONE) {
            return;
        }
        refreshConnection();
        proxy.validate();
        proxy.onReady();
        invalidateNetworkCache();
        AERecipeProfileManager.registerHost(host);
        updateWirelessCraftingProviderRegistration();
        ready = true;
        lastNetworkUsable = false;
        notifyConnectionSide(connectionSide);
        refreshNeighborGridNode(connectionSide);
        queuePatternChange();
        flushPendingPatternChange(canUseNetwork());
    }

    public void deactivate() {
        clearObservedContainers();
        AERecipeProfileManager.unregisterHost(host);
        unregisterWirelessCraftingProvider();
        EnumFacing previousSide = connectionSide;
        ready = false;
        lastNetworkUsable = false;
        lastRecipeSourceKey = null;
        connectionSide = null;
        pendingPatternChangeTicks = 0;
        nextConnectionRefreshTick = Long.MIN_VALUE;
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        invalidateExposureModeCache();
        updateProxyValidSides();
        proxy.invalidate();
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        notifyConnectionSide(previousSide);
        refreshNeighborGridNode(previousSide);
        markHostDirty();
    }

    public void onNeighborChanged() {
        if (getExposureMode() != ExposureMode.NONE) {
            if (refreshConnectionNow()) {
                queuePatternChange();
            }
            if (isCraftingMode(getExposureMode())) {
                flushPendingPatternChange(canUseNetwork());
            }
        }
    }

    public void onGridChanged() {
        invalidateNetworkCache();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        queuePatternChange();
    }

    public void onUpgradeConfigurationChanged() {
        clearObservedContainers();
        invalidateExposureModeCache();
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
    }

    @Nullable
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        if (!usesWiredNetwork() || connectionSide == null) {
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
        return usesWiredNetwork() && side != null && side == connectionSide ? AECableType.COVERED : AECableType.NONE;
    }

    public boolean isBusy() {
        if (!isCraftingMode(getExposureMode())) {
            debugBusy("busy: AE upgrade is not installed or host is not exposing AE");
            return true;
        }
        if (!canUseNetwork()) {
            debugBusy("busy: network unavailable node={} active={} powered={} side={}",
                  proxy.getNode() != null, proxy.isActive(), proxy.isPowered(), connectionSide);
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

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        boolean exposesCrafting = isCraftingMode(getExposureMode());
        boolean networkUsable = canUseNetwork();
        if (!exposesCrafting || !networkUsable) {
            AEUpgradeDebug.log(host, "skipping crafting provider expose={} networkUsable={}", exposesCrafting, networkUsable);
            return;
        }
        List<AEExposedRecipe> recipes = recipeCache.getRecipes();
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "providing {} AE processing recipes", recipes.size());
        }
        for (AEExposedRecipe recipe : recipes) {
            craftingTracker.addCraftingOption(host, recipe);
        }
    }

    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (!isCraftingMode(getExposureMode())) {
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
        if (!callMachineContainerTransaction(() -> AEUpgradeInputInjector.push(itemHost, recipe, table))) {
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

    private boolean shouldRunAutoProcessingThisTick() {
        long time = getWorldTime();
        return autoProcessingRequested || time == Long.MIN_VALUE || nextAutoProcessingTick == Long.MIN_VALUE || time >= nextAutoProcessingTick;
    }

    private boolean supportsOutputDrain() {
        ExposureMode mode = getExposureMode();
        return host instanceof IAEItemRecipeHost && mode != ExposureMode.NONE ||
              !(host instanceof IAEItemRecipeHost) && host instanceof IAEOutputHost && isOutputMode(mode);
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
        return outputDrainRequested || outputDrainRetryPending &&
              (tick == Long.MIN_VALUE || nextOutputDrainTick == Long.MIN_VALUE || tick >= nextOutputDrainTick);
    }

    private void scheduleNextOutputDrain() {
        outputDrainRetryPending = outputBlocked || outputPollingRequired;
        if (!outputDrainRetryPending) {
            outputDrainInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
            nextOutputDrainTick = Long.MIN_VALUE;
            return;
        }
        long tick = getWorldTime();
        nextOutputDrainTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + outputDrainInterval;
        outputDrainInterval = Math.min(OUTPUT_DRAIN_MAX_INTERVAL_TICKS, outputDrainInterval << 1);
    }

    private void resetOutputDrainSchedule() {
        outputDrainRequested = true;
        outputDrainRetryPending = false;
        outputDrainInterval = OUTPUT_DRAIN_RETRY_INITIAL_TICKS;
        nextOutputDrainTick = Long.MIN_VALUE;
    }

    private void scheduleNextAutoProcessing(boolean processed) {
        long tick = getWorldTime();
        if (processed) {
            autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
            nextAutoProcessingTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + AUTO_PROCESSING_INTERVAL_TICKS;
        } else {
            nextAutoProcessingTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + autoProcessingRetryInterval + autoProcessingTickOffset;
            autoProcessingRetryInterval = Math.min(AUTO_PROCESSING_RETRY_MAX_TICKS, autoProcessingRetryInterval << 1);
        }
    }

    private void resetAutoProcessingSchedule() {
        autoProcessingRequested = true;
        autoProcessingRetryInterval = AUTO_PROCESSING_RETRY_INITIAL_TICKS;
        nextAutoProcessingTick = Long.MIN_VALUE;
    }

    private void requestOutputDrain() {
        outputDrainRequested = true;
        autoProcessingRequested = true;
    }

    /** 标记当前排空遇到仍有内容但 AE 暂时无法完整接收的端口。 */
    public void markOutputBlocked() {
        outputBlocked = true;
    }

    private void requestAutoProcessing() {
        autoProcessingRequested = true;
    }

    /**
     * 注册 adapter 实际使用的输出槽位或储罐。
     *
     * @param container 输出槽位或储罐
     */
    public void observeOutputContainer(Object container) {
        if (container == null) {
            return;
        }
        if (!observeContainer(container, outputContentsListener, observedOutputContainers)) {
            outputPollingRequired = true;
        }
    }

    /**
     * 注册自动处理实际写入的输入槽位或储罐。
     *
     * @param container 输入槽位或储罐
     */
    public void observeInputContainer(Object container) {
        observeContainer(container, inputContentsListener, observedInputContainers);
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

    private static void removeObservedContainers(Map<IContentsListenerRegistry, Boolean> containers, IContentsListener listener) {
        for (IContentsListenerRegistry container : containers.keySet()) {
            container.removeContentsListener(listener);
        }
        containers.clear();
    }

    public ItemStack injectItem(ItemStack stack, Actionable action) {
        if (stack.isEmpty() || !canUseNetwork()) {
            return stack;
        }
        try {
            IMEInventory<IAEItemStack> inventory = getInventory(getItemStorageChannel());
            IAEItemStack toInsert = AEItemStack.fromItemStack(stack);
            IAEItemStack remainder = inventory.injectItems(toInsert, action, source);
            return remainder == null ? ItemStack.EMPTY : remainder.createItemStack();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return stack;
        }
    }

    public ItemStack extractItem(ItemStack request, Actionable action) {
        if (request.isEmpty() || !canUseNetwork()) {
            return ItemStack.EMPTY;
        }
        try {
            IMEInventory<IAEItemStack> inventory = getInventory(getItemStorageChannel());
            IAEItemStack toExtract = AEItemStack.fromItemStack(request);
            IAEItemStack extracted = inventory.extractItems(toExtract, action, source);
            return extracted == null ? ItemStack.EMPTY : extracted.createItemStack();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    public boolean hasAvailableItem(ItemStack request) {
        return !request.isEmpty() && hasAvailableItem(AEItemStack.fromItemStack(request));
    }

    public boolean hasAvailableItem(@Nullable IAEItemStack request) {
        if (request == null || request.getStackSize() <= 0 || !canUseNetwork()) {
            return false;
        }
        try {
            IMEInventory<IAEItemStack> inventory = getInventory(getItemStorageChannel());
            if (inventory instanceof IMEMonitor<?> rawMonitor) {
                @SuppressWarnings("unchecked")
                IMEMonitor<IAEItemStack> monitor = (IMEMonitor<IAEItemStack>) rawMonitor;
                IAEItemStack available = monitor.getStorageList().findPrecise(request);
                return available != null && available.getStackSize() >= request.getStackSize();
            }
            IAEItemStack extracted = inventory.extractItems(request.copy(), Actionable.SIMULATE, source);
            return extracted != null && extracted.getStackSize() >= request.getStackSize();
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public GasStack injectGas(GasStack stack, Actionable action) {
        return AEUpgradeGasBridge.inject(this, stack, action);
    }

    public GasStack extractGas(GasStack request, Actionable action) {
        return AEUpgradeGasBridge.extract(this, request, action);
    }

    public boolean hasAvailableGas(GasStack request) {
        return AEUpgradeGasBridge.hasAvailable(this, request);
    }

    public boolean hasAvailableGas(@Nullable Object request) {
        return AEUpgradeGasBridge.hasAvailable(this, request);
    }

    public FluidStack injectFluid(FluidStack stack, Actionable action) {
        return AEUpgradeFluidBridge.inject(this, stack, action);
    }

    public FluidStack extractFluid(FluidStack request, Actionable action) {
        return AEUpgradeFluidBridge.extract(this, request, action);
    }

    public boolean hasAvailableFluid(FluidStack request) {
        return AEUpgradeFluidBridge.hasAvailable(this, request);
    }

    public boolean hasAvailableFluid(@Nullable appeng.api.storage.data.IAEFluidStack request) {
        return AEUpgradeFluidBridge.hasAvailable(this, request);
    }

    public IStorageGrid getStorage() throws GridAccessException {
        refreshNetworkCache();
        if (cachedStorage == null) {
            throw new GridAccessException();
        }
        return cachedStorage;
    }

    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> IMEInventory<T> getInventory(IStorageChannel<T> channel) throws GridAccessException {
        if (channel == null) {
            throw new GridAccessException();
        }
        refreshNetworkCache();
        if (cachedStorage == null) {
            throw new GridAccessException();
        }
        IMEInventory<?> inventory = cachedInventories.get(channel);
        if (inventory == null) {
            inventory = cachedStorage.getInventory(channel);
            cachedInventories.put(channel, inventory);
        }
        return (IMEInventory<T>) inventory;
    }

    private static IItemStorageChannel getItemStorageChannel() {
        IItemStorageChannel channel = itemStorageChannel;
        if (channel == null) {
            channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            itemStorageChannel = channel;
        }
        return channel;
    }

    public void invalidateRecipeCache() {
        recipeCache.invalidate();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        if (isCraftingMode(getExposureMode())) {
            queuePatternChange();
        }
    }

    /**
     * 机器模式切换导致输入、输出端口角色或实例变化时，丢弃旧监听并重新发现端口。
     */
    public void onRecipePortsChanged() {
        clearObservedContainers();
        invalidateRecipeCache();
    }

    private boolean refreshRecipeSourceKey() {
        ExposureMode mode = getExposureMode();
        Object currentRecipeSourceKey = (isCraftingMode(mode) || isAutoProcessingMode(mode)) && host instanceof IAEItemRecipeHost itemHost ?
              itemHost.getAERecipeSourceKey() : null;
        if (Objects.equals(lastRecipeSourceKey, currentRecipeSourceKey)) {
            return false;
        }
        lastRecipeSourceKey = currentRecipeSourceKey;
        recipeCache.invalidate();
        return true;
    }

    private boolean refreshConnection() {
        EnumFacing previousSide = connectionSide;
        EnumFacing side = usesWiredNetwork() ? chooseConnectionSide() : null;
        if (side != connectionSide) {
            connectionSide = side;
            updateProxyValidSides();
            notifyConnectionSide(previousSide);
            refreshNeighborGridNode(previousSide);
            notifyConnectionSide(side);
            refreshNeighborGridNode(side);
            invalidateNetworkCache();
            scheduleNextConnectionRefresh();
            markHostDirty();
            return true;
        }
        scheduleNextConnectionRefresh();
        return false;
    }

    private boolean refreshConnectionNow() {
        nextConnectionRefreshTick = Long.MIN_VALUE;
        return refreshConnection();
    }

    private boolean refreshConnectionIfNeeded() {
        if (!usesWiredNetwork()) {
            return refreshConnection();
        }
        long tick = getWorldTime();
        if (tick == Long.MIN_VALUE || nextConnectionRefreshTick == Long.MIN_VALUE || tick >= nextConnectionRefreshTick) {
            return refreshConnection();
        }
        return false;
    }

    private void scheduleNextConnectionRefresh() {
        long tick = getWorldTime();
        if (tick == Long.MIN_VALUE || !usesWiredNetwork()) {
            nextConnectionRefreshTick = Long.MIN_VALUE;
            return;
        }
        nextConnectionRefreshTick = tick + (connectionSide == null ? WIRED_CONNECTION_RETRY_INTERVAL_TICKS : WIRED_CONNECTION_REFRESH_INTERVAL_TICKS);
    }

    @Nullable
    private EnumFacing chooseConnectionSide() {
        if (!(host instanceof TileEntity tile) || tile.getWorld() == null) {
            return null;
        }
        boolean currentSideUnloaded = false;
        if (connectionSide != null) {
            BlockPos currentNeighborPos = tile.getPos().offset(connectionSide);
            if (!tile.getWorld().isBlockLoaded(currentNeighborPos, false)) {
                currentSideUnloaded = true;
            } else if (isConnectableGridHost(tile.getWorld().getTileEntity(currentNeighborPos), connectionSide)) {
                return connectionSide;
            }
        }
        for (EnumFacing side : EnumFacing.VALUES) {
            if (side == connectionSide) {
                continue;
            }
            BlockPos neighborPos = tile.getPos().offset(side);
            if (!tile.getWorld().isBlockLoaded(neighborPos, false)) {
                continue;
            }
            TileEntity neighbor = tile.getWorld().getTileEntity(neighborPos);
            if (isConnectableGridHost(neighbor, side)) {
                return side;
            }
        }
        return currentSideUnloaded ? connectionSide : null;
    }

    private static boolean isConnectableGridHost(@Nullable TileEntity neighbor, EnumFacing side) {
        return !(neighbor instanceof IAEUpgradeHost) && neighbor instanceof IGridHost gridHost &&
              gridHost.getCableConnectionType(AEPartLocation.fromFacing(side.getOpposite())).isValid();
    }

    private void ensureActive() {
        if (getExposureMode() == ExposureMode.NONE) {
            return;
        }
        if (!ready || proxy.getNode() == null) {
            activate();
        } else {
            refreshConnection();
            if (isCraftingMode(getExposureMode())) {
                queuePatternChange();
                flushPendingPatternChange(canUseNetwork());
            }
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

    private boolean usesWiredNetwork() {
        ExposureMode mode = getExposureMode();
        return mode == ExposureMode.WIRED_OUTPUT || mode == ExposureMode.WIRED_CRAFTING || mode == ExposureMode.WIRED_AUTO_PROCESSING;
    }

    private boolean usesWirelessNetwork() {
        ExposureMode mode = getExposureMode();
        return mode == ExposureMode.WIRELESS_OUTPUT || mode == ExposureMode.WIRELESS_CRAFTING || mode == ExposureMode.WIRELESS_AUTO_PROCESSING;
    }

    private ExposureMode getExposureMode() {
        if (!isHostAvailable()) {
            return ExposureMode.NONE;
        }
        if (exposureModeValid) {
            return exposureMode;
        }
        exposureMode = resolveExposureMode();
        exposureModeValid = true;
        return exposureMode;
    }

    private ExposureMode resolveExposureMode() {
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
        return host instanceof TileEntity tile && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid();
    }

    private void invalidateExposureModeCache() {
        exposureMode = ExposureMode.NONE;
        exposureModeValid = false;
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
    private Long getActiveWirelessSerial() {
        return switch (getExposureMode()) {
            case WIRELESS_CRAFTING -> wirelessCraftingSerial;
            case WIRELESS_AUTO_PROCESSING -> wirelessAutoProcessingSerial;
            case WIRELESS_OUTPUT -> wirelessOutputSerial;
            default -> null;
        };
    }

    @Nullable
    private IGridNode getWirelessActionableNode() {
        Long serial = getActiveWirelessSerial();
        if (serial == null) {
            invalidateWirelessTargetCache();
            return null;
        }
        if (!Objects.equals(resolvedWirelessSerial, serial)) {
            resolvedWirelessSerial = serial;
            resolvedWirelessNode = null;
            nextWirelessTargetRefreshTick = Long.MIN_VALUE;
        }
        long tick = getWorldTime();
        if (resolvedWirelessNode != null && !resolvedWirelessNode.isActive() && tick != Long.MIN_VALUE) {
            long retryTick = tick + WIRELESS_TARGET_RETRY_INTERVAL_TICKS;
            if (nextWirelessTargetRefreshTick == Long.MIN_VALUE || nextWirelessTargetRefreshTick > retryTick) {
                nextWirelessTargetRefreshTick = retryTick;
            }
        }
        if (tick != Long.MIN_VALUE && nextWirelessTargetRefreshTick != Long.MIN_VALUE && tick < nextWirelessTargetRefreshTick) {
            return resolvedWirelessNode;
        }
        resolvedWirelessNode = findWirelessActionableNode(serial);
        if (tick != Long.MIN_VALUE) {
            nextWirelessTargetRefreshTick = tick + (resolvedWirelessNode == null || !resolvedWirelessNode.isActive() ?
                  WIRELESS_TARGET_RETRY_INTERVAL_TICKS : WIRELESS_TARGET_REFRESH_INTERVAL_TICKS);
        }
        return resolvedWirelessNode;
    }

    @Nullable
    private IGridNode findWirelessActionableNode(long serial) {
        try {
            ILocatable locatable = AEApi.instance().registries().locatable().getLocatableBy(serial);
            if (locatable instanceof IActionHost actionHost) {
                return actionHost.getActionableNode();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return null;
    }

    private void invalidateWirelessTargetCache() {
        resolvedWirelessSerial = null;
        resolvedWirelessNode = null;
        nextWirelessTargetRefreshTick = Long.MIN_VALUE;
    }

    @Nullable
    private IGrid getActiveGrid() {
        refreshNetworkCache();
        return cachedActiveGrid;
    }

    public boolean isWirelessTargetGrid(IGrid grid) {
        refreshNetworkCache();
        return cachedActiveGrid != null && cachedActiveGrid == grid;
    }

    public boolean isWirelessCraftingProviderValid() {
        return getExposureMode() == ExposureMode.WIRELESS_CRAFTING && wirelessCraftingKey != null && host instanceof IAEItemRecipeHost && host instanceof TileEntity tile &&
              tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid();
    }

    private void updateWirelessCraftingProviderRegistration() {
        if (isWirelessCraftingProviderValid()) {
            refreshNetworkCache();
            IGridNode node = cachedActionableNode;
            IGrid grid = cachedActiveGrid;
            if (grid == null) {
                unregisterWirelessCraftingProvider();
                return;
            }
            if (grid != lastWirelessCraftingGrid) {
                if (registeredWirelessCraftingProvider) {
                    unregisterWirelessCraftingProvider();
                }
                lastWirelessCraftingGrid = grid;
                lastWirelessCraftingNode = node;
                queuePatternChange();
            } else {
                lastWirelessCraftingNode = node;
            }
            if (!registeredWirelessCraftingProvider) {
                AEWirelessCraftingProviderRegistry.register(this, grid);
                registeredWirelessCraftingProvider = true;
            }
        } else {
            unregisterWirelessCraftingProvider();
        }
    }

    private void unregisterWirelessCraftingProvider() {
        if (registeredWirelessCraftingProvider) {
            AEWirelessCraftingProviderRegistry.unregister(this);
            registeredWirelessCraftingProvider = false;
            postPatternChange(lastWirelessCraftingGrid, lastWirelessCraftingNode);
        }
        lastWirelessCraftingGrid = null;
        lastWirelessCraftingNode = null;
    }

    private void refreshNetworkCache() {
        long tick = getWorldTime();
        if (networkCacheValid && tick != Long.MIN_VALUE && networkCacheTick == tick) {
            return;
        }
        networkCacheValid = tick != Long.MIN_VALUE;
        networkCacheTick = tick;
        cachedActionableNode = null;
        cachedNetworkUsable = false;
        if (getExposureMode() == ExposureMode.NONE) {
            clearNetworkTopologyCache();
            return;
        }
        if (usesWirelessNetwork()) {
            cachedActionableNode = getWirelessActionableNode();
            cachedNetworkUsable = cachedActionableNode != null && cachedActionableNode.isActive();
            if (!cachedNetworkUsable || shouldRefreshNetworkTopology(cachedActionableNode, tick)) {
                refreshWirelessNetworkTopology(cachedActionableNode, tick);
            }
            cachedNetworkUsable &= cachedActiveGrid != null && cachedStorage != null;
            return;
        }
        cachedActionableNode = proxy.getNode();
        cachedNetworkUsable = cachedActionableNode != null && proxy.isActive() && proxy.isPowered();
        if (!cachedNetworkUsable) {
            clearNetworkTopologyCache();
            return;
        }
        if (shouldRefreshNetworkTopology(cachedActionableNode, tick)) {
            refreshWiredNetworkTopology(cachedActionableNode, tick);
        }
        cachedNetworkUsable &= cachedActiveGrid != null && cachedStorage != null;
    }

    private boolean shouldRefreshNetworkTopology(@Nullable IGridNode node, long tick) {
        return node == null || node != cachedTopologyNode || cachedActiveGrid == null || cachedStorage == null || tick == Long.MIN_VALUE ||
              nextNetworkTopologyRefreshTick == Long.MIN_VALUE || tick >= nextNetworkTopologyRefreshTick;
    }

    private void refreshWirelessNetworkTopology(@Nullable IGridNode node, long tick) {
        IGrid grid = getGridFromNode(node);
        cachedTopologyNode = node;
        cachedActiveGrid = grid;
        updateCachedStorage(grid == null ? null : grid.getCache(IStorageGrid.class));
        scheduleNetworkTopologyRefresh(tick);
    }

    private void refreshWiredNetworkTopology(@Nullable IGridNode node, long tick) {
        cachedTopologyNode = node;
        try {
            cachedActiveGrid = proxy.getGrid();
            updateCachedStorage(proxy.getStorage());
        } catch (GridAccessException ignored) {
            cachedActiveGrid = null;
            updateCachedStorage(null);
            cachedNetworkUsable = false;
        }
        scheduleNetworkTopologyRefresh(tick);
    }

    private void scheduleNetworkTopologyRefresh(long tick) {
        nextNetworkTopologyRefreshTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + NETWORK_TOPOLOGY_REFRESH_INTERVAL_TICKS;
    }

    private void clearNetworkTopologyCache() {
        cachedTopologyNode = null;
        cachedActiveGrid = null;
        nextNetworkTopologyRefreshTick = Long.MIN_VALUE;
        updateCachedStorage(null);
    }

    private void updateCachedStorage(@Nullable IStorageGrid storage) {
        if (cachedStorage != storage) {
            cachedStorage = storage;
            cachedInventories.clear();
        }
    }

    private void invalidateNetworkCache() {
        networkCacheValid = false;
        networkCacheTick = Long.MIN_VALUE;
        cachedNetworkUsable = false;
        cachedActionableNode = null;
        clearNetworkTopologyCache();
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

    @Nullable
    private static String cleanWirelessKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static Long parseWirelessSerial(@Nullable String key) {
        if (key == null) {
            return null;
        }
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void queuePatternChange() {
        pendingPatternChangeTicks = isCraftingMode(getExposureMode()) ? 1 : 0;
    }

    private void flushPendingPatternChange(boolean networkUsable) {
        if (pendingPatternChangeTicks <= 0 || !networkUsable || !isCraftingMode(getExposureMode())) {
            return;
        }
        if (postPatternChange()) {
            pendingPatternChangeTicks = 0;
        }
    }

    private boolean postPatternChange() {
        IGridNode node = getActionableNode();
        IGrid grid = getActiveGrid();
        if (getExposureMode() == ExposureMode.WIRELESS_CRAFTING && grid != null) {
            lastWirelessCraftingGrid = grid;
            lastWirelessCraftingNode = node;
        }
        return postPatternChange(grid, node);
    }

    private boolean postPatternChange(@Nullable IGrid grid, @Nullable IGridNode node) {
        if (grid == null || node == null) {
            return false;
        }
        try {
            grid.postEvent(new MENetworkCraftingPatternChange(host, node));
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    private IGrid getGridFromNode(@Nullable IGridNode node) {
        if (node == null || !node.isActive()) {
            return null;
        }
        try {
            return node.getGrid();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
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
