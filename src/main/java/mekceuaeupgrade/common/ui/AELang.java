package mekceuaeupgrade.common.ui;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.text.ILangEntry;
import mekanism.common.util.LangUtils;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

@NothingNullByDefault
public enum AELang implements ILangEntry {

    AE_RECIPE_CONFIG("gui", "ae_recipe_config.title"),
    AE_RECIPE_CONFIG_EMPTY("gui", "ae_recipe_config.empty"),
    AE_RECIPE_CONFIG_NO_MATCH("gui", "ae_recipe_config.no_match"),
    AE_RECIPE_CONFIG_PRODUCTS("gui", "ae_recipe_config.products"),
    AE_RECIPE_CONFIG_PRODUCTS_ALL("gui", "ae_recipe_config.products_all"),
    AE_RECIPE_CONFIG_PRODUCTS_MULTI("gui", "ae_recipe_config.products_multi"),
    AE_RECIPE_CONFIG_PRODUCTS_SINGLE("gui", "ae_recipe_config.products_single"),
    AE_RECIPE_CONFIG_SEARCH("gui", "ae_recipe_config.search"),
    AE_RECIPE_CONFIG_ROUTES("gui", "ae_recipe_config.routes"),
    AE_RECIPE_CONFIG_ENABLE("gui", "ae_recipe_config.enable"),
    AE_RECIPE_CONFIG_DISABLE("gui", "ae_recipe_config.disable"),
    AE_RECIPE_CONFIG_RESET_PRODUCT("gui", "ae_recipe_config.reset_product"),
    AE_RECIPE_CONFIG_RESET_ALL("gui", "ae_recipe_config.reset_all"),
    AE_RECIPE_CONFIG_MULTI_ROUTE("gui", "ae_recipe_config.multi_route"),
    AE_RECIPE_CONFIG_GLOBAL_AMOUNT("gui", "ae_recipe_config.global_amount"),
    AE_RECIPE_CONFIG_ROUTE_AMOUNT("gui", "ae_recipe_config.route_amount"),
    AE_RECIPE_CONFIG_AMOUNT_INHERIT("gui", "ae_recipe_config.amount_inherit"),
    AE_RECIPE_CONFIG_ROUTE_ENABLED("gui", "ae_recipe_config.route_enabled"),
    AE_RECIPE_CONFIG_ROUTE_PARTIAL("gui", "ae_recipe_config.route_partial"),
    AE_RECIPE_CONFIG_ROUTE_DISABLED("gui", "ae_recipe_config.route_disabled"),
    AE_RECIPE_CONFIG_PROFILE_GLOBAL("gui", "ae_recipe_config.profile_global"),
    AE_RECIPE_CONFIG_PROFILE_SINGLE("gui", "ae_recipe_config.profile_single"),
    AE_RECIPE_CONFIG_PROFILE_SLOT("gui", "ae_recipe_config.profile_slot"),
    AE_RECIPE_CONFIG_FILTER_BLACKLIST("gui", "ae_recipe_config.filter_blacklist"),
    AE_RECIPE_CONFIG_FILTER_WHITELIST("gui", "ae_recipe_config.filter_whitelist"),
    AE_RECIPE_CONFIG_DISABLE_PRODUCT("gui", "ae_recipe_config.disable_product"),
    AE_RECIPE_CONFIG_ENABLE_ALL("gui", "ae_recipe_config.enable_all"),
    AE_RECIPE_CONFIG_DISABLE_ALL("gui", "ae_recipe_config.disable_all"),
    AE_RECIPE_CONFIG_MOVE_UP("gui", "ae_recipe_config.move_up"),
    AE_RECIPE_CONFIG_MOVE_DOWN("gui", "ae_recipe_config.move_down"),
    AE_RECIPE_CONFIG_MOVE_TOP("gui", "ae_recipe_config.move_top"),
    AE_RECIPE_CONFIG_MOVE_BOTTOM("gui", "ae_recipe_config.move_bottom"),
    AE_RECIPE_CONFIG_PROFILE_MODE_TOOLTIP("gui", "ae_recipe_config.profile_mode.tooltip"),
    AE_RECIPE_CONFIG_PROFILE_SLOT_TOOLTIP("gui", "ae_recipe_config.profile_slot.tooltip"),
    AE_RECIPE_CONFIG_FILTER_MODE_TOOLTIP("gui", "ae_recipe_config.filter_mode.tooltip"),
    AE_RECIPE_CONFIG_PRODUCT_FILTER_TOOLTIP("gui", "ae_recipe_config.product_filter.tooltip"),
    AE_RECIPE_CONFIG_SEARCH_TOOLTIP("gui", "ae_recipe_config.search.tooltip"),
    AE_RECIPE_CONFIG_TOGGLE_BUTTON_TOOLTIP("gui", "ae_recipe_config.toggle_button.tooltip"),
    AE_RECIPE_CONFIG_MOVE_UP_BUTTON_TOOLTIP("gui", "ae_recipe_config.move_up_button.tooltip"),
    AE_RECIPE_CONFIG_MOVE_DOWN_BUTTON_TOOLTIP("gui", "ae_recipe_config.move_down_button.tooltip"),
    AE_RECIPE_CONFIG_RESET_PRODUCT_TOOLTIP("gui", "ae_recipe_config.reset_product.tooltip"),
    AE_RECIPE_CONFIG_RESET_ALL_TOOLTIP("gui", "ae_recipe_config.reset_all.tooltip"),
    AE_RECIPE_CONFIG_GLOBAL_AMOUNT_TOOLTIP("gui", "ae_recipe_config.global_amount.tooltip"),
    AE_RECIPE_CONFIG_ROUTE_AMOUNT_TOOLTIP("gui", "ae_recipe_config.route_amount.tooltip"),
    ;

    private final String key;

    AELang(String type, String path) {
        this(makeDescriptionId(type, rl(path)));
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MEKCeuAEUpgrade.MODID, path);
    }

    public static String makeDescriptionId(String type, @Nullable ResourceLocation id) {
        return id == null ? type + ".unregistered_sadface" : type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    AELang(String key) {
        this.key = key;
    }

    @Override
    public String getTranslationKey() {
        return LangUtils.localize(key);
    }
}
