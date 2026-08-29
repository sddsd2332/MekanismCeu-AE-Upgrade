package mekceuaeupgrade.mixin.mekanism;

import mekanism.api.gas.GasStack;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.ingredients.IMekanismIngredient;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GasConversionHandler.class, remap = false)
public abstract class MixinGasConversionHandler {

    @Inject(
          method = "addGasMapping(Lmekanism/common/recipe/ingredients/IMekanismIngredient;Lmekanism/api/gas/GasStack;)Z",
          at = @At("RETURN")
    )
    private static void mekceuaeupgrade$invalidateAfterAdd(IMekanismIngredient<ItemStack> ingredient, GasStack gasStack,
          CallbackInfoReturnable<Boolean> cir) {
        if (gasStack != null && gasStack.getGas() != null && gasStack.amount > 0) {
            RecipeHandler.markRecipeCachesInvalid();
        }
    }

    @Inject(
          method = "removeGasMapping(Lmekanism/common/recipe/ingredients/IMekanismIngredient;Lmekanism/api/gas/GasStack;)I",
          at = @At("RETURN")
    )
    private static void mekceuaeupgrade$invalidateAfterRemove(IMekanismIngredient<ItemStack> ingredient, GasStack gasStack,
          CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue() > 0) {
            RecipeHandler.markRecipeCachesInvalid();
        }
    }

    @Inject(method = "removeAllGasMappings()V", at = @At("RETURN"))
    private static void mekceuaeupgrade$invalidateAfterRemoveAll(CallbackInfo ci) {
        RecipeHandler.markRecipeCachesInvalid();
    }
}
