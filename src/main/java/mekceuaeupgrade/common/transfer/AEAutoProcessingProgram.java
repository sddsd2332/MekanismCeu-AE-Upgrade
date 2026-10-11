package mekceuaeupgrade.common.transfer;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

/**
 * Per-host compiled recipe generation. Recipe order is fixed until identity/version changes.
 * Transfer journals are private to this program; neither capacity nor AE availability is cached.
 */
public final class AEAutoProcessingProgram {
    private final Entry[] entries;
    private final List<AEExposedRecipe> recipes;
    private boolean executing;

    public AEAutoProcessingProgram(List<AEExposedRecipe> recipes) {
        this.recipes = Collections.unmodifiableList(new ArrayList<>(recipes));
        entries = new Entry[this.recipes.size()];
        for (int i = 0; i < entries.length; i++) entries[i] = new Entry(this.recipes.get(i));
    }

    List<AEExposedRecipe> recipes() { return recipes; }
    int size() { return entries.length; }
    Entry entry(int index) { return entries[index]; }
    boolean enter() {
        if (executing) return false;
        executing = true;
        return true;
    }
    void exit() { executing = false; }

    static final class Entry {
        final AEExposedRecipe recipe;
        final AERecipeNetworkTransferPlan plan;
        // Bound retained search variants even for recipes with very large configured batches.
        private Map<Integer, Entry> batches;

        Entry(AEExposedRecipe recipe) {
            this.recipe = recipe;
            plan = AERecipeNetworkTransferPlan.fromRecipe(recipe);
        }

        Entry batch(int amount) {
            if (amount == recipe.getCraftAmount()) return this;
            if (batches == null) {
                batches = new LinkedHashMap<Integer, Entry>(16, 0.75F, true) {
                    @Override protected boolean removeEldestEntry(Map.Entry<Integer, AEAutoProcessingProgram.Entry> eldest) {
                        return size() > 16;
                    }
                };
            }
            Entry entry = batches.get(amount);
            if (entry == null) {
                entry = new Entry(recipe.withCraftAmount(amount));
                batches.put(amount, entry);
            }
            return entry;
        }
    }
}
