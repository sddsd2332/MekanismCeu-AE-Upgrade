package mekceuaeupgrade.common.host;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.ILocatable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
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
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import mekceuaeupgrade.common.config.AERecipeConfigType;
import mekceuaeupgrade.common.config.AERecipeProfile;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.adapter.AEProviderBackedRecipeAdapter;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.integration.appeng.IAEIncrementalCraftingGridCache;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import mekceuaeupgrade.common.transfer.AEPendingTransferBuffer;
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
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final String PENDING_TRANSFERS_TAG = "aePendingTransfers";
    private static final int AUTO_PROCESSING_INTERVAL_TICKS = 5;
    private static final int AUTO_PROCESSING_RETRY_INITIAL_TICKS = 20;
    private static final int AUTO_PROCESSING_RETRY_MAX_TICKS = 100;
    private static final int WIRED_CONNECTION_RETRY_INTERVAL_TICKS = 5;
    private static final int WIRED_CONNECTION_REFRESH_INTERVAL_TICKS = 20;
    private static final int WIRELESS_TARGET_RETRY_INTERVAL_TICKS = 5;
    private static final int WIRELESS_TARGET_REFRESH_INTERVAL_TICKS = 20;
    private static final int RECIPE_SOURCE_REFRESH_INTERVAL_TICKS = 20;
    private static final int NETWORK_TOPOLOGY_RETRY_INTERVAL_TICKS = 5;
    private static final int NETWORK_TOPOLOGY_REFRESH_INTERVAL_TICKS = 20;
    private static final int PENDING_TRANSFER_RETRY_INITIAL_TICKS = 5;
    private static final int PENDING_TRANSFER_RETRY_MAX_TICKS = 40;
    private static final int OUTPUT_DRAIN_RETRY_INITIAL_TICKS = 5;
    private static final int OUTPUT_DRAIN_MAX_INTERVAL_TICKS = 40;
    private static final ClassValue<Boolean> CUSTOM_NETWORK_CHECK = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getDeclaredMethod("canUseNetwork").getDeclaringClass() != AEUpgradeNode.class;
            } catch (NoSuchMethodException ignored) {
                return type.getSuperclass() != null && get(type.getSuperclass());
            }
        }
    };
    public static final int MIN_GLOBAL_PROFILE_SLOT = 1;
    public static final int MAX_GLOBAL_PROFILE_SLOT = 10;

    private final IAEUpgradeHost host;
    private final AEUpgradeStateCache upgradeStateCache;
    private final boolean legacyExposureCheck;
    private final boolean customNetworkCheck;
    /**
     * Bound once so the hot path costs one virtual call instead of a type test plus an interface
     * dispatch. The host hierarchy spans many machine classes and interfaces, so
     * {@code host instanceof IAEItemRecipeHost} resolves megamorphically and dominated the cost of
     * every busy query. The bound target still reads live state, so the gate stays live.
     */
    private final BooleanSupplier admissionBlockedCheck;
    private final AEProviderBackedRecipeAdapter.ContextCache providerContextCache =
          new AEProviderBackedRecipeAdapter.ContextCache();
    private final Object machineAccessMonitor;
    private final AENetworkProxy proxy;
    private final AEUpgradeRecipeCache recipeCache;
    private final MachineSource source;
    private final AEPendingTransferBuffer pendingTransfers;
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
    private IGrid incrementalCraftingGrid;
    @Nullable
    private IAEIncrementalCraftingGridCache incrementalCraftingCache;
    @Nullable
    private IGridNode incrementalCraftingNode;
    @Nullable
    private Collection<? extends ICraftingPatternDetails> incrementalCraftingRecipes;
    private boolean incrementalCraftingRefreshRequired = true;
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
    private final IContentsListener outputContentsListener = this::requestOutputContainerChange;
    private final IContentsListener inputContentsListener = this::requestInputContainerChange;
    private final Map<IContentsListenerRegistry, Boolean> observedOutputContainers = new IdentityHashMap<>();
    private final Map<IContentsListenerRegistry, Boolean> observedInputContainers = new IdentityHashMap<>();
    private long lastBusyDebugTick = Long.MIN_VALUE;
    private final AtomicLong busyCacheGeneration = new AtomicLong();
    private volatile long busyCacheReadGeneration = Long.MIN_VALUE;
    private volatile boolean busyCacheValue;
    @Nullable
    private Object lastRecipeSourceKey;
    private int lastRecipeVersion = Integer.MIN_VALUE;
    private long nextRecipeSourceRefreshTick = Long.MIN_VALUE;
    private long nextPendingTransferRetryTick = Long.MIN_VALUE;
    private int pendingTransferRetryInterval = PENDING_TRANSFER_RETRY_INITIAL_TICKS;
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
    private int exposureModeMask = Integer.MIN_VALUE;
    @Nullable
    private static IItemStorageChannel itemStorageChannel;

    public AEUpgradeNode(IAEUpgradeHost host, Object machineAccessMonitor) {
        this(host, machineAccessMonitor, new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade));
    }

    AEUpgradeNode(IAEUpgradeHost host, Object machineAccessMonitor, ItemStack visualRepresentation) {
        this.host = host;
        upgradeStateCache = new AEUpgradeStateCache(host);
        legacyExposureCheck = upgradeStateCache.requiresLegacyExposureCheck();
        customNetworkCheck = CUSTOM_NETWORK_CHECK.get(getClass());
        admissionBlockedCheck = host instanceof IAEItemRecipeHost itemHost
              ? itemHost::isAEAdmissionBlocked
              : () -> false;
        this.machineAccessMonitor = Objects.requireNonNull(machineAccessMonitor);
        proxy = new AENetworkProxy(host, "aeUpgrade", visualRepresentation, true);
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(0.0);
        proxy.setValidSides(EnumSet.noneOf(EnumFacing.class));
        recipeCache = new AEUpgradeRecipeCache(host);
        source = new MachineSource(host);
        pendingTransfers = new AEPendingTransferBuffer(this::onPendingTransferChanged);
        autoProcessingTickOffset = Math.floorMod(System.identityHashCode(host), AUTO_PROCESSING_INTERVAL_TICKS);
    }

    public AENetworkProxy getProxy() {
        return proxy;
    }

    public IActionSource getActionSource() {
        return source;
    }

    public AEPendingTransferBuffer getPendingTransferBuffer() {
        return pendingTransfers;
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
        if (registeredWirelessCraftingProvider || AEWirelessCraftingProviderRegistry.isRegistered(this) ||
              incrementalCraftingCache != null) {
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
        // A delegate may be reused across a chunk/world reload. Do not carry a provider from the
        // previous serialized state into the new network while the upgrade flags are being read.
        unregisterWirelessCraftingProvider();
        invalidateExposureModeCache();
        proxy.readFromNBT(nbtTags);
        pendingTransfers.read(nbtTags.hasKey(PENDING_TRANSFERS_TAG, NBT.TAG_COMPOUND) ?
              nbtTags.getCompoundTag(PENDING_TRANSFERS_TAG) : null);
        if (pendingTransfers.hasQuarantinedData()) {
            MEKCeuAEUpgrade.logger.error("Invalid or unsupported AE pending transfer data for {}; " +
                  "the original data was preserved and this machine will remain blocked.", host.getClass().getName());
        }
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
        if (pendingTransfers.hasOwnedResources()) {
            nbtTags.setTag(PENDING_TRANSFERS_TAG, pendingTransfers.write());
        } else {
            nbtTags.removeTag(PENDING_TRANSFERS_TAG);
        }
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
        invalidateBusyCache("lifecycle");
        clearObservedContainers();
        providerContextCache.clear();
        AERecipeProfileManager.unregisterHost(host);
        unregisterWirelessCraftingProvider();
        unregisterIncrementalCraftingProvider();
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        ready = false;
        lastNetworkUsable = false;
        resetRecipeSourceTracking();
        pendingPatternChangeTicks = 0;
        nextConnectionRefreshTick = Long.MIN_VALUE;
        resetPendingTransferRetrySchedule();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        proxy.invalidate();
    }

    public void onChunkUnload() {
        invalidateBusyCache("chunk_unload");
        clearObservedContainers();
        providerContextCache.clear();
        AERecipeProfileManager.unregisterHost(host);
        unregisterWirelessCraftingProvider();
        unregisterIncrementalCraftingProvider();
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        ready = false;
        lastNetworkUsable = false;
        resetRecipeSourceTracking();
        pendingPatternChangeTicks = 0;
        nextConnectionRefreshTick = Long.MIN_VALUE;
        resetPendingTransferRetrySchedule();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        proxy.onChunkUnload();
    }

    public void tickServer() {
        ExposureMode mode = getExposureMode();
        if (!isCraftingMode(mode)) {
            unregisterIncrementalCraftingProvider();
        }
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
        if (networkUsable != lastNetworkUsable) {
            invalidateBusyCache("network");
        }
        if (!networkUsable) {
            unregisterIncrementalCraftingProvider();
        }
        boolean craftingGridChanged = networkUsable && incrementalCraftingCache != null &&
              incrementalCraftingGrid != getActiveGrid();
        if (craftingGridChanged) {
            unregisterIncrementalCraftingProvider();
        }
        boolean networkRestored = networkUsable && !lastNetworkUsable;
        if (networkRestored) {
            resetPendingTransferRetrySchedule();
            resetOutputDrainSchedule();
            resetAutoProcessingSchedule();
        }
        if (networkUsable && pendingTransfers.hasOwnedResources() && shouldRetryPendingTransfersThisTick()) {
            scheduleNextPendingTransferRetry(pendingTransfers.retryNetwork(this));
        }
        boolean pendingTransferBlocked = pendingTransfers.hasOwnedResources();
        if (networkUsable && (networkRestored || connectionChanged || craftingGridChanged || incrementalCraftingRefreshRequired)) {
            queuePatternChange();
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
        if (networkUsable && !pendingTransferBlocked && supportsOutputDrain() && shouldDrainOutputsThisTick()) {
            outputDrainRequested = false;
            drainOutputs();
            scheduleNextOutputDrain();
        }
        if (networkUsable && !pendingTransferBlocked && isAutoProcessingMode(mode) && host instanceof IAEItemRecipeHost itemHost &&
            shouldRunAutoProcessingThisTick()) {
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
        incrementalCraftingRefreshRequired = true;
        notifyConnectionSide(connectionSide);
        refreshNeighborGridNode(connectionSide);
        queuePatternChange();
        flushPendingPatternChange(canUseNetwork());
    }

    public void deactivate() {
        invalidateBusyCache("lifecycle");
        clearObservedContainers();
        AERecipeProfileManager.unregisterHost(host);
        unregisterWirelessCraftingProvider();
        unregisterIncrementalCraftingProvider();
        EnumFacing previousSide = connectionSide;
        ready = false;
        lastNetworkUsable = false;
        resetRecipeSourceTracking();
        providerContextCache.clear();
        connectionSide = null;
        pendingPatternChangeTicks = 0;
        nextConnectionRefreshTick = Long.MIN_VALUE;
        resetPendingTransferRetrySchedule();
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
        invalidateBusyCache("grid");
        invalidateNetworkCache();
        resetOutputDrainSchedule();
        resetAutoProcessingSchedule();
        incrementalCraftingRefreshRequired = true;
        queuePatternChange();
    }

    public void onUpgradeConfigurationChanged() {
        invalidateBusyCache("upgrade");
        clearObservedContainers();
        unregisterIncrementalCraftingProvider();
        invalidateExposureModeCache();
        invalidateWirelessTargetCache();
        invalidateNetworkCache();
        resetRecipeSourceTracking();
        resetPendingTransferRetrySchedule();
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
        // Preserve custom host gates; the normal host path uses the versioned installed-state cache.
        if (legacyExposureCheck) {
            if (!host.shouldExposeAECrafting()) return true;
        }
        if (!isCraftingMode(getExposureMode())) {
            debugBusy("busy: AE upgrade is not installed or host is not exposing AE");
            return true;
        }
        // Host gates that do not participate in invalidation must be read on every query, hit or
        // miss: the generation can be unchanged while a custom host flips its own admission state.
        if (admissionBlockedCheck.getAsBoolean()) return true;
        // Extension nodes may override the network probe and can have state which is not
        // represented by this node's invalidation callbacks. Keep that contract live.
        if (customNetworkCheck) {
            if (!canUseNetwork()) {
                return true;
            }
        }
        long generation = busyCacheGeneration.get();
        if (busyCacheReadGeneration == generation) {
            return busyCacheValue;
        }
        return recomputeBusy(generation);
    }

    private boolean recomputeBusy(long generation) {
        if (!customNetworkCheck && !canUseNetwork()) {
            if (AEUpgradeDebug.enabled()) {
                debugBusy("busy: network unavailable node={} active={} powered={} side={}",
                      proxy.getNode() != null, proxy.isActive(), proxy.isPowered(), connectionSide);
            }
            return true;
        }
        if (pendingTransfers.hasOwnedResources()) {
            debugBusy("busy: pending AE transfer buffer still owns resources");
            return true;
        }
        if (host instanceof IAEItemRecipeHost itemHost) {
            boolean busy = callMachineContainerTransaction(() -> {
                itemHost.observeAEInputContainers(this::observeInputContainer);
                itemHost.observeAEOutputContainers(this::observeOutputContainer);
                if (itemHost.canAttemptAEItemInput()) {
                    return false;
                }
                debugBusy("busy: machine admission is blocked");
                return true;
            });
            // Only cache when output capacity is observable. Legacy adapters without output
            // listeners must retain the live check so silent capacity changes remain visible.
            if (generation == busyCacheGeneration.get() && !observedOutputContainers.isEmpty()) {
                busyCacheValue = busy;
                busyCacheReadGeneration = generation;
            }
            return busy;
        }
        return false;
    }

    public void invalidateBusyCache() {
        invalidateBusyCache("unspecified");
    }

    public void invalidateBusyCache(String reason) {
        busyCacheGeneration.incrementAndGet();
        busyCacheReadGeneration = Long.MIN_VALUE;
    }

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        boolean exposesCrafting = isCraftingMode(getExposureMode());
        boolean networkUsable = canUseNetwork();
        if (!exposesCrafting || !networkUsable) {
            // AE can invoke this callback during a native cache rebuild while the node is
            // already disconnected. Drop any sidecar state instead of leaving an unreachable
            // provider visible through the query mixins.
            if (craftingTracker instanceof IAEIncrementalCraftingGridCache incrementalCache) {
                if (incrementalCraftingCache == incrementalCache) {
                    unregisterIncrementalCraftingProvider();
                }
            } else {
                unregisterIncrementalCraftingProvider();
            }
            AEUpgradeDebug.log(host, "skipping crafting provider expose={} networkUsable={}", exposesCrafting, networkUsable);
            return;
        }
        List<AEExposedRecipe> recipes = recipeCache.getRecipes();
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "providing {} AE processing recipes", recipes.size());
        }
        if (craftingTracker instanceof IAEIncrementalCraftingGridCache incrementalCache) {
            IGrid providerGrid = incrementalCache.mekceuaeupgrade$getGrid();
            IGrid activeGrid = getActiveGrid();
            if (providerGrid != activeGrid) {
                // A callback from an old cache can race a split/join. Never associate that cache
                // with the newly resolved grid; the next ready tick will publish to the new one.
                if (incrementalCraftingCache == incrementalCache && incrementalCraftingGrid == providerGrid) {
                    unregisterIncrementalCraftingProvider();
                }
                return;
            }
            publishIncrementalCraftingProvider(incrementalCache, providerGrid, recipes);
            return;
        }
        unregisterIncrementalCraftingProvider();
        for (AEExposedRecipe recipe : recipes) {
            craftingTracker.addCraftingOption(host, recipe);
        }
        incrementalCraftingRefreshRequired = false;
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
        if (pendingTransfers.hasOwnedResources()) {
            AEUpgradeDebug.log(host, "pushPattern rejected: pending AE transfer buffer still owns resources");
            return false;
        }
        if (!(host instanceof IAEItemRecipeHost itemHost)) {
            AEUpgradeDebug.log(host, "pushPattern rejected: host does not implement item recipe host");
            return false;
        }
        if (itemHost.isAEAdmissionBlocked()) {
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

    private boolean shouldRetryPendingTransfersThisTick() {
        long tick = getWorldTime();
        return tick == Long.MIN_VALUE || nextPendingTransferRetryTick == Long.MIN_VALUE || tick >= nextPendingTransferRetryTick;
    }

    private void scheduleNextPendingTransferRetry(boolean completed) {
        if (completed || !pendingTransfers.hasOwnedResources()) {
            resetPendingTransferRetrySchedule();
            return;
        }
        long tick = getWorldTime();
        nextPendingTransferRetryTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + pendingTransferRetryInterval;
        pendingTransferRetryInterval = Math.min(PENDING_TRANSFER_RETRY_MAX_TICKS, pendingTransferRetryInterval << 1);
    }

    private void resetPendingTransferRetrySchedule() {
        nextPendingTransferRetryTick = Long.MIN_VALUE;
        pendingTransferRetryInterval = PENDING_TRANSFER_RETRY_INITIAL_TICKS;
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
        return AEProviderBackedRecipeAdapter.drainOutputs(host, this, () -> {
            if (host instanceof IAEItemRecipeHost itemHost) {
                return itemHost.drainAEItemOutputs(this);
            }
            return host instanceof IAEOutputHost outputHost && outputHost.drainAEOutputs(this);
        });
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

    private void requestOutputContainerChange() {
        requestOutputDrain();
        invalidateBusyCache("output_contents");
    }

    /** 标记当前排空遇到仍有内容但 AE 暂时无法完整接收的端口。 */
    public void markOutputBlocked() {
        outputBlocked = true;
    }

    /** Returns whether another machine integration currently owns the physical output container. */
    public boolean isContainerExtractionGuarded(@Nullable Object container) {
        return host instanceof TileEntityContainerBlock containerHost &&
              containerHost.isContainerExtractionGuarded(container);
    }

    private void requestAutoProcessing() {
        autoProcessingRequested = true;
    }

    private void requestInputContainerChange() {
        requestAutoProcessing();
        invalidateBusyCache("input_contents");
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
        invalidateBusyCache("recipe");
        providerContextCache.clear();
        recipeCache.invalidate();
        nextRecipeSourceRefreshTick = Long.MIN_VALUE;
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

    public AEProviderBackedRecipeAdapter.ContextCache getProviderContextCache() {
        return providerContextCache;
    }

    private boolean refreshRecipeSourceKey() {
        ExposureMode mode = getExposureMode();
        int currentRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
        boolean recipeVersionChanged = lastRecipeVersion != currentRecipeVersion;
        boolean recipeSourceChanged = false;
        long tick = getWorldTime();
        if (tick == Long.MIN_VALUE || nextRecipeSourceRefreshTick == Long.MIN_VALUE || tick >= nextRecipeSourceRefreshTick) {
            Object currentRecipeSourceKey = (isCraftingMode(mode) || isAutoProcessingMode(mode)) && host instanceof IAEItemRecipeHost itemHost ?
                  itemHost.getAERecipeSourceKey() : null;
            recipeSourceChanged = !Objects.equals(lastRecipeSourceKey, currentRecipeSourceKey);
            lastRecipeSourceKey = currentRecipeSourceKey;
            nextRecipeSourceRefreshTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick + RECIPE_SOURCE_REFRESH_INTERVAL_TICKS;
        }
        if (!recipeVersionChanged && !recipeSourceChanged) {
            return false;
        }
        invalidateBusyCache("recipe");
        lastRecipeVersion = currentRecipeVersion;
        providerContextCache.clear();
        recipeCache.invalidate();
        return true;
    }

    private void resetRecipeSourceTracking() {
        lastRecipeSourceKey = null;
        lastRecipeVersion = Integer.MIN_VALUE;
        nextRecipeSourceRefreshTick = Long.MIN_VALUE;
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
        int modes = upgradeStateCache.supportedModes();
        if (exposureModeMask == modes) return exposureMode;
        ExposureMode resolved = resolveExposureMode(modes);
        if (resolved != exposureMode) invalidateBusyCache("exposure");
        exposureMode = resolved;
        exposureModeMask = modes;
        return exposureMode;
    }

    private ExposureMode resolveExposureMode(int modes) {
        if ((modes & AEUpgradeStateCache.WIRED_CRAFTING) != 0) {
            return ExposureMode.WIRED_CRAFTING;
        }
        if ((modes & AEUpgradeStateCache.WIRELESS_CRAFTING) != 0) {
            return ExposureMode.WIRELESS_CRAFTING;
        }
        if ((modes & AEUpgradeStateCache.WIRED_AUTO_PROCESSING) != 0) {
            return ExposureMode.WIRED_AUTO_PROCESSING;
        }
        if ((modes & AEUpgradeStateCache.WIRELESS_AUTO_PROCESSING) != 0) {
            return ExposureMode.WIRELESS_AUTO_PROCESSING;
        }
        if ((modes & AEUpgradeStateCache.WIRED_OUTPUT) != 0) {
            return ExposureMode.WIRED_OUTPUT;
        }
        if ((modes & AEUpgradeStateCache.WIRELESS_OUTPUT) != 0) {
            return ExposureMode.WIRELESS_OUTPUT;
        }
        return ExposureMode.NONE;
    }

    private boolean isHostAvailable() {
        return host instanceof TileEntity tile && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid();
    }

    private void invalidateExposureModeCache() {
        exposureMode = ExposureMode.NONE;
        exposureModeMask = Integer.MIN_VALUE;
        upgradeStateCache.invalidate();
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
        if (resolvedWirelessNode != null && !isNodeActive(resolvedWirelessNode) && tick != Long.MIN_VALUE) {
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
            nextWirelessTargetRefreshTick = tick + (resolvedWirelessNode == null || !isNodeActive(resolvedWirelessNode) ?
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
                // The registry can outlive the node's boolean after a split or an exception.
                // Always tear down the old registration/cache before adopting the new grid.
                unregisterWirelessCraftingProvider();
                lastWirelessCraftingGrid = grid;
                lastWirelessCraftingNode = node;
                queuePatternChange();
            } else {
                lastWirelessCraftingNode = node;
            }
            registeredWirelessCraftingProvider = AEWirelessCraftingProviderRegistry.isRegistered(this, grid);
            if (!registeredWirelessCraftingProvider) {
                AEWirelessCraftingProviderRegistry.register(this, grid);
                registeredWirelessCraftingProvider = true;
            }
        } else {
            unregisterWirelessCraftingProvider();
        }
    }

    private void unregisterWirelessCraftingProvider() {
        IGrid previousGrid = lastWirelessCraftingGrid;
        IGridNode previousNode = lastWirelessCraftingNode;
        boolean wasRegistered = registeredWirelessCraftingProvider || AEWirelessCraftingProviderRegistry.isRegistered(this);
        AEWirelessCraftingProviderRegistry.unregister(this);
        registeredWirelessCraftingProvider = false;
        boolean sidecarRemoved = unregisterIncrementalCraftingProvider();
        if (!sidecarRemoved && wasRegistered) {
            postPatternChange(previousGrid, previousNode);
        }
        lastWirelessCraftingGrid = null;
        lastWirelessCraftingNode = null;
    }

    void onWirelessCraftingProviderEvicted(IGrid grid) {
        if (grid == null) {
            return;
        }
        boolean sidecarBelongsToGrid = incrementalCraftingGrid == grid;
        boolean registrationBelongsToGrid = lastWirelessCraftingGrid == grid;
        if (!sidecarBelongsToGrid && !registrationBelongsToGrid) {
            return;
        }
        boolean sidecarRemoved = sidecarBelongsToGrid && unregisterIncrementalCraftingProvider();
        if (registrationBelongsToGrid) {
            registeredWirelessCraftingProvider = false;
            if (!sidecarRemoved) {
                postPatternChange(grid, lastWirelessCraftingNode);
            }
            lastWirelessCraftingGrid = null;
            lastWirelessCraftingNode = null;
        }
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
            cachedNetworkUsable = isNodeActive(cachedActionableNode);
            if (shouldRefreshNetworkTopology(cachedActionableNode, tick)) {
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
        return node != cachedTopologyNode || tick == Long.MIN_VALUE ||
              nextNetworkTopologyRefreshTick == Long.MIN_VALUE || tick >= nextNetworkTopologyRefreshTick;
    }

    private void refreshWirelessNetworkTopology(@Nullable IGridNode node, long tick) {
        IGrid grid = getGridFromNode(node);
        cachedTopologyNode = node;
        try {
            cachedActiveGrid = grid;
            updateCachedStorage(grid == null ? null : grid.getCache(IStorageGrid.class));
        } catch (RuntimeException | LinkageError ignored) {
            cachedActiveGrid = null;
            updateCachedStorage(null);
            cachedNetworkUsable = false;
        }
        scheduleNetworkTopologyRefresh(tick, cachedActiveGrid != null && cachedStorage != null);
    }

    private void refreshWiredNetworkTopology(@Nullable IGridNode node, long tick) {
        cachedTopologyNode = node;
        try {
            cachedActiveGrid = proxy.getGrid();
            updateCachedStorage(proxy.getStorage());
        } catch (GridAccessException | RuntimeException | LinkageError ignored) {
            cachedActiveGrid = null;
            updateCachedStorage(null);
            cachedNetworkUsable = false;
        }
        scheduleNetworkTopologyRefresh(tick, cachedActiveGrid != null && cachedStorage != null);
    }

    private void scheduleNetworkTopologyRefresh(long tick, boolean available) {
        nextNetworkTopologyRefreshTick = tick == Long.MIN_VALUE ? Long.MIN_VALUE : tick +
              (available ? NETWORK_TOPOLOGY_REFRESH_INTERVAL_TICKS : NETWORK_TOPOLOGY_RETRY_INTERVAL_TICKS);
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
        IGrid grid = getActiveGrid();
        IAEIncrementalCraftingGridCache incrementalCache = getIncrementalCraftingCache(grid);
        if (incrementalCache != null) {
            IGrid cacheGrid = incrementalCache.mekceuaeupgrade$getGrid();
            if (cacheGrid != grid) {
                return;
            }
            publishIncrementalCraftingProvider(incrementalCache, cacheGrid, recipeCache.getRecipes());
            return;
        }
        if (postPatternChange()) {
            pendingPatternChangeTicks = 0;
        }
    }

    private void publishIncrementalCraftingProvider(IAEIncrementalCraftingGridCache cache, @Nullable IGrid grid,
          Collection<? extends ICraftingPatternDetails> recipes) {
        if (incrementalCraftingCache != null && (incrementalCraftingCache != cache || incrementalCraftingGrid != grid)) {
            unregisterIncrementalCraftingProvider();
        }
        // The AE cache may have been rebuilt in place and the sidecar may have been cleared by
        // removeNode. Always reconcile the provider when AE asks for its provider list; object
        // identity alone cannot prove that the sidecar still contains this provider.
        cache.mekceuaeupgrade$setProviderRecipes(host, host, recipes);
        incrementalCraftingGrid = grid;
        incrementalCraftingCache = cache;
        incrementalCraftingNode = getActionableNode();
        incrementalCraftingRecipes = recipes;
        incrementalCraftingRefreshRequired = false;
        pendingPatternChangeTicks = 0;
    }

    @Nullable
    private IAEIncrementalCraftingGridCache getIncrementalCraftingCache(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        try {
            ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
            return craftingGrid instanceof IAEIncrementalCraftingGridCache ?
                  (IAEIncrementalCraftingGridCache) craftingGrid : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private boolean unregisterIncrementalCraftingProvider() {
        IAEIncrementalCraftingGridCache cache = incrementalCraftingCache;
        IGrid grid = incrementalCraftingGrid;
        IGridNode node = incrementalCraftingNode;
        boolean registered = cache != null;
        incrementalCraftingGrid = null;
        incrementalCraftingCache = null;
        incrementalCraftingNode = null;
        incrementalCraftingRecipes = null;
        incrementalCraftingRefreshRequired = true;
        if (cache != null) {
            cache.mekceuaeupgrade$removeProvider(host);
            postPatternChange(grid, node);
        }
        return registered;
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
        if (grid == null) {
            return false;
        }
        if (node == null) {
            try {
                node = grid.getPivot();
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
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
        if (!isNodeActive(node)) {
            return null;
        }
        try {
            return node.getGrid();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isNodeActive(@Nullable IGridNode node) {
        if (node == null) {
            return false;
        }
        try {
            return node.isActive();
        } catch (RuntimeException | LinkageError ignored) {
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

    private void onPendingTransferChanged() {
        markHostDirty();
        invalidateBusyCache("pending_transfer");
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
