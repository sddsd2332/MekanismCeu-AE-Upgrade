package mekceuaeupgrade.mixin;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@SuppressWarnings({"unused", "SameParameterValue"})
public class MEKCeuAEUpgradeMixin implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("MekanismCEu AE Upgrade");
    private static final Map<String, BooleanSupplier> MIXIN_CONFIGS = new LinkedHashMap<>();

    static {
        addModdedMixinCFG("mixins.mekceuaeupgrade.appeng.json", "appliedenergistics2");
        addModdedMixinCFG("mixins.mekceuaeupgrade.json", "mekanism");
        addModdedMixinCFG("mixins.mekceuaeupgrade.multiblockmachine.json", "mekanismmultiblockmachine");
        addModdedMixinCFG("mixins.mekceuaeupgrade.mekceumoremachine.json", "mekceumoremachine");
    }

    @Override
    public List<String> getMixinConfigs() {
        return new ArrayList<>(MIXIN_CONFIGS.keySet());
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        BooleanSupplier supplier = MIXIN_CONFIGS.get(mixinConfig);
        if (supplier == null) {
            LOGGER.warn("Mixin config {} is not found in config map. It will not be loaded.", mixinConfig);
            return false;
        }
        return supplier.getAsBoolean();
    }

    private static boolean modLoaded(String modID) {
        return Loader.isModLoaded(modID);
    }

    private static void addModdedMixinCFG(String mixinConfig, String modID) {
        addMixinCFG(mixinConfig, () -> modLoaded(modID));
    }

    private static void addMixinCFG(String mixinConfig, BooleanSupplier conditions) {
        MIXIN_CONFIGS.put(mixinConfig, conditions);
    }
}
