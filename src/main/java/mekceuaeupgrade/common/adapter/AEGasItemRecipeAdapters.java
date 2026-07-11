package mekceuaeupgrade.common.adapter;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AERecipeStacks;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteCollectors;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteLegacyIO;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;
import mekceuaeupgrade.common.transfer.AERecipePort;
import mekceuaeupgrade.common.transfer.AERecipeTransferPlan;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.RotaryInput;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.machines.RotaryRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.recipe.outputs.MachineOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AEGasItemRecipeAdapters {

    private AEGasItemRecipeAdapters() {
    }

    public static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> IAERecipeMachineAdapter itemToGas(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends IExtendedGasTank> outputTank,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectItemToGasRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                InputInventorySlot input = inputSlot.get();
                IExtendedGasTank tank = outputTank.get();
                if (input == null || tank == null) {
                    return AEItemRecipeAdapters.reject(host, "input slot or output tank is not initialized");
                }
                if (!AEUpgradeFakeGas.isAvailable()) {
                    return AEItemRecipeAdapters.reject(host, "AE generic gas output is not available");
                }
                if (stack.isEmpty()) {
                    return AEItemRecipeAdapters.reject(host, "AE supplied an empty input stack");
                }
                if (!AERecipeRouteLegacyIO.matchesInput(recipe, stack)) {
                    return AEItemRecipeAdapters.reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack),
                          AEUpgradeDebug.inputStack(recipe));
                }
                ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(input, stack);
                RECIPE machineRecipe = getItemToGasRecipe(recipes, refreshRecipeLookupCache, simulatedInput);
                if (machineRecipe == null) {
                    return AEItemRecipeAdapters.reject(host, "no Mekanism recipe for simulated input {}", AEUpgradeDebug.stack(simulatedInput));
                }
                if (!MachineInput.inputContains(simulatedInput, machineRecipe.getInput().ingredient)) {
                    return AEItemRecipeAdapters.reject(host, "simulated input {} does not contain Mekanism recipe ingredient {}",
                          AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(machineRecipe.getInput().ingredient));
                }
                GasStack machineOutput = machineRecipe.getOutput().output;
                if (!AEUpgradeFakeGas.outputMatches(recipe.getOutputStack(), machineOutput, recipe.getCraftAmount())) {
                    return AEItemRecipeAdapters.reject(host, "machine gas output {} does not match exposed output {}",
                          machineOutput, AEUpgradeDebug.outputStack(recipe));
                }
                if (!canGasOutputToTank(tank, machineOutput)) {
                    return AEItemRecipeAdapters.reject(host, "output tank cannot accept {}", machineOutput);
                }
                ItemStack remainder = input.insertItem(stack.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
                if (!remainder.isEmpty()) {
                    return AEItemRecipeAdapters.reject(host, "input slot simulation left remainder {} from {}", AEUpgradeDebug.stack(remainder),
                          AEUpgradeDebug.stack(stack));
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
                    return AEItemRecipeAdapters.reject(host, "item input execute failed for {}", AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                IExtendedGasTank tank = outputTank.get();
                if (input == null || tank == null || !AEUpgradeFakeGas.isAvailable()) {
                    return false;
                }
                ItemStack current = input.getStack();
                if (current.isEmpty()) {
                    return tank.getNeeded() > 0;
                }
                RECIPE recipe = getItemToGasRecipe(recipes, refreshRecipeLookupCache, current);
                return recipe != null && canGasOutputToTank(tank, recipe.getOutput().output) && AEItemRecipeAdapters.hasInputRoom(input, current);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.gas("gas_output", outputTank.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<GasInput, GasOutput, RECIPE>> IAERecipeMachineAdapter gasToGas(
          Supplier<Map<GasInput, RECIPE>> recipes,
          Supplier<? extends IExtendedGasTank> inputTank,
          Supplier<? extends IExtendedGasTank> outputTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectGasToGas(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return getGasToGasRecipe(host, recipe, stack, recipes, inputTank.get(), outputTank.get(), refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                if (getGasToGasRecipe(host, recipe, stack, recipes, inputTank.get(), outputTank.get(), refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.gas("gas_input", inputTank.get()));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "{} input gas execute failed for {}", machineName, AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank input = inputTank.get();
                IExtendedGasTank output = outputTank.get();
                if (input == null || output == null || !AEUpgradeFakeGas.isAvailable() || input.getNeeded() <= 0) {
                    return false;
                }
                GasStack current = input.getGas();
                if (current == null) {
                    return output.getNeeded() > 0;
                }
                RECIPE recipe = getGasRecipe(recipes, refreshRecipeLookupCache, current);
                return recipe != null && canGasOutputToTank(output, recipe.getOutput().output);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.gas("gas_output", outputTank.get()));
            }
        };
    }

    public static IAERecipeMachineAdapter gasToItem(
          Supplier<Map<GasInput, CrystallizerRecipe>> recipes,
          Supplier<? extends IExtendedGasTank> inputTank,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectGasToItem(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return getGasToItemRecipe(host, recipe, stack, recipes, inputTank.get(), outputSlot.get(), refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                if (getGasToItemRecipe(host, recipe, stack, recipes, inputTank.get(), outputSlot.get(), refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.gas("gas_input", inputTank.get()));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "{} gas input execute failed for {}", machineName, AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank input = inputTank.get();
                OutputInventorySlot output = outputSlot.get();
                if (input == null || output == null || input.getNeeded() <= 0) {
                    return false;
                }
                GasStack stored = input.getGas();
                if (stored == null) {
                    return !getExposedItemRecipes(host).isEmpty();
                }
                CrystallizerRecipe recipe = getGasRecipe(recipes, refreshRecipeLookupCache, stored);
                return recipe != null && AEItemRecipeAdapters.canOutputToSlot(output, recipe.getOutput().output);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.item("item_output", outputSlot.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<ChemicalPairInput, GasOutput, RECIPE>> IAERecipeMachineAdapter gasPairToGas(
          Supplier<Map<ChemicalPairInput, RECIPE>> recipes,
          Supplier<? extends IExtendedGasTank> leftTank,
          Supplier<? extends IExtendedGasTank> rightTank,
          Supplier<? extends IExtendedGasTank> outputTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectChemicalPairToGas(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires two gas conversion inputs", machineName);
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires two gas conversion inputs", machineName);
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getGasPairToGasRecipe(host, recipe, stacks, recipes, leftTank.get(), rightTank.get(), outputTank.get(), refreshRecipeLookupCache,
                      machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getGasPairToGasRecipe(host, recipe, stacks, recipes, leftTank.get(), rightTank.get(), outputTank.get(), refreshRecipeLookupCache,
                      machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.gas("left_gas", leftTank.get()), AERecipePort.gas("right_gas", rightTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} gas input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank left = leftTank.get();
                IExtendedGasTank right = rightTank.get();
                IExtendedGasTank output = outputTank.get();
                if (left == null || right == null || output == null || !AEUpgradeFakeGas.isAvailable()) {
                    return false;
                }
                if (left.getNeeded() <= 0 || right.getNeeded() <= 0 || output.getNeeded() <= 0) {
                    return false;
                }
                GasStack storedLeft = left.getGas();
                GasStack storedRight = right.getGas();
                if (storedLeft == null && storedRight == null) {
                    return !getExposedItemRecipes(host).isEmpty();
                }
                if (storedLeft == null || storedRight == null) {
                    return true;
                }
                RECIPE machineRecipe = getChemicalPairRecipe(recipes, refreshRecipeLookupCache, storedLeft, storedRight);
                return machineRecipe != null && canGasOutputToTank(output, machineRecipe.getOutput().output);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(leftTank.get());
                observer.accept(rightTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.gas("gas_output", outputTank.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<GasAndFluidInput, GasOutput, RECIPE>> IAERecipeMachineAdapter gasFluidToGas(
          Supplier<Map<GasAndFluidInput, RECIPE>> recipes,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends IExtendedFluidTank> fluidTank,
          Supplier<? extends IExtendedGasTank> outputTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectGasFluidToGas(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires generic gas input and generic fluid input", machineName);
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires generic gas input and generic fluid input", machineName);
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getGasFluidToGasRecipe(host, recipe, stacks, recipes, gasTank.get(), fluidTank.get(), outputTank.get(), refreshRecipeLookupCache,
                      machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getGasFluidToGasRecipe(host, recipe, stacks, recipes, gasTank.get(), fluidTank.get(), outputTank.get(), refreshRecipeLookupCache,
                      machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.gas("gas_input", gasTank.get()), AERecipePort.fluid("fluid_input", fluidTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank gas = gasTank.get();
                IExtendedFluidTank fluid = fluidTank.get();
                IExtendedGasTank output = outputTank.get();
                if (gas == null || fluid == null || output == null || !AEUpgradeFakeGas.isAvailable() || !AEUpgradeFakeFluid.isAvailable()) {
                    return false;
                }
                if (gas.getNeeded() <= 0 || fluid.getNeeded() <= 0 || output.getNeeded() <= 0) {
                    return false;
                }
                GasStack storedGas = gas.getGas();
                FluidStack storedFluid = fluid.getFluid();
                if (storedGas == null && storedFluid == null) {
                    return !getExposedItemRecipes(host).isEmpty();
                }
                if (storedGas == null || storedFluid == null) {
                    return true;
                }
                RECIPE machineRecipe = getGasFluidRecipe(recipes, refreshRecipeLookupCache, storedGas, storedFluid);
                return machineRecipe != null && canGasOutputToTank(output, machineRecipe.getOutput().output);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(gasTank.get());
                observer.accept(fluidTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.gas("gas_output", outputTank.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<FluidInput, ChemicalPairOutput, RECIPE>> IAERecipeMachineAdapter fluidToGasPair(
          Supplier<Map<FluidInput, RECIPE>> recipes,
          Supplier<? extends IExtendedFluidTank> inputTank,
          Supplier<? extends IExtendedGasTank> leftOutputTank,
          Supplier<? extends IExtendedGasTank> rightOutputTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectFluidToGasPairRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return getFluidToGasPairRecipe(host, recipe, stack, recipes, inputTank.get(), leftOutputTank.get(), rightOutputTank.get(),
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                if (getFluidToGasPairRecipe(host, recipe, stack, recipes, inputTank.get(), leftOutputTank.get(), rightOutputTank.get(),
                      refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.fluid("fluid_input", inputTank.get()));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "{} input fluid execute failed for {}", machineName, AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedFluidTank input = inputTank.get();
                IExtendedGasTank leftOutput = leftOutputTank.get();
                IExtendedGasTank rightOutput = rightOutputTank.get();
                if (input == null || leftOutput == null || rightOutput == null || !AEUpgradeFakeFluid.isAvailable() || !AEUpgradeFakeGas.isAvailable()) {
                    return false;
                }
                if (input.getNeeded() <= 0 || leftOutput.getNeeded() <= 0 || rightOutput.getNeeded() <= 0) {
                    return false;
                }
                FluidStack storedFluid = input.getFluid();
                if (storedFluid == null) {
                    return !getExposedItemRecipes(host).isEmpty();
                }
                RECIPE machineRecipe = getFluidRecipe(recipes, refreshRecipeLookupCache, storedFluid);
                return machineRecipe != null && canGasPairOutputToTanks(leftOutput, rightOutput, machineRecipe.getOutput());
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.gas("left_gas_output", leftOutputTank.get()),
                      AERecipePort.gas("right_gas_output", rightOutputTank.get()));
            }
        };
    }

    public static IAERecipeMachineAdapter rotaryGasFluid(
          Supplier<Map<RotaryInput, RotaryRecipe>> recipes,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends IExtendedFluidTank> fluidTank,
          IntSupplier mode,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), mode.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                if (mode.getAsInt() == 0) {
                    return AEUpgradeRecipeCache.collectRotaryGasToFluidRecipes(recipes.get());
                }
                return AEUpgradeRecipeCache.collectRotaryFluidToGasRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return mode.getAsInt() == 0 ?
                      getRotaryGasToFluidRecipe(host, recipe, stack, recipes, gasTank.get(), fluidTank.get(), mode.getAsInt(), machineName) != null :
                      getRotaryFluidToGasRecipe(host, recipe, stack, recipes, gasTank.get(), fluidTank.get(), mode.getAsInt(), machineName) != null;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                if (mode.getAsInt() == 0) {
                    if (getRotaryGasToFluidRecipe(host, recipe, stack, recipes, gasTank.get(), fluidTank.get(), mode.getAsInt(), machineName) == null) {
                        return false;
                    }
                    AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.gas("gas_input", gasTank.get()));
                    if (plan == null || !plan.execute()) {
                        return AEItemRecipeAdapters.reject(host, "{} gas input execute failed for {}", machineName, AEUpgradeDebug.stack(stack));
                    }
                    return true;
                }
                if (getRotaryFluidToGasRecipe(host, recipe, stack, recipes, gasTank.get(), fluidTank.get(), mode.getAsInt(), machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.fluid("fluid_input", fluidTank.get()));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "{} fluid input execute failed for {}", machineName, AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank gas = gasTank.get();
                IExtendedFluidTank fluid = fluidTank.get();
                if (gas == null || fluid == null || !AEUpgradeFakeGas.isAvailable() || !AEUpgradeFakeFluid.isAvailable()) {
                    return false;
                }
                if (mode.getAsInt() == 0) {
                    if (gas.getNeeded() <= 0 || fluid.getNeeded() <= 0) {
                        return false;
                    }
                    GasStack storedGas = gas.getGas();
                    if (storedGas == null) {
                        return !getExposedItemRecipes(host).isEmpty();
                    }
                    RotaryRecipe recipe = getRotaryGasToFluidRecipe(recipes, storedGas);
                    return recipe != null && canFluidInputToTank(fluid, recipe.getFluidOutput(storedGas));
                }
                if (fluid.getNeeded() <= 0 || gas.getNeeded() <= 0) {
                    return false;
                }
                FluidStack storedFluid = fluid.getFluid();
                if (storedFluid == null) {
                    return !getExposedItemRecipes(host).isEmpty();
                }
                RotaryRecipe recipe = getRotaryFluidToGasRecipe(recipes, storedFluid);
                return recipe != null && canGasOutputToTank(gas, recipe.getGasOutput(storedFluid));
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(gasTank.get());
                observer.accept(fluidTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return mode.getAsInt() == 0 ? AERecipePort.drainAll(node, AERecipePort.fluid("fluid_output", fluidTank.get())) :
                      AERecipePort.drainAll(node, AERecipePort.gas("gas_output", gasTank.get()));
            }
        };
    }

    public static <RECIPE extends AdvancedMachineRecipe<RECIPE>> IAERecipeMachineAdapter advancedItemGasToItem(
          Supplier<Map<AdvancedMachineInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends GasInventorySlot> gasSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<? extends IExtendedGasTank> gasTank,
          Predicate<Gas> isValidGas,
          BooleanSupplier supportsRecipes,
          IntSupplier gasUsagePerOperation,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), supportsRecipes.getAsBoolean(), gasUsagePerOperation.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                if (!supportsRecipes.getAsBoolean()) {
                    return Collections.emptyList();
                }
                return AEUpgradeRecipeCache.collectAdvancedGasItemRecipes(recipes.get(), gasUsagePerOperation.getAsInt());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "advanced gas machine requires item input and gas conversion input");
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "advanced gas machine requires item input and gas conversion input");
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getAdvancedGasRecipe(host, recipe, stacks, recipes, inputSlot, gasSlot, outputSlot, gasTank, isValidGas, supportsRecipes,
                      gasUsagePerOperation, refreshRecipeLookupCache) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getAdvancedGasRecipe(host, recipe, stacks, recipes, inputSlot, gasSlot, outputSlot, gasTank, isValidGas, supportsRecipes,
                      gasUsagePerOperation, refreshRecipeLookupCache) == null) {
                    return false;
                }
                return acceptItemGasInputs(host, "advanced gas", stacks, inputSlot.get(), gasSlot.get(), gasTank.get(), isValidGas);
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                GasInventorySlot gas = gasSlot.get();
                OutputInventorySlot output = outputSlot.get();
                IExtendedGasTank tank = gasTank.get();
                if (!supportsRecipes.getAsBoolean() || input == null || gas == null || output == null || tank == null) {
                    return false;
                }
                ItemStack currentInput = input.getStack();
                ItemStack currentGasSource = gas.getStack();
                if (currentGasSource.isEmpty()) {
                    return currentInput.isEmpty() || AEItemRecipeAdapters.hasInputRoom(input, currentInput);
                }
                if (!AEItemRecipeAdapters.hasInputRoom(gas, currentGasSource)) {
                    return false;
                }
                GasStack conversion = getGasFromSource(currentGasSource, isValidGas);
                if (conversion == null || !canGasTankAccept(tank, conversion)) {
                    return false;
                }
                if (currentInput.isEmpty()) {
                    return true;
                }
                RECIPE recipe = getRecipe(recipes, refreshRecipeLookupCache, currentInput, conversion.getGas());
                return recipe != null && AEItemRecipeAdapters.hasInputRoom(input, currentInput) &&
                      AEItemRecipeAdapters.canOutputToSlot(output, recipe.getOutput().output);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
                observer.accept(gasSlot.get());
                observer.accept(gasTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.item("item_output", outputSlot.get()));
            }
        };
    }

    public static <RECIPE extends FarmMachineRecipe<RECIPE>> IAERecipeMachineAdapter farmItemGasToItem(
          Supplier<Map<AdvancedMachineInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends GasInventorySlot> gasSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<? extends OutputInventorySlot> secondaryOutputSlot,
          Supplier<? extends IExtendedGasTank> gasTank,
          Predicate<Gas> isValidGas,
          BooleanSupplier supportsRecipes,
          IntSupplier gasUsagePerOperation,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), supportsRecipes.getAsBoolean(), gasUsagePerOperation.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                if (!supportsRecipes.getAsBoolean()) {
                    return Collections.emptyList();
                }
                return AEUpgradeRecipeCache.collectFarmGasItemRecipes(recipes.get(), gasUsagePerOperation.getAsInt());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "farm machine requires item input and gas conversion input");
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "farm machine requires item input and gas conversion input");
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getFarmGasRecipe(host, recipe, stacks, recipes, inputSlot, gasSlot, outputSlot, secondaryOutputSlot, gasTank, isValidGas,
                      supportsRecipes, gasUsagePerOperation, refreshRecipeLookupCache) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getFarmGasRecipe(host, recipe, stacks, recipes, inputSlot, gasSlot, outputSlot, secondaryOutputSlot, gasTank, isValidGas,
                      supportsRecipes, gasUsagePerOperation, refreshRecipeLookupCache) == null) {
                    return false;
                }
                return acceptItemGasInputs(host, "farm", stacks, inputSlot.get(), gasSlot.get(), gasTank.get(), isValidGas);
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                GasInventorySlot gas = gasSlot.get();
                OutputInventorySlot output = outputSlot.get();
                OutputInventorySlot secondaryOutput = secondaryOutputSlot.get();
                IExtendedGasTank tank = gasTank.get();
                if (!supportsRecipes.getAsBoolean() || input == null || gas == null || output == null || secondaryOutput == null || tank == null) {
                    return false;
                }
                ItemStack currentInput = input.getStack();
                ItemStack currentGasSource = gas.getStack();
                if (currentGasSource.isEmpty()) {
                    return currentInput.isEmpty() || AEItemRecipeAdapters.hasInputRoom(input, currentInput);
                }
                if (!AEItemRecipeAdapters.hasInputRoom(gas, currentGasSource)) {
                    return false;
                }
                GasStack conversion = getGasFromSource(currentGasSource, isValidGas);
                if (conversion == null || !canGasTankAccept(tank, conversion)) {
                    return false;
                }
                if (currentInput.isEmpty()) {
                    return true;
                }
                RECIPE recipe = getRecipe(recipes, refreshRecipeLookupCache, currentInput, conversion.getGas());
                return recipe != null && AEItemRecipeAdapters.hasInputRoom(input, currentInput) &&
                      canChanceOutputsToSlots(output, secondaryOutput, recipe.getOutput(), 1);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
                observer.accept(gasSlot.get());
                observer.accept(gasTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.item("item_output", outputSlot.get()),
                      AERecipePort.item("secondary_item_output", secondaryOutputSlot.get()));
            }
        };
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> RECIPE getItemToGasRecipe(
          Supplier<Map<ItemStackInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new ItemStackInput(input), recipes.get());
    }

    private static boolean canGasOutputToTank(IExtendedGasTank tank, GasStack output) {
        return output != null && output.amount > 0 && tank.insert(output.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL) == null;
    }

    private static boolean canFluidInputToTank(IExtendedFluidTank tank, FluidStack input) {
        if (tank == null || input == null || input.getFluid() == null || input.amount <= 0) {
            return false;
        }
        FluidStack remainder = tank.insert(input.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }

    private static boolean canGasPairOutputToTanks(IExtendedGasTank leftTank, IExtendedGasTank rightTank, ChemicalPairOutput output) {
        return output != null && output.isValid() && output.applyOutputs(leftTank, rightTank, false, 1);
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ChemicalPairInput, GasOutput, RECIPE>> RECIPE getGasPairToGasRecipe(IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<ChemicalPairInput, RECIPE>> recipes,
          @Nullable IExtendedGasTank leftTank, @Nullable IExtendedGasTank rightTank, @Nullable IExtendedGasTank outputTank,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (leftTank == null || rightTank == null || outputTank == null) {
            AEItemRecipeAdapters.reject(host, "left, right, or output gas tank is not initialized");
            return null;
        }
        if (!AEUpgradeFakeGas.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE generic gas output is not available");
            return null;
        }
        if (stacks.size() != 2) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} inputs but {} requires 2", stacks.size(), machineName);
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "AE supplied inputs that do not match the exposed {} route", machineName);
            return null;
        }
        GasStack leftGas = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 0, gas -> hasMatchingChemicalPairGas(recipes.get(), gas));
        GasStack rightGas = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, gas -> hasMatchingChemicalPairGas(recipes.get(), gas));
        if (leftGas == null || rightGas == null) {
            AEItemRecipeAdapters.reject(host, "gas sources {} and {} cannot provide conversion gases",
                  AEUpgradeDebug.stack(stacks.get(0)), AEUpgradeDebug.stack(stacks.get(1)));
            return null;
        }
        RECIPE machineRecipe = getChemicalPairRecipe(recipes, refreshRecipeLookupCache, leftGas, rightGas);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for gases {} and {}", machineName, leftGas, rightGas);
            return null;
        }
        int operations = getChemicalPairOperations(machineRecipe.getInput(), leftGas, rightGas);
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "gas sources {} and {} do not provide a whole number of recipe operations", leftGas, rightGas);
            return null;
        }
        GasStack expectedOutput = scaledGasStack(machineRecipe.getOutput().output, operations);
        if (!AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), expectedOutput)) {
            AEItemRecipeAdapters.reject(host, "machine gas output {} does not match exposed output {}",
                  expectedOutput, AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        if (!canGasOutputToTank(leftTank, leftGas) || !canGasOutputToTank(rightTank, rightGas)) {
            AEItemRecipeAdapters.reject(host, "input gas tanks cannot accept {} and {}", leftGas, rightGas);
            return null;
        }
        if (!canGasOutputToTank(outputTank, expectedOutput)) {
            AEItemRecipeAdapters.reject(host, "output gas tank cannot accept {}", expectedOutput);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<GasAndFluidInput, GasOutput, RECIPE>> RECIPE getGasFluidToGasRecipe(IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<GasAndFluidInput, RECIPE>> recipes,
          @Nullable IExtendedGasTank gasTank, @Nullable IExtendedFluidTank fluidTank, @Nullable IExtendedGasTank outputTank,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (gasTank == null || fluidTank == null || outputTank == null) {
            AEItemRecipeAdapters.reject(host, "input gas tank, input fluid tank, or output gas tank is not initialized");
            return null;
        }
        if (!AEUpgradeFakeGas.isAvailable() || !AEUpgradeFakeFluid.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE generic gas/fluid support is not available");
            return null;
        }
        if (stacks.size() != 2) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} inputs but {} requires 2", stacks.size(), machineName);
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "AE supplied inputs that do not match the exposed {} route", machineName);
            return null;
        }
        GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 0, gas -> hasMatchingGasFluidGas(recipes.get(), gas));
        FluidStack fluidInput = AERecipeRouteLegacyIO.getFluidInput(exposedRecipe, stacks, 1);
        if (gasInput == null || fluidInput == null) {
            AEItemRecipeAdapters.reject(host, "{} inputs {} and {} are not generic gas/fluid wrapper stacks",
                  machineName, AEUpgradeDebug.stack(stacks.get(0)), AEUpgradeDebug.stack(stacks.get(1)));
            return null;
        }
        RECIPE machineRecipe = getGasFluidRecipe(recipes, refreshRecipeLookupCache, gasInput, fluidInput);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for gas {} and fluid {}", machineName, gasInput, fluidInput);
            return null;
        }
        int operations = getGasFluidOperations(machineRecipe.getInput(), gasInput, fluidInput);
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "{} inputs do not provide a whole number of gas/fluid operations", machineName);
            return null;
        }
        GasStack expectedOutput = scaledGasStack(machineRecipe.getOutput().output, operations);
        if (!AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), expectedOutput)) {
            AEItemRecipeAdapters.reject(host, "machine gas output {} does not match exposed output {}",
                  expectedOutput, AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        if (!canGasOutputToTank(gasTank, gasInput)) {
            AEItemRecipeAdapters.reject(host, "input gas tank cannot accept {}", gasInput);
            return null;
        }
        if (!canFluidInputToTank(fluidTank, fluidInput)) {
            AEItemRecipeAdapters.reject(host, "input fluid tank cannot accept {}", fluidInput);
            return null;
        }
        if (!canGasOutputToTank(outputTank, expectedOutput)) {
            AEItemRecipeAdapters.reject(host, "output gas tank cannot accept {}", expectedOutput);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<FluidInput, ChemicalPairOutput, RECIPE>> RECIPE getFluidToGasPairRecipe(IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe, ItemStack stack, Supplier<Map<FluidInput, RECIPE>> recipes, @Nullable IExtendedFluidTank inputTank,
          @Nullable IExtendedGasTank leftOutputTank, @Nullable IExtendedGasTank rightOutputTank, Runnable refreshRecipeLookupCache,
          String machineName) {
        if (inputTank == null || leftOutputTank == null || rightOutputTank == null) {
            AEItemRecipeAdapters.reject(host, "input fluid tank or output gas tanks are not initialized");
            return null;
        }
        if (!AEUpgradeFakeFluid.isAvailable() || !AEUpgradeFakeGas.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE generic fluid/gas support is not available");
            return null;
        }
        if (stack.isEmpty()) {
            AEItemRecipeAdapters.reject(host, "AE supplied an empty input stack");
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInput(exposedRecipe, stack)) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack),
                  AEUpgradeDebug.inputStack(exposedRecipe));
            return null;
        }
        FluidStack fluidInput = AERecipeRouteLegacyIO.getFluidInput(exposedRecipe, stack, 0);
        if (fluidInput == null) {
            AEItemRecipeAdapters.reject(host, "{} input {} is not a generic fluid wrapper stack", machineName, AEUpgradeDebug.stack(stack));
            return null;
        }
        RECIPE machineRecipe = getFluidRecipe(recipes, refreshRecipeLookupCache, fluidInput);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for fluid {}", machineName, fluidInput);
            return null;
        }
        FluidStack recipeFluid = machineRecipe.getInput().ingredient;
        if (!fluidInput.isFluidEqual(recipeFluid) || fluidInput.amount < recipeFluid.amount) {
            AEItemRecipeAdapters.reject(host, "{} input does not contain recipe fluid requirement", machineName);
            return null;
        }
        if (!outputsMatch(exposedRecipe, machineRecipe.getOutput())) {
            AEItemRecipeAdapters.reject(host, "machine gas outputs do not match exposed outputs {}", AEUpgradeDebug.outputStacks(exposedRecipe));
            return null;
        }
        if (!canFluidInputToTank(inputTank, fluidInput)) {
            AEItemRecipeAdapters.reject(host, "input fluid tank cannot accept {}", fluidInput);
            return null;
        }
        if (!canGasPairOutputToTanks(leftOutputTank, rightOutputTank, machineRecipe.getOutput())) {
            AEItemRecipeAdapters.reject(host, "output gas tanks cannot accept exposed {} outputs", machineName);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static RotaryRecipe getRotaryGasToFluidRecipe(IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, ItemStack stack,
          Supplier<Map<RotaryInput, RotaryRecipe>> recipes, @Nullable IExtendedGasTank gasTank, @Nullable IExtendedFluidTank fluidTank,
          int mode, String machineName) {
        if (gasTank == null || fluidTank == null) {
            AEItemRecipeAdapters.reject(host, "gas or fluid tank is not initialized");
            return null;
        }
        if (!AEUpgradeFakeGas.isAvailable() || !AEUpgradeFakeFluid.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE generic gas/fluid support is not available");
            return null;
        }
        if (mode != 0) {
            AEItemRecipeAdapters.reject(host, "{} is not in gas to fluid mode", machineName);
            return null;
        }
        if (stack.isEmpty()) {
            AEItemRecipeAdapters.reject(host, "AE supplied an empty input stack");
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInput(exposedRecipe, stack)) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack),
                  AEUpgradeDebug.inputStack(exposedRecipe));
            return null;
        }
        GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stack, gas -> hasMatchingRotaryGas(recipes.get(), gas));
        if (gasInput == null) {
            AEItemRecipeAdapters.reject(host, "{} input {} is not a generic gas wrapper stack", machineName, AEUpgradeDebug.stack(stack));
            return null;
        }
        RotaryRecipe machineRecipe = getRotaryGasToFluidRecipe(recipes, gasInput);
        if (machineRecipe == null || !machineRecipe.hasGasToFluid()) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} gas to fluid recipe for gas {}", machineName, gasInput);
            return null;
        }
        GasStack recipeGas = machineRecipe.getGasInput();
        int operations = getGasOperations(recipeGas, gasInput);
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "{} gas input does not provide a whole number of recipe operations", machineName);
            return null;
        }
        FluidStack fluidOutput = scaledFluidStack(machineRecipe.getFluidOutput(recipeGas), operations);
        if (!AEUpgradeFakeFluid.outputMatches(exposedRecipe.getOutputStack(), fluidOutput)) {
            AEItemRecipeAdapters.reject(host, "machine fluid output {} does not match exposed output {}",
                  fluidOutput, AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        if (!canGasOutputToTank(gasTank, gasInput)) {
            AEItemRecipeAdapters.reject(host, "input gas tank cannot accept {}", gasInput);
            return null;
        }
        if (!canFluidInputToTank(fluidTank, fluidOutput)) {
            AEItemRecipeAdapters.reject(host, "output fluid tank cannot accept {}", fluidOutput);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static RotaryRecipe getRotaryFluidToGasRecipe(IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, ItemStack stack,
          Supplier<Map<RotaryInput, RotaryRecipe>> recipes, @Nullable IExtendedGasTank gasTank, @Nullable IExtendedFluidTank fluidTank,
          int mode, String machineName) {
        if (gasTank == null || fluidTank == null) {
            AEItemRecipeAdapters.reject(host, "gas or fluid tank is not initialized");
            return null;
        }
        if (!AEUpgradeFakeGas.isAvailable() || !AEUpgradeFakeFluid.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE generic gas/fluid support is not available");
            return null;
        }
        if (mode != 1) {
            AEItemRecipeAdapters.reject(host, "{} is not in fluid to gas mode", machineName);
            return null;
        }
        if (stack.isEmpty()) {
            AEItemRecipeAdapters.reject(host, "AE supplied an empty input stack");
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInput(exposedRecipe, stack)) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack),
                  AEUpgradeDebug.inputStack(exposedRecipe));
            return null;
        }
        FluidStack fluidInput = AERecipeRouteLegacyIO.getFluidInput(exposedRecipe, stack, 0);
        if (fluidInput == null) {
            AEItemRecipeAdapters.reject(host, "{} input {} is not a generic fluid wrapper stack", machineName, AEUpgradeDebug.stack(stack));
            return null;
        }
        RotaryRecipe machineRecipe = getRotaryFluidToGasRecipe(recipes, fluidInput);
        if (machineRecipe == null || !machineRecipe.hasFluidToGas()) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} fluid to gas recipe for fluid {}", machineName, fluidInput);
            return null;
        }
        FluidStack recipeFluid = machineRecipe.getFluidInput();
        int operations = getFluidOperations(recipeFluid, fluidInput);
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "{} fluid input does not provide a whole number of recipe operations", machineName);
            return null;
        }
        GasStack gasOutput = scaledGasStack(machineRecipe.getGasOutput(recipeFluid), operations);
        if (!AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), gasOutput)) {
            AEItemRecipeAdapters.reject(host, "machine gas output {} does not match exposed output {}",
                  gasOutput, AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        if (!canFluidInputToTank(fluidTank, fluidInput)) {
            AEItemRecipeAdapters.reject(host, "input fluid tank cannot accept {}", fluidInput);
            return null;
        }
        if (!canGasOutputToTank(gasTank, gasOutput)) {
            AEItemRecipeAdapters.reject(host, "output gas tank cannot accept {}", gasOutput);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static RotaryRecipe getRotaryGasToFluidRecipe(Supplier<Map<RotaryInput, RotaryRecipe>> recipes, GasStack input) {
        if (input == null || input.getGas() == null || input.amount <= 0) {
            return null;
        }
        for (RotaryRecipe recipe : recipes.get().values()) {
            if (recipe.test(input)) {
                return recipe.copy();
            }
        }
        return null;
    }

    @Nullable
    private static RotaryRecipe getRotaryFluidToGasRecipe(Supplier<Map<RotaryInput, RotaryRecipe>> recipes, FluidStack input) {
        if (input == null || input.getFluid() == null || input.amount <= 0) {
            return null;
        }
        for (RotaryRecipe recipe : recipes.get().values()) {
            if (recipe.test(input)) {
                return recipe.copy();
            }
        }
        return null;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<GasInput, GasOutput, RECIPE>> RECIPE getGasToGasRecipe(IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe, ItemStack stack, Supplier<Map<GasInput, RECIPE>> recipes, @Nullable IExtendedGasTank inputTank,
          @Nullable IExtendedGasTank outputTank, Runnable refreshRecipeLookupCache, String machineName) {
        if (inputTank == null || outputTank == null) {
            AEItemRecipeAdapters.reject(host, "input or output gas tank is not initialized");
            return null;
        }
        if (!AEUpgradeFakeGas.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE generic gas output is not available");
            return null;
        }
        if (stack.isEmpty()) {
            AEItemRecipeAdapters.reject(host, "AE supplied an empty input stack");
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInput(exposedRecipe, stack)) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack),
                  AEUpgradeDebug.inputStack(exposedRecipe));
            return null;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stack, gas -> hasMatchingGas(recipes.get(), gas));
        if (gasStack == null) {
            AEItemRecipeAdapters.reject(host, "gas source {} cannot provide a supported conversion gas", AEUpgradeDebug.stack(stack));
            return null;
        }
        RECIPE machineRecipe = getGasRecipe(recipes, refreshRecipeLookupCache, gasStack);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for gas {}", machineName, gasStack);
            return null;
        }
        GasStack requiredGas = machineRecipe.getInput().ingredient;
        if (requiredGas == null || requiredGas.amount <= 0 || gasStack.amount % requiredGas.amount != 0) {
            AEItemRecipeAdapters.reject(host, "gas source {} does not provide a whole number of recipe operations", gasStack);
            return null;
        }
        int operations = gasStack.amount / requiredGas.amount;
        GasStack expectedOutput = scaledGasStack(machineRecipe.getOutput().output, operations);
        if (!AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), expectedOutput)) {
            AEItemRecipeAdapters.reject(host, "machine gas output {} does not match exposed output {}",
                  expectedOutput, AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        if (!canGasOutputToTank(inputTank, gasStack)) {
            AEItemRecipeAdapters.reject(host, "input gas tank cannot accept {}", gasStack);
            return null;
        }
        if (!canGasOutputToTank(outputTank, expectedOutput)) {
            AEItemRecipeAdapters.reject(host, "output gas tank cannot accept {}", expectedOutput);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static CrystallizerRecipe getGasToItemRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, ItemStack stack, Supplier<Map<GasInput, CrystallizerRecipe>> recipes,
          @Nullable IExtendedGasTank inputTank, @Nullable OutputInventorySlot outputSlot, Runnable refreshRecipeLookupCache, String machineName) {
        if (inputTank == null || outputSlot == null) {
            AEItemRecipeAdapters.reject(host, "input gas tank or output slot is not initialized");
            return null;
        }
        if (stack.isEmpty()) {
            AEItemRecipeAdapters.reject(host, "AE supplied an empty input stack");
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInput(exposedRecipe, stack)) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack),
                  AEUpgradeDebug.inputStack(exposedRecipe));
            return null;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stack, gas -> hasMatchingGas(recipes.get(), gas));
        if (gasStack == null) {
            AEItemRecipeAdapters.reject(host, "gas conversion input {} cannot provide a supported gas", AEUpgradeDebug.stack(stack));
            return null;
        }
        if (!canGasOutputToTank(inputTank, gasStack)) {
            AEItemRecipeAdapters.reject(host, "gas tank cannot accept {}", gasStack);
            return null;
        }
        CrystallizerRecipe machineRecipe = getGasRecipe(recipes, refreshRecipeLookupCache, gasStack);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for gas {}", machineName, gasStack);
            return null;
        }
        int operations = getOutputOperations(machineRecipe.getOutput().output, exposedRecipe.getOutputStack());
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "machine output {} does not match exposed output {}",
                  AEUpgradeDebug.stack(machineRecipe.getOutput().output), AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        GasStack requiredGas = machineRecipe.getInput().ingredient;
        if (requiredGas == null || (long) requiredGas.amount * operations != gasStack.amount) {
            AEItemRecipeAdapters.reject(host, "gas conversion input {} provides {} but route requires {}",
                  AEUpgradeDebug.stack(stack), gasStack.amount, requiredGas == null ? 0 : (long) requiredGas.amount * operations);
            return null;
        }
        if (!AEItemRecipeAdapters.canOutputToSlot(outputSlot, exposedRecipe.getOutputStack())) {
            AEItemRecipeAdapters.reject(host, "output slot cannot accept {}", AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <OUTPUT extends MachineOutput<?>, RECIPE extends MachineRecipe<GasInput, OUTPUT, RECIPE>> RECIPE getGasRecipe(
          Supplier<Map<GasInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, GasStack input) {
        if (input == null || input.getGas() == null || input.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new GasInput(input), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ChemicalPairInput, GasOutput, RECIPE>> RECIPE getChemicalPairRecipe(
          Supplier<Map<ChemicalPairInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, GasStack leftGas, GasStack rightGas) {
        if (leftGas == null || leftGas.getGas() == null || leftGas.amount <= 0 ||
            rightGas == null || rightGas.getGas() == null || rightGas.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new ChemicalPairInput(leftGas, rightGas), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<GasAndFluidInput, GasOutput, RECIPE>> RECIPE getGasFluidRecipe(
          Supplier<Map<GasAndFluidInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, GasStack gasInput, FluidStack fluidInput) {
        if (gasInput == null || gasInput.getGas() == null || gasInput.amount <= 0 ||
            fluidInput == null || fluidInput.getFluid() == null || fluidInput.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new GasAndFluidInput(gasInput, fluidInput), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<FluidInput, ChemicalPairOutput, RECIPE>> RECIPE getFluidRecipe(
          Supplier<Map<FluidInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, FluidStack fluidInput) {
        if (fluidInput == null || fluidInput.getFluid() == null || fluidInput.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new FluidInput(fluidInput), recipes.get());
    }

    private static boolean hasMatchingGas(Map<GasInput, ?> recipes, @Nullable Gas gas) {
        if (gas == null) {
            return false;
        }
        for (GasInput input : recipes.keySet()) {
            GasStack stack = input.ingredient;
            if (stack != null && stack.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingChemicalPairGas(Map<ChemicalPairInput, ?> recipes, @Nullable Gas gas) {
        if (gas == null) {
            return false;
        }
        for (ChemicalPairInput input : recipes.keySet()) {
            if (input.leftGas != null && input.leftGas.getGas() == gas || input.rightGas != null && input.rightGas.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingGasFluidGas(Map<GasAndFluidInput, ?> recipes, @Nullable Gas gas) {
        if (gas == null) {
            return false;
        }
        for (GasAndFluidInput input : recipes.keySet()) {
            if (input.ingredientGas != null && input.ingredientGas.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingRotaryGas(Map<RotaryInput, ? extends RotaryRecipe> recipes, @Nullable Gas gas) {
        if (gas == null) {
            return false;
        }
        for (RotaryRecipe recipe : recipes.values()) {
            GasStack input = recipe.hasGasToFluid() ? recipe.getGasInput() : null;
            if (input != null && input.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static GasStack scaledGasStack(GasStack stack, int multiplier) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0 || multiplier <= 0) {
            return null;
        }
        long amount = (long) stack.amount * multiplier;
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        return stack.copy().withAmount((int) amount);
    }

    @Nullable
    private static FluidStack scaledFluidStack(FluidStack stack, int multiplier) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0 || multiplier <= 0) {
            return null;
        }
        long amount = (long) stack.amount * multiplier;
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        FluidStack scaled = stack.copy();
        scaled.amount = (int) amount;
        return scaled;
    }

    private static int getGasAmount(@Nullable GasStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    private static int getFluidAmount(@Nullable FluidStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    private static boolean outputsMatch(AEExposedRecipe exposedRecipe, ChemicalPairOutput output) {
        List<ItemStack> outputs = exposedRecipe.getOutputStacks();
        return outputs.size() == 2 && output != null && output.isValid() &&
              AEUpgradeFakeGas.outputMatches(outputs.get(0), output.leftGas, exposedRecipe.getCraftAmount()) &&
              AEUpgradeFakeGas.outputMatches(outputs.get(1), output.rightGas, exposedRecipe.getCraftAmount());
    }

    private static int getGasFluidOperations(GasAndFluidInput input, GasStack gas, FluidStack fluid) {
        if (input.ingredientGas == null || input.ingredientFluid == null || gas == null || fluid == null ||
            !gas.isGasEqual(input.ingredientGas) || !fluid.isFluidEqual(input.ingredientFluid)) {
            return -1;
        }
        return getPairOperations(input.ingredientGas.amount, gas.amount, input.ingredientFluid.amount, fluid.amount);
    }

    private static int getGasOperations(@Nullable GasStack required, GasStack provided) {
        if (required == null || provided == null || !provided.isGasEqual(required)) {
            return -1;
        }
        return getSingleOperations(required.amount, provided.amount);
    }

    private static int getFluidOperations(@Nullable FluidStack required, FluidStack provided) {
        if (required == null || provided == null || !provided.isFluidEqual(required)) {
            return -1;
        }
        return getSingleOperations(required.amount, provided.amount);
    }

    private static int getChemicalPairOperations(ChemicalPairInput input, GasStack leftGas, GasStack rightGas) {
        if (input.leftGas == null || input.rightGas == null || leftGas == null || rightGas == null) {
            return -1;
        }
        if (leftGas.isGasEqual(input.leftGas) && rightGas.isGasEqual(input.rightGas)) {
            return getGasPairOperations(input.leftGas, leftGas, input.rightGas, rightGas);
        }
        if (leftGas.isGasEqual(input.rightGas) && rightGas.isGasEqual(input.leftGas)) {
            return getGasPairOperations(input.rightGas, leftGas, input.leftGas, rightGas);
        }
        return -1;
    }

    private static int getGasPairOperations(GasStack leftRequired, GasStack leftProvided, GasStack rightRequired, GasStack rightProvided) {
        if (leftRequired.amount <= 0 || rightRequired.amount <= 0 || leftProvided.amount % leftRequired.amount != 0 ||
            rightProvided.amount % rightRequired.amount != 0) {
            return -1;
        }
        int leftOperations = leftProvided.amount / leftRequired.amount;
        int rightOperations = rightProvided.amount / rightRequired.amount;
        return leftOperations == rightOperations ? leftOperations : -1;
    }

    private static int getPairOperations(int firstRequired, int firstProvided, int secondRequired, int secondProvided) {
        int firstOperations = getSingleOperations(firstRequired, firstProvided);
        int secondOperations = getSingleOperations(secondRequired, secondProvided);
        return firstOperations > 0 && firstOperations == secondOperations ? firstOperations : -1;
    }

    private static int getSingleOperations(int required, int provided) {
        return required > 0 && provided > 0 && provided % required == 0 ? provided / required : -1;
    }

    @Nullable
    private static <RECIPE extends AdvancedMachineRecipe<RECIPE>> RECIPE getAdvancedGasRecipe(IAEItemRecipeHost host, AEExposedRecipe exposedRecipe,
          List<ItemStack> stacks, Supplier<Map<AdvancedMachineInput, RECIPE>> recipes, Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends GasInventorySlot> gasSlot, Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<? extends IExtendedGasTank> gasTank, Predicate<Gas> isValidGas, BooleanSupplier supportsRecipes,
          IntSupplier gasUsagePerOperation, Runnable refreshRecipeLookupCache) {
        InputInventorySlot input = inputSlot.get();
        GasInventorySlot gas = gasSlot.get();
        OutputInventorySlot output = outputSlot.get();
        IExtendedGasTank tank = gasTank.get();
        RECIPE recipe = getItemGasRecipe(host, "advanced gas", exposedRecipe, stacks, recipes, input, gas, output, tank, isValidGas, supportsRecipes,
              gasUsagePerOperation, refreshRecipeLookupCache);
        if (recipe == null) {
            return null;
        }
        if (!AEItemRecipeAdapters.canOutputToSlot(output, exposedRecipe.getOutputStack())) {
            AEItemRecipeAdapters.reject(host, "output slot cannot accept {}", AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        return recipe;
    }

    @Nullable
    private static <RECIPE extends FarmMachineRecipe<RECIPE>> RECIPE getFarmGasRecipe(IAEItemRecipeHost host, AEExposedRecipe exposedRecipe,
          List<ItemStack> stacks, Supplier<Map<AdvancedMachineInput, RECIPE>> recipes, Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends GasInventorySlot> gasSlot, Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<? extends OutputInventorySlot> secondaryOutputSlot, Supplier<? extends IExtendedGasTank> gasTank, Predicate<Gas> isValidGas,
          BooleanSupplier supportsRecipes, IntSupplier gasUsagePerOperation, Runnable refreshRecipeLookupCache) {
        InputInventorySlot input = inputSlot.get();
        GasInventorySlot gas = gasSlot.get();
        OutputInventorySlot output = outputSlot.get();
        OutputInventorySlot secondaryOutput = secondaryOutputSlot.get();
        IExtendedGasTank tank = gasTank.get();
        RECIPE recipe = getItemGasRecipe(host, "farm", exposedRecipe, stacks, recipes, input, gas, output, tank, isValidGas, supportsRecipes,
              gasUsagePerOperation, refreshRecipeLookupCache);
        if (recipe == null) {
            return null;
        }
        int operations = getOutputOperations(recipe.getOutput().getMainOutput(), exposedRecipe.getOutputStack());
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "machine output {} does not match exposed output {}",
                  AEUpgradeDebug.stack(recipe.getOutput().getMainOutput()), AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        if (!canChanceOutputsToSlots(output, secondaryOutput, recipe.getOutput(), operations)) {
            AEItemRecipeAdapters.reject(host, "output slots cannot accept farm route output {}", AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        return recipe;
    }

    @Nullable
    private static <OUTPUT extends MachineOutput<?>, RECIPE extends MachineRecipe<AdvancedMachineInput, OUTPUT, RECIPE>> RECIPE getItemGasRecipe(IAEItemRecipeHost host,
          String machineName, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<AdvancedMachineInput, RECIPE>> recipes,
          @Nullable InputInventorySlot inputSlot, @Nullable GasInventorySlot gasSlot, @Nullable OutputInventorySlot outputSlot,
          @Nullable IExtendedGasTank gasTank, Predicate<Gas> isValidGas, BooleanSupplier supportsRecipes, IntSupplier gasUsagePerOperation,
          Runnable refreshRecipeLookupCache) {
        if (!supportsRecipes.getAsBoolean()) {
            AEItemRecipeAdapters.reject(host, "{} recipe exposure is not supported for this machine", machineName);
            return null;
        }
        if (inputSlot == null || gasSlot == null || outputSlot == null || gasTank == null) {
            AEItemRecipeAdapters.reject(host, "input, gas, output slot, or gas tank is not initialized");
            return null;
        }
        if (stacks.size() != 2) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} inputs but {} machine requires 2", stacks.size(), machineName);
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "AE supplied inputs that do not match the exposed {} route", machineName);
            return null;
        }
        ItemStack gasSource = stacks.get(1);
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, isValidGas);
        if (gasStack == null) {
            AEItemRecipeAdapters.reject(host, "gas source {} cannot provide a supported conversion gas", AEUpgradeDebug.stack(gasSource));
            return null;
        }
        if (!canGasTankAccept(gasTank, gasStack)) {
            AEItemRecipeAdapters.reject(host, "gas tank cannot accept {}", gasStack);
            return null;
        }
        ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(inputSlot, stacks.get(0));
        RECIPE machineRecipe = getRecipe(recipes, refreshRecipeLookupCache, simulatedInput, gasStack.getGas());
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for simulated input {} and gas {}",
                  machineName, AEUpgradeDebug.stack(simulatedInput), gasStack.getGas());
            return null;
        }
        int operations = getOutputOperations(getPrimaryOutput(machineRecipe), exposedRecipe.getOutputStack());
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "machine output {} does not match exposed output {}",
                  AEUpgradeDebug.stack(getPrimaryOutput(machineRecipe)), AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        ItemStack requiredInput = scaledStack(machineRecipe.getInput().itemStack, operations);
        if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
            AEItemRecipeAdapters.reject(host, "simulated input {} does not contain Mekanism recipe ingredient {}",
                  AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(requiredInput));
            return null;
        }
        long requiredGasLong = (long) gasUsagePerOperation.getAsInt() * operations;
        if (requiredGasLong <= 0 || requiredGasLong > Integer.MAX_VALUE || gasStack.amount != (int) requiredGasLong) {
            AEItemRecipeAdapters.reject(host, "gas source {} provides {} but route requires {}", AEUpgradeDebug.stack(gasSource), gasStack.amount,
                  requiredGasLong);
            return null;
        }
        ItemStack inputRemainder = inputSlot.insertItem(stacks.get(0).copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        GasStack fakeGasInput = getFakeGasInput(gasSource, isValidGas);
        if (fakeGasInput != null) {
            GasStack gasRemainder = gasTank.insert(fakeGasInput.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
            if (!inputRemainder.isEmpty() || gasRemainder != null && gasRemainder.amount > 0) {
                AEItemRecipeAdapters.reject(host, "input simulation left item remainder {} or gas remainder {}",
                      AEUpgradeDebug.stack(inputRemainder), gasRemainder);
                return null;
            }
        } else {
            ItemStack gasRemainder = gasSlot.insertItem(gasSource.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
            if (!inputRemainder.isEmpty() || !gasRemainder.isEmpty()) {
                AEItemRecipeAdapters.reject(host, "input simulation left remainders {} and {}", AEUpgradeDebug.stack(inputRemainder),
                      AEUpgradeDebug.stack(gasRemainder));
                return null;
            }
        }
        return machineRecipe;
    }

    private static boolean acceptItemGasInputs(IAEItemRecipeHost host, String machineName, List<ItemStack> stacks,
          @Nullable InputInventorySlot inputSlot, @Nullable GasInventorySlot gasSlot, @Nullable IExtendedGasTank gasTank, Predicate<Gas> isValidGas) {
        if (inputSlot == null || gasSlot == null || gasTank == null || stacks.size() != 2) {
            return false;
        }
        GasStack fakeGasInput = getFakeGasInput(stacks.get(1), isValidGas);
        AERecipeTransferPlan plan = AERecipeTransferPlan.create()
              .add(AERecipePort.item("item_input", inputSlot), AERecipeRouteStack.item("item_input", stacks.get(0)));
        if (fakeGasInput != null) {
            if (!plan.add(AERecipePort.gas("gas_input", gasTank), AERecipeRouteStack.gas("gas_input", fakeGasInput)).execute()) {
                return AEItemRecipeAdapters.reject(host, "atomic {} generic gas input execute failed for {}", machineName,
                      AEUpgradeDebug.stacks(stacks));
            }
            return true;
        }
        if (!plan.add(AERecipePort.item("gas_slot_input", gasSlot), AERecipeRouteStack.item("gas_slot_input", stacks.get(1))).execute()) {
            return AEItemRecipeAdapters.reject(host, "atomic input execute failed for {}", AEUpgradeDebug.stacks(stacks));
        }
        return true;
    }

    @Nullable
    private static <OUTPUT extends MachineOutput<?>, RECIPE extends MachineRecipe<AdvancedMachineInput, OUTPUT, RECIPE>> RECIPE getRecipe(
          Supplier<Map<AdvancedMachineInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, ItemStack input, @Nullable Gas gas) {
        if (input.isEmpty() || gas == null) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new AdvancedMachineInput(input, gas), recipes.get());
    }

    @Nullable
    private static GasStack getGasFromSource(ItemStack stack, Predicate<Gas> isValidGas) {
        if (stack.isEmpty()) {
            return null;
        }
        GasStack fakeGasInput = getFakeGasInput(stack, isValidGas);
        if (fakeGasInput != null) {
            return fakeGasInput;
        }
        if (GasInventorySlot.isGasContainerItem(stack)) {
            return null;
        }
        GasStack perItem = GasConversionHandler.getConversionGas(stack, isValidGas);
        if (perItem == null || perItem.amount <= 0 || perItem.getGas() == null) {
            return null;
        }
        long amount = (long) perItem.amount * stack.getCount();
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        return new GasStack(perItem.getGas(), (int) amount);
    }

    @Nullable
    private static GasStack getFakeGasInput(ItemStack stack, Predicate<Gas> isValidGas) {
        GasStack fakeGasInput = AEUpgradeFakeGas.unpackInput(stack);
        return fakeGasInput != null && isValidGas.test(fakeGasInput.getGas()) ? fakeGasInput.copy() : null;
    }

    private static boolean canGasTankAccept(IExtendedGasTank gasTank, GasStack stack) {
        return stack != null && stack.getGas() != null && gasTank.canReceiveType(stack.getGas());
    }

    private static int getOutputOperations(ItemStack recipeOutput, ItemStack exposedOutput) {
        if (recipeOutput.isEmpty() || exposedOutput.isEmpty() || !ItemHandlerHelper.canItemStacksStack(recipeOutput, exposedOutput)) {
            return -1;
        }
        int recipeCount = recipeOutput.getCount();
        return recipeCount > 0 && exposedOutput.getCount() % recipeCount == 0 ? exposedOutput.getCount() / recipeCount : -1;
    }

    private static <OUTPUT extends MachineOutput<?>, RECIPE extends MachineRecipe<AdvancedMachineInput, OUTPUT, RECIPE>> ItemStack getPrimaryOutput(RECIPE recipe) {
        OUTPUT output = recipe.getOutput();
        if (output instanceof mekanism.common.recipe.outputs.ItemStackOutput itemOutput) {
            return itemOutput.output;
        } else if (output instanceof ChanceOutput chanceOutput) {
            return chanceOutput.getMainOutput();
        }
        return ItemStack.EMPTY;
    }

    private static boolean canChanceOutputsToSlots(OutputInventorySlot outputSlot, OutputInventorySlot secondaryOutputSlot, ChanceOutput output,
          int operations) {
        ItemStack primaryOutput = scaledStack(output.getMainOutput(), operations);
        if (!primaryOutput.isEmpty() && !AEItemRecipeAdapters.canOutputToSlot(outputSlot, primaryOutput)) {
            return false;
        }
        ItemStack secondaryOutput = scaledStack(output.getMaxSecondaryOutput(), operations);
        return secondaryOutput.isEmpty() || AEItemRecipeAdapters.canOutputToSlot(secondaryOutputSlot, secondaryOutput);
    }

    private static ItemStack scaledStack(ItemStack stack, int multiplier) {
        return AERecipeStacks.scale(stack, multiplier);
    }
}
