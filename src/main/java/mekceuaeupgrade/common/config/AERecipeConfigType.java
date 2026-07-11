package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.ui.AELang;
import mekceuaeupgrade.common.ui.AEUpgradeWindowTypes;

import mekanism.common.inventory.container.SelectedWindowData.WindowType;

public enum AERecipeConfigType {
    CRAFTING("crafting", AERecipeProfile.RouteFilterMode.BLACKLIST, true, true),
    AUTO_PROCESSING("processing", AERecipeProfile.RouteFilterMode.WHITELIST, false, false);

    private final String id;
    private final AERecipeProfile.RouteFilterMode defaultFilterMode;
    private final boolean routeFilterMutable;
    private final boolean routesEnabledByDefault;

    AERecipeConfigType(String id, AERecipeProfile.RouteFilterMode defaultFilterMode, boolean routeFilterMutable, boolean routesEnabledByDefault) {
        this.id = id;
        this.defaultFilterMode = defaultFilterMode;
        this.routeFilterMutable = routeFilterMutable;
        this.routesEnabledByDefault = routesEnabledByDefault;
    }

    public String getId() {
        return id;
    }

    public AERecipeProfile.RouteFilterMode getDefaultFilterMode() {
        return defaultFilterMode;
    }

    public boolean isRouteFilterMutable() {
        return routeFilterMutable;
    }

    public boolean areRoutesEnabledByDefault() {
        return routesEnabledByDefault;
    }

    /**
     * @return 当前配置类型是否允许输入与输出包含同种资源的路线
     */
    public boolean allowsSelfReferentialRoutes() {
        return this == AUTO_PROCESSING;
    }

    public boolean isSupportedBy(IAEUpgradeHost host) {
        return switch (this) {
            case CRAFTING -> host.supportsAECraftingUpgrade();
            case AUTO_PROCESSING -> host.supportsAEAutoProcessingUpgrade();
        };
    }

    public boolean isInstalledIn(IAEUpgradeHost host) {
        return switch (this) {
            case CRAFTING -> host.hasAECraftingUpgrade();
            case AUTO_PROCESSING -> host.hasAEAutoProcessingUpgrade();
        };
    }

    public WindowType getWindowType() {
        return switch (this) {
            case CRAFTING -> AEUpgradeWindowTypes.AE_RECIPE_CONFIG;
            case AUTO_PROCESSING -> AEUpgradeWindowTypes.AE_AUTO_PROCESSING_RECIPE_CONFIG;
        };
    }

    public AELang getTitle() {
        return switch (this) {
            case CRAFTING -> AELang.AE_RECIPE_CONFIG;
            case AUTO_PROCESSING -> AELang.AE_AUTO_PROCESSING_RECIPE_CONFIG;
        };
    }

    public static AERecipeConfigType byWindowType(WindowType windowType) {
        return windowType == AEUpgradeWindowTypes.AE_AUTO_PROCESSING_RECIPE_CONFIG ? AUTO_PROCESSING : CRAFTING;
    }

    public static AERecipeConfigType byIndex(int index) {
        AERecipeConfigType[] values = values();
        return index >= 0 && index < values.length ? values[index] : CRAFTING;
    }
}
