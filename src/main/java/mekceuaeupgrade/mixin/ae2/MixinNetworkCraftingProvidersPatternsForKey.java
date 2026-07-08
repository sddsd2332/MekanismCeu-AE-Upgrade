package mekceuaeupgrade.mixin.ae2;

import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import ae2.api.crafting.IPatternDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(targets = "ae2.me.service.helpers.NetworkCraftingProviders$PatternsForKey", remap = false)
public abstract class MixinNetworkCraftingProvidersPatternsForKey {

    @Inject(method = "getSortedPatterns", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$sortAEUpgradePatterns(CallbackInfoReturnable<List<IPatternDetails>> cir) {
        List<IPatternDetails> patterns = cir.getReturnValue();
        if (patterns == null || patterns.size() < 2 || !mekceuaeupgrade$hasAEUpgradePattern(patterns)) {
            return;
        }
        List<IPatternDetails> reordered = new ArrayList<>(patterns);
        int blockStart = -1;
        for (int i = 0; i <= reordered.size(); i++) {
            boolean aeUpgradePattern = i < reordered.size() && reordered.get(i) instanceof AEExposedRecipe;
            if (aeUpgradePattern && blockStart < 0) {
                blockStart = i;
            } else if (!aeUpgradePattern && blockStart >= 0) {
                mekceuaeupgrade$sortAEUpgradeBlock(reordered, blockStart, i);
                blockStart = -1;
            }
        }
        cir.setReturnValue(List.copyOf(reordered));
    }

    private static boolean mekceuaeupgrade$hasAEUpgradePattern(List<IPatternDetails> patterns) {
        for (IPatternDetails pattern : patterns) {
            if (pattern instanceof AEExposedRecipe) {
                return true;
            }
        }
        return false;
    }

    private static void mekceuaeupgrade$sortAEUpgradeBlock(List<IPatternDetails> patterns, int fromIndex, int toIndex) {
        if (toIndex - fromIndex < 2) {
            return;
        }
        patterns.subList(fromIndex, toIndex).sort(Comparator
              .comparingInt((IPatternDetails pattern) -> ((AEExposedRecipe) pattern).getPriority())
              .reversed());
    }
}
