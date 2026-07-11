package mekceuaeupgrade.common.core;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.Version;
import mekanism.common.base.IModule;
import mekanism.common.config.MekanismConfig;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.network.MEKCeuAEUpgradePacketHandler;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import mekceuaeupgrade.common.ui.AEUpgradeWindowTypes;
import mekceuaeupgrade.mekceuaeupgrade.Tags;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = MEKCeuAEUpgrade.MODID, useMetadata = true, dependencies = "required-after:mekanism;required-after:appliedenergistics2;required-after:ae2fc;required-after:mekeng;required-after:mixinbooter")
@Mod.EventBusSubscriber
public class MEKCeuAEUpgrade implements IModule {

    public static final String MODID = Tags.MOD_ID;
    public static final String LOG_TAG = "MekanismCEu AE Upgrade";
    public static final Logger logger = LogManager.getLogger(LOG_TAG);
    public static final Version versionNumber = new Version(999, 999, 999);
    public static final CreativeTabMEKCeuAEUpgrade tabMEKCeuAEUpgrade = new CreativeTabMEKCeuAEUpgrade();
    public static final MEKCeuAEUpgradePacketHandler packetHandler = new MEKCeuAEUpgradePacketHandler();

    @SidedProxy(clientSide = "mekceuaeupgrade.client.ClientProxy", serverSide = "mekceuaeupgrade.common.core.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance(MEKCeuAEUpgrade.MODID)
    public static MEKCeuAEUpgrade instance;

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MODID, path);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        MEKCeuAEUpgradeItems.registerItems(event.getRegistry());
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        MEKCeuAEUpgradeRecipes.addRecipes();
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        proxy.registerItemRenders();
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) {
            AERecipeProfileManager.clearWorld(event.getWorld());
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Mekanism.modulesLoaded.add(this);
        AEUpgradeWindowTypes.init();
        packetHandler.initialize();
        proxy.init();
    }

    @Override
    public Version getVersion() {
        return versionNumber;
    }

    @Override
    public String getName() {
        return "AEUpgrade";
    }

    @Override
    public void writeConfig(ByteBuf byteBuf, MekanismConfig mekanismConfig) {
    }

    @Override
    public void readConfig(ByteBuf byteBuf, MekanismConfig mekanismConfig) {
    }

    @Override
    public void resetClient() {
    }
}
