package mekceuaeupgrade.mixin.mekanism;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekceuaeupgrade.client.gui.GuiAEAutoProcessingRecipeConfigWindowTab;
import mekceuaeupgrade.client.gui.GuiAERecipeConfigWindowTab;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = GuiMekanismTile.class, remap = false)
public abstract class MixinGuiMekanismTile<TILE extends TileEntityContainerBlock, CONTAINER extends Container> extends GuiMekanism<CONTAINER> {

    @Shadow
    protected TILE tileEntity;

    @Unique
    @Nullable
    private GuiAERecipeConfigWindowTab mekceuaeupgrade$aeRecipeConfigWindowTab;

    @Unique
    @Nullable
    private GuiAERecipeConfigWindowTab mekceuaeupgrade$aeAutoProcessingRecipeConfigWindowTab;

    protected MixinGuiMekanismTile(CONTAINER container) {
        super(container);
    }

    @Inject(method = "addGenericTabs", at = @At("TAIL"))
    private void mekceuaeupgrade$addAERecipeConfigTab(CallbackInfo ci) {
        if (GuiAERecipeConfigWindowTab.shouldAdd(tileEntity)) {
            mekceuaeupgrade$aeRecipeConfigWindowTab = addButton(new GuiAERecipeConfigWindowTab(this, tileEntity, () -> mekceuaeupgrade$aeRecipeConfigWindowTab));
        }
        if (GuiAEAutoProcessingRecipeConfigWindowTab.shouldAdd(tileEntity)) {
            mekceuaeupgrade$aeAutoProcessingRecipeConfigWindowTab = addButton(new GuiAEAutoProcessingRecipeConfigWindowTab(this, tileEntity,
                  () -> mekceuaeupgrade$aeAutoProcessingRecipeConfigWindowTab));
        }
    }
}
