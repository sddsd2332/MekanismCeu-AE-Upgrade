package mekceuaeupgrade.common.integration.qio;

import mekanism.api.qio.external.QIOCapabilities;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekceuaeupgrade.common.config.AEUpgradeConfig;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = MEKCeuAEUpgrade.MODID)
public final class QIOAEStorageBridge {

    private static final ResourceLocation CAPABILITY_ID = MEKCeuAEUpgrade.rl("qio_ae_storage");
    private static final Set<SupergiantQIOCapabilityProvider> PROVIDERS =
          Collections.newSetFromMap(new WeakHashMap<>());
    private static final AtomicBoolean MISSING_CORE_CAPABILITY_LOGGED = new AtomicBoolean();

    private QIOAEStorageBridge() {
    }

    @SubscribeEvent
    public static void attachTileCapabilities(AttachCapabilitiesEvent<TileEntity> event) {
        if (!AEUpgradeConfig.isQIOStorageBridgeEnabled()) {
            return;
        }
        if (!(event.getObject() instanceof TileEntityQIODashboard dashboard)) {
            return;
        }
        if (QIOCapabilities.STORAGE_ACCESSOR == null) {
            if (MISSING_CORE_CAPABILITY_LOGGED.compareAndSet(false, true)) {
                MEKCeuAEUpgrade.logger.error("Mekanism QIO storage capability is unavailable; the AE QIO bridge is disabled.");
            }
            return;
        }
        var provider = new SupergiantQIOCapabilityProvider(dashboard);
        event.addCapability(CAPABILITY_ID, provider);
    }

    static void activate(SupergiantQIOCapabilityProvider provider) {
        synchronized (PROVIDERS) {
            PROVIDERS.add(provider);
        }
    }

    static void deactivate(SupergiantQIOCapabilityProvider provider) {
        synchronized (PROVIDERS) {
            PROVIDERS.remove(provider);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (var provider : snapshotProviders()) {
            provider.tick();
        }
        SupergiantQIOMountRegistry.flushPendingChanges();
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            return;
        }
        for (var provider : snapshotProviders()) {
            if (provider.isForWorld(event.getWorld())) {
                provider.close();
            }
        }
        SupergiantQIOMountRegistry.flushPendingChanges();
    }

    private static ArrayList<SupergiantQIOCapabilityProvider> snapshotProviders() {
        synchronized (PROVIDERS) {
            return new ArrayList<>(PROVIDERS);
        }
    }
}
