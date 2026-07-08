package mekceuaeupgrade.common.adapter;

import mekceuaeupgrade.common.config.AEItemStackKey;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AERecipeStacks;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteCollectors;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteLegacyIO;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;
import mekceuaeupgrade.common.transfer.AERecipePort;
import mekceuaeupgrade.common.transfer.AERecipeTransferPlan;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.ChemicalGasInput;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.FluidOutput;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.recipe.outputs.MachineOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AEMoreMachineRecipeAdapters {

    private AEMoreMachineRecipeAdapters() {
    }

    public static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> IAERecipeMachineAdapter multiItemToGas(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends List<? extends IInventorySlot>> inputSlots,
          Supplier<? extends IExtendedGasTank[]> outputTanks,
          IntSupplier processCount,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), processCount.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectItemToGas(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return getMultiItemToGasTarget(host, recipe, stack, recipes, inputSlots.get(), outputTanks.get(), processCount.getAsInt(),
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                ItemGasTarget<RECIPE> target = getMultiItemToGasTarget(host, recipe, stack, recipes, inputSlots.get(), outputTanks.get(),
                      processCount.getAsInt(), refreshRecipeLookupCache, machineName);
                if (target == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.item("item_input", target.inputSlot));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "{} input item execute failed for {}", machineName, AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                List<? extends IInventorySlot> slots = inputSlots.get();
                IExtendedGasTank[] tanks = outputTanks.get();
                int count = processCount.getAsInt();
                if (slots == null || tanks == null || !AEUpgradeFakeGas.isAvailable()) {
                    return false;
                }
                for (int process = 0; process < count; process++) {
                    IInventorySlot slot = getSlot(slots, process);
                    IExtendedGasTank tank = getTank(tanks, process);
                    if (slot == null || tank == null || tank.getNeeded() <= 0) {
                        continue;
                    }
                    ItemStack current = slot.getStack();
                    if (current.isEmpty()) {
                        return !getExposedItemRecipes(host).isEmpty();
                    }
                    RECIPE recipe = getItemRecipe(recipes, refreshRecipeLookupCache, current);
                    if (recipe != null && AEItemRecipeAdapters.hasInputRoom(slot, current) && canGasStackInsert(tank, recipe.getOutput().output)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return drainGasTanks(node, outputTanks.get(), processCount.getAsInt());
            }
        };
    }

    public static IAERecipeMachineAdapter multiGasToItem(
          Supplier<Map<GasInput, CrystallizerRecipe>> recipes,
          Supplier<? extends IExtendedGasTank[]> inputTanks,
          Supplier<? extends List<? extends IInventorySlot>> outputSlots,
          IntSupplier processCount,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), processCount.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectGasToItem(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return getMultiGasToItemTarget(host, recipe, stack, recipes, inputTanks.get(), outputSlots.get(), processCount.getAsInt(),
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                GasItemTarget<CrystallizerRecipe> target = getMultiGasToItemTarget(host, recipe, stack, recipes, inputTanks.get(),
                      outputSlots.get(), processCount.getAsInt(), refreshRecipeLookupCache, machineName);
                if (target == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInput(recipe, stack, AERecipePort.gas("gas_input", target.inputTank));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "{} input gas execute failed for {}", machineName, AEUpgradeDebug.stack(stack));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank[] tanks = inputTanks.get();
                List<? extends IInventorySlot> slots = outputSlots.get();
                int count = processCount.getAsInt();
                if (tanks == null || slots == null || (!AEUpgradeFakeGas.isAvailable() && !hasGasConversionRecipes(recipes.get()))) {
                    return false;
                }
                for (int process = 0; process < count; process++) {
                    IExtendedGasTank tank = getTank(tanks, process);
                    IInventorySlot output = getSlot(slots, process);
                    if (tank == null || output == null || tank.getNeeded() <= 0) {
                        continue;
                    }
                    GasStack current = tank.getGas();
                    if (current == null) {
                        return !getExposedItemRecipes(host).isEmpty();
                    }
                    CrystallizerRecipe recipe = getGasRecipe(recipes, refreshRecipeLookupCache, current);
                    if (recipe != null && AEItemRecipeAdapters.canOutputToSlot(output, recipe.getOutput().output)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return drainItemSlots(node, outputSlots.get(), processCount.getAsInt());
            }
        };
    }

    public static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> IAERecipeMachineAdapter multiItemGasToGas(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends List<? extends IInventorySlot>> inputSlots,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends IExtendedGasTank[]> outputTanks,
          IntSupplier processCount,
          Supplier<Gas> gasType,
          Predicate<Gas> isValidGas,
          IntSupplier gasUsagePerOperation,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), processCount.getAsInt(), gasUsagePerOperation.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectItemGasToGas(
                      recipes.get(), gasType.get(), gasUsagePerOperation.getAsInt()));
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getMultiItemGasToGasTarget(host, recipe, stacks, recipes, inputSlots.get(), gasTank.get(), outputTanks.get(),
                      processCount.getAsInt(), isValidGas, gasUsagePerOperation, refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                ItemGasTarget<RECIPE> target = getMultiItemGasToGasTarget(host, recipe, stacks, recipes, inputSlots.get(), gasTank.get(),
                      outputTanks.get(), processCount.getAsInt(), isValidGas, gasUsagePerOperation, refreshRecipeLookupCache, machineName);
                if (target == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.item("item_input", target.inputSlot), AERecipePort.gas("gas_input", gasTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                List<? extends IInventorySlot> slots = inputSlots.get();
                IExtendedGasTank sharedGas = gasTank.get();
                IExtendedGasTank[] tanks = outputTanks.get();
                int count = processCount.getAsInt();
                if (slots == null || sharedGas == null || tanks == null || !AEUpgradeFakeGas.isAvailable()) {
                    return false;
                }
                for (int process = 0; process < count; process++) {
                    IInventorySlot slot = getSlot(slots, process);
                    IExtendedGasTank output = getTank(tanks, process);
                    if (slot == null || output == null || output.getNeeded() <= 0) {
                        continue;
                    }
                    ItemStack current = slot.getStack();
                    if (current.isEmpty()) {
                        return sharedGas.getNeeded() > 0 && !getExposedItemRecipes(host).isEmpty();
                    }
                    RECIPE recipe = getItemRecipe(recipes, refreshRecipeLookupCache, current);
                    if (recipe != null && AEItemRecipeAdapters.hasInputRoom(slot, current) && canGasStackInsert(output, recipe.getOutput().output)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return drainGasTanks(node, outputTanks.get(), processCount.getAsInt());
            }
        };
    }

    public static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> IAERecipeMachineAdapter itemGasToItemNoGasSlot(
          Supplier<Map<NucleosynthesizerInput, RECIPE>> recipes,
          Supplier<? extends IInventorySlot> inputSlot,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends IInventorySlot> outputSlot,
          Predicate<Gas> isValidGas,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectNucleosynthesizerGasToItem(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getItemGasToItemRecipe(host, recipe, stacks, recipes, inputSlot.get(), gasTank.get(), outputSlot.get(), isValidGas,
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getItemGasToItemRecipe(host, recipe, stacks, recipes, inputSlot.get(), gasTank.get(), outputSlot.get(), isValidGas,
                      refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.item("item_input", inputSlot.get()), AERecipePort.gas("gas_input", gasTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IInventorySlot input = inputSlot.get();
                IExtendedGasTank gas = gasTank.get();
                IInventorySlot output = outputSlot.get();
                if (input == null || gas == null || output == null) {
                    return false;
                }
                ItemStack currentInput = input.getStack();
                GasStack currentGas = gas.getGas();
                if (currentInput.isEmpty()) {
                    return gas.getNeeded() > 0 || currentGas != null;
                }
                if (currentGas == null) {
                    return gas.getNeeded() > 0 && AEItemRecipeAdapters.hasInputRoom(input, currentInput);
                }
                RECIPE recipe = getNucleosynthesizerRecipe(recipes, refreshRecipeLookupCache, currentInput, currentGas);
                return recipe != null && AEItemRecipeAdapters.hasInputRoom(input, currentInput) &&
                      AEItemRecipeAdapters.canOutputToSlot(output, recipe.getOutput().output);
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.item("item_output", outputSlot.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> IAERecipeMachineAdapter gasGasToGas(
          Supplier<Map<ChemicalGasInput, RECIPE>> recipes,
          Supplier<? extends IExtendedGasTank> inputTank,
          Supplier<? extends IExtendedGasTank> uuTank,
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
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectChemicalGasToGas(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getGasGasToGasRecipe(host, recipe, stacks, recipes, inputTank.get(), uuTank.get(), outputTank.get(), refreshRecipeLookupCache,
                      machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getGasGasToGasRecipe(host, recipe, stacks, recipes, inputTank.get(), uuTank.get(), outputTank.get(), refreshRecipeLookupCache,
                      machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.gas("left_gas", inputTank.get()), AERecipePort.gas("right_gas", uuTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} gas input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank left = inputTank.get();
                IExtendedGasTank right = uuTank.get();
                IExtendedGasTank output = outputTank.get();
                if (left == null || right == null || output == null || !AEUpgradeFakeGas.isAvailable() || output.getNeeded() <= 0) {
                    return false;
                }
                GasStack leftGas = left.getGas();
                GasStack rightGas = right.getGas();
                if (leftGas == null || rightGas == null) {
                    return left.getNeeded() > 0 || right.getNeeded() > 0;
                }
                RECIPE recipe = getChemicalGasRecipe(recipes, refreshRecipeLookupCache, leftGas, rightGas);
                return recipe != null && canGasStackInsert(output, recipe.getOutput().output);
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.gas("gas_output", outputTank.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> IAERecipeMachineAdapter gasFluidToFluid(
          Supplier<Map<GasAndFluidInput, RECIPE>> recipes,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends IExtendedFluidTank> inputFluidTank,
          Supplier<? extends IExtendedFluidTank> outputFluidTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectGasFluidToFluid(recipes.get()));
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getGasFluidToFluidRecipe(host, recipe, stacks, recipes, gasTank.get(), inputFluidTank.get(), outputFluidTank.get(),
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getGasFluidToFluidRecipe(host, recipe, stacks, recipes, gasTank.get(), inputFluidTank.get(), outputFluidTank.get(),
                      refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.gas("gas_input", gasTank.get()), AERecipePort.fluid("fluid_input", inputFluidTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                IExtendedGasTank gas = gasTank.get();
                IExtendedFluidTank inputFluid = inputFluidTank.get();
                IExtendedFluidTank outputFluid = outputFluidTank.get();
                if (gas == null || inputFluid == null || outputFluid == null || !AEUpgradeFakeFluid.isAvailable() ||
                    outputFluid.getNeeded() <= 0) {
                    return false;
                }
                GasStack storedGas = gas.getGas();
                FluidStack storedFluid = inputFluid.getFluid();
                if (storedGas == null || storedFluid == null) {
                    return gas.getNeeded() > 0 || inputFluid.getNeeded() > 0;
                }
                RECIPE recipe = getGasFluidRecipe(recipes, refreshRecipeLookupCache, storedGas, storedFluid);
                return recipe != null && canFluidStackInsert(outputFluid, recipe.getOutput().output);
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.fluid("fluid_output", outputFluidTank.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> IAERecipeMachineAdapter replicatorItemTemplateToItem(
          Supplier<Map<NucleosynthesizerInput, RECIPE>> recipes,
          Supplier<? extends IInventorySlot> templateSlot,
          Supplier<? extends IExtendedGasTank> uuTank,
          Supplier<? extends IInventorySlot> outputSlot,
          Predicate<Gas> isValidGas,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), itemTemplateKey(templateSlot.get()));
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return collectReplicatorItemTemplateRecipes(recipes.get(), templateSlot.get(), isValidGas);
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getReplicatorItemTemplateRecipe(host, recipe, stacks, recipes, templateSlot.get(), uuTank.get(), outputSlot.get(), isValidGas,
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getReplicatorItemTemplateRecipe(host, recipe, stacks, recipes, templateSlot.get(), uuTank.get(), outputSlot.get(), isValidGas,
                      refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Collections.singletonList(AERecipePort.gas("uu_gas", uuTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} UU input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                return canAcceptAnyReplicatorItemTemplateInput(recipes.get(), templateSlot.get(), uuTank.get(), outputSlot.get(), isValidGas);
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.item("item_output", outputSlot.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> IAERecipeMachineAdapter replicatorGasTemplateToGas(
          Supplier<Map<ChemicalGasInput, RECIPE>> recipes,
          Supplier<? extends IExtendedGasTank> templateTank,
          Supplier<? extends IExtendedGasTank> uuTank,
          Supplier<? extends IExtendedGasTank> outputTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), gasTemplateKey(templateTank.get()));
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return collectReplicatorGasTemplateRecipes(recipes.get(), templateTank.get());
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getReplicatorGasTemplateRecipe(host, recipe, stacks, recipes, templateTank.get(), uuTank.get(), outputTank.get(),
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getReplicatorGasTemplateRecipe(host, recipe, stacks, recipes, templateTank.get(), uuTank.get(), outputTank.get(),
                      refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Collections.singletonList(AERecipePort.gas("uu_gas", uuTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} UU input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                return canAcceptAnyReplicatorGasTemplateInput(recipes.get(), templateTank.get(), uuTank.get(), outputTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.gas("gas_output", outputTank.get()));
            }
        };
    }

    public static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> IAERecipeMachineAdapter replicatorFluidTemplateToFluid(
          Supplier<Map<GasAndFluidInput, RECIPE>> recipes,
          Supplier<? extends IExtendedFluidTank> templateTank,
          Supplier<? extends IExtendedGasTank> uuTank,
          Supplier<? extends IExtendedFluidTank> outputTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), fluidTemplateKey(templateTank.get()));
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return collectReplicatorFluidTemplateRecipes(recipes.get(), templateTank.get());
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getReplicatorFluidTemplateRecipe(host, recipe, stacks, recipes, templateTank.get(), uuTank.get(), outputTank.get(),
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getReplicatorFluidTemplateRecipe(host, recipe, stacks, recipes, templateTank.get(), uuTank.get(), outputTank.get(),
                      refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Collections.singletonList(AERecipePort.gas("uu_gas", uuTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} UU input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                return canAcceptAnyReplicatorFluidTemplateInput(recipes.get(), templateTank.get(), uuTank.get(), outputTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainAll(node, AERecipePort.fluid("fluid_output", outputTank.get()));
            }
        };
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> ItemGasTarget<RECIPE> getMultiItemToGasTarget(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, ItemStack stack, Supplier<Map<ItemStackInput, RECIPE>> recipes,
          @Nullable List<? extends IInventorySlot> inputSlots, @Nullable IExtendedGasTank[] outputTanks, int processCount,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (inputSlots == null || outputTanks == null) {
            AEItemRecipeAdapters.reject(host, "{} input slots or output tanks are not initialized", machineName);
            return null;
        }
        if (stack.isEmpty() || !AERecipeRouteLegacyIO.matchesInput(exposedRecipe, stack)) {
            AEItemRecipeAdapters.reject(host, "{} supplied input does not match route", machineName);
            return null;
        }
        for (int process = 0; process < processCount; process++) {
            IInventorySlot slot = getSlot(inputSlots, process);
            IExtendedGasTank outputTank = getTank(outputTanks, process);
            if (slot == null || outputTank == null) {
                continue;
            }
            ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(slot, stack);
            RECIPE machineRecipe = getItemRecipe(recipes, refreshRecipeLookupCache, simulatedInput);
            if (machineRecipe == null || !MachineInput.inputContains(simulatedInput, machineRecipe.getInput().ingredient)) {
                continue;
            }
            if (!AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), machineRecipe.getOutput().output, exposedRecipe.getCraftAmount()) ||
                !canGasStackInsert(outputTank, machineRecipe.getOutput().output)) {
                continue;
            }
            if (slot.insertItem(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                return new ItemGasTarget<>(slot, outputTank, machineRecipe);
            }
        }
        AEItemRecipeAdapters.reject(host, "{} has no process that can accept {}", machineName, AEUpgradeDebug.stack(stack));
        return null;
    }

    @Nullable
    private static GasItemTarget<CrystallizerRecipe> getMultiGasToItemTarget(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, ItemStack stack, Supplier<Map<GasInput, CrystallizerRecipe>> recipes,
          @Nullable IExtendedGasTank[] inputTanks, @Nullable List<? extends IInventorySlot> outputSlots, int processCount,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (inputTanks == null || outputSlots == null) {
            AEItemRecipeAdapters.reject(host, "{} input tanks or output slots are not initialized", machineName);
            return null;
        }
        if (stack.isEmpty() || !AERecipeRouteLegacyIO.matchesInput(exposedRecipe, stack)) {
            AEItemRecipeAdapters.reject(host, "{} supplied input does not match route", machineName);
            return null;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stack, gas -> hasMatchingGas(recipes.get(), gas));
        if (gasStack == null) {
            AEItemRecipeAdapters.reject(host, "{} gas conversion input cannot provide supported gas", machineName);
            return null;
        }
        CrystallizerRecipe machineRecipe = getGasRecipe(recipes, refreshRecipeLookupCache, gasStack);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no {} recipe for gas {}", machineName, gasStack);
            return null;
        }
        int operations = getItemOutputOperations(machineRecipe.getOutput().output, exposedRecipe.getOutputStack());
        GasStack requiredGas = machineRecipe.getInput().ingredient;
        if (operations <= 0 || requiredGas == null || (long) requiredGas.amount * operations != gasStack.amount) {
            AEItemRecipeAdapters.reject(host, "{} gas amount does not match exposed route", machineName);
            return null;
        }
        for (int process = 0; process < processCount; process++) {
            IExtendedGasTank inputTank = getTank(inputTanks, process);
            IInventorySlot outputSlot = getSlot(outputSlots, process);
            if (inputTank != null && outputSlot != null && canGasStackInsert(inputTank, gasStack) &&
                AEItemRecipeAdapters.canOutputToSlot(outputSlot, exposedRecipe.getOutputStack())) {
                return new GasItemTarget<>(inputTank, outputSlot, machineRecipe);
            }
        }
        AEItemRecipeAdapters.reject(host, "{} has no process that can accept {}", machineName, gasStack);
        return null;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> ItemGasTarget<RECIPE> getMultiItemGasToGasTarget(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<ItemStackInput, RECIPE>> recipes,
          @Nullable List<? extends IInventorySlot> inputSlots, @Nullable IExtendedGasTank gasTank, @Nullable IExtendedGasTank[] outputTanks,
          int processCount, Predicate<Gas> isValidGas, IntSupplier gasUsagePerOperation, Runnable refreshRecipeLookupCache, String machineName) {
        if (inputSlots == null || gasTank == null || outputTanks == null) {
            AEItemRecipeAdapters.reject(host, "{} input slots or tanks are not initialized", machineName);
            return null;
        }
        if (stacks.size() != 2 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "{} requires item input and gas input", machineName);
            return null;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, isValidGas);
        if (gasStack == null) {
            AEItemRecipeAdapters.reject(host, "{} gas input cannot provide supported gas", machineName);
            return null;
        }
        int gasPerOperation = gasUsagePerOperation.getAsInt();
        if (gasPerOperation <= 0 || gasStack.amount % gasPerOperation != 0 || !canGasStackInsert(gasTank, gasStack)) {
            AEItemRecipeAdapters.reject(host, "{} gas input amount {} is invalid for usage {}", machineName, gasStack.amount, gasPerOperation);
            return null;
        }
        int operations = gasStack.amount / gasPerOperation;
        for (int process = 0; process < processCount; process++) {
            IInventorySlot slot = getSlot(inputSlots, process);
            IExtendedGasTank outputTank = getTank(outputTanks, process);
            if (slot == null || outputTank == null) {
                continue;
            }
            ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(slot, stacks.get(0));
            RECIPE machineRecipe = getItemRecipe(recipes, refreshRecipeLookupCache, simulatedInput);
            if (machineRecipe == null) {
                continue;
            }
            ItemStack requiredInput = scaledStack(machineRecipe.getInput().ingredient, operations);
            GasStack requiredOutput = scaledGasStack(machineRecipe.getOutput().output, operations);
            if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput) ||
                !AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), requiredOutput) || !canGasStackInsert(outputTank, requiredOutput)) {
                continue;
            }
            if (slot.insertItem(stacks.get(0).copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                return new ItemGasTarget<>(slot, outputTank, machineRecipe);
            }
        }
        AEItemRecipeAdapters.reject(host, "{} has no process that can accept {}", machineName, AEUpgradeDebug.stacks(stacks));
        return null;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> RECIPE getItemGasToItemRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<NucleosynthesizerInput, RECIPE>> recipes,
          @Nullable IInventorySlot inputSlot, @Nullable IExtendedGasTank gasTank, @Nullable IInventorySlot outputSlot, Predicate<Gas> isValidGas,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (inputSlot == null || gasTank == null || outputSlot == null) {
            AEItemRecipeAdapters.reject(host, "{} input slot, gas tank, or output slot is not initialized", machineName);
            return null;
        }
        if (stacks.size() != 2 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "{} requires item input and gas input", machineName);
            return null;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, isValidGas);
        if (gasStack == null || !canGasStackInsert(gasTank, gasStack)) {
            AEItemRecipeAdapters.reject(host, "{} gas input cannot be accepted", machineName);
            return null;
        }
        ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(inputSlot, stacks.get(0));
        RECIPE machineRecipe = getNucleosynthesizerRecipe(recipes, refreshRecipeLookupCache, simulatedInput, gasStack);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no {} recipe for {} and {}", machineName, AEUpgradeDebug.stack(simulatedInput), gasStack);
            return null;
        }
        int operations = getItemOutputOperations(machineRecipe.getOutput().output, exposedRecipe.getOutputStack());
        GasStack requiredGas = machineRecipe.getInput().getGas();
        ItemStack requiredInput = scaledStack(machineRecipe.getInput().getSolid(), operations);
        if (operations <= 0 || requiredGas == null || (long) requiredGas.amount * operations != gasStack.amount ||
            requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
            AEItemRecipeAdapters.reject(host, "{} route inputs do not contain recipe requirements", machineName);
            return null;
        }
        if (!AEItemRecipeAdapters.canOutputToSlot(outputSlot, exposedRecipe.getOutputStack()) ||
            !inputSlot.insertItem(stacks.get(0).copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
            AEItemRecipeAdapters.reject(host, "{} slot simulation failed", machineName);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> RECIPE getGasGasToGasRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<ChemicalGasInput, RECIPE>> recipes,
          @Nullable IExtendedGasTank inputTank, @Nullable IExtendedGasTank uuTank, @Nullable IExtendedGasTank outputTank,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (inputTank == null || uuTank == null || outputTank == null) {
            AEItemRecipeAdapters.reject(host, "{} tanks are not initialized", machineName);
            return null;
        }
        if (stacks.size() != 2 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "{} requires two gas inputs", machineName);
            return null;
        }
        GasStack left = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 0, gas -> hasMatchingChemicalGas(recipes.get(), gas));
        GasStack right = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, gas -> hasMatchingChemicalGas(recipes.get(), gas));
        if (left == null || right == null || !canGasStackInsert(inputTank, left) || !canGasStackInsert(uuTank, right)) {
            AEItemRecipeAdapters.reject(host, "{} gas inputs cannot be accepted", machineName);
            return null;
        }
        RECIPE machineRecipe = getChemicalGasRecipe(recipes, refreshRecipeLookupCache, left, right);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no {} recipe for {} and {}", machineName, left, right);
            return null;
        }
        int operations = getChemicalGasOperations(machineRecipe.getInput(), left, right);
        GasStack requiredOutput = scaledGasStack(machineRecipe.getOutput().output, operations);
        if (operations <= 0 || !AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), requiredOutput) ||
            !canGasStackInsert(outputTank, requiredOutput)) {
            AEItemRecipeAdapters.reject(host, "{} route output does not match or cannot fit", machineName);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> RECIPE getGasFluidToFluidRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<GasAndFluidInput, RECIPE>> recipes,
          @Nullable IExtendedGasTank gasTank, @Nullable IExtendedFluidTank inputFluidTank, @Nullable IExtendedFluidTank outputFluidTank,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (gasTank == null || inputFluidTank == null || outputFluidTank == null) {
            AEItemRecipeAdapters.reject(host, "{} tanks are not initialized", machineName);
            return null;
        }
        if (stacks.size() != 2 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "{} requires gas and fluid inputs", machineName);
            return null;
        }
        GasStack gas = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 0, gasType -> hasMatchingGasFluidGas(recipes.get(), gasType));
        FluidStack fluid = AERecipeRouteLegacyIO.getFluidInput(exposedRecipe, stacks, 1);
        if (gas == null || fluid == null || !canGasStackInsert(gasTank, gas) || !canFluidStackInsert(inputFluidTank, fluid)) {
            AEItemRecipeAdapters.reject(host, "{} gas or fluid input cannot be accepted", machineName);
            return null;
        }
        RECIPE machineRecipe = getGasFluidRecipe(recipes, refreshRecipeLookupCache, gas, fluid);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no {} recipe for {} and {}", machineName, gas, fluid);
            return null;
        }
        int operations = getGasFluidOperations(machineRecipe.getInput(), gas, fluid);
        FluidStack requiredOutput = scaledFluidStack(machineRecipe.getOutput().output, operations);
        if (operations <= 0 || !AEUpgradeFakeFluid.outputMatches(exposedRecipe.getOutputStack(), requiredOutput) ||
            !canFluidStackInsert(outputFluidTank, requiredOutput)) {
            AEItemRecipeAdapters.reject(host, "{} route output does not match or cannot fit", machineName);
            return null;
        }
        return machineRecipe;
    }

    private static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> List<AEExposedRecipe> collectReplicatorItemTemplateRecipes(
          Map<NucleosynthesizerInput, RECIPE> recipes, @Nullable IInventorySlot templateSlot, Predicate<Gas> isValidGas) {
        if (templateSlot == null || !AEUpgradeFakeGas.isAvailable()) {
            return Collections.emptyList();
        }
        ItemStack template = templateSlot.getStack();
        if (template.isEmpty()) {
            return Collections.emptyList();
        }
        List<AERecipeRoute> routes = new ArrayList<>();
        for (RECIPE recipe : recipes.values()) {
            NucleosynthesizerInput input = recipe.getInput();
            GasStack uu = input == null ? null : input.getGas();
            ItemStack requiredTemplate = input == null ? ItemStack.EMPTY : input.getSolid();
            ItemStack output = recipe.getOutput().output;
            if (MachineInput.inputContains(template, requiredTemplate) && isPositiveGas(uu) && isValidGas.test(uu.getGas()) &&
                isPositiveItem(output)) {
                routes.add(AERecipeRoute.builder("route:replicator_item.template")
                      .inputGas("uu_gas", uu)
                      .outputItem("item_output", output)
                      .build());
            }
        }
        return AERecipeRoute.toLegacyRecipes(routes);
    }

    private static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> List<AEExposedRecipe> collectReplicatorGasTemplateRecipes(
          Map<ChemicalGasInput, RECIPE> recipes, @Nullable IExtendedGasTank templateTank) {
        if (templateTank == null || !AEUpgradeFakeGas.isAvailable()) {
            return Collections.emptyList();
        }
        GasStack template = templateTank.getGas();
        if (!isPositiveGas(template)) {
            return Collections.emptyList();
        }
        List<AERecipeRoute> routes = new ArrayList<>();
        for (RECIPE recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (input != null && hasGas(template, input.input) && isPositiveGas(input.uu) && isPositiveGas(output)) {
                routes.add(AERecipeRoute.builder("route:replicator_gas.template")
                      .inputGas("uu_gas", input.uu)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return AERecipeRoute.toLegacyRecipes(routes);
    }

    private static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> List<AEExposedRecipe> collectReplicatorFluidTemplateRecipes(
          Map<GasAndFluidInput, RECIPE> recipes, @Nullable IExtendedFluidTank templateTank) {
        if (templateTank == null || !AEUpgradeFakeGas.isAvailable() || !AEUpgradeFakeFluid.isAvailable()) {
            return Collections.emptyList();
        }
        FluidStack template = templateTank.getFluid();
        if (!isPositiveFluid(template)) {
            return Collections.emptyList();
        }
        List<AERecipeRoute> routes = new ArrayList<>();
        for (RECIPE recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            FluidStack output = recipe.getOutput().output;
            if (input != null && hasFluid(template, input.ingredientFluid) && isPositiveGas(input.ingredientGas) && isPositiveFluid(output)) {
                routes.add(AERecipeRoute.builder("route:replicator_fluid.template")
                      .inputGas("uu_gas", input.ingredientGas)
                      .outputFluid("fluid_output", output)
                      .build());
            }
        }
        return AERecipeRoute.toLegacyRecipes(routes);
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> RECIPE getReplicatorItemTemplateRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<NucleosynthesizerInput, RECIPE>> recipes,
          @Nullable IInventorySlot templateSlot, @Nullable IExtendedGasTank uuTank, @Nullable IInventorySlot outputSlot, Predicate<Gas> isValidGas,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (templateSlot == null || uuTank == null || outputSlot == null) {
            AEItemRecipeAdapters.reject(host, "{} template slot, UU tank, or output slot is not initialized", machineName);
            return null;
        }
        if (stacks.size() != 1 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "{} requires only UU gas input", machineName);
            return null;
        }
        GasStack uu = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 0, isValidGas);
        if (uu == null || !canGasStackInsert(uuTank, uu)) {
            AEItemRecipeAdapters.reject(host, "{} UU gas input cannot be accepted", machineName);
            return null;
        }
        ItemStack template = templateSlot.getStack();
        refreshRecipeLookupCache.run();
        RECIPE machineRecipe = findReplicatorItemTemplateRecipe(recipes.get(), template, uu, isValidGas);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no {} template recipe for {} and {}", machineName, AEUpgradeDebug.stack(template), uu);
            return null;
        }
        int operations = getGasOperations(machineRecipe.getInput().getGas(), uu);
        ItemStack requiredOutput = scaledStack(machineRecipe.getOutput().output, operations);
        if (operations <= 0 || requiredOutput.isEmpty() || !itemOutputMatches(exposedRecipe.getOutputStack(), requiredOutput) ||
            !AEItemRecipeAdapters.canOutputToSlot(outputSlot, requiredOutput)) {
            AEItemRecipeAdapters.reject(host, "{} route output does not match or cannot fit", machineName);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> RECIPE getReplicatorGasTemplateRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<ChemicalGasInput, RECIPE>> recipes,
          @Nullable IExtendedGasTank templateTank, @Nullable IExtendedGasTank uuTank, @Nullable IExtendedGasTank outputTank,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (templateTank == null || uuTank == null || outputTank == null) {
            AEItemRecipeAdapters.reject(host, "{} template tank, UU tank, or output tank is not initialized", machineName);
            return null;
        }
        if (stacks.size() != 1 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "{} requires only UU gas input", machineName);
            return null;
        }
        GasStack uu = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 0,
              gas -> hasMatchingReplicatorGasTemplate(recipes.get(), templateTank.getGas(), gas));
        if (uu == null || !canGasStackInsert(uuTank, uu)) {
            AEItemRecipeAdapters.reject(host, "{} UU gas input cannot be accepted", machineName);
            return null;
        }
        refreshRecipeLookupCache.run();
        RECIPE machineRecipe = findReplicatorGasTemplateRecipe(recipes.get(), templateTank.getGas(), uu);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no {} template recipe for {} and {}", machineName, templateTank.getGas(), uu);
            return null;
        }
        int operations = getGasOperations(machineRecipe.getInput().uu, uu);
        GasStack requiredOutput = scaledGasStack(machineRecipe.getOutput().output, operations);
        if (operations <= 0 || requiredOutput == null || !AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), requiredOutput) ||
            !canGasStackInsert(outputTank, requiredOutput)) {
            AEItemRecipeAdapters.reject(host, "{} route output does not match or cannot fit", machineName);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> RECIPE getReplicatorFluidTemplateRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<GasAndFluidInput, RECIPE>> recipes,
          @Nullable IExtendedFluidTank templateTank, @Nullable IExtendedGasTank uuTank, @Nullable IExtendedFluidTank outputTank,
          Runnable refreshRecipeLookupCache, String machineName) {
        if (templateTank == null || uuTank == null || outputTank == null) {
            AEItemRecipeAdapters.reject(host, "{} template tank, UU tank, or output tank is not initialized", machineName);
            return null;
        }
        if (stacks.size() != 1 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "{} requires only UU gas input", machineName);
            return null;
        }
        GasStack uu = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 0,
              gas -> hasMatchingReplicatorFluidTemplate(recipes.get(), templateTank.getFluid(), gas));
        if (uu == null || !canGasStackInsert(uuTank, uu)) {
            AEItemRecipeAdapters.reject(host, "{} UU gas input cannot be accepted", machineName);
            return null;
        }
        refreshRecipeLookupCache.run();
        RECIPE machineRecipe = findReplicatorFluidTemplateRecipe(recipes.get(), templateTank.getFluid(), uu);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no {} template recipe for {} and {}", machineName, templateTank.getFluid(), uu);
            return null;
        }
        int operations = getGasOperations(machineRecipe.getInput().ingredientGas, uu);
        FluidStack requiredOutput = scaledFluidStack(machineRecipe.getOutput().output, operations);
        if (operations <= 0 || requiredOutput == null || !AEUpgradeFakeFluid.outputMatches(exposedRecipe.getOutputStack(), requiredOutput) ||
            !canFluidStackInsert(outputTank, requiredOutput)) {
            AEItemRecipeAdapters.reject(host, "{} route output does not match or cannot fit", machineName);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <OUTPUT extends MachineOutput<?>, RECIPE extends MachineRecipe<ItemStackInput, OUTPUT, RECIPE>> RECIPE getItemRecipe(
          Supplier<Map<ItemStackInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new ItemStackInput(input), recipes.get());
    }

    @Nullable
    private static CrystallizerRecipe getGasRecipe(Supplier<Map<GasInput, CrystallizerRecipe>> recipes, Runnable refreshRecipeLookupCache,
          GasStack input) {
        if (input == null || input.getGas() == null || input.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new GasInput(input), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> RECIPE getNucleosynthesizerRecipe(
          Supplier<Map<NucleosynthesizerInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, ItemStack input, @Nullable GasStack gasStack) {
        if (input.isEmpty() || gasStack == null || gasStack.getGas() == null || gasStack.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new NucleosynthesizerInput(input, gasStack), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> RECIPE getChemicalGasRecipe(
          Supplier<Map<ChemicalGasInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, GasStack left, GasStack right) {
        if (left == null || left.getGas() == null || left.amount <= 0 || right == null || right.getGas() == null || right.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new ChemicalGasInput(left, right), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> RECIPE getGasFluidRecipe(
          Supplier<Map<GasAndFluidInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, GasStack gas, FluidStack fluid) {
        if (gas == null || gas.getGas() == null || gas.amount <= 0 || fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new GasAndFluidInput(gas, fluid), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> RECIPE findReplicatorItemTemplateRecipe(
          Map<NucleosynthesizerInput, RECIPE> recipes, ItemStack template, GasStack uu, Predicate<Gas> isValidGas) {
        if (template.isEmpty() || !isPositiveGas(uu) || !isValidGas.test(uu.getGas())) {
            return null;
        }
        for (RECIPE recipe : recipes.values()) {
            NucleosynthesizerInput input = recipe.getInput();
            if (input != null && MachineInput.inputContains(template, input.getSolid()) && isMatchingPositiveGas(input.getGas(), uu)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> RECIPE findReplicatorGasTemplateRecipe(
          Map<ChemicalGasInput, RECIPE> recipes, @Nullable GasStack template, GasStack uu) {
        if (!isPositiveGas(template) || !isPositiveGas(uu)) {
            return null;
        }
        for (RECIPE recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            if (input != null && hasGas(template, input.input) && isMatchingPositiveGas(input.uu, uu)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> RECIPE findReplicatorFluidTemplateRecipe(
          Map<GasAndFluidInput, RECIPE> recipes, @Nullable FluidStack template, GasStack uu) {
        if (!isPositiveFluid(template) || !isPositiveGas(uu)) {
            return null;
        }
        for (RECIPE recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            if (input != null && hasFluid(template, input.ingredientFluid) && isMatchingPositiveGas(input.ingredientGas, uu)) {
                return recipe;
            }
        }
        return null;
    }

    private static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> boolean canAcceptAnyReplicatorItemTemplateInput(
          Map<NucleosynthesizerInput, RECIPE> recipes, @Nullable IInventorySlot templateSlot, @Nullable IExtendedGasTank uuTank,
          @Nullable IInventorySlot outputSlot, Predicate<Gas> isValidGas) {
        if (templateSlot == null || uuTank == null || outputSlot == null || !AEUpgradeFakeGas.isAvailable()) {
            return false;
        }
        ItemStack template = templateSlot.getStack();
        if (template.isEmpty()) {
            return false;
        }
        for (RECIPE recipe : recipes.values()) {
            NucleosynthesizerInput input = recipe.getInput();
            GasStack uu = input == null ? null : input.getGas();
            ItemStack output = recipe.getOutput().output;
            if (input != null && MachineInput.inputContains(template, input.getSolid()) && isPositiveGas(uu) && isValidGas.test(uu.getGas()) &&
                canGasStackInsert(uuTank, uu) && AEItemRecipeAdapters.canOutputToSlot(outputSlot, output)) {
                return true;
            }
        }
        return false;
    }

    private static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> boolean canAcceptAnyReplicatorGasTemplateInput(
          Map<ChemicalGasInput, RECIPE> recipes, @Nullable IExtendedGasTank templateTank, @Nullable IExtendedGasTank uuTank,
          @Nullable IExtendedGasTank outputTank) {
        if (templateTank == null || uuTank == null || outputTank == null || !AEUpgradeFakeGas.isAvailable()) {
            return false;
        }
        GasStack template = templateTank.getGas();
        if (!isPositiveGas(template)) {
            return false;
        }
        for (RECIPE recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (input != null && hasGas(template, input.input) && canGasStackInsert(uuTank, input.uu) && canGasStackInsert(outputTank, output)) {
                return true;
            }
        }
        return false;
    }

    private static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> boolean canAcceptAnyReplicatorFluidTemplateInput(
          Map<GasAndFluidInput, RECIPE> recipes, @Nullable IExtendedFluidTank templateTank, @Nullable IExtendedGasTank uuTank,
          @Nullable IExtendedFluidTank outputTank) {
        if (templateTank == null || uuTank == null || outputTank == null || !AEUpgradeFakeGas.isAvailable() || !AEUpgradeFakeFluid.isAvailable()) {
            return false;
        }
        FluidStack template = templateTank.getFluid();
        if (!isPositiveFluid(template)) {
            return false;
        }
        for (RECIPE recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            FluidStack output = recipe.getOutput().output;
            if (input != null && hasFluid(template, input.ingredientFluid) && canGasStackInsert(uuTank, input.ingredientGas) &&
                canFluidStackInsert(outputTank, output)) {
                return true;
            }
        }
        return false;
    }

    private static <RECIPE extends MachineRecipe<ChemicalGasInput, GasOutput, RECIPE>> boolean hasMatchingReplicatorGasTemplate(
          Map<ChemicalGasInput, RECIPE> recipes, @Nullable GasStack template, @Nullable Gas gas) {
        if (!isPositiveGas(template) || gas == null) {
            return false;
        }
        for (RECIPE recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            if (input != null && hasGas(template, input.input) && input.uu != null && input.uu.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static <RECIPE extends MachineRecipe<GasAndFluidInput, FluidOutput, RECIPE>> boolean hasMatchingReplicatorFluidTemplate(
          Map<GasAndFluidInput, RECIPE> recipes, @Nullable FluidStack template, @Nullable Gas gas) {
        if (!isPositiveFluid(template) || gas == null) {
            return false;
        }
        for (RECIPE recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            if (input != null && hasFluid(template, input.ingredientFluid) && input.ingredientGas != null && input.ingredientGas.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMatchingPositiveGas(@Nullable GasStack required, GasStack provided) {
        return isPositiveGas(required) && isPositiveGas(provided) && provided.isGasEqual(required) && provided.amount >= required.amount;
    }

    private static boolean hasGas(@Nullable GasStack stored, @Nullable GasStack required) {
        return isPositiveGas(stored) && isPositiveGas(required) && stored.isGasEqual(required) && stored.amount >= required.amount;
    }

    private static boolean hasFluid(@Nullable FluidStack stored, @Nullable FluidStack required) {
        return isPositiveFluid(stored) && isPositiveFluid(required) && stored.isFluidEqual(required) && stored.amount >= required.amount;
    }

    private static boolean isPositiveGas(@Nullable GasStack stack) {
        return stack != null && stack.getGas() != null && stack.amount > 0;
    }

    private static boolean isPositiveFluid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() != null && stack.amount > 0;
    }

    private static boolean isPositiveItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0;
    }

    private static boolean itemOutputMatches(ItemStack expected, ItemStack actual) {
        return isPositiveItem(expected) && isPositiveItem(actual) && expected.getCount() == actual.getCount() &&
              ItemHandlerHelper.canItemStacksStack(expected, actual);
    }

    private static int getGasOperations(@Nullable GasStack required, GasStack provided) {
        if (!isPositiveGas(required) || !isPositiveGas(provided) || !provided.isGasEqual(required)) {
            return -1;
        }
        return getSingleOperations(required.amount, provided.amount);
    }

    private static int getSingleOperations(int required, int provided) {
        return required > 0 && provided > 0 && provided % required == 0 ? provided / required : -1;
    }

    private static String itemTemplateKey(@Nullable IInventorySlot slot) {
        if (slot == null || slot.getStack().isEmpty()) {
            return "";
        }
        return AEItemStackKey.fromStack(slot.getStack()).getEncoded();
    }

    private static String gasTemplateKey(@Nullable IExtendedGasTank tank) {
        GasStack stack = tank == null ? null : tank.getGas();
        return isPositiveGas(stack) ? stack.getGas().getName() + "@" + stack.amount : "";
    }

    private static String fluidTemplateKey(@Nullable IExtendedFluidTank tank) {
        FluidStack stack = tank == null ? null : tank.getFluid();
        if (!isPositiveFluid(stack)) {
            return "";
        }
        return stack.getFluid().getName() + "@" + stack.amount + (stack.tag == null ? "" : ":" + stack.tag);
    }

    private static boolean drainGasTanks(AEUpgradeNode node, @Nullable IExtendedGasTank[] tanks, int processCount) {
        if (tanks == null) {
            return false;
        }
        boolean drained = false;
        for (int process = 0; process < processCount; process++) {
            drained |= AERecipePort.drainAll(node, AERecipePort.gas("gas_output_" + process, getTank(tanks, process)));
        }
        return drained;
    }

    private static boolean drainItemSlots(AEUpgradeNode node, @Nullable List<? extends IInventorySlot> slots, int processCount) {
        if (slots == null) {
            return false;
        }
        boolean drained = false;
        for (int process = 0; process < processCount; process++) {
            drained |= AERecipePort.drainAll(node, AERecipePort.item("item_output_" + process, getSlot(slots, process)));
        }
        return drained;
    }

    @Nullable
    private static IInventorySlot getSlot(List<? extends IInventorySlot> slots, int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : null;
    }

    @Nullable
    private static IExtendedGasTank getTank(IExtendedGasTank[] tanks, int index) {
        return index >= 0 && index < tanks.length ? tanks[index] : null;
    }

    private static boolean canGasStackInsert(IExtendedGasTank tank, GasStack stack) {
        if (tank == null || stack == null || stack.getGas() == null || stack.amount <= 0) {
            return false;
        }
        GasStack remainder = tank.insert(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return getGasAmount(remainder) <= 0;
    }

    private static boolean canFluidStackInsert(IExtendedFluidTank tank, FluidStack stack) {
        if (tank == null || stack == null || stack.getFluid() == null || stack.amount <= 0) {
            return false;
        }
        FluidStack remainder = tank.insert(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return getFluidAmount(remainder) <= 0;
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

    private static boolean hasMatchingChemicalGas(Map<ChemicalGasInput, ?> recipes, @Nullable Gas gas) {
        if (gas == null) {
            return false;
        }
        for (ChemicalGasInput input : recipes.keySet()) {
            if (input.input != null && input.input.getGas() == gas || input.uu != null && input.uu.getGas() == gas) {
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

    private static boolean hasGasConversionRecipes(Map<GasInput, ?> recipes) {
        for (GasInput input : recipes.keySet()) {
            GasStack stack = input.ingredient;
            if (stack != null && stack.getGas() != null && !GasConversionHandler.getConversionSourcesForGas(stack.getGas()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int getItemOutputOperations(ItemStack recipeOutput, ItemStack exposedOutput) {
        if (recipeOutput.isEmpty() || exposedOutput.isEmpty() || !ItemHandlerHelper.canItemStacksStack(recipeOutput, exposedOutput)) {
            return -1;
        }
        int recipeCount = recipeOutput.getCount();
        return recipeCount > 0 && exposedOutput.getCount() % recipeCount == 0 ? exposedOutput.getCount() / recipeCount : -1;
    }

    private static int getChemicalGasOperations(ChemicalGasInput input, GasStack left, GasStack right) {
        if (input == null || input.input == null || input.uu == null || left == null || right == null) {
            return -1;
        }
        if (left.isGasEqual(input.input) && right.isGasEqual(input.uu)) {
            return getPairOperations(input.input.amount, left.amount, input.uu.amount, right.amount);
        }
        return -1;
    }

    private static int getGasFluidOperations(GasAndFluidInput input, GasStack gas, FluidStack fluid) {
        if (input == null || input.ingredientGas == null || input.ingredientFluid == null || gas == null || fluid == null ||
            !gas.isGasEqual(input.ingredientGas) || !fluid.isFluidEqual(input.ingredientFluid)) {
            return -1;
        }
        return getPairOperations(input.ingredientGas.amount, gas.amount, input.ingredientFluid.amount, fluid.amount);
    }

    private static int getPairOperations(int firstRequired, int firstProvided, int secondRequired, int secondProvided) {
        if (firstRequired <= 0 || secondRequired <= 0 || firstProvided % firstRequired != 0 || secondProvided % secondRequired != 0) {
            return -1;
        }
        int firstOperations = firstProvided / firstRequired;
        int secondOperations = secondProvided / secondRequired;
        return firstOperations == secondOperations ? firstOperations : -1;
    }

    @Nullable
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

    private static ItemStack scaledStack(ItemStack stack, int multiplier) {
        return AERecipeStacks.scale(stack, multiplier);
    }

    private static int getGasAmount(@Nullable GasStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    private static int getFluidAmount(@Nullable FluidStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @Nullable
    private static GasStack copy(@Nullable GasStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static final class ItemGasTarget<RECIPE> {

        private final IInventorySlot inputSlot;
        private final IExtendedGasTank outputTank;
        private final RECIPE recipe;

        private ItemGasTarget(IInventorySlot inputSlot, IExtendedGasTank outputTank, RECIPE recipe) {
            this.inputSlot = inputSlot;
            this.outputTank = outputTank;
            this.recipe = recipe;
        }
    }

    private static final class GasItemTarget<RECIPE> {

        private final IExtendedGasTank inputTank;
        private final IInventorySlot outputSlot;
        private final RECIPE recipe;

        private GasItemTarget(IExtendedGasTank inputTank, IInventorySlot outputSlot, RECIPE recipe) {
            this.inputTank = inputTank;
            this.outputSlot = outputSlot;
            this.recipe = recipe;
        }
    }
}
