package mekceuaeupgrade.common.integration.appeng;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.storage.data.IAEItemStack;
import com.google.common.collect.ImmutableList;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Sidecar crafting index for AE upgrade recipes. Mutations are reconciled on the grid tick with a strict time budget,
 * while readers receive immutable snapshots suitable for AE's asynchronous crafting calculator.
 */
public final class AEIncrementalCraftingIndex {

    public static final long PUBLISH_BUDGET_NANOS = 1_000_000L;

    private static final Comparator<ICraftingPatternDetails> PATTERN_ORDER = (left, right) -> {
        if (left == right) {
            return 0;
        }
        int priority = Integer.compare(right.getPriority(), left.getPriority());
        if (priority != 0) {
            return priority;
        }
        if (left instanceof AEExposedRecipe && right instanceof AEExposedRecipe) {
            int route = ((AEExposedRecipe) left).getRecipeKey().getRouteKey()
                  .compareTo(((AEExposedRecipe) right).getRecipeKey().getRouteKey());
            if (route != 0) {
                return route;
            }
        }
        int hash = Integer.compare(left.hashCode(), right.hashCode());
        if (hash != 0) {
            return hash;
        }
        return Integer.compare(System.identityHashCode(left), System.identityHashCode(right));
    };

    private final LongSupplier nanoTime;
    private final IdentityHashMap<ICraftingProvider, ProviderState> providers = new IdentityHashMap<>();
    private final Map<ICraftingPatternDetails, PatternEntry> patterns = new HashMap<>();
    private final Map<IAEItemStack, List<PatternEntry>> outputs = new HashMap<>();
    private final Map<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftingByOutput = new HashMap<>();
    private final Map<IAEItemStack, MergedPatternSnapshot> mergedCraftingByOutput = new HashMap<>();
    private final ArrayDeque<PendingReconcile> pending = new ArrayDeque<>();
    private ImmutableList<IAEItemStack> craftableOutputs = ImmutableList.of();
    private boolean craftableOutputsDirty;

    public AEIncrementalCraftingIndex() {
        this(System::nanoTime);
    }

    AEIncrementalCraftingIndex(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    public synchronized void setProviderRecipes(ICraftingProvider provider, ICraftingMedium medium,
          Collection<? extends ICraftingPatternDetails> recipes) {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(medium);

        ProviderState state = providers.computeIfAbsent(provider, ProviderState::new);
        LinkedHashMap<ICraftingPatternDetails, ICraftingPatternDetails> desired = new LinkedHashMap<>();
        if (recipes != null) {
            for (ICraftingPatternDetails recipe : recipes) {
                if (recipe != null) {
                    desired.putIfAbsent(recipe, recipe);
                }
            }
        }

        boolean mediumChanged = state.desiredMedium != medium;
        Map<ICraftingPatternDetails, ICraftingPatternDetails> previousDesired = state.desired;
        state.desiredMedium = medium;
        state.desired = desired;
        state.removeWhenEmpty = false;

        for (ICraftingPatternDetails previous : previousDesired.keySet()) {
            ICraftingPatternDetails replacement = desired.get(previous);
            if (replacement == null || previous.getPriority() != replacement.getPriority() || mediumChanged) {
                queueReconcile(state, previous);
            }
        }
        for (ICraftingPatternDetails recipe : desired.keySet()) {
            ICraftingPatternDetails previous = previousDesired.get(recipe);
            PublishedPattern published = state.published.get(recipe);
            if (previous == null || previous.getPriority() != recipe.getPriority() || mediumChanged || published == null ||
                  published.publishedPriority != recipe.getPriority() || !published.snapshot.matches(recipe)) {
                queueReconcile(state, recipe);
            }
        }
    }

    /**
     * Removes a provider synchronously. Provider removal is a lifecycle operation rather than a
     * recipe update: waiting for the grid tick can leave a sidecar recipe visible after AE has
     * stopped ticking an empty grid.
     */
    public synchronized List<IAEItemStack> removeProvider(ICraftingProvider provider) {
        ProviderState state = providers.remove(provider);
        if (state == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<IAEItemStack> affectedOutputs = new LinkedHashSet<>();
        for (PublishedPattern published : state.published.values()) {
            affectedOutputs.addAll(published.entry.outputs);
        }
        for (ICraftingPatternDetails recipe : state.desired.values()) {
            affectedOutputs.addAll(normalizeOutputs(recipe));
        }
        Map<IAEItemStack, Boolean> previousCraftability = new LinkedHashMap<>();
        for (IAEItemStack output : affectedOutputs) {
            previousCraftability.put(output, isCraftable(output));
        }

        // A provider can have both additions and removals waiting in the bounded reconcile queue.
        // None of those operations may be allowed to resurrect the provider after this call.
        pending.removeIf(change -> change.state == state);
        state.queued.clear();
        state.desired.clear();

        for (PublishedPattern published : new ArrayList<>(state.published.values())) {
            removePublished(state, published);
        }
        state.published.clear();
        state.removeWhenEmpty = true;

        if (previousCraftability.isEmpty()) {
            return Collections.emptyList();
        }
        List<IAEItemStack> changed = new ArrayList<>();
        for (Map.Entry<IAEItemStack, Boolean> entry : previousCraftability.entrySet()) {
            boolean craftable = isCraftable(entry.getKey());
            if (craftable != entry.getValue()) {
                IAEItemStack stack = entry.getKey().copy();
                stack.reset();
                stack.setCraftable(craftable);
                changed.add(stack);
            }
        }
        return changed;
    }

    public synchronized List<IAEItemStack> processPendingChanges(Predicate<IAEItemStack> nativeCraftable) {
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }

        long started = nanoTime.getAsLong();
        long deadline = started + PUBLISH_BUDGET_NANOS;
        if (deadline < started) {
            deadline = Long.MAX_VALUE;
        }
        Map<IAEItemStack, Boolean> originalCraftability = new LinkedHashMap<>();

        while (!pending.isEmpty() && nanoTime.getAsLong() < deadline) {
            PendingReconcile change = pending.removeFirst();
            ProviderState state = change.state;
            state.queued.remove(change.recipe);
            for (IAEItemStack output : getAffectedOutputs(state, change.recipe)) {
                originalCraftability.putIfAbsent(output, isCraftable(output));
            }
            reconcile(state, change.recipe);
            cleanupProvider(state);
        }

        if (originalCraftability.isEmpty()) {
            return Collections.emptyList();
        }
        List<IAEItemStack> changed = new ArrayList<>();
        for (Map.Entry<IAEItemStack, Boolean> entry : originalCraftability.entrySet()) {
            boolean craftable = isCraftable(entry.getKey());
            if (craftable != entry.getValue() && (nativeCraftable == null || !nativeCraftable.test(entry.getKey()))) {
                IAEItemStack stack = entry.getKey().copy();
                stack.reset();
                stack.setCraftable(craftable);
                changed.add(stack);
            }
        }
        return changed;
    }

    public synchronized ImmutableList<IAEItemStack> getCraftableOutputs() {
        if (craftableOutputsDirty) {
            craftableOutputs = ImmutableList.copyOf(craftingByOutput.keySet());
            craftableOutputsDirty = false;
        }
        return craftableOutputs;
    }

    public synchronized ImmutableList<ICraftingPatternDetails> getCraftingFor(IAEItemStack output) {
        ImmutableList<ICraftingPatternDetails> result = craftingByOutput.get(output);
        return result == null ? ImmutableList.of() : result;
    }

    public synchronized ImmutableList<ICraftingPatternDetails> mergeCraftingFor(IAEItemStack output,
          Collection<ICraftingPatternDetails> nativePatterns) {
        ImmutableList<ICraftingPatternDetails> incrementalPatterns = craftingByOutput.get(output);
        if (incrementalPatterns == null || incrementalPatterns.isEmpty()) {
            if (nativePatterns == null) {
                return ImmutableList.of();
            }
            // Native AE already returns an immutable collection. Preserve it so ordinary
            // AE-only lookups do not pay for a copy through this sidecar.
            if (nativePatterns instanceof ImmutableList) {
                @SuppressWarnings("unchecked")
                ImmutableList<ICraftingPatternDetails> immutable = (ImmutableList<ICraftingPatternDetails>) nativePatterns;
                return immutable;
            }
            return ImmutableList.copyOf(nativePatterns);
        }
        if (nativePatterns == null || nativePatterns.isEmpty()) {
            return incrementalPatterns;
        }
        MergedPatternSnapshot cached = mergedCraftingByOutput.get(output);
        if (cached != null && cached.nativePatterns == nativePatterns && cached.incrementalPatterns == incrementalPatterns) {
            return cached.mergedPatterns;
        }
        ImmutableList<ICraftingPatternDetails> merged = mergePatterns(nativePatterns, incrementalPatterns);
        mergedCraftingByOutput.put(output, new MergedPatternSnapshot(nativePatterns, incrementalPatterns, merged));
        return merged;
    }

    public synchronized ImmutableList<ICraftingMedium> getMediums(ICraftingPatternDetails details) {
        PatternEntry entry = patterns.get(details);
        if (entry == null || entry.providers.isEmpty()) {
            return ImmutableList.of();
        }
        return entry.mediums;
    }

    public synchronized boolean isCraftable(IAEItemStack output) {
        List<PatternEntry> entries = outputs.get(output);
        return entries != null && containsPublishedPattern(entries);
    }

    public synchronized List<IAEItemStack> filterNativeCraftabilityChanges(Iterable<IAEItemStack> changes) {
        if (changes == null) {
            return Collections.emptyList();
        }
        List<IAEItemStack> filtered = new ArrayList<>();
        for (IAEItemStack change : changes) {
            if (change != null && (!change.isCraftable() && isCraftable(change))) {
                continue;
            }
            filtered.add(change);
        }
        return filtered;
    }

    public static ImmutableList<ICraftingPatternDetails> mergePatterns(Collection<ICraftingPatternDetails> nativePatterns,
          Collection<ICraftingPatternDetails> incrementalPatterns) {
        if (incrementalPatterns == null || incrementalPatterns.isEmpty()) {
            if (nativePatterns == null) {
                return ImmutableList.of();
            }
            if (nativePatterns instanceof ImmutableList) {
                @SuppressWarnings("unchecked")
                ImmutableList<ICraftingPatternDetails> immutable = (ImmutableList<ICraftingPatternDetails>) nativePatterns;
                return immutable;
            }
            return ImmutableList.copyOf(nativePatterns);
        }
        if (nativePatterns == null || nativePatterns.isEmpty()) {
            return immutableList(incrementalPatterns);
        }
        List<ICraftingPatternDetails> merged = new ArrayList<>((nativePatterns == null ? 0 : nativePatterns.size()) + incrementalPatterns.size());
        Set<ICraftingPatternDetails> seen = new HashSet<>();
        if (nativePatterns != null) {
            for (ICraftingPatternDetails pattern : nativePatterns) {
                if (seen.add(pattern)) {
                    merged.add(pattern);
                }
            }
        }
        for (ICraftingPatternDetails pattern : incrementalPatterns) {
            if (seen.add(pattern)) {
                merged.add(pattern);
            }
        }
        merged.sort(PATTERN_ORDER);
        return ImmutableList.copyOf(merged);
    }

    public static List<ICraftingMedium> mergeMediums(Collection<ICraftingMedium> nativeMediums,
          Collection<ICraftingMedium> incrementalMediums) {
        if (incrementalMediums == null || incrementalMediums.isEmpty()) {
            return nativeMediums == null ? ImmutableList.of() : list(nativeMediums);
        }
        if (nativeMediums == null || nativeMediums.isEmpty()) {
            return list(incrementalMediums);
        }
        List<ICraftingMedium> merged = new ArrayList<>((nativeMediums == null ? 0 : nativeMediums.size()) + incrementalMediums.size());
        Set<ICraftingMedium> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (nativeMediums != null) {
            for (ICraftingMedium medium : nativeMediums) {
                if (seen.add(medium)) {
                    merged.add(medium);
                }
            }
        }
        for (ICraftingMedium medium : incrementalMediums) {
            if (seen.add(medium)) {
                merged.add(medium);
            }
        }
        return ImmutableList.copyOf(merged);
    }

    @SuppressWarnings("unchecked")
    private static <T> ImmutableList<T> immutableList(Collection<T> values) {
        return values instanceof ImmutableList ? (ImmutableList<T>) values : ImmutableList.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Collection<T> values) {
        return values instanceof List ? (List<T>) values : ImmutableList.copyOf(values);
    }

    synchronized int getPendingChangeCount() {
        return pending.size();
    }

    synchronized int getPublishedRecipeCount(ICraftingProvider provider) {
        ProviderState state = providers.get(provider);
        return state == null ? 0 : state.published.size();
    }

    synchronized int getPublishedPatternCount() {
        return patterns.size();
    }

    private void reconcile(ProviderState state, ICraftingPatternDetails key) {
        ICraftingPatternDetails desired = state.desired.get(key);
        PublishedPattern published = state.published.get(key);
        if (desired == null) {
            if (published != null) {
                removePublished(state, published);
            }
            return;
        }
        if (published == null) {
            addPublished(state, desired);
            return;
        }
        replacePublished(state, published, desired);
    }

    private void addPublished(ProviderState state, ICraftingPatternDetails details) {
        PatternEntry entry = patterns.get(details);
        if (entry == null) {
            entry = new PatternEntry(details);
            patterns.put(details, entry);
        }
        PublishedPattern published = new PublishedPattern(entry, details, state.desiredMedium);
        entry.providers.put(state, published);
        state.published.put(details, published);
        refreshEntry(entry);
    }

    private void replacePublished(ProviderState state, PublishedPattern published, ICraftingPatternDetails desired) {
        PatternEntry entry = published.entry;
        state.published.remove(published.details);
        published.details = desired;
        published.medium = state.desiredMedium;
        published.publishedPriority = desired.getPriority();
        published.snapshot = PatternSnapshot.capture(desired);
        state.published.put(desired, published);
        refreshEntry(entry);
    }

    private void removePublished(ProviderState state, PublishedPattern published) {
        PatternEntry entry = published.entry;
        state.published.remove(published.details);
        entry.providers.remove(state);
        if (entry.providers.isEmpty()) {
            patterns.remove(entry.mapKey);
            for (IAEItemStack output : entry.outputs) {
                List<PatternEntry> entries = outputs.get(output);
                if (entries != null) {
                    entries.remove(entry);
                    if (entries.isEmpty()) {
                        outputs.remove(output);
                        craftableOutputsDirty = true;
                    }
                }
            }
            refreshOutputSnapshots(entry.outputs);
            return;
        }
        refreshEntry(entry);
    }

    private void refreshEntry(PatternEntry entry) {
        LinkedHashSet<IAEItemStack> previousOutputs = new LinkedHashSet<>(entry.outputs);
        LinkedHashSet<IAEItemStack> currentOutputs = new LinkedHashSet<>();
        for (PublishedPattern published : entry.providers.values()) {
            currentOutputs.addAll(published.snapshot.outputKeys);
        }
        for (IAEItemStack output : previousOutputs) {
            if (!currentOutputs.contains(output)) {
                List<PatternEntry> entries = outputs.get(output);
                if (entries != null) {
                    entries.remove(entry);
                    if (entries.isEmpty()) {
                        outputs.remove(output);
                        craftableOutputsDirty = true;
                    }
                }
            }
        }
        for (IAEItemStack output : currentOutputs) {
            if (!previousOutputs.contains(output)) {
                List<PatternEntry> entries = outputs.get(output);
                if (entries == null) {
                    entries = new ArrayList<>();
                    outputs.put(output, entries);
                    craftableOutputsDirty = true;
                }
                entries.add(entry);
            }
        }
        entry.outputs.clear();
        entry.outputs.addAll(currentOutputs);
        entry.refreshRepresentative();
        previousOutputs.addAll(currentOutputs);
        refreshOutputSnapshots(previousOutputs);
    }

    private void refreshOutputSnapshots(Collection<IAEItemStack> affectedOutputs) {
        for (IAEItemStack output : affectedOutputs) {
            List<PatternEntry> entries = outputs.get(output);
            if (entries == null || entries.isEmpty()) {
                if (craftingByOutput.remove(output) != null) {
                    craftableOutputsDirty = true;
                }
                mergedCraftingByOutput.remove(output);
                continue;
            }
            entries.sort((left, right) -> PATTERN_ORDER.compare(left.representative, right.representative));
            ImmutableList.Builder<ICraftingPatternDetails> snapshot = ImmutableList.builder();
            for (PatternEntry entry : entries) {
                if (entry.hasProviders()) {
                    snapshot.add(entry.representative);
                }
            }
            if (craftingByOutput.put(output, snapshot.build()) == null) {
                craftableOutputsDirty = true;
            }
            mergedCraftingByOutput.remove(output);
        }
    }

    private Collection<IAEItemStack> getAffectedOutputs(ProviderState state, ICraftingPatternDetails key) {
        LinkedHashSet<IAEItemStack> affected = new LinkedHashSet<>();
        PublishedPattern published = state.published.get(key);
        if (published != null) {
            affected.addAll(published.entry.outputs);
        }
        ICraftingPatternDetails desired = state.desired.get(key);
        if (desired != null) {
            affected.addAll(normalizeOutputs(desired));
        }
        return affected;
    }

    private static List<IAEItemStack> normalizeOutputs(ICraftingPatternDetails details) {
        return normalizeOutputs(details.getOutputs());
    }

    private static List<IAEItemStack> normalizeOutputs(@Nullable IAEItemStack[] recipeOutputs) {
        if (recipeOutputs == null || recipeOutputs.length == 0) {
            return Collections.emptyList();
        }
        LinkedHashSet<IAEItemStack> normalized = new LinkedHashSet<>();
        for (IAEItemStack output : recipeOutputs) {
            if (output != null) {
                IAEItemStack key = output.copy();
                key.reset();
                key.setCraftable(true);
                normalized.add(key);
            }
        }
        return new ArrayList<>(normalized);
    }

    private static boolean containsPublishedPattern(List<PatternEntry> entries) {
        for (PatternEntry entry : entries) {
            if (entry.hasProviders()) {
                return true;
            }
        }
        return false;
    }

    private void queueReconcile(ProviderState state, ICraftingPatternDetails recipe) {
        if (state.queued.add(recipe)) {
            pending.addLast(new PendingReconcile(state, recipe));
        }
    }

    private void cleanupProvider(ProviderState state) {
        if (state.removeWhenEmpty && state.desired.isEmpty() && state.published.isEmpty() && state.queued.isEmpty()) {
            providers.remove(state.provider);
        }
    }

    private static final class ProviderState {

        private final ICraftingProvider provider;
        private ICraftingMedium desiredMedium;
        private LinkedHashMap<ICraftingPatternDetails, ICraftingPatternDetails> desired = new LinkedHashMap<>();
        private final LinkedHashMap<ICraftingPatternDetails, PublishedPattern> published = new LinkedHashMap<>();
        private final Set<ICraftingPatternDetails> queued = new HashSet<>();
        private boolean removeWhenEmpty;

        private ProviderState(ICraftingProvider provider) {
            this.provider = provider;
        }
    }

    private static final class PatternEntry {

        private final ICraftingPatternDetails mapKey;
        private final List<IAEItemStack> outputs = new ArrayList<>();
        private final IdentityHashMap<ProviderState, PublishedPattern> providers = new IdentityHashMap<>();
        private ICraftingPatternDetails representative;
        private ImmutableList<ICraftingMedium> mediums = ImmutableList.of();

        private PatternEntry(ICraftingPatternDetails details) {
            this.mapKey = details;
            this.representative = details;
        }

        private boolean hasProviders() {
            return !providers.isEmpty();
        }

        private void refreshRepresentative() {
            ICraftingPatternDetails selected = null;
            ImmutableList.Builder<ICraftingMedium> mediumSnapshot = ImmutableList.builder();
            Set<ICraftingMedium> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (PublishedPattern published : providers.values()) {
                if (selected == null || PATTERN_ORDER.compare(published.details, selected) < 0) {
                    selected = published.details;
                }
                if (published.medium != null && seen.add(published.medium)) {
                    mediumSnapshot.add(published.medium);
                }
            }
            representative = selected;
            mediums = mediumSnapshot.build();
        }
    }

    private static final class PublishedPattern {

        private final PatternEntry entry;
        private ICraftingPatternDetails details;
        private ICraftingMedium medium;
        private int publishedPriority;
        private PatternSnapshot snapshot;

        private PublishedPattern(PatternEntry entry, ICraftingPatternDetails details, ICraftingMedium medium) {
            this.entry = entry;
            this.details = details;
            this.medium = medium;
            this.publishedPriority = details.getPriority();
            this.snapshot = PatternSnapshot.capture(details);
        }
    }

    private static final class PatternSnapshot {

        private final List<StackSnapshot> inputs;
        private final List<StackSnapshot> condensedInputs;
        private final List<StackSnapshot> outputs;
        private final List<StackSnapshot> condensedOutputs;
        private final List<IAEItemStack> outputKeys;
        private final boolean craftable;
        private final boolean substitute;

        private PatternSnapshot(List<StackSnapshot> inputs, List<StackSnapshot> condensedInputs,
              List<StackSnapshot> outputs, List<StackSnapshot> condensedOutputs, List<IAEItemStack> outputKeys,
              boolean craftable, boolean substitute) {
            this.inputs = inputs;
            this.condensedInputs = condensedInputs;
            this.outputs = outputs;
            this.condensedOutputs = condensedOutputs;
            this.outputKeys = outputKeys;
            this.craftable = craftable;
            this.substitute = substitute;
        }

        private static PatternSnapshot capture(ICraftingPatternDetails details) {
            IAEItemStack[] outputs = details.getOutputs();
            return new PatternSnapshot(snapshot(details.getInputs()), snapshot(details.getCondensedInputs()),
                  snapshot(outputs), snapshot(details.getCondensedOutputs()), normalizeOutputs(outputs),
                  details.isCraftable(), details.canSubstitute());
        }

        private boolean matches(ICraftingPatternDetails details) {
            return equals(capture(details));
        }

        private static List<StackSnapshot> snapshot(@Nullable IAEItemStack[] stacks) {
            if (stacks == null || stacks.length == 0) {
                return Collections.emptyList();
            }
            List<StackSnapshot> result = new ArrayList<>(stacks.length);
            for (IAEItemStack stack : stacks) {
                result.add(stack == null ? null : new StackSnapshot(stack));
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PatternSnapshot other)) {
                return false;
            }
            return craftable == other.craftable && substitute == other.substitute && inputs.equals(other.inputs) &&
                  condensedInputs.equals(other.condensedInputs) && outputs.equals(other.outputs) &&
                  condensedOutputs.equals(other.condensedOutputs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inputs, condensedInputs, outputs, condensedOutputs, craftable, substitute);
        }
    }

    private static final class StackSnapshot {

        private final IAEItemStack key;
        private final long amount;

        private StackSnapshot(IAEItemStack stack) {
            amount = stack.getStackSize();
            key = stack.copy();
            key.reset();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof StackSnapshot other && amount == other.amount && key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, amount);
        }
    }

    private static final class PendingReconcile {

        private final ProviderState state;
        private final ICraftingPatternDetails recipe;

        private PendingReconcile(ProviderState state, ICraftingPatternDetails recipe) {
            this.state = state;
            this.recipe = recipe;
        }
    }

    private static final class MergedPatternSnapshot {

        private final Collection<ICraftingPatternDetails> nativePatterns;
        private final ImmutableList<ICraftingPatternDetails> incrementalPatterns;
        private final ImmutableList<ICraftingPatternDetails> mergedPatterns;

        private MergedPatternSnapshot(Collection<ICraftingPatternDetails> nativePatterns,
              ImmutableList<ICraftingPatternDetails> incrementalPatterns,
              ImmutableList<ICraftingPatternDetails> mergedPatterns) {
            this.nativePatterns = nativePatterns;
            this.incrementalPatterns = incrementalPatterns;
            this.mergedPatterns = mergedPatterns;
        }
    }
}
