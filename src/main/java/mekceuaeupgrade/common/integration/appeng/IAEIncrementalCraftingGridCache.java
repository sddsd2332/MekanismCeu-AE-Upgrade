package mekceuaeupgrade.common.integration.appeng;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

import java.util.Collection;

/**
 * Bridge implemented by the AE crafting grid mixin for recipes managed outside AE's full rebuild path.
 */
public interface IAEIncrementalCraftingGridCache {

    void mekceuaeupgrade$setProviderRecipes(ICraftingProvider provider, ICraftingMedium medium,
          Collection<? extends ICraftingPatternDetails> recipes);

    void mekceuaeupgrade$removeProvider(ICraftingProvider provider);

}
