package mekceuaeupgrade.common.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class AEUpgradeConfig {

    private static boolean qioStorageBridgeEnabled = true;

    private AEUpgradeConfig() {
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        qioStorageBridgeEnabled = config.getBoolean("enableQIOStorageBridge", "integration", true,
              "Expose QIO dashboard storage to compatible AE storage buses.");
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean isQIOStorageBridgeEnabled() {
        return qioStorageBridgeEnabled;
    }
}
