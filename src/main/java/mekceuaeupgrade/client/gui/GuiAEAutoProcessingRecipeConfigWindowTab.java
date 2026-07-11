package mekceuaeupgrade.client.gui;

import mekceuaeupgrade.common.config.AERecipeConfigType;

import mekanism.client.gui.IGuiWrapper;
import mekanism.common.tile.prefab.TileEntityContainerBlock;

import java.util.function.Supplier;

public class GuiAEAutoProcessingRecipeConfigWindowTab extends GuiAERecipeConfigWindowTab {

    public GuiAEAutoProcessingRecipeConfigWindowTab(IGuiWrapper gui, TileEntityContainerBlock tile,
          Supplier<GuiAERecipeConfigWindowTab> elementSupplier) {
        super(gui, tile, elementSupplier, AERecipeConfigType.AUTO_PROCESSING, 6);
    }

    public static boolean shouldAdd(TileEntityContainerBlock tile) {
        return GuiAERecipeConfigWindowTab.shouldAdd(tile, AERecipeConfigType.AUTO_PROCESSING);
    }
}
