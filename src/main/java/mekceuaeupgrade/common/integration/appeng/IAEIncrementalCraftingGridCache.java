package mekceuaeupgrade.common.integration.appeng;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.storage.data.IAEItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Bridge implemented by the AE crafting grid mixin for recipes managed outside AE's full rebuild path.
 */
public interface IAEIncrementalCraftingGridCache {

    IGrid mekceuaeupgrade$getGrid();

    void mekceuaeupgrade$setProviderRecipes(ICraftingProvider provider, ICraftingMedium medium,
          Collection<? extends ICraftingPatternDetails> recipes);

    List<IAEItemStack> mekceuaeupgrade$removeProvider(ICraftingProvider provider);

}
