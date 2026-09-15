package mekceuaeupgrade.common.host;

import mekanism.common.base.IUpgradeTile;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityMachine;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
import mekanism.common.Upgrade;
import mekceuaeupgrade.common.item.AEUpgrade;
import java.util.function.BooleanSupplier;

/** Reuses type-based support by revision; arbitrary predicates and custom host queries stay live. */
final class AEUpgradeStateCache {
    private static final boolean VERSIONED_UPGRADES = hasVersionedUpgrades();
    private static final boolean SUPPORT_INVALIDATION = hasSupportInvalidation();
    static final int WIRED_CRAFTING = 1;
    static final int WIRELESS_CRAFTING = 2;
    static final int WIRED_AUTO_PROCESSING = 4;
    static final int WIRELESS_AUTO_PROCESSING = 8;
    static final int WIRED_OUTPUT = 16;
    static final int WIRELESS_OUTPUT = 32;

    private static final ClassValue<Boolean> DEFAULT_INSTALLED_QUERIES = defaultMethods(
          "hasAEWiredCraftingUpgrade", "hasAEWirelessCraftingUpgrade",
          "hasAEWiredAutoProcessingUpgrade", "hasAEWirelessAutoProcessingUpgrade",
          "hasAEWiredOutputUpgrade", "hasAEWirelessOutputUpgrade");
    private static final ClassValue<Boolean> DEFAULT_EXPOSURE = defaultMethods(
          "shouldExposeAE", "shouldExposeAECrafting", "supportsAEUpgrade", "hasAEUpgrade",
          "supportsAECraftingUpgrade", "hasAECraftingUpgrade");
    private static final ClassValue<Boolean> DEFAULT_SUPPORT = defaultMethods(
          "supportsAEWiredCraftingUpgrade", "supportsAEWirelessCraftingUpgrade",
          "supportsAEWiredAutoProcessingUpgrade", "supportsAEWirelessAutoProcessingUpgrade",
          "supportsAEWiredOutputUpgrade", "supportsAEWirelessOutputUpgrade");
    private static final ClassValue<Boolean> DEFAULT_TILE_SUPPORT = new ClassValue<Boolean>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("supportsUpgrade", Upgrade.class).getDeclaringClass() == IUpgradeTile.class &&
                      type.getMethod("supportsUpgrades").getDeclaringClass() == IUpgradeTile.class;
            } catch (ReflectiveOperationException | SecurityException unavailable) { return false; }
        }
    };
    private static final ClassValue<Boolean> DEFAULT_COMPONENT_SUPPORT = new ClassValue<Boolean>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try { return type.getMethod("supports", Upgrade.class).getDeclaringClass() == TileComponentUpgrade.class; }
            catch (ReflectiveOperationException | SecurityException unavailable) { return false; }
        }
    };
    private static final ClassValue<Boolean> DEFAULT_COMPONENT_REVISION = new ClassValue<Boolean>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getConfigurationVersion").getDeclaringClass() == TileComponentUpgrade.class &&
                      type.getMethod("trackSupportValidity", long.class, long.class).getDeclaringClass() == TileComponentUpgrade.class;
            } catch (ReflectiveOperationException | SecurityException unavailable) { return false; }
        }
    };

    private final IAEUpgradeHost host;
    private final TileEntityMachine machineHost;
    private final boolean cacheInstalled;
    private final boolean legacyExposureCheck;
    private final boolean cacheSupported;
    private volatile StateSnapshot installedCache;
    private volatile StateSnapshot supportCache;
    private long invalidationVersion;

    AEUpgradeStateCache(IAEUpgradeHost host) {
        this.host = host;
        machineHost = host instanceof TileEntityMachine ? (TileEntityMachine) host : null;
        cacheInstalled = VERSIONED_UPGRADES && host instanceof IUpgradeTile && DEFAULT_INSTALLED_QUERIES.get(host.getClass());
        legacyExposureCheck = !DEFAULT_EXPOSURE.get(host.getClass());
        cacheSupported = cacheInstalled && AEUpgrade.hasClassSupportApi() &&
              DEFAULT_SUPPORT.get(host.getClass()) && DEFAULT_TILE_SUPPORT.get(host.getClass());
    }

    boolean requiresLegacyExposureCheck() { return legacyExposureCheck; }

    int supportedModes() {
        TileComponentUpgrade current = cacheSupported ? currentComponent() : null;
        StateSnapshot cached = supportCache;
        // Identity stays live: the public component reference may be replaced without changing its revision.
        if (cached != null && cached.component == current && cached.validity != null && cached.validity.getAsBoolean()) {
            return cached.modes;
        }
        return refreshSupportedModes(current, cached);
    }

    // Keep the repeated query small; compatibility checks and registration only run on a miss.
    private int refreshSupportedModes(TileComponentUpgrade current, StateSnapshot cached) {
        long currentVersion = current == null ? 0 : current.getConfigurationVersion();
        long registryVersion = cacheSupported ? ExternalUpgradeSupportRegistry.getVersion() : 0;
        if (cached != null && cached.validity == null && cached.matches(current, currentVersion, registryVersion)) {
            return cached.modes;
        }
        long localVersion;
        synchronized (this) { localVersion = invalidationVersion; }
        int modes = installedModes();
        int supported = 0;
        if ((modes & WIRED_CRAFTING) != 0 && host.supportsAEWiredCraftingUpgrade()) supported |= WIRED_CRAFTING;
        if ((modes & WIRELESS_CRAFTING) != 0 && host.supportsAEWirelessCraftingUpgrade()) supported |= WIRELESS_CRAFTING;
        if ((modes & WIRED_AUTO_PROCESSING) != 0 && host.supportsAEWiredAutoProcessingUpgrade()) supported |= WIRED_AUTO_PROCESSING;
        if ((modes & WIRELESS_AUTO_PROCESSING) != 0 && host.supportsAEWirelessAutoProcessingUpgrade()) supported |= WIRELESS_AUTO_PROCESSING;
        if ((modes & WIRED_OUTPUT) != 0 && host.supportsAEWiredOutputUpgrade()) supported |= WIRED_OUTPUT;
        if ((modes & WIRELESS_OUTPUT) != 0 && host.supportsAEWirelessOutputUpgrade()) supported |= WIRELESS_OUTPUT;
        StateSnapshot computed = null;
        if (current != null && DEFAULT_COMPONENT_SUPPORT.get(current.getClass()) && stableSupport(modes) &&
              currentVersion == current.getConfigurationVersion() && registryVersion == ExternalUpgradeSupportRegistry.getVersion()) {
            BooleanSupplier validity = SUPPORT_INVALIDATION && DEFAULT_COMPONENT_REVISION.get(current.getClass()) ?
                  current.trackSupportValidity(currentVersion, registryVersion) : null;
            computed = new StateSnapshot(current, currentVersion, registryVersion, supported, validity);
        }
        synchronized (this) {
            if (localVersion == invalidationVersion) supportCache = computed;
        }
        return supported;
    }

    synchronized void invalidate() {
        invalidationVersion++;
        installedCache = null;
        supportCache = null;
    }

    private static boolean stableSupport(int modes) {
        return ((modes & WIRED_CRAFTING) == 0 || ExternalUpgradeSupportRegistry.isSupportStable(AEUpgrade.AE_CRAFTING)) &&
              ((modes & WIRELESS_CRAFTING) == 0 || ExternalUpgradeSupportRegistry.isSupportStable(AEUpgrade.AE_WIRELESS_CRAFTING)) &&
              ((modes & WIRED_AUTO_PROCESSING) == 0 || ExternalUpgradeSupportRegistry.isSupportStable(AEUpgrade.AE_AUTO_PROCESSING)) &&
              ((modes & WIRELESS_AUTO_PROCESSING) == 0 || ExternalUpgradeSupportRegistry.isSupportStable(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING)) &&
              ((modes & WIRED_OUTPUT) == 0 || ExternalUpgradeSupportRegistry.isSupportStable(AEUpgrade.AE_OUTPUT)) &&
              ((modes & WIRELESS_OUTPUT) == 0 || ExternalUpgradeSupportRegistry.isSupportStable(AEUpgrade.AE_WIRELESS_OUTPUT));
    }

    private int installedModes() {
        if (!cacheInstalled) return readInstalledModes();
        TileComponentUpgrade current = currentComponent();
        long currentVersion = current.getConfigurationVersion();
        StateSnapshot cached = installedCache;
        if (cached != null && cached.matches(current, currentVersion, 0)) return cached.modes;
        int modes = readInstalledModes();
        if (currentVersion == current.getConfigurationVersion()) {
            installedCache = new StateSnapshot(current, currentVersion, 0, modes);
        } else invalidate();
        return modes;
    }

    private TileComponentUpgrade currentComponent() {
        // A virtual call on the common base preserves overrides while avoiding the broad interface dispatch.
        return machineHost != null ? machineHost.getComponent() : ((IUpgradeTile) host).getComponent();
    }

    private static final class StateSnapshot {
        private final TileComponentUpgrade component;
        private final long version;
        private final long declarationVersion;
        private final int modes;
        private final BooleanSupplier validity;

        private StateSnapshot(TileComponentUpgrade component, long version, long declarationVersion, int modes) {
            this(component, version, declarationVersion, modes, null);
        }

        private StateSnapshot(TileComponentUpgrade component, long version, long declarationVersion, int modes, BooleanSupplier validity) {
            this.component = component;
            this.version = version;
            this.declarationVersion = declarationVersion;
            this.modes = modes;
            this.validity = validity;
        }

        private boolean matches(TileComponentUpgrade component, long version, long declarationVersion) {
            return this.component == component && this.version == version && this.declarationVersion == declarationVersion;
        }
    }

    private int readInstalledModes() {
        int modes = 0;
        if (host.hasAEWiredCraftingUpgrade()) modes |= WIRED_CRAFTING;
        if (host.hasAEWirelessCraftingUpgrade()) modes |= WIRELESS_CRAFTING;
        if (host.hasAEWiredAutoProcessingUpgrade()) modes |= WIRED_AUTO_PROCESSING;
        if (host.hasAEWirelessAutoProcessingUpgrade()) modes |= WIRELESS_AUTO_PROCESSING;
        if (host.hasAEWiredOutputUpgrade()) modes |= WIRED_OUTPUT;
        if (host.hasAEWirelessOutputUpgrade()) modes |= WIRELESS_OUTPUT;
        return modes;
    }

    private static ClassValue<Boolean> defaultMethods(String... methods) {
        return new ClassValue<Boolean>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                try {
                    for (String method : methods) {
                        if (type.getMethod(method).getDeclaringClass() != IAEUpgradeHost.class) return false;
                    }
                    return true;
                } catch (ReflectiveOperationException | SecurityException unavailable) {
                    return false;
                }
            }
        };
    }

    private static boolean hasVersionedUpgrades() {
        try {
            TileComponentUpgrade.class.getMethod("getConfigurationVersion");
            return true;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            // Older supported core versions retain live installed-state queries.
            return false;
        }
    }

    private static boolean hasSupportInvalidation() {
        try {
            TileComponentUpgrade.class.getMethod("trackSupportValidity", long.class, long.class);
            return true;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return false;
        }
    }
}
