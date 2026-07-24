package mekceuaeupgrade.client.gui;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.ui.AELang;
import mekceuaeupgrade.common.ui.AEUpgradeWindowTypes;

import mekceuaeupgrade.common.config.AERecipeConfigSnapshot;
import mekceuaeupgrade.common.config.AERecipeConfigSnapshot.Product;
import mekceuaeupgrade.common.config.AERecipeConfigSnapshot.Route;
import mekceuaeupgrade.common.config.AERecipeConfigClientCache;
import mekceuaeupgrade.common.config.AERecipeConfigType;
import mekceuaeupgrade.common.config.AERecipeProfile;
import mekceuaeupgrade.common.network.PacketAERecipeConfig.AERecipeConfigMessage;
import mekceuaeupgrade.common.network.PacketAERecipeConfig.RecipeConfigPacket;
import mekanism.api.Coord4D;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GuiAERecipeConfigWindow extends GuiWindow {

    public static final int WIDTH = 336;
    private static final int HEIGHT = 220;
    private static final int PRODUCT_LIST_WIDTH = 154;
    private static final int PRODUCT_FILTER_BUTTON_X = 6;
    private static final int PRODUCT_FILTER_BUTTON_WIDTH = 70;
    private static final int PRODUCT_SEARCH_X = 80;
    private static final int PRODUCT_SEARCH_WIDTH = PRODUCT_LIST_WIDTH - (PRODUCT_SEARCH_X - PRODUCT_FILTER_BUTTON_X);
    private static final int ROUTE_LIST_X = 170;
    private static final int ROUTE_LIST_WIDTH = 160;
    private static final int LIST_Y = 38;
    private static final int LIST_HEIGHT = 144;
    private static final int ROW_HEIGHT = 24;
    private static final int AMOUNT_ROW_Y = 186;
    private static final int BOTTOM_BUTTON_Y = 202;
    private static final int AMOUNT_FIELD_WIDTH = 64;
    private static final int GLOBAL_AMOUNT_FIELD_X = 6 + PRODUCT_LIST_WIDTH - AMOUNT_FIELD_WIDTH;
    private static final int ROUTE_AMOUNT_FIELD_X = ROUTE_LIST_X + ROUTE_LIST_WIDTH - AMOUNT_FIELD_WIDTH;
    private static final int PROFILE_MODE_BUTTON_X = WIDTH - 98;
    private static final int PROFILE_MODE_BUTTON_WIDTH = 92;
    private static final int ROUTE_FILTER_MODE_BUTTON_X = ROUTE_LIST_X;
    private static final int ROUTE_FILTER_MODE_BUTTON_WIDTH = 64;
    private static final int INDICATOR_SIZE = 6;
    private static final int INDICATOR_ENABLED = 0xFF57C78B;
    private static final int INDICATOR_PARTIAL = 0xFFE0C05A;
    private static final int INDICATOR_DISABLED = 0xFFC75656;
    private static final int INDICATOR_MULTI_ROUTE = 0xFF4E9FDB;
    private static final int ROW_HOVER_COLOR = 0x503A4B5F;
    private static final int ROW_SELECTED_COLOR = 0x80608CC1;
    private static final int ROW_FOCUSED_SELECTED_COLOR = 0xB04E9FDB;
    private static final int ROW_SELECTED_OUTLINE_COLOR = 0xFF8FC7FF;
    private static final int ROW_FOCUSED_SELECTED_OUTLINE_COLOR = 0xFFDDF2FF;
    private static final int ROW_LOCKED_INNER_COLOR = 0x70C72F2F;
    private static final int ROW_LOCKED_INNER_OUTLINE_COLOR = 0xFFE05A5A;
    private static final int SCROLL_ITEM_STEP = 18;
    private static final double SCROLL_PIXELS_PER_SECOND = 12.0D;
    private static final double MIN_SCROLL_EDGE_PAUSE = 0.5D;

    private final TileEntityContainerBlock tile;
    private final Coord4D coord;
    private final AERecipeConfigType configType;
    private final ProductScrollList productList;
    private final RouteScrollList routeList;
    private final TranslationButton productFilterButton;
    private final TranslationButton toggleButton;
    private final TranslationButton upButton;
    private final TranslationButton downButton;
    private final TranslationButton resetProductButton;
    private final TranslationButton resetAllButton;
    private final TranslationButton profileModeButton;
    private final TranslationButton globalProfileButton;
    private final TranslationButton routeFilterModeButton;
    private final GuiTextField searchField;
    private final GuiTextField globalAmountField;
    private final GuiTextField routeAmountField;

    private AERecipeConfigSnapshot snapshot = AERecipeConfigSnapshot.EMPTY;
    private ProductFilterMode productFilterMode = ProductFilterMode.ALL;
    private SelectionFocus selectionFocus = SelectionFocus.PRODUCT;
    private String productSearch = "";
    @Nullable
    private String selectedOutputKey;
    @Nullable
    private String selectedRouteKey;
    private boolean closeScheduled;
    private boolean closed;

    private void drawSmoothScrollingString(TextComponentString text, int x, int y, int width, int height, int color) {
        int textWidth = getFont().getStringWidth(text.getFormattedText());
        if (textWidth <= 0 || width <= 0) {
            return;
        }
        boolean scrolling = textWidth > width;
        float drawX = x;
        if (scrolling) {
            enableGuiScissor(x, y, x + width, y + height);
            drawX -= getScrollingOffset(textWidth, width, getTimeOpened(), !getFont().getBidiFlag());
        }
        float drawY = y + (height - getFont().FONT_HEIGHT) / 2F;
        getFont().drawString(text.getFormattedText(), drawX, drawY, color, false);
        if (scrolling) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void drawScrollingStackStrip(List<ItemStack> stacks, int x, int y, int width, int height) {
        if (stacks.isEmpty() || width <= 0) {
            return;
        }
        int contentWidth = getStackStripWidth(stacks);
        boolean scrolling = contentWidth > width;
        float offset = scrolling ? getScrollingOffset(contentWidth, width, getTimeOpened(), true) : 0;
        if (scrolling) {
            enableGuiScissor(x, y, x + width, y + height);
        }
        for (int i = 0; i < stacks.size(); i++) {
            float itemX = x + i * SCROLL_ITEM_STEP - offset;
            if (itemX > x - SCROLL_ITEM_STEP && itemX < x + width) {
                gui().renderItemWithOverlay(stacks.get(i), Math.round(itemX), y + Math.max(0, (height - 16) / 2), 1F, null);
            }
        }
        if (scrolling) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Nullable
    private ItemStack getHoveredScrollingStack(List<ItemStack> stacks, int mouseX, int mouseY, int x, int y, int width, int height) {
        if (stacks.isEmpty() || width <= 0) {
            return null;
        }
        int absoluteX = getGuiLeft() + x;
        int absoluteY = getGuiTop() + y;
        if (mouseX < absoluteX || mouseX >= absoluteX + width || mouseY < absoluteY || mouseY >= absoluteY + height) {
            return null;
        }
        int contentWidth = getStackStripWidth(stacks);
        float offset = contentWidth > width ? getScrollingOffset(contentWidth, width, getTimeOpened(), true) : 0;
        for (int i = 0; i < stacks.size(); i++) {
            float itemX = absoluteX + i * SCROLL_ITEM_STEP - offset;
            if (mouseX >= itemX && mouseX < itemX + 16) {
                return stacks.get(i);
            }
        }
        return null;
    }

    private int getStackStripWidth(List<ItemStack> stacks) {
        return Math.max(16, (stacks.size() - 1) * SCROLL_ITEM_STEP + 16);
    }

    private float getScrollingOffset(double contentWidth, double areaWidth, long msVisible, boolean leftToRight) {
        double overflowWidth = contentWidth - areaWidth;
        if (overflowWidth <= 0) {
            return 0;
        }
        long visibleDuration = Math.max(0, GuiElement.getMillis() - msVisible);
        double seconds = visibleDuration / 1_000D;
        double travelTime = overflowWidth / SCROLL_PIXELS_PER_SECOND;
        double cycleTime = MIN_SCROLL_EDGE_PAUSE * 2 + travelTime * 2;
        double cyclePosition = seconds % cycleTime;
        if (cyclePosition < MIN_SCROLL_EDGE_PAUSE) {
            return leftToRight ? 0 : (float) overflowWidth;
        }
        cyclePosition -= MIN_SCROLL_EDGE_PAUSE;
        double offset;
        if (cyclePosition < travelTime) {
            offset = cyclePosition * SCROLL_PIXELS_PER_SECOND;
        } else if (cyclePosition < travelTime + MIN_SCROLL_EDGE_PAUSE) {
            offset = overflowWidth;
        } else {
            cyclePosition -= travelTime + MIN_SCROLL_EDGE_PAUSE;
            offset = overflowWidth - cyclePosition * SCROLL_PIXELS_PER_SECOND;
        }
        return (float) (leftToRight ? offset : overflowWidth - offset);
    }

    private void enableGuiScissor(int minX, int minY, int maxX, int maxY) {
        double scaleX = minecraft.displayWidth / (double) minecraft.currentScreen.width;
        double scaleY = minecraft.displayHeight / (double) minecraft.currentScreen.height;
        int scissorX = (int) Math.floor((getGuiLeft() + minX) * scaleX);
        int scissorY = (int) Math.floor(minecraft.displayHeight - (getGuiTop() + maxY) * scaleY);
        int scissorWidth = Math.max(0, (int) Math.ceil((maxX - minX) * scaleX));
        int scissorHeight = Math.max(0, (int) Math.ceil((maxY - minY) * scaleY));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    public GuiAERecipeConfigWindow(IGuiWrapper gui, int x, int y, TileEntityContainerBlock tile, SelectedWindowData windowData) {
        this(gui, x, y, tile, windowData, AERecipeConfigType.byWindowType(windowData.type));
    }

    public GuiAERecipeConfigWindow(IGuiWrapper gui, int x, int y, TileEntityContainerBlock tile, SelectedWindowData windowData, AERecipeConfigType configType) {
        super(gui, x, y, WIDTH, HEIGHT, windowData);
        if (windowData.type != configType.getWindowType()) {
            throw new IllegalArgumentException("AE recipe config windows must have an AE recipe config window type");
        }
        this.tile = tile;
        this.configType = configType;
        coord = Coord4D.get(tile);
        interactionStrategy = InteractionStrategy.ALL;
        profileModeButton = addChild(new TranslationButton(gui, relativeX + PROFILE_MODE_BUTTON_X, relativeY + 6, PROFILE_MODE_BUTTON_WIDTH, 12,
              AELang.AE_RECIPE_CONFIG_PROFILE_GLOBAL,
              (element, mouseX, mouseY) -> {
                  sendProfileModeAction();
                  return true;
              }));
        routeFilterModeButton = addChild(new TranslationButton(gui, relativeX + ROUTE_FILTER_MODE_BUTTON_X, relativeY + 22, ROUTE_FILTER_MODE_BUTTON_WIDTH, 12,
              AELang.AE_RECIPE_CONFIG_FILTER_BLACKLIST,
              (element, mouseX, mouseY) -> {
                  sendRouteFilterModeAction();
                  return true;
              }));
        globalProfileButton = addChild(new TranslationButton(gui, relativeX + PROFILE_MODE_BUTTON_X, relativeY + 22, PROFILE_MODE_BUTTON_WIDTH, 12,
              AELang.AE_RECIPE_CONFIG_PROFILE_SLOT,
              (element, mouseX, mouseY) -> {
                  sendGlobalProfileAction(1);
                  return true;
              }) {
            @Override
            public void onClick(double mouseX, double mouseY, int button) {
                sendGlobalProfileAction(button == 1 ? -1 : 1);
            }

            @Override
            public boolean isValidClickButton(int button) {
                return button == 0 || button == 1;
            }
        });
        productFilterButton = addChild(new TranslationButton(gui, relativeX + PRODUCT_FILTER_BUTTON_X, relativeY + 22, PRODUCT_FILTER_BUTTON_WIDTH, 12,
              productFilterMode.getLabel(), (element, mouseX, mouseY) -> {
                  cycleProductFilterMode();
                  return true;
              }));
        searchField = addChild(new GuiTextField(gui, relativeX + PRODUCT_SEARCH_X, relativeY + 22, PRODUCT_SEARCH_WIDTH, 12)
              .setBackground(BackgroundType.DIGITAL)
              .setTextColor(screenTextColor())
              .setMaxLength(64));
        productList = addChild(new ProductScrollList(gui, relativeX + 6, relativeY + LIST_Y, PRODUCT_LIST_WIDTH, LIST_HEIGHT));
        routeList = addChild(new RouteScrollList(gui, relativeX + ROUTE_LIST_X, relativeY + LIST_Y, ROUTE_LIST_WIDTH, LIST_HEIGHT));
        globalAmountField = addChild(new GuiTextField(gui, relativeX + GLOBAL_AMOUNT_FIELD_X, relativeY + AMOUNT_ROW_Y, AMOUNT_FIELD_WIDTH, 12)
              .setBackground(BackgroundType.DIGITAL)
              .setTextColor(screenTextColor())
              .setMaxLength(Integer.toString(AERecipeProfile.MAX_CRAFT_AMOUNT).length())
              .setInputValidator(this::isPositiveIntegerInput)
              .setTextValidator(this::isPositiveIntegerText)
              .setEnterHandler(this::sendGlobalCraftAmountAction));
        routeAmountField = addChild(new GuiTextField(gui, relativeX + ROUTE_AMOUNT_FIELD_X, relativeY + AMOUNT_ROW_Y, AMOUNT_FIELD_WIDTH, 12)
              .setBackground(BackgroundType.DIGITAL)
              .setTextColor(screenTextColor())
              .setMaxLength(Integer.toString(AERecipeProfile.MAX_CRAFT_AMOUNT).length())
              .setInputValidator(this::isPositiveIntegerInput)
              .setTextValidator(this::isPositiveIntegerText)
              .setEnterHandler(this::sendRouteCraftAmountAction));
        searchField.setResponder(text -> {
            productSearch = text.trim().toLowerCase(Locale.ROOT);
            productList.resetScroll();
            routeList.resetScroll();
            validateSelection();
            updateButtons();
        });
        toggleButton = addChild(new TranslationButton(gui, relativeX + 174, relativeY + BOTTOM_BUTTON_Y, 54, 12, AELang.AE_RECIPE_CONFIG_ENABLE,
              (element, mouseX, mouseY) -> {
                  sendToggleAction();
                  return true;
              }));
        upButton = addChild(new TranslationButton(gui, relativeX + 232, relativeY + BOTTOM_BUTTON_Y, 46, 12, AELang.AE_RECIPE_CONFIG_MOVE_UP,
              (element, mouseX, mouseY) -> {
                  sendMoveAction(-1);
                  return true;
              }));
        downButton = addChild(new TranslationButton(gui, relativeX + 282, relativeY + BOTTOM_BUTTON_Y, 48, 12, AELang.AE_RECIPE_CONFIG_MOVE_DOWN,
              (element, mouseX, mouseY) -> {
                  sendMoveAction(1);
                  return true;
              }));
        resetProductButton = addChild(new TranslationButton(gui, relativeX + 6, relativeY + BOTTOM_BUTTON_Y, 94, 12, AELang.AE_RECIPE_CONFIG_RESET_PRODUCT,
              (element, mouseX, mouseY) -> {
                  sendProductAction(RecipeConfigPacket.RESET_PRODUCT);
                  return true;
              }));
        resetAllButton = addChild(new TranslationButton(gui, relativeX + 104, relativeY + BOTTOM_BUTTON_Y, 66, 12, AELang.AE_RECIPE_CONFIG_RESET_ALL,
              (element, mouseX, mouseY) -> {
                  sendResetAllAction();
                  return true;
              }));
        AERecipeConfigClientCache.clear(coord, configType);
        if (isConfigAvailable()) {
            requestSnapshot();
        }
        updateFromCache();
        updateButtons();
    }

    public GuiAERecipeConfigWindow(IGuiWrapper gui, int x, int y, TileEntityContainerBlock tile) {
        this(gui, x, y, tile, new SelectedWindowData(AEUpgradeWindowTypes.AE_RECIPE_CONFIG), AERecipeConfigType.CRAFTING);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isConfigAvailable()) {
            AERecipeConfigClientCache.clear(coord, configType);
            snapshot = AERecipeConfigSnapshot.EMPTY;
            validateSelection();
            updateButtons();
            scheduleClose();
            return;
        }
        updateFromCache();
        updateButtons();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        super.close();
        AERecipeConfigClientCache.clear(coord, configType);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(configType.getTitle().translate(), 5);
        if (searchField.isEmpty() && !searchField.isFocused()) {
            drawScaledScrollingString(AELang.AE_RECIPE_CONFIG_SEARCH.translate(), PRODUCT_SEARCH_X + 5, 24, TextAlignment.LEFT, 0x707070,
                  PRODUCT_SEARCH_WIDTH - 7, 0, false, 0.8F,
                  getTimeOpened());
        }
        if (snapshot.isEmpty() || snapshot.isIndividualProfile()) {
            drawScaledScrollingString(AELang.AE_RECIPE_CONFIG_ROUTES.translate(), ROUTE_LIST_X + 2, 24, TextAlignment.LEFT, titleTextColor(), ROUTE_LIST_WIDTH, 0,
                  false, 0.9F, getTimeOpened());
        }
        drawScaledScrollingString(AELang.AE_RECIPE_CONFIG_GLOBAL_AMOUNT.translate(), 8, AMOUNT_ROW_Y + 2, TextAlignment.LEFT, titleTextColor(),
              GLOBAL_AMOUNT_FIELD_X - 12, 0, false, 0.82F, getTimeOpened());
        Route selectedRoute = getSelectedRoute();
        String routeAmountText = AELang.AE_RECIPE_CONFIG_ROUTE_AMOUNT.translate().getFormattedText() + ": " +
              (selectedRoute == null ? "-" : selectedRoute.getCraftAmount());
        drawScaledScrollingString(new TextComponentString(routeAmountText), ROUTE_LIST_X + 2, AMOUNT_ROW_Y + 2, TextAlignment.LEFT,
              titleTextColor(), ROUTE_AMOUNT_FIELD_X - ROUTE_LIST_X - 6, 0, false, 0.82F, getTimeOpened());
        if (routeAmountField.isEmpty() && !routeAmountField.isFocused()) {
            drawScaledScrollingString(AELang.AE_RECIPE_CONFIG_AMOUNT_INHERIT.translate(), ROUTE_AMOUNT_FIELD_X + 4, AMOUNT_ROW_Y + 2, TextAlignment.LEFT,
                  0x707070, AMOUNT_FIELD_WIDTH - 6, 0, false, 0.8F, getTimeOpened());
        }
        if (snapshot.isEmpty()) {
            drawScaledScrollingString(AELang.AE_RECIPE_CONFIG_EMPTY.translate(), 6, 100, TextAlignment.CENTER, screenTextColor(), WIDTH - 12, 0, false, 1F,
                  getTimeOpened());
        } else if (getFilteredProducts().isEmpty()) {
            drawScaledScrollingString(AELang.AE_RECIPE_CONFIG_NO_MATCH.translate(), 6, 100, TextAlignment.CENTER, screenTextColor(), WIDTH - 12, 0, false, 1F,
                  getTimeOpened());
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (showTooltip(profileModeButton, AELang.AE_RECIPE_CONFIG_PROFILE_MODE_TOOLTIP, mouseX, mouseY) ||
              showTooltip(globalProfileButton, AELang.AE_RECIPE_CONFIG_PROFILE_SLOT_TOOLTIP, mouseX, mouseY) ||
              (configType.isRouteFilterMutable() && showTooltip(routeFilterModeButton, AELang.AE_RECIPE_CONFIG_FILTER_MODE_TOOLTIP, mouseX, mouseY)) ||
              showTooltip(productFilterButton, configType == AERecipeConfigType.AUTO_PROCESSING
                    ? AELang.AE_AUTO_PROCESSING_PRODUCT_FILTER_TOOLTIP : AELang.AE_RECIPE_CONFIG_PRODUCT_FILTER_TOOLTIP, mouseX, mouseY) ||
              showTooltip(searchField, AELang.AE_RECIPE_CONFIG_SEARCH_TOOLTIP, mouseX, mouseY) ||
              showTooltip(toggleButton, AELang.AE_RECIPE_CONFIG_TOGGLE_BUTTON_TOOLTIP, mouseX, mouseY) ||
              showTooltip(upButton, AELang.AE_RECIPE_CONFIG_MOVE_UP_BUTTON_TOOLTIP, mouseX, mouseY) ||
              showTooltip(downButton, AELang.AE_RECIPE_CONFIG_MOVE_DOWN_BUTTON_TOOLTIP, mouseX, mouseY) ||
              showTooltip(resetProductButton, AELang.AE_RECIPE_CONFIG_RESET_PRODUCT_TOOLTIP, mouseX, mouseY) ||
              showTooltip(resetAllButton, AELang.AE_RECIPE_CONFIG_RESET_ALL_TOOLTIP, mouseX, mouseY) ||
              showTooltip(globalAmountField, AELang.AE_RECIPE_CONFIG_GLOBAL_AMOUNT_TOOLTIP, mouseX, mouseY) ||
              showTooltip(routeAmountField, AELang.AE_RECIPE_CONFIG_ROUTE_AMOUNT_TOOLTIP, mouseX, mouseY)) {
            return;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (routeList.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (productList.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean showTooltip(GuiElement element, AELang tooltip, int mouseX, int mouseY) {
        if (element.visible && isMouseOverArea(mouseX, mouseY, element.getX(), element.getY(), element.getWidth(), element.getHeight())) {
            displayTooltip(tooltip.translate(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void drawRowSelection(int x, int y, int width, int height, boolean selected, boolean focused, boolean hovered) {
        if (selected) {
            GuiUtils.fill(x, y, x + width, y + height, focused ? ROW_FOCUSED_SELECTED_COLOR : ROW_SELECTED_COLOR);
            GuiUtils.drawOutline(x, y, width, height, focused ? ROW_FOCUSED_SELECTED_OUTLINE_COLOR : ROW_SELECTED_OUTLINE_COLOR);
        } else if (hovered) {
            GuiUtils.fill(x, y, x + width, y + height, ROW_HOVER_COLOR);
        }
    }

    private boolean isMouseOverArea(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void scheduleClose() {
        if (!closeScheduled) {
            closeScheduled = true;
            if (gui() instanceof GuiMekanism<?> mekanismGui) {
                mekanismGui.queueWindowClose(this);
            } else {
                close();
            }
        }
    }

    private void requestSnapshot() {
        MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType));
    }

    private boolean isConfigAvailable() {
        return GuiAERecipeConfigWindowTab.canOpen(tile, configType);
    }

    private void updateFromCache() {
        AERecipeConfigSnapshot cached = AERecipeConfigClientCache.getSnapshot(coord, configType);
        if (cached != null && cached != snapshot) {
            snapshot = cached;
            validateSelection();
        }
    }

    private void validateSelection() {
        List<Product> products = getFilteredProducts();
        if (snapshot.isEmpty() || products.isEmpty()) {
            selectedOutputKey = null;
            selectedRouteKey = null;
            return;
        }
        Product product = getSelectedProduct();
        if (product == null) {
            selectedOutputKey = products.get(0).getOutputKey();
            product = getSelectedProduct();
        }
        if (product == null) {
            selectedRouteKey = null;
            return;
        }
        if (getSelectedRoute() == null && !product.getRoutes().isEmpty()) {
            selectedRouteKey = product.getRoutes().get(0).getRouteKey();
        }
    }

    private void updateButtons() {
        Product product = getSelectedProduct();
        Route route = getSelectedRoute();
        boolean hasProduct = product != null;
        boolean hasRoute = route != null;
        boolean ctrlDown = GuiScreen.isCtrlKeyDown();
        boolean editable = snapshot.isEditable();
        toggleButton.active = editable && (ctrlDown ? hasModifiableRoute(product) : hasRoute && route.isModifiable());
        if (selectionFocus == SelectionFocus.PRODUCT) {
            int productIndex = getModifiableProductIndex(product);
            int productCount = getModifiableProductCount();
            upButton.active = editable && productIndex > 0;
            downButton.active = editable && productIndex >= 0 && productIndex < productCount - 1;
        } else {
            int routeIndex = getModifiableRouteIndex(product, route);
            int routeCount = getModifiableRouteCount(product);
            upButton.active = editable && routeIndex > 0;
            downButton.active = editable && routeIndex >= 0 && routeIndex < routeCount - 1;
        }
        resetProductButton.active = editable && hasModifiableRoute(product);
        resetAllButton.active = editable && hasAnyModifiableRoute();
        productFilterButton.active = !snapshot.isEmpty();
        globalAmountField.active = editable && isConfigAvailable() && hasAnyModifiableRoute();
        routeAmountField.active = editable && hasRoute && route.isModifiable();
        profileModeButton.active = editable && isConfigAvailable() && !snapshot.isEmpty();
        routeFilterModeButton.visible = configType.isRouteFilterMutable() && !snapshot.isEmpty();
        routeFilterModeButton.active = editable && configType.isRouteFilterMutable() && isConfigAvailable() && !snapshot.isEmpty();
        globalProfileButton.visible = !snapshot.isIndividualProfile() && !snapshot.isEmpty();
        globalProfileButton.active = editable && isConfigAvailable() && !snapshot.isEmpty() && !snapshot.isIndividualProfile();
        updateProfileButtonLayout();
        productFilterButton.setMessage(productFilterMode.getLabel().translate());
        toggleButton.setMessage(ctrlDown ? AELang.AE_RECIPE_CONFIG_DISABLE_PRODUCT.translate() :
              (route != null && route.isEnabled() ? AELang.AE_RECIPE_CONFIG_DISABLE : AELang.AE_RECIPE_CONFIG_ENABLE).translate());
        upButton.setMessage((ctrlDown ? AELang.AE_RECIPE_CONFIG_MOVE_TOP : AELang.AE_RECIPE_CONFIG_MOVE_UP).translate());
        downButton.setMessage((ctrlDown ? AELang.AE_RECIPE_CONFIG_MOVE_BOTTOM : AELang.AE_RECIPE_CONFIG_MOVE_DOWN).translate());
        profileModeButton.setMessage((snapshot.isIndividualProfile() ? AELang.AE_RECIPE_CONFIG_PROFILE_SINGLE : AELang.AE_RECIPE_CONFIG_PROFILE_GLOBAL).translate());
        routeFilterModeButton.setMessage(getRouteFilterModeLabel().translate());
        globalProfileButton.setMessage(AELang.AE_RECIPE_CONFIG_PROFILE_SLOT.translate(snapshot.getGlobalProfileSlot()));
        resetAllButton.setMessage(GuiScreen.isShiftKeyDown()
              ? (areAllRoutesEnabled() ? AELang.AE_RECIPE_CONFIG_DISABLE_ALL : AELang.AE_RECIPE_CONFIG_ENABLE_ALL).translate()
              : AELang.AE_RECIPE_CONFIG_RESET_ALL.translate());
        syncAmountFields(route);
    }

    private void updateProfileButtonLayout() {
        if (configType.isRouteFilterMutable()) {
            if (snapshot.isIndividualProfile()) {
                moveElementToX(routeFilterModeButton, getGuiLeft() + relativeX + PROFILE_MODE_BUTTON_X);
                routeFilterModeButton.setWidth(PROFILE_MODE_BUTTON_WIDTH);
            } else {
                moveElementToX(routeFilterModeButton, getGuiLeft() + relativeX + ROUTE_FILTER_MODE_BUTTON_X);
                routeFilterModeButton.setWidth(ROUTE_FILTER_MODE_BUTTON_WIDTH);
            }
        }
        moveElementToX(globalProfileButton, getGuiLeft() + relativeX + PROFILE_MODE_BUTTON_X);
        globalProfileButton.setWidth(PROFILE_MODE_BUTTON_WIDTH);
    }

    private void moveElementToX(GuiElement element, int targetX) {
        int delta = targetX - element.getX();
        if (delta != 0) {
            element.move(delta, 0);
        }
    }

    private void drawLockedRouteInner(int x, int y, int width, int height) {
        GuiUtils.fill(x + 2, y + 2, x + width - 2, y + height - 2, ROW_LOCKED_INNER_COLOR);
        GuiUtils.drawOutline(x + 2, y + 2, width - 4, height - 4, ROW_LOCKED_INNER_OUTLINE_COLOR);
    }

    private AELang getRouteFilterModeLabel() {
        return snapshot.getRouteFilterMode() == AERecipeProfile.RouteFilterMode.WHITELIST ? AELang.AE_RECIPE_CONFIG_FILTER_WHITELIST :
              AELang.AE_RECIPE_CONFIG_FILTER_BLACKLIST;
    }

    private boolean areAllRoutesEnabled() {
        if (snapshot.isEmpty()) {
            return false;
        }
        boolean foundModifiable = false;
        for (Product product : snapshot.getProducts()) {
            for (Route route : product.getRoutes()) {
                if (route.isModifiable()) {
                    foundModifiable = true;
                    if (!route.isEnabled()) {
                        return false;
                    }
                }
            }
        }
        return foundModifiable;
    }

    private int getModifiableProductIndex(@Nullable Product product) {
        if (product == null) {
            return -1;
        }
        int index = 0;
        for (Product candidate : snapshot.getProducts()) {
            if (!hasModifiableRoute(candidate)) {
                continue;
            }
            if (product.getOutputKey().equals(candidate.getOutputKey())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private int getModifiableProductCount() {
        int count = 0;
        for (Product product : snapshot.getProducts()) {
            if (hasModifiableRoute(product)) {
                count++;
            }
        }
        return count;
    }

    private int getModifiableRouteIndex(@Nullable Product product, @Nullable Route route) {
        if (product == null || route == null || !route.isModifiable()) {
            return -1;
        }
        int index = 0;
        for (Route candidate : product.getRoutes()) {
            if (!candidate.isModifiable()) {
                continue;
            }
            if (route.getRouteKey().equals(candidate.getRouteKey())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private int getModifiableRouteCount(@Nullable Product product) {
        if (product == null) {
            return 0;
        }
        int count = 0;
        for (Route route : product.getRoutes()) {
            if (route.isModifiable()) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private Product getSelectedProduct() {
        if (selectedOutputKey == null) {
            return null;
        }
        for (Product product : getFilteredProducts()) {
            if (selectedOutputKey.equals(product.getOutputKey())) {
                return product;
            }
        }
        return null;
    }

    private List<Product> getFilteredProducts() {
        List<Product> products = snapshot.getProducts();
        if ((productFilterMode == ProductFilterMode.ALL && productSearch.isEmpty()) || products.isEmpty()) {
            return products;
        }
        List<Product> filtered = new ArrayList<>();
        for (Product product : products) {
            if (productFilterMode.matches(product) && matchesSearch(product)) {
                filtered.add(product);
            }
        }
        return filtered;
    }

    private boolean matchesSearch(Product product) {
        for (ItemStack stack : product.getOutputStacks()) {
            if (stack.getDisplayName().toLowerCase(Locale.ROOT).contains(productSearch)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProductEnabled(Product product) {
        for (Route route : product.getRoutes()) {
            if (route.isModifiable() && route.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    private boolean isProductPartiallyEnabled(Product product) {
        boolean hasEnabled = false;
        boolean hasDisabled = false;
        for (Route route : product.getRoutes()) {
            if (!route.isModifiable()) {
                continue;
            }
            if (route.isEnabled()) {
                hasEnabled = true;
            } else {
                hasDisabled = true;
            }
            if (hasEnabled && hasDisabled) {
                return true;
            }
        }
        return false;
    }

    private boolean hasModifiableRoute(@Nullable Product product) {
        if (product == null) {
            return false;
        }
        for (Route route : product.getRoutes()) {
            if (route.isModifiable()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnmodifiableRoute(@Nullable Product product) {
        if (product == null) {
            return false;
        }
        for (Route route : product.getRoutes()) {
            if (!route.isModifiable()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyModifiableRoute() {
        for (Product product : snapshot.getProducts()) {
            if (hasModifiableRoute(product)) {
                return true;
            }
        }
        return false;
    }

    private int getProductEnabledIndicatorColor(Product product) {
        if (isProductPartiallyEnabled(product)) {
            return INDICATOR_PARTIAL;
        }
        return isProductEnabled(product) ? INDICATOR_ENABLED : INDICATOR_DISABLED;
    }

    private AELang getProductEnabledIndicatorLang(Product product) {
        if (!hasModifiableRoute(product)) {
            return AELang.AE_RECIPE_CONFIG_ROUTE_SELF_REFERENTIAL;
        }
        if (isProductPartiallyEnabled(product)) {
            return AELang.AE_RECIPE_CONFIG_ROUTE_PARTIAL;
        }
        return isProductEnabled(product) ? AELang.AE_RECIPE_CONFIG_ROUTE_ENABLED : AELang.AE_RECIPE_CONFIG_ROUTE_DISABLED;
    }

    @Nullable
    private Route getSelectedRoute() {
        Product product = getSelectedProduct();
        if (product == null || selectedRouteKey == null) {
            return null;
        }
        for (Route route : product.getRoutes()) {
            if (selectedRouteKey.equals(route.getRouteKey())) {
                return route;
            }
        }
        return null;
    }

    private void sendRouteAction(RecipeConfigPacket packetType, int direction) {
        if (!canEditConfiguration()) {
            return;
        }
        Product product = getSelectedProduct();
        Route route = getSelectedRoute();
        if (product != null && route != null && route.isModifiable()) {
            MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, packetType, product.getOutputKey(), route.getRouteKey(), direction));
        }
    }

    private void sendToggleAction() {
        if (GuiScreen.isCtrlKeyDown()) {
            sendProductAction(RecipeConfigPacket.DISABLE_PRODUCT);
        } else {
            sendRouteAction(RecipeConfigPacket.TOGGLE_ROUTE, 0);
        }
    }

    private void sendMoveAction(int direction) {
        if (GuiScreen.isCtrlKeyDown()) {
            sendMoveToEdgeAction(direction < 0);
            return;
        }
        if (selectionFocus == SelectionFocus.PRODUCT) {
            sendProductAction(RecipeConfigPacket.MOVE_PRODUCT, direction);
        } else {
            sendRouteAction(RecipeConfigPacket.MOVE_ROUTE, direction);
        }
    }

    private void sendMoveToEdgeAction(boolean top) {
        if (selectionFocus == SelectionFocus.PRODUCT) {
            sendProductAction(top ? RecipeConfigPacket.MOVE_PRODUCT_TO_TOP : RecipeConfigPacket.MOVE_PRODUCT_TO_BOTTOM);
        } else {
            sendRouteAction(top ? RecipeConfigPacket.MOVE_ROUTE_TO_TOP : RecipeConfigPacket.MOVE_ROUTE_TO_BOTTOM, 0);
        }
    }

    private void sendProductAction(RecipeConfigPacket packetType) {
        sendProductAction(packetType, 0);
    }

    private void sendProductAction(RecipeConfigPacket packetType, int direction) {
        if (!canEditConfiguration()) {
            return;
        }
        String outputKey = selectedOutputKey == null ? "" : selectedOutputKey;
        MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, packetType, outputKey, "", direction));
    }

    private void sendProfileModeAction() {
        if (canEditConfiguration() && !snapshot.isEmpty()) {
            MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, RecipeConfigPacket.TOGGLE_PROFILE_MODE, "", "", 0));
        }
    }

    private void sendRouteFilterModeAction() {
        if (configType.isRouteFilterMutable() && canEditConfiguration() && !snapshot.isEmpty()) {
            MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, RecipeConfigPacket.TOGGLE_ROUTE_FILTER_MODE, "", "", 0));
        }
    }

    private void sendGlobalProfileAction(int direction) {
        if (canEditConfiguration() && !snapshot.isEmpty() && !snapshot.isIndividualProfile()) {
            MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, RecipeConfigPacket.CYCLE_GLOBAL_PROFILE, "", "", direction));
        }
    }

    private void sendResetAllAction() {
        if (!canEditConfiguration()) {
            return;
        }
        if (GuiScreen.isShiftKeyDown()) {
            MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, RecipeConfigPacket.SET_ALL_ROUTES_ENABLED, "", "",
                  areAllRoutesEnabled() ? 0 : 1));
        } else {
            sendProductAction(RecipeConfigPacket.RESET_ALL);
        }
    }

    private void sendGlobalCraftAmountAction() {
        if (!canEditConfiguration() || snapshot.isEmpty()) {
            return;
        }
        int amount = parseCraftAmount(globalAmountField.getText(), AERecipeProfile.DEFAULT_CRAFT_AMOUNT, snapshot.getMaxCraftAmount());
        MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, RecipeConfigPacket.SET_GLOBAL_CRAFT_AMOUNT, "", "", 0, amount));
    }

    private void sendRouteCraftAmountAction() {
        if (!canEditConfiguration()) {
            return;
        }
        Product product = getSelectedProduct();
        Route route = getSelectedRoute();
        if (product == null || route == null || !route.isModifiable()) {
            return;
        }
        if (routeAmountField.isEmpty()) {
            MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, RecipeConfigPacket.CLEAR_ROUTE_CRAFT_AMOUNT,
                  product.getOutputKey(), route.getRouteKey(), 0));
        } else {
            int amount = parseCraftAmount(routeAmountField.getText(), snapshot.getCraftAmount(), route.getMaxCraftAmount());
            MEKCeuAEUpgrade.packetHandler.sendToServer(new AERecipeConfigMessage(coord, configType, RecipeConfigPacket.SET_ROUTE_CRAFT_AMOUNT,
                  product.getOutputKey(), route.getRouteKey(), 0, amount));
        }
    }

    private boolean canEditConfiguration() {
        return snapshot.isEditable() && isConfigAvailable();
    }

    private int parseCraftAmount(String text, int fallback, int maxCraftAmount) {
        int clampedFallback = AERecipeProfile.clampCraftAmount(fallback, maxCraftAmount);
        if (text == null || text.isEmpty()) {
            return clampedFallback;
        }
        try {
            long parsed = Long.parseLong(text);
            if (parsed > AERecipeProfile.MAX_CRAFT_AMOUNT) {
                return AERecipeProfile.clampCraftAmount(AERecipeProfile.MAX_CRAFT_AMOUNT, maxCraftAmount);
            }
            return AERecipeProfile.clampCraftAmount((int) parsed, maxCraftAmount);
        } catch (NumberFormatException ignored) {
            return clampedFallback;
        }
    }

    private boolean isPositiveIntegerInput(char c, int keyCode) {
        return Character.isDigit(c) || GuiMekanism.isTextboxKey(c, keyCode);
    }

    private boolean isPositiveIntegerText(String text) {
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            return text.isEmpty();
        }
        try {
            long amount = Long.parseLong(text);
            return amount >= AERecipeProfile.DEFAULT_CRAFT_AMOUNT && amount <= AERecipeProfile.MAX_CRAFT_AMOUNT;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void syncAmountFields(@Nullable Route route) {
        if (!globalAmountField.isFocused()) {
            String globalText = Integer.toString(snapshot.getCraftAmount());
            if (!globalText.equals(globalAmountField.getText())) {
                globalAmountField.setTextSilently(globalText);
            }
        }
        if (!routeAmountField.isFocused()) {
            String routeText = route != null && route.hasCraftAmountOverride() ? Integer.toString(route.getCraftAmount()) : "";
            if (!routeText.equals(routeAmountField.getText())) {
                routeAmountField.setTextSilently(routeText);
            }
        }
    }

    private void cycleProductFilterMode() {
        productFilterMode = productFilterMode.next(configType == AERecipeConfigType.CRAFTING);
        productList.resetScroll();
        routeList.resetScroll();
        validateSelection();
        updateButtons();
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    private class ProductScrollList extends GuiScrollList {

        private ProductScrollList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, ROW_HEIGHT, GuiInnerScreen.SCREEN, GuiInnerScreen.SCREEN_SIZE);
        }

        @Override
        public void resetScroll() {
            scroll = 0;
        }

        @Override
        protected int getMaxElements() {
            return getFilteredProducts().size();
        }

        @Override
        public boolean hasSelection() {
            return getSelectedProduct() != null;
        }

        @Override
        protected void setSelected(int index) {
            List<Product> products = getFilteredProducts();
            if (index >= 0 && index < products.size()) {
                selectProduct(products.get(index));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (canEditConfiguration() && button == 1 && GuiScreen.isShiftKeyDown() && isMouseOverRows(mouseX, mouseY)) {
                Product product = getHoveredProduct((int) mouseY);
                if (product != null && hasModifiableRoute(product)) {
                    selectProduct(product);
                    sendProductAction(RecipeConfigPacket.TOGGLE_PRODUCT);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void clearSelection() {
            selectedOutputKey = null;
            selectedRouteKey = null;
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            List<Product> products = getFilteredProducts();
            int currentSelection = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(), products.size() - currentSelection));
            for (int i = 0; i < max; i++) {
                Product product = products.get(currentSelection + i);
                int y = relativeY + 1 + i * elementHeight;
                boolean selected = product.getOutputKey().equals(selectedOutputKey);
                boolean hovered = mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 && mouseY >= getY() + 1 + i * elementHeight
                      && mouseY < getY() + 1 + (i + 1) * elementHeight;
                boolean modifiable = hasModifiableRoute(product);
                drawRowSelection(relativeX + 1, y, barXShift - 2, elementHeight, selected, selectionFocus == SelectionFocus.PRODUCT, hovered);
                if (hasUnmodifiableRoute(product)) {
                    drawLockedRouteInner(relativeX + 1, y, barXShift - 2, elementHeight);
                }
                int indicatorX = relativeX + barXShift - 10;
                if (modifiable) {
                    GuiUtils.fill(indicatorX, y + 4, indicatorX + INDICATOR_SIZE, y + 4 + INDICATOR_SIZE, getProductEnabledIndicatorColor(product));
                }
                if (product.hasMultipleRoutes()) {
                    GuiUtils.fill(indicatorX, y + 12, indicatorX + INDICATOR_SIZE, y + 12 + INDICATOR_SIZE, INDICATOR_MULTI_ROUTE);
                }
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            List<Product> products = getFilteredProducts();
            int currentSelection = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(), products.size() - currentSelection));
            for (int i = 0; i < max; i++) {
                Product product = products.get(currentSelection + i);
                int rowY = relativeY + 4 + i * elementHeight;

                drawScrollingStackStrip(product.getOutputStacks(), relativeX + 4, rowY, 18, 16);
                drawSmoothScrollingString(new TextComponentString(product.getOutputStack().getDisplayName()), relativeX + 25, rowY, barXShift - 38, 16,
                      screenTextColor());

            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            Product product = getHoveredProduct(mouseY);
            if (product == null) {
                return;
            }
            int visibleIndex = getHoveredVisibleIndex(mouseY);
            if (visibleIndex == -1) {
                return;
            }
            int indicatorX = getX() + barXShift - 10;
            if (mouseX >= indicatorX && mouseX < indicatorX + INDICATOR_SIZE) {
                int relativeMouseY = mouseY - getY() - 1;
                int rowY = relativeMouseY % elementHeight;
                if (hasModifiableRoute(product) && rowY >= 4 && rowY < 4 + INDICATOR_SIZE) {
                    displayTooltip(getProductEnabledIndicatorLang(product).translate(), mouseX, mouseY);
                    return;
                } else if (product.hasMultipleRoutes() && rowY >= 12 && rowY < 12 + INDICATOR_SIZE) {
                    displayTooltip(AELang.AE_RECIPE_CONFIG_MULTI_ROUTE.translate(), mouseX, mouseY);
                    return;
                }
            }
            List<ItemStack> outputs = product.getOutputStacks();
            ItemStack hoveredStack = getHoveredScrollingStack(outputs, mouseX, mouseY, relativeX + 4, relativeY + 4 + visibleIndex * elementHeight, 18, 16);
            if (hoveredStack != null) {
                gui().renderItemTooltip(hoveredStack, mouseX, mouseY);
            } else if (hasUnmodifiableRoute(product)) {
                displayTooltip(AELang.AE_RECIPE_CONFIG_ROUTE_SELF_REFERENTIAL.translate(), mouseX, mouseY);
            } else if (outputs.size() > 1) {
                displayTooltips(getStackNames(outputs), mouseX, mouseY);
            } else if (product.hasMultipleRoutes()) {
                displayTooltip(AELang.AE_RECIPE_CONFIG_MULTI_ROUTE.translate(), mouseX, mouseY);
            }
        }

        @Nullable
        private Product getHoveredProduct(int mouseY) {
            int currentSelection = getCurrentSelection();
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0) {
                return null;
            }
            int index = currentSelection + relativeMouseY / elementHeight;
            List<Product> products = getFilteredProducts();
            return index >= 0 && index < products.size() ? products.get(index) : null;
        }

        private int getHoveredVisibleIndex(int mouseY) {
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0 || relativeMouseY >= height - 2) {
                return -1;
            }
            int visibleIndex = relativeMouseY / elementHeight;
            return visibleIndex >= 0 && visibleIndex < getFocusedElements() ? visibleIndex : -1;
        }

        private void selectProduct(Product product) {
            selectionFocus = SelectionFocus.PRODUCT;
            selectedOutputKey = product.getOutputKey();
            selectedRouteKey = product.getRoutes().isEmpty() ? null : product.getRoutes().get(0).getRouteKey();
            routeList.resetScroll();
            updateButtons();
        }

        private boolean isMouseOverRows(double mouseX, double mouseY) {
            return mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 && mouseY >= getY() + 1 && mouseY < getY() + height - 1;
        }
    }

    private class RouteScrollList extends GuiScrollList {

        private RouteScrollList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, ROW_HEIGHT, GuiInnerScreen.SCREEN, GuiInnerScreen.SCREEN_SIZE);
        }

        @Override
        public void resetScroll() {
            scroll = 0;
        }

        private List<Route> getRoutes() {
            Product product = getSelectedProduct();
            return product == null ? new ArrayList<>() : product.getRoutes();
        }

        @Override
        protected int getMaxElements() {
            return getRoutes().size();
        }

        @Override
        public boolean hasSelection() {
            return getSelectedRoute() != null;
        }

        @Override
        protected void setSelected(int index) {
            List<Route> routes = getRoutes();
            if (index >= 0 && index < routes.size()) {
                selectRoute(routes.get(index));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (canEditConfiguration() && button == 1 && GuiScreen.isShiftKeyDown() && isMouseOverRows(mouseX, mouseY)) {
                Route route = getHoveredRoute((int) mouseY);
                if (route != null && route.isModifiable()) {
                    selectRoute(route);
                    sendRouteAction(RecipeConfigPacket.TOGGLE_ROUTE, 0);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void clearSelection() {
            selectedRouteKey = null;
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            List<Route> routes = getRoutes();
            int currentSelection = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(), routes.size() - currentSelection));
            for (int i = 0; i < max; i++) {
                Route route = routes.get(currentSelection + i);
                int y = relativeY + 1 + i * elementHeight;
                boolean selected = route.getRouteKey().equals(selectedRouteKey);
                boolean hovered = mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 && mouseY >= getY() + 1 + i * elementHeight
                      && mouseY < getY() + 1 + (i + 1) * elementHeight;
                drawRowSelection(relativeX + 1, y, barXShift - 2, elementHeight, selected, selectionFocus == SelectionFocus.ROUTE, hovered);
                if (!route.isModifiable()) {
                    drawLockedRouteInner(relativeX + 1, y, barXShift - 2, elementHeight);
                } else {
                    GuiUtils.fill(relativeX + barXShift - 10, y + 4, relativeX + barXShift - 4, y + 10,
                          route.isEnabled() ? 0xFF57C78B : 0xFFC75656);
                }
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            List<Route> routes = getRoutes();
            int currentSelection = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(), routes.size() - currentSelection));
            for (int i = 0; i < max; i++) {
                Route route = routes.get(currentSelection + i);
                int y = relativeY + 4 + i * elementHeight;
                List<ItemStack> inputs = route.getInputStacks();
                List<ItemStack> outputs = route.getOutputStacks();
                int textColor = !route.isModifiable() ? 0xE6B0B0 : route.isEnabled() ? screenTextColor() : 0x8A8A8A;
                int inputX = relativeX + 4;
                int inputWidth = 38;
                int outputWidth = 38;
                int outputX = relativeX + barXShift - 54;
                int textX = inputX + inputWidth + 4;
                int textWidth = Math.max(20, outputX - textX - 4);
                drawScrollingStackStrip(inputs, inputX, y, inputWidth, 16);
                drawSmoothScrollingString(new TextComponentString(getRouteInputDisplay(inputs)), textX, y, textWidth, 16, textColor);
                drawScrollingStackStrip(outputs, outputX, y, outputWidth, 16);
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            Route route = getHoveredRoute(mouseY);
            if (route == null) {
                return;
            }
            int visibleIndex = getHoveredVisibleIndex(mouseY);
            if (visibleIndex == -1) {
                return;
            }
            int rowY = relativeY + 4 + visibleIndex * elementHeight;
            List<ItemStack> inputs = route.getInputStacks();
            ItemStack hoveredInput = getHoveredScrollingStack(inputs, mouseX, mouseY, relativeX + 4, rowY, 38, 16);
            if (hoveredInput != null) {
                gui().renderItemTooltip(hoveredInput, mouseX, mouseY);
                return;
            }
            List<ItemStack> outputs = route.getOutputStacks();
            ItemStack hoveredOutput = getHoveredScrollingStack(outputs, mouseX, mouseY, relativeX + barXShift - 54, rowY, 38, 16);
            if (hoveredOutput != null) {
                gui().renderItemTooltip(hoveredOutput, mouseX, mouseY);
                return;
            }
            if (inputs.size() > 1 || outputs.size() > 1) {
                List<String> tooltips = new ArrayList<>();
                for (ItemStack input : inputs) {
                    tooltips.add(input.getDisplayName());
                }
                tooltips.add("->");
                for (ItemStack output : outputs) {
                    tooltips.add(output.getDisplayName());
                }
                tooltips.add(getRouteStatusLang(route).translate().getFormattedText());
                displayTooltips(tooltips, mouseX, mouseY);
            } else {
                displayTooltip(getRouteStatusLang(route).translate(), mouseX, mouseY);
            }
        }

        @Nullable
        private Route getHoveredRoute(int mouseY) {
            List<Route> routes = getRoutes();
            int currentSelection = getCurrentSelection();
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0) {
                return null;
            }
            int index = currentSelection + relativeMouseY / elementHeight;
            return index >= 0 && index < routes.size() ? routes.get(index) : null;
        }

        private int getHoveredVisibleIndex(int mouseY) {
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0 || relativeMouseY >= height - 2) {
                return -1;
            }
            int visibleIndex = relativeMouseY / elementHeight;
            return visibleIndex >= 0 && visibleIndex < getFocusedElements() ? visibleIndex : -1;
        }

        private String getRouteInputDisplay(List<ItemStack> inputs) {
            if (inputs.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < inputs.size(); i++) {
                if (i > 0) {
                    builder.append(" + ");
                }
                builder.append(inputs.get(i).getDisplayName());
            }
            return builder.toString();
        }

        private void selectRoute(Route route) {
            selectionFocus = SelectionFocus.ROUTE;
            selectedRouteKey = route.getRouteKey();
            updateButtons();
        }

        private boolean isMouseOverRows(double mouseX, double mouseY) {
            return mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 && mouseY >= getY() + 1 && mouseY < getY() + height - 1;
        }
    }

    private AELang getRouteStatusLang(Route route) {
        if (!route.isModifiable()) {
            return AELang.AE_RECIPE_CONFIG_ROUTE_SELF_REFERENTIAL;
        }
        return route.isEnabled() ? AELang.AE_RECIPE_CONFIG_ROUTE_ENABLED : AELang.AE_RECIPE_CONFIG_ROUTE_DISABLED;
    }

    private List<String> getStackNames(List<ItemStack> stacks) {
        List<String> names = new ArrayList<>();
        for (ItemStack stack : stacks) {
            names.add(stack.getDisplayName());
        }
        return names;
    }

    private enum ProductFilterMode {
        ALL(AELang.AE_RECIPE_CONFIG_PRODUCTS_ALL) {
            @Override
            boolean matches(Product product) {
                return true;
            }
        },
        MULTI_ROUTE(AELang.AE_RECIPE_CONFIG_PRODUCTS_MULTI) {
            @Override
            boolean matches(Product product) {
                return product.hasMultipleRoutes();
            }
        },
        SINGLE_ROUTE(AELang.AE_RECIPE_CONFIG_PRODUCTS_SINGLE) {
            @Override
            boolean matches(Product product) {
                return !product.hasMultipleRoutes();
            }
        },
        SELF_REFERENTIAL(AELang.AE_RECIPE_CONFIG_PRODUCTS_SELF_REFERENTIAL) {
            @Override
            boolean matches(Product product) {
                for (Route route : product.getRoutes()) {
                    if (!route.isModifiable()) {
                        return true;
                    }
                }
                return false;
            }
        };

        private final AELang label;

        ProductFilterMode(AELang label) {
            this.label = label;
        }

        private AELang getLabel() {
            return label;
        }

        private ProductFilterMode next(boolean includeSelfReferential) {
            ProductFilterMode[] modes = values();
            ProductFilterMode next = modes[(ordinal() + 1) % modes.length];
            return !includeSelfReferential && next == SELF_REFERENTIAL ? ALL : next;
        }

        abstract boolean matches(Product product);
    }

    private enum SelectionFocus {
        PRODUCT,
        ROUTE
    }
}
