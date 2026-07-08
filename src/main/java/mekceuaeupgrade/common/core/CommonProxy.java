package mekceuaeupgrade.common.core;

import mekceuaeupgrade.common.recipe.AEUpgradePatternDecoder;

public class CommonProxy {

    public void preInit() {
        AEUpgradePatternDecoder.register();
    }

    public void init() {
    }

    public void registerItemRenders() {
    }
}
