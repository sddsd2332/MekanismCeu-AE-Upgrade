package mekceuaeupgrade.common.recipe;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteCollectors;
import mekceuaeupgrade.common.transfer.AEUpgradeFluidBridge;

import mekceuaeupgrade.common.config.AERecipeProfile;
import mekceuaeupgrade.common.config.AERecipeConfigType;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekanism.api.gas.Gas;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.inputs.RotaryInput;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.recipe.machines.Chance2MachineRecipe;
import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.machines.RotaryRecipe;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.recipe.outputs.PressurizedOutput;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public class AEUpgradeRecipeCache {

    @Nullable
    private static Comparator<AEExposedRecipe> priorityComparator;

    private final IAEUpgradeHost host;
    private final List<AEExposedRecipe> recipes = new ArrayList<>();
    private final List<AEExposedRecipe> autoProcessingRecipes = new ArrayList<>();
    private final Random priorityRandom = new Random();
    private List<AEExposedRecipe> recipeView = Collections.unmodifiableList(recipes);
    private List<AEExposedRecipe> autoProcessingRecipeView = Collections.unmodifiableList(autoProcessingRecipes);
    private int craftingRecipeVersion = -1;
    private int autoProcessingRecipeVersion = -1;
    private int profileVersion = -1;
    private int autoProcessingProfileVersion = -1;
    @Nullable
    private AERecipeProfile lastProfile;
    @Nullable
    private AERecipeProfile lastAutoProcessingProfile;
    private Object lastRecipeSourceKey;
    private Object lastAutoProcessingRecipeSourceKey;

    public AEUpgradeRecipeCache(IAEUpgradeHost host) {
        this.host = host;
    }

    public List<AEExposedRecipe> getRecipes() {
        rebuildIfNeeded();
        return recipeView;
    }

    public List<AEExposedRecipe> getAutoProcessingRecipes() {
        rebuildAutoProcessingIfNeeded();
        return autoProcessingRecipeView;
    }

    public AEExposedRecipe find(AEExposedRecipeMatcher matcher) {
        rebuildIfNeeded();
        for (AEExposedRecipe recipe : recipes) {
            if (matcher.matches(recipe)) {
                return recipe;
            }
        }
        return null;
    }

    public void invalidate() {
        craftingRecipeVersion = -1;
        autoProcessingRecipeVersion = -1;
    }

    public static void setRecipePriorityComparator(@Nullable Comparator<AEExposedRecipe> comparator) {
        priorityComparator = comparator;
        RecipeHandler.markRecipeCachesInvalid();
    }

    private void rebuildIfNeeded() {
        int currentVersion = RecipeHandler.getGlobalRecipeVersion();
        Object recipeSourceKey = host instanceof IAEItemRecipeHost itemHost ? itemHost.getAERecipeSourceKey() : null;
        AERecipeProfile profile = AERecipeProfileManager.getProfile(host, AERecipeConfigType.CRAFTING);
        int currentProfileVersion = profile == null ? -1 : profile.getVersion();
        if (craftingRecipeVersion == currentVersion && profileVersion == currentProfileVersion && profile == lastProfile
              && Objects.equals(recipeSourceKey, lastRecipeSourceKey)) {
            return;
        }
        craftingRecipeVersion = currentVersion;
        profileVersion = currentProfileVersion;
        lastProfile = profile;
        lastRecipeSourceKey = recipeSourceKey;
        recipes.clear();
        recipes.addAll(collectCraftingRecipes(host));
        assignPriorities(recipes, profile);
        recipeView = Collections.unmodifiableList(new ArrayList<>(recipes));
    }

    private void rebuildAutoProcessingIfNeeded() {
        int currentVersion = RecipeHandler.getGlobalRecipeVersion();
        Object recipeSourceKey = host instanceof IAEItemRecipeHost itemHost ? itemHost.getAERecipeSourceKey() : null;
        AERecipeProfile profile = AERecipeProfileManager.getProfile(host, AERecipeConfigType.AUTO_PROCESSING);
        int currentProfileVersion = profile == null ? -1 : profile.getVersion();
        if (autoProcessingRecipeVersion == currentVersion
              && autoProcessingProfileVersion == currentProfileVersion
              && profile == lastAutoProcessingProfile
              && Objects.equals(recipeSourceKey, lastAutoProcessingRecipeSourceKey)) {
            return;
        }
        autoProcessingRecipeVersion = currentVersion;
        autoProcessingProfileVersion = currentProfileVersion;
        lastAutoProcessingProfile = profile;
        lastAutoProcessingRecipeSourceKey = recipeSourceKey;
        autoProcessingRecipes.clear();
        if (profile != null && profile.getRouteFilterMode() == AERecipeProfile.RouteFilterMode.WHITELIST) {
            autoProcessingRecipes.addAll(collectConfigurableRecipes(host));
            assignPriorities(autoProcessingRecipes, profile);
        }
        autoProcessingRecipeView = Collections.unmodifiableList(new ArrayList<>(autoProcessingRecipes));
    }

    public static List<AEExposedRecipe> collectCraftingRecipes(IAEUpgradeHost host) {
        List<AEExposedRecipe> recipes = collectConfigurableRecipes(host);
        if (!recipes.isEmpty()) {
            recipes.removeIf(recipe -> !recipe.isExposableToCrafting());
        }
        return recipes;
    }

    public static List<AEExposedRecipe> collectConfigurableRecipes(IAEUpgradeHost host) {
        if (!(host instanceof IAEItemRecipeHost itemHost)) {
            return Collections.emptyList();
        }
        List<AEExposedRecipe> recipes = itemHost.getAEExposedItemRecipes();
        if (recipes == null) {
            return Collections.emptyList();
        }
        if (recipes.isEmpty()) {
            return recipes;
        }
        return new ArrayList<>(new LinkedHashSet<>(recipes));
    }

    public static List<AEExposedRecipe> collectBasicItemRecipes(Map<ItemStackInput, ? extends BasicMachineRecipe<?>> recipeMap) {
        List<AEExposedRecipe> collected = new ArrayList<>();
        recipeMap.values().forEach(recipe -> {
            ItemStack input = recipe.getInput().ingredient;
            ItemStack output = recipe.getOutput().output;
            addExpandedIfExposable(collected, input, output, candidate -> basicRecipeMatches(recipeMap, candidate, output));
        });
        return collected;
    }

    public static List<AEExposedRecipe> collectChanceItemRecipes(Map<ItemStackInput, ? extends ChanceMachineRecipe<?>> recipeMap) {
        List<AEExposedRecipe> collected = new ArrayList<>();
        recipeMap.values().forEach(recipe -> {
            ItemStack output = recipe.getOutput().getMainOutput();
            addExpandedIfExposable(collected, recipe.getInput().ingredient, output, candidate -> chanceRecipeMatches(recipeMap, candidate, output));
        });
        return collected;
    }

    public static List<AEExposedRecipe> collectDoubleItemRecipes(Map<?, ? extends DoubleMachineRecipe<?>> recipeMap) {
        List<AEExposedRecipe> collected = new ArrayList<>();
        recipeMap.values().forEach(recipe -> {
            ItemStack output = recipe.getOutput().output;
            addExpandedIfExposable(collected, Arrays.asList(recipe.getInput().itemStack, recipe.getInput().extraStack), output,
                  inputs -> doubleRecipeMatches(recipeMap, inputs.get(0), inputs.get(1), output));
        });
        return collected;
    }

    public static List<AEExposedRecipe> collectInfusionItemRecipes(Map<InfusionInput, ? extends MachineRecipe<InfusionInput, ItemStackOutput, ?>> recipeMap) {
        List<AEExposedRecipe> collected = new ArrayList<>();
        recipeMap.values().forEach(recipe -> {
            InfusionInput input = recipe.getInput();
            if (input.infuse == null || input.infuse.getType() == null || input.infuse.getAmount() <= 0) {
                return;
            }
            for (Entry<ItemStack, InfuseObject> source : InfuseRegistry.getObjectMap().entrySet()) {
                InfuseObject object = source.getValue();
                if (object != null && object.type == input.infuse.getType() && object.stored > 0) {
                    addInfusionRecipeIfExposable(collected, recipeMap, recipe, source.getKey(), object.stored);
                }
            }
        });
        return collected;
    }

    public static List<AEExposedRecipe> collectAdvancedGasItemRecipes(Map<AdvancedMachineInput, ? extends AdvancedMachineRecipe<?>> recipeMap,
          int gasPerOperation) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectAdvancedGasToItem(recipeMap, gasPerOperation));
    }

    public static List<AEExposedRecipe> collectFarmGasItemRecipes(Map<AdvancedMachineInput, ? extends FarmMachineRecipe<?>> recipeMap,
          int gasPerOperation) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectFarmGasToItem(recipeMap, gasPerOperation));
    }

    public static List<AEExposedRecipe> collectNucleosynthesizerGasItemRecipes(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipeMap) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectNucleosynthesizerGasToItem(recipeMap));
    }

    public static List<AEExposedRecipe> collectGuaranteedChance2ItemRecipes(Map<ItemStackInput, ? extends Chance2MachineRecipe<?>> recipeMap) {
        List<AEExposedRecipe> collected = new ArrayList<>();
        recipeMap.values().forEach(recipe -> {
            if (recipe.getOutput().primaryChance >= 1) {
                ItemStack output = recipe.getOutput().getMaxPrimaryOutput();
                addExpandedIfExposable(collected, recipe.getInput().ingredient, output, candidate -> chance2RecipeMatches(recipeMap, candidate, output));
            }
        });
        return collected;
    }

    public static List<AEExposedRecipe> collectItemToGasRecipes(
          Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipeMap) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectItemToGas(recipeMap));
    }

    public static List<AEExposedRecipe> collectItemGasToGasRecipes(
          Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipeMap, Gas gasType, int gasPerOperation) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectItemGasToGas(recipeMap, gasType, gasPerOperation));
    }

    public static List<AEExposedRecipe> collectGasFluidToGasRecipes(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, GasOutput, ?>> recipeMap) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectGasFluidToGas(recipeMap));
    }

    public static List<AEExposedRecipe> collectFluidToGasPairRecipes(
          Map<FluidInput, ? extends MachineRecipe<FluidInput, ChemicalPairOutput, ?>> recipeMap) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectFluidToGasPair(recipeMap));
    }

    public static List<AEExposedRecipe> collectPressurizedRecipes(Map<PressurizedInput, ? extends MachineRecipe<PressurizedInput, PressurizedOutput, ?>> recipeMap) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectPressurized(recipeMap));
    }

    public static List<AEExposedRecipe> collectRotaryGasToFluidRecipes(Map<RotaryInput, ? extends RotaryRecipe> recipeMap) {
        if (!AEUpgradeFluidBridge.isAvailable()) {
            return Collections.emptyList();
        }
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectRotaryGasToFluid(recipeMap));
    }

    public static List<AEExposedRecipe> collectRotaryFluidToGasRecipes(Map<RotaryInput, ? extends RotaryRecipe> recipeMap) {
        return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectRotaryFluidToGas(recipeMap));
    }

    private void assignPriorities(List<AEExposedRecipe> targetRecipes, @Nullable AERecipeProfile profile) {
        Comparator<AEExposedRecipe> comparator = priorityComparator;
        if (comparator == null) {
            Collections.shuffle(targetRecipes, priorityRandom);
        } else {
            targetRecipes.sort(comparator);
        }
        if (profile != null && (!profile.isEmpty()
              || profile.getRouteFilterMode() == AERecipeProfile.RouteFilterMode.WHITELIST)) {
            List<AEExposedRecipe> filtered = profile.applyTo(targetRecipes, true);
            targetRecipes.clear();
            targetRecipes.addAll(filtered);
        }
        int priority = targetRecipes.size();
        for (AEExposedRecipe recipe : targetRecipes) {
            recipe.setPriority(priority--);
        }
    }

    private static boolean isExposable(ItemStack input, ItemStack output) {
        return AERecipeItemInputs.isConcreteInput(input) && isExposableOutput(output);
    }

    private static void addExpandedIfExposable(List<AEExposedRecipe> recipes, ItemStack input, ItemStack output, Predicate<ItemStack> acceptsInput) {
        if (!isExposableOutput(output)) {
            return;
        }
        for (ItemStack expandedInput : AERecipeItemInputs.expand(input, acceptsInput)) {
            if (!isExposable(expandedInput, output)) {
                continue;
            }
            recipes.add(new AEExposedRecipe(expandedInput, output));
        }
    }

    private static void addExpandedIfExposable(List<AEExposedRecipe> recipes, List<ItemStack> inputs, ItemStack output,
          Predicate<List<ItemStack>> acceptsInputs) {
        if (!isExposableOutput(output)) {
            return;
        }
        for (List<ItemStack> expandedInputs : AERecipeItemInputs.expandCombinations(inputs, acceptsInputs)) {
            boolean valid = true;
            for (ItemStack input : expandedInputs) {
                if (!AERecipeItemInputs.isConcreteInput(input)) {
                    valid = false;
                    break;
                }
            }
            if (!valid) {
                continue;
            }
            recipes.add(new AEExposedRecipe(expandedInputs, output));
        }
    }

    private static void addInfusionRecipeIfExposable(List<AEExposedRecipe> recipes,
          Map<InfusionInput, ? extends MachineRecipe<InfusionInput, ItemStackOutput, ?>> recipeMap,
          MachineRecipe<InfusionInput, ItemStackOutput, ?> recipe, ItemStack sourceStack, int sourceAmount) {
        int required = recipe.getInput().infuse.getAmount();
        int gcd = gcd(required, sourceAmount);
        int operations = sourceAmount / gcd;
        int sourceCount = required / gcd;
        ItemStack primaryInput = scaledStack(recipe.getInput().inputStack, operations);
        ItemStack infuseInput = scaledStack(sourceStack, sourceCount);
        ItemStack output = scaledStack(recipe.getOutput().output, operations);
        if (!primaryInput.isEmpty() && !infuseInput.isEmpty() && !output.isEmpty()) {
            addExpandedIfExposable(recipes, Arrays.asList(primaryInput, infuseInput), output,
                  inputs -> infusionRecipeMatches(recipeMap, recipe, inputs.get(0), output, operations) &&
                            infuseSourceMatches(inputs.get(1), sourceStack, recipe.getInput().infuse.getType(), sourceAmount));
        }
    }

    private static ItemStack scaledStack(ItemStack stack, int multiplier) {
        return AERecipeStacks.scale(stack, multiplier);
    }

    private static boolean isExposableOutput(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0;
    }

    private static boolean basicRecipeMatches(Map<ItemStackInput, ? extends BasicMachineRecipe<?>> recipeMap, ItemStack input, ItemStack output) {
        Object matched = getRecipe(recipeMap, new ItemStackInput(input.copy()));
        return matched instanceof BasicMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().output, output);
    }

    private static boolean chanceRecipeMatches(Map<ItemStackInput, ? extends ChanceMachineRecipe<?>> recipeMap, ItemStack input, ItemStack output) {
        Object matched = getRecipe(recipeMap, new ItemStackInput(input.copy()));
        return matched instanceof ChanceMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().getMainOutput(), output);
    }

    private static boolean chance2RecipeMatches(Map<ItemStackInput, ? extends Chance2MachineRecipe<?>> recipeMap, ItemStack input, ItemStack output) {
        Object matched = getRecipe(recipeMap, new ItemStackInput(input.copy()));
        return matched instanceof Chance2MachineRecipe<?> recipe && recipe.getOutput().primaryChance >= 1 &&
              ItemStack.areItemStacksEqual(recipe.getOutput().getMaxPrimaryOutput(), output);
    }

    private static boolean doubleRecipeMatches(Map<?, ? extends DoubleMachineRecipe<?>> recipeMap, ItemStack input, ItemStack extra,
          ItemStack output) {
        Object matched = getRecipe(recipeMap, new DoubleMachineInput(input.copy(), extra.copy()));
        return matched instanceof DoubleMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().output, output);
    }

    private static boolean infusionRecipeMatches(Map<InfusionInput, ? extends MachineRecipe<InfusionInput, ItemStackOutput, ?>> recipeMap,
          MachineRecipe<InfusionInput, ItemStackOutput, ?> sourceRecipe, ItemStack input, ItemStack output, int operations) {
        InfusionInput sourceInput = sourceRecipe.getInput();
        Object matched = getRecipe(recipeMap, new InfusionInput(sourceInput.infuse, input.copy()));
        if (matched instanceof MachineRecipe<?, ?, ?> recipe && recipe.getOutput() instanceof ItemStackOutput matchedOutput) {
            return ItemStack.areItemStacksEqual(scaledStack(matchedOutput.output, operations), output);
        }
        return MachineInput.inputContains(input, sourceInput.inputStack) &&
              ItemStack.areItemStacksEqual(scaledStack(sourceRecipe.getOutput().output, operations), output);
    }

    private static boolean infuseSourceMatches(ItemStack sourceInput, ItemStack sourceDefinition, Object infuseType, int sourceAmount) {
        InfuseObject object = InfuseRegistry.getObject(sourceInput);
        if (object != null) {
            return object.type == infuseType && object.stored == sourceAmount;
        }
        return MachineInput.inputContains(sourceInput, sourceDefinition);
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getRecipe(Map recipeMap, MachineInput input) {
        return RecipeHandler.getRecipe(input, recipeMap);
    }

    private static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int next = a % b;
            a = b;
            b = next;
        }
        return a == 0 ? 1 : a;
    }

    @FunctionalInterface
    public interface AEExposedRecipeMatcher {

        boolean matches(AEExposedRecipe recipe);
    }
}
