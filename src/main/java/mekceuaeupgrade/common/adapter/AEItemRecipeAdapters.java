package mekceuaeupgrade.common.adapter;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.recipe.machines.Chance2MachineRecipe;
import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.transfer.AERecipePort;
import mekceuaeupgrade.common.transfer.AERecipeTransferPlan;
import mekceuaeupgrade.common.util.AEUpgradeDebug;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AEItemRecipeAdapters {

    private AEItemRecipeAdapters() {
    }

    public static <RECIPE extends BasicMachineRecipe<RECIPE>> IAERecipeMachineAdapter singleItemToItem(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Runnable refreshRecipeLookupCache) {
        return createSingleItemToItem(recipes, inputSlot, outputSlot, refreshRecipeLookupCache);
    }

    private static <RECIPE extends BasicMachineRecipe<RECIPE>> IAERecipeMachineAdapter createSingleItemToItem(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectBasicItemRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                InputInventorySlot input = inputSlot.get();
                OutputInventorySlot output = outputSlot.get();
                if (input == null || output == null) {
                    return reject(host, "input or output slot is not initialized");
                }
                if (stack.isEmpty()) {
                    return reject(host, "AE supplied an empty input stack");
                }
                if (!recipe.matchesInput(stack)) {
                    return reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack), AEUpgradeDebug.inputStack(recipe));
                }
                ItemStack simulatedInput = getSimulatedStackWithInsert(input, stack);
                BasicMachineRecipe<?> machineRecipe = getRecipe(recipes, refreshRecipeLookupCache, simulatedInput);
                if (machineRecipe == null) {
                    return reject(host, "no Mekanism recipe for simulated input {}", AEUpgradeDebug.stack(simulatedInput));
                }
                if (!MachineInput.inputContains(simulatedInput, machineRecipe.getInput().ingredient)) {
                    return reject(host, "simulated input {} does not contain Mekanism recipe ingredient {}",
                          AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(machineRecipe.getInput().ingredient));
                }
                ItemStack machineOutput = machineRecipe.getOutput().output;
                if (!outputsMatch(recipe, machineOutput)) {
                    return reject(host, "machine output {} does not match exposed output {}",
                          AEUpgradeDebug.stack(machineOutput), AEUpgradeDebug.outputStack(recipe));
                }
                if (!canOutputToSlot(output, machineOutput)) {
                    return reject(host, "output slot cannot accept {}", AEUpgradeDebug.stack(machineOutput));
                }
                ItemStack remainder = input.insertItem(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
                if (!remainder.isEmpty()) {
                    return reject(host, "input slot simulation left remainder {} from {}", AEUpgradeDebug.stack(remainder), AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                if (!canAcceptItemInput(host, recipe, stack)) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.item("item_input", inputSlot.get()));
                if (plan == null || !plan.execute()) {
                    return reject(host, "input slot execute failed for {}", AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                OutputInventorySlot output = outputSlot.get();
                if (input == null || output == null) {
                    return false;
                }
                ItemStack current = input.getStack();
                if (current.isEmpty()) {
                    return true;
                }
                BasicMachineRecipe<?> recipe = getRecipe(recipes, refreshRecipeLookupCache, current);
                return recipe != null && canOutputToSlot(output, recipe.getOutput().output) && input.getCount() < input.getLimit(current);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainItem(node, outputSlot.get());
            }

        };
    }

    public static <RECIPE extends ChanceMachineRecipe<RECIPE>> IAERecipeMachineAdapter chanceItemToItem(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<? extends OutputInventorySlot> secondaryOutputSlot,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectChanceItemRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                InputInventorySlot input = inputSlot.get();
                OutputInventorySlot output = outputSlot.get();
                OutputInventorySlot secondaryOutput = secondaryOutputSlot.get();
                if (input == null || output == null || secondaryOutput == null) {
                    return reject(host, "input or output slot is not initialized");
                }
                if (stack.isEmpty()) {
                    return reject(host, "AE supplied an empty input stack");
                }
                if (!recipe.matchesInput(stack)) {
                    return reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack), AEUpgradeDebug.inputStack(recipe));
                }
                ItemStack simulatedInput = getSimulatedStackWithInsert(input, stack);
                RECIPE machineRecipe = getChanceRecipe(recipes, refreshRecipeLookupCache, simulatedInput);
                if (machineRecipe == null) {
                    return reject(host, "no Mekanism recipe for simulated input {}", AEUpgradeDebug.stack(simulatedInput));
                }
                if (!MachineInput.inputContains(simulatedInput, machineRecipe.getInput().ingredient)) {
                    return reject(host, "simulated input {} does not contain Mekanism recipe ingredient {}",
                          AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(machineRecipe.getInput().ingredient));
                }
                ChanceOutput machineOutput = machineRecipe.getOutput();
                ItemStack primaryOutput = machineOutput.getMainOutput();
                if (!outputsMatch(recipe, primaryOutput)) {
                    return reject(host, "machine output {} does not match exposed output {}",
                          AEUpgradeDebug.stack(primaryOutput), AEUpgradeDebug.outputStack(recipe));
                }
                if (!canOutputToSlot(output, primaryOutput)) {
                    return reject(host, "primary output slot cannot accept {}", AEUpgradeDebug.stack(primaryOutput));
                }
                ItemStack secondaryStack = machineOutput.getMaxSecondaryOutput();
                if (!secondaryStack.isEmpty() && !canOutputToSlot(secondaryOutput, secondaryStack)) {
                    return reject(host, "secondary output slot cannot accept {}", AEUpgradeDebug.stack(secondaryStack));
                }
                ItemStack remainder = input.insertItem(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
                if (!remainder.isEmpty()) {
                    return reject(host, "input slot simulation left remainder {} from {}", AEUpgradeDebug.stack(remainder), AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                if (!canAcceptItemInput(host, recipe, stack)) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.item("item_input", inputSlot.get()));
                if (plan == null || !plan.execute()) {
                    return reject(host, "input slot execute failed for {}", AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                OutputInventorySlot output = outputSlot.get();
                OutputInventorySlot secondaryOutput = secondaryOutputSlot.get();
                if (input == null || output == null || secondaryOutput == null) {
                    return false;
                }
                ItemStack current = input.getStack();
                if (current.isEmpty()) {
                    return true;
                }
                RECIPE recipe = getChanceRecipe(recipes, refreshRecipeLookupCache, current);
                if (recipe == null || !hasInputRoom(input, current)) {
                    return false;
                }
                ChanceOutput machineOutput = recipe.getOutput();
                return canOutputToSlot(output, machineOutput.getMainOutput()) &&
                      canOutputToSlot(secondaryOutput, machineOutput.getMaxSecondaryOutput());
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainItems(node, outputSlot.get(), secondaryOutputSlot.get());
            }

        };
    }

    public static <RECIPE extends Chance2MachineRecipe<RECIPE>> IAERecipeMachineAdapter guaranteedChance2ItemToItem(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectGuaranteedChance2ItemRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                InputInventorySlot input = inputSlot.get();
                OutputInventorySlot output = outputSlot.get();
                if (input == null || output == null) {
                    return reject(host, "input or output slot is not initialized");
                }
                if (stack.isEmpty()) {
                    return reject(host, "AE supplied an empty input stack");
                }
                if (!recipe.matchesInput(stack)) {
                    return reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack), AEUpgradeDebug.inputStack(recipe));
                }
                ItemStack simulatedInput = getSimulatedStackWithInsert(input, stack);
                RECIPE machineRecipe = getChance2Recipe(recipes, refreshRecipeLookupCache, simulatedInput);
                if (machineRecipe == null) {
                    return reject(host, "no Mekanism recipe for simulated input {}", AEUpgradeDebug.stack(simulatedInput));
                }
                if (machineRecipe.getOutput().primaryChance < 1) {
                    return reject(host, "recipe output is not guaranteed for {}", AEUpgradeDebug.stack(simulatedInput));
                }
                if (!MachineInput.inputContains(simulatedInput, machineRecipe.getInput().ingredient)) {
                    return reject(host, "simulated input {} does not contain Mekanism recipe ingredient {}",
                          AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(machineRecipe.getInput().ingredient));
                }
                ItemStack machineOutput = machineRecipe.getOutput().getMaxPrimaryOutput();
                if (!outputsMatch(recipe, machineOutput)) {
                    return reject(host, "machine output {} does not match exposed output {}",
                          AEUpgradeDebug.stack(machineOutput), AEUpgradeDebug.outputStack(recipe));
                }
                if (!canOutputToSlot(output, machineOutput)) {
                    return reject(host, "output slot cannot accept {}", AEUpgradeDebug.stack(machineOutput));
                }
                ItemStack remainder = input.insertItem(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
                if (!remainder.isEmpty()) {
                    return reject(host, "input slot simulation left remainder {} from {}", AEUpgradeDebug.stack(remainder), AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                if (!canAcceptItemInput(host, recipe, stack)) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.item("item_input", inputSlot.get()));
                if (plan == null || !plan.execute()) {
                    return reject(host, "input slot execute failed for {}", AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                OutputInventorySlot output = outputSlot.get();
                if (input == null || output == null) {
                    return false;
                }
                ItemStack current = input.getStack();
                if (current.isEmpty()) {
                    return true;
                }
                RECIPE recipe = getChance2Recipe(recipes, refreshRecipeLookupCache, current);
                return recipe != null && recipe.getOutput().primaryChance >= 1 && canOutputToSlot(output, recipe.getOutput().getMaxPrimaryOutput()) &&
                      hasInputRoom(input, current);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainItem(node, outputSlot.get());
            }

        };
    }

    public static <RECIPE extends DoubleMachineRecipe<RECIPE>> IAERecipeMachineAdapter doubleItemToItem(
          Supplier<Map<DoubleMachineInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends InputInventorySlot> extraSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectDoubleItemRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return reject(host, "double-input machine requires two AE inputs");
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return reject(host, "double-input machine requires two AE inputs");
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                InputInventorySlot input = inputSlot.get();
                InputInventorySlot extra = extraSlot.get();
                OutputInventorySlot output = outputSlot.get();
                if (input == null || extra == null || output == null) {
                    return reject(host, "input, extra, or output slot is not initialized");
                }
                if (stacks.size() != 2) {
                    return reject(host, "AE supplied {} inputs but recipe expects 2", stacks.size());
                }
                if (!recipe.matchesInputs(stacks)) {
                    return reject(host, "AE supplied inputs that do not match the exposed recipe");
                }
                ItemStack simulatedInput = getSimulatedStackWithInsert(input, stacks.get(0));
                ItemStack simulatedExtra = getSimulatedStackWithInsert(extra, stacks.get(1));
                RECIPE machineRecipe = getDoubleRecipe(recipes, refreshRecipeLookupCache, simulatedInput, simulatedExtra);
                if (machineRecipe == null) {
                    return reject(host, "no Mekanism recipe for simulated inputs {} and {}",
                          AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(simulatedExtra));
                }
                if (!MachineInput.inputContains(simulatedInput, machineRecipe.getInput().itemStack) ||
                    !MachineInput.inputContains(simulatedExtra, machineRecipe.getInput().extraStack)) {
                    return reject(host, "simulated inputs do not contain Mekanism recipe ingredients");
                }
                ItemStack machineOutput = machineRecipe.getOutput().output;
                if (!outputsMatch(recipe, machineOutput)) {
                    return reject(host, "machine output {} does not match exposed output {}",
                          AEUpgradeDebug.stack(machineOutput), AEUpgradeDebug.outputStack(recipe));
                }
                if (!canOutputToSlot(output, machineOutput)) {
                    return reject(host, "output slot cannot accept {}", AEUpgradeDebug.stack(machineOutput));
                }
                ItemStack inputRemainder = input.insertItem(stacks.get(0).copy(), Action.SIMULATE, AutomationType.INTERNAL);
                ItemStack extraRemainder = extra.insertItem(stacks.get(1).copy(), Action.SIMULATE, AutomationType.INTERNAL);
                if (!inputRemainder.isEmpty() || !extraRemainder.isEmpty()) {
                    return reject(host, "input simulation left remainders {} and {}", AEUpgradeDebug.stack(inputRemainder), AEUpgradeDebug.stack(extraRemainder));
                }
                return true;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (!canAcceptItemInputs(host, recipe, stacks)) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.item("item_input", inputSlot.get()), AERecipePort.item("extra_input", extraSlot.get())));
                if (plan == null || !plan.execute()) {
                    return reject(host, "atomic input execute failed for {}", AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                InputInventorySlot extra = extraSlot.get();
                OutputInventorySlot output = outputSlot.get();
                if (input == null || extra == null || output == null) {
                    return false;
                }
                Map<DoubleMachineInput, RECIPE> recipeMap = recipes.get();
                ItemStack currentInput = input.getStack();
                ItemStack currentExtra = extra.getStack();
                if (currentInput.isEmpty() && currentExtra.isEmpty()) {
                    return true;
                } else if (currentInput.isEmpty()) {
                    return hasMatchingExtra(recipeMap, currentExtra) && hasInputRoom(extra, currentExtra);
                } else if (currentExtra.isEmpty()) {
                    return hasMatchingInput(recipeMap, currentInput) && hasInputRoom(input, currentInput);
                }
                RECIPE recipe = getDoubleRecipe(recipes, refreshRecipeLookupCache, currentInput, currentExtra);
                return recipe != null && canOutputToSlot(output, recipe.getOutput().output) &&
                      hasInputRoom(input, currentInput) && hasInputRoom(extra, currentExtra);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
                observer.accept(extraSlot.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainItem(node, outputSlot.get());
            }

        };
    }

    private static <RECIPE extends BasicMachineRecipe<RECIPE>> RECIPE getRecipe(Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Runnable refreshRecipeLookupCache, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new ItemStackInput(input), recipes.get());
    }

    private static <RECIPE extends ChanceMachineRecipe<RECIPE>> RECIPE getChanceRecipe(Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Runnable refreshRecipeLookupCache, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getChanceRecipe(new ItemStackInput(input), recipes.get());
    }

    private static <RECIPE extends Chance2MachineRecipe<RECIPE>> RECIPE getChance2Recipe(Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Runnable refreshRecipeLookupCache, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getChance2Recipe(new ItemStackInput(input), recipes.get());
    }

    private static <RECIPE extends DoubleMachineRecipe<RECIPE>> RECIPE getDoubleRecipe(Supplier<Map<DoubleMachineInput, RECIPE>> recipes,
          Runnable refreshRecipeLookupCache, ItemStack input, ItemStack extra) {
        if (input.isEmpty() || extra.isEmpty()) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new DoubleMachineInput(input, extra), recipes.get());
    }

    private static boolean hasMatchingInput(Map<DoubleMachineInput, ?> recipes, ItemStack stack) {
        return recipes.keySet().stream().anyMatch(input -> ItemHandlerHelper.canItemStacksStack(input.itemStack, stack));
    }

    private static boolean hasMatchingExtra(Map<DoubleMachineInput, ?> recipes, ItemStack stack) {
        return recipes.keySet().stream().anyMatch(input -> ItemHandlerHelper.canItemStacksStack(input.extraStack, stack));
    }

    public static ItemStack getSimulatedStackWithInsert(IInventorySlot slot, ItemStack stack) {
        if (slot == null) {
            return ItemStack.EMPTY;
        }
        ItemStack current = slot.getStack();
        if (current.isEmpty()) {
            return stack.copy();
        }
        ItemStack simulated = current.copy();
        simulated.grow(stack.getCount());
        return simulated;
    }

    public static boolean canOutputToSlot(IInventorySlot outputSlot, ItemStack output) {
        return output.isEmpty() || outputSlot != null && outputSlot.insertItem(output.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    public static boolean hasInputRoom(IInventorySlot slot, ItemStack current) {
        return current.isEmpty() || slot.getCount() < slot.getLimit(current);
    }

    public static boolean outputsMatch(ItemStack expected, ItemStack actual) {
        if (expected.isEmpty() || actual.isEmpty() || expected.getCount() != actual.getCount()) {
            return false;
        }
        return ItemHandlerHelper.canItemStacksStack(expected, actual);
    }

    public static boolean outputsMatch(AEExposedRecipe recipe, ItemStack actual) {
        return outputsMatch(recipe.getOutputStack(), actual, recipe.getCraftAmount());
    }

    public static boolean outputsMatch(ItemStack expected, ItemStack actual, int multiplier) {
        if (expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        long scaledCount = (long) actual.getCount() * Math.max(1, multiplier);
        return scaledCount <= Integer.MAX_VALUE && expected.getCount() == (int) scaledCount && ItemHandlerHelper.canItemStacksStack(expected, actual);
    }

    public static boolean reject(IAEItemRecipeHost host, String reason) {
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "rejecting AE item input: " + reason);
        }
        return false;
    }

    public static boolean reject(IAEItemRecipeHost host, String reason, Object arg0) {
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "rejecting AE item input: " + reason, arg0);
        }
        return false;
    }

    public static boolean reject(IAEItemRecipeHost host, String reason, Object arg0, Object arg1) {
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "rejecting AE item input: " + reason, arg0, arg1);
        }
        return false;
    }

    public static boolean reject(IAEItemRecipeHost host, String reason, Object arg0, Object arg1, Object arg2) {
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "rejecting AE item input: " + reason, arg0, arg1, arg2);
        }
        return false;
    }

    public static boolean reject(IAEItemRecipeHost host, String reason, Object arg0, Object arg1, Object arg2, Object arg3) {
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "rejecting AE item input: " + reason, arg0, arg1, arg2, arg3);
        }
        return false;
    }

    public static boolean reject(IAEItemRecipeHost host, String reason, Object... args) {
        if (AEUpgradeDebug.enabled()) {
            AEUpgradeDebug.log(host, "rejecting AE item input: " + reason, args);
        }
        return false;
    }
}
