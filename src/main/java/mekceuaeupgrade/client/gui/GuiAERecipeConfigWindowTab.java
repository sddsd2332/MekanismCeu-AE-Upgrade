package mekceuaeupgrade.client.gui;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.item.AEUpgrade;
import mekceuaeupgrade.common.ui.AELang;
import mekceuaeupgrade.common.ui.AEUpgradeWindowTypes;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.window.GuiWindowCreatorTab;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiAERecipeConfigWindowTab extends GuiWindowCreatorTab<TileEntityContainerBlock, GuiAERecipeConfigWindowTab> {

    private static final ResourceLocation AE_RECIPE_CONFIG = MekanismUtils.getResource(MekanismUtils.ResourceType.TEXTURE_ITEMS, "craftingformulaencoded.png");
    private static final SelectedWindowData WINDOW_DATA = new SelectedWindowData(AEUpgradeWindowTypes.AE_RECIPE_CONFIG);

    private boolean windowOpen;

    public GuiAERecipeConfigWindowTab(IGuiWrapper gui, TileEntityContainerBlock tile, Supplier<GuiAERecipeConfigWindowTab> elementSupplier) {
        super(AE_RECIPE_CONFIG, gui, tile, gui.getWidth() + 24, 6, 26, 18, false, elementSupplier);
        visible = canOpen(tile);
        active = visible;
    }

    public static boolean shouldAdd(TileEntityContainerBlock tile) {
        return tile instanceof IAEUpgradeHost && tile instanceof IUpgradeTile upgradeTile && upgradeTile.supportsUpgrade(AEUpgrade.AE_CRAFTING);
    }

    public static boolean canOpen(TileEntityContainerBlock tile) {
        return tile instanceof IAEUpgradeHost && tile instanceof IUpgradeTile upgradeTile && upgradeTile.isUpgradeInstalled(AEUpgrade.AE_CRAFTING);
    }

    @Override
    public void tick() {
        super.tick();
        boolean shouldShow = canOpen(dataSource);
        visible = shouldShow;
        active = shouldShow && !windowOpen;
    }

    @Override
    public void openPinnedWindows() {
        if (canOpen(dataSource)) {
            super.openPinnedWindows();
        }
    }

    @Override
    protected void disableTab() {
        windowOpen = true;
        super.disableTab();
    }

    @Override
    protected Consumer<GuiWindow> getCloseListener() {
        return window -> {
            GuiAERecipeConfigWindowTab tab = getElementSupplier().get();
            tab.windowOpen = false;
            tab.active = tab.visible && canOpen(tab.dataSource);
        };
    }

    @Override
    protected Consumer<GuiWindow> getReAttachListener() {
        return window -> {
            GuiAERecipeConfigWindowTab tab = getElementSupplier().get();
            tab.windowOpen = true;
            tab.disableTab();
        };
    }

    @Override
    @Nullable
    protected Integer getTabColor() {
        return SpecialColors.TAB_CRAFTING_WINDOW.argb();
    }

    @Override
    protected ITextComponent getTooltipText() {
        return AELang.AE_RECIPE_CONFIG.translate();
    }

    @Override
    protected GuiWindow createWindow(SelectedWindowData windowData) {
        return new GuiAERecipeConfigWindow(gui(), (getGuiWidth() - GuiAERecipeConfigWindow.WIDTH) / 2, 18, dataSource, windowData);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return WINDOW_DATA;
    }
}
