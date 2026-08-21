package mekceuaeupgrade.common.adapter;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AERecipeStacks;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteCollectors;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteLegacyIO;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.recipe.route.AERecipeStackKind;
import mekceuaeupgrade.common.transfer.AERecipePort;
import mekceuaeupgrade.common.transfer.AERecipeTransferPlan;
import mekceuaeupgrade.common.util.AEUpgradeDebug;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * AE adapter shared by the vanilla organic farm and MoreMachine's tiered farms.
 */
public final class AEFarmRecipeAdapters {

    private AEFarmRecipeAdapters() {
    }

    public static <RECIPE extends FarmMachineRecipe<RECIPE>> IAERecipeMachineAdapter itemMediumToItems(
          Supplier<Map<FarmInput, RECIPE>> recipes,
          Supplier<? extends List<? extends IInventorySlot>> inputSlots,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends IExtendedFluidTank> fluidTank,
          Supplier<? extends List<? extends IInventorySlot>> outputSlots,
          IntSupplier processCount,
          IntSupplier mediumUsagePerOperation,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), processCount.getAsInt(), mediumUsagePerOperation.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AERecipeRoute.toLegacyRecipes(AERecipeRouteCollectors.collectFarmGasToItem(
                      recipes.get(), mediumUsagePerOperation.getAsInt()));
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires an item and a gas or fluid input", machineName);
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires an item and a gas or fluid input", machineName);
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return resolveTarget(host, recipe, stacks, recipes, inputSlots.get(), gasTank.get(), fluidTank.get(),
                      outputSlots.get(), processCount.getAsInt(), mediumUsagePerOperation.getAsInt(),
                      refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                FarmTarget target = resolveTarget(host, recipe, stacks, recipes, inputSlots.get(), gasTank.get(),
                      fluidTank.get(), outputSlots.get(), processCount.getAsInt(), mediumUsagePerOperation.getAsInt(),
                      refreshRecipeLookupCache, machineName);
                if (target == null) {
                    return false;
                }
                List<AERecipePort> ports = new ArrayList<>(2);
                ports.add(AERecipePort.item("item_input", target.inputSlot));
                if (target.mediumKind == AERecipeStackKind.GAS) {
                    ports.add(AERecipePort.gas("gas_input", gasTank.get()));
                } else {
                    ports.add(AERecipePort.fluid("fluid_input", fluidTank.get()));
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks, ports);
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} input transfer failed for {}", machineName,
                          AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                List<AEExposedRecipe> exposed = getExposedItemRecipes(host);
                if (exposed.isEmpty()) {
                    return false;
                }
                List<? extends IInventorySlot> inputs = inputSlots.get();
                List<? extends IInventorySlot> outputs = outputSlots.get();
                for (AEExposedRecipe recipe : exposed) {
                    if (resolveTarget(host, recipe, recipe.getInputStacks(), recipes, inputs, gasTank.get(), fluidTank.get(),
                          outputs, processCount.getAsInt(), mediumUsagePerOperation.getAsInt(),
                          refreshRecipeLookupCache, machineName) != null) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                List<? extends IInventorySlot> inputs = inputSlots.get();
                if (inputs != null) {
                    int count = Math.min(Math.max(0, processCount.getAsInt()), inputs.size());
                    for (int process = 0; process < count; process++) {
                        observer.accept(inputs.get(process));
                    }
                }
                observer.accept(gasTank.get());
                observer.accept(fluidTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return drainOutputs(node, outputSlots.get());
            }
        };
    }

    @Nullable
    private static <RECIPE extends FarmMachineRecipe<RECIPE>> FarmTarget resolveTarget(
          IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe,
          List<ItemStack> stacks,
          Supplier<Map<FarmInput, RECIPE>> recipes,
          @Nullable List<? extends IInventorySlot> inputSlots,
          @Nullable IExtendedGasTank gasTank,
          @Nullable IExtendedFluidTank fluidTank,
          @Nullable List<? extends IInventorySlot> outputSlots,
          int processCount,
          int mediumUsagePerOperation,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        if (inputSlots == null || outputSlots == null || gasTank == null || fluidTank == null ||
            processCount <= 0 || mediumUsagePerOperation <= 0) {
            AEItemRecipeAdapters.reject(host, "{} slots or merged tank are not initialized", machineName);
            return null;
        }
        if (stacks == null || stacks.size() != 2 || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "AE supplied inputs that do not match the exposed {} route", machineName);
            return null;
        }
        AERecipeRoute route = exposedRecipe.getRecipeRoute();
        if (route == null || route.inputs().size() != 2 || route.inputs().get(0).kind() != AERecipeStackKind.ITEM) {
            AEItemRecipeAdapters.reject(host, "{} route does not describe item plus merged-tank inputs", machineName);
            return null;
        }
        ItemStack itemInput = stacks.get(0);
        if (itemInput.isEmpty()) {
            AEItemRecipeAdapters.reject(host, "AE supplied an empty {} item input", machineName);
            return null;
        }
        AERecipeRouteStack mediumRoute = route.inputs().get(1);
        GasStack gasInput = null;
        FluidStack fluidInput = null;
        if (mediumRoute.kind() == AERecipeStackKind.GAS) {
            gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1,
                  gas -> hasFarmGasRecipe(recipes.get(), gas));
            if (!fluidTank.isEmpty() || !canInsertGas(gasTank, gasInput)) {
                AEItemRecipeAdapters.reject(host, "{} gas input cannot enter the merged tank", machineName);
                return null;
            }
        } else if (mediumRoute.kind() == AERecipeStackKind.FLUID) {
            fluidInput = AERecipeRouteLegacyIO.getFluidInput(exposedRecipe, stacks, 1);
            if (!gasTank.isEmpty() || !canInsertFluid(fluidTank, fluidInput)) {
                AEItemRecipeAdapters.reject(host, "{} fluid input cannot enter the merged tank", machineName);
                return null;
            }
        } else {
            AEItemRecipeAdapters.reject(host, "{} route has an invalid merged-tank input", machineName);
            return null;
        }

        refreshRecipeLookupCache.run();
        FarmInput lookupInput = gasInput == null ? new FarmInput(itemInput, fluidInput) : new FarmInput(itemInput, gasInput);
        RECIPE machineRecipe = RecipeHandler.getFarmRecipe(lookupInput, recipes.get());
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe matches the supplied item and medium", machineName);
            return null;
        }
        int operations = getOperations(machineRecipe.getInput(), itemInput, gasInput, fluidInput, mediumUsagePerOperation);
        if (operations <= 0 || !outputsMatch(exposedRecipe.getOutputStacks(), machineRecipe.getOutput(), operations)) {
            AEItemRecipeAdapters.reject(host, "{} inputs or outputs do not form a whole recipe batch", machineName);
            return null;
        }
        if (!canFitFarmOutputs(outputSlots, machineRecipe.getOutput(), operations)) {
            AEItemRecipeAdapters.reject(host, "{} shared output inventory cannot accept the farm batch", machineName);
            return null;
        }

        int count = Math.min(processCount, inputSlots.size());
        for (int process = 0; process < count; process++) {
            IInventorySlot inputSlot = inputSlots.get(process);
            if (inputSlot != null && inputSlot.insertItem(itemInput.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                return new FarmTarget(inputSlot, mediumRoute.kind());
            }
        }
        AEItemRecipeAdapters.reject(host, "no {} process input can accept {}", machineName, AEUpgradeDebug.stack(itemInput));
        return null;
    }

    private static int getOperations(FarmInput recipeInput, ItemStack itemInput, @Nullable GasStack gasInput,
          @Nullable FluidStack fluidInput, int mediumUsagePerOperation) {
        if (recipeInput == null || !recipeInput.isValid() || !MachineInput.inputContains(itemInput, recipeInput.itemStack)) {
            return -1;
        }
        int itemOperations = getSingleOperations(recipeInput.itemStack.getCount(), itemInput.getCount());
        long requiredMedium;
        int mediumOperations;
        if (recipeInput.isGasInput()) {
            if (gasInput == null || !gasInput.isGasEqual(recipeInput.gasInput)) {
                return -1;
            }
            requiredMedium = (long) recipeInput.gasInput.amount * mediumUsagePerOperation;
            mediumOperations = getSingleOperations(requiredMedium, gasInput.amount);
        } else {
            if (fluidInput == null || !fluidInput.isFluidEqual(recipeInput.fluidInput)) {
                return -1;
            }
            requiredMedium = (long) recipeInput.fluidInput.amount * mediumUsagePerOperation;
            mediumOperations = getSingleOperations(requiredMedium, fluidInput.amount);
        }
        return itemOperations > 0 && itemOperations == mediumOperations ? itemOperations : -1;
    }

    private static int getSingleOperations(long required, int provided) {
        return required > 0 && required <= Integer.MAX_VALUE && provided > 0 && provided % required == 0 ?
              (int) (provided / required) : -1;
    }

    private static boolean outputsMatch(List<ItemStack> exposedOutputs, FarmOutput output, int operations) {
        if (exposedOutputs == null || exposedOutputs.isEmpty() || output == null || !output.isValid()) {
            return false;
        }
        List<ItemStack> guaranteed = new ArrayList<>();
        guaranteed.add(output.getGuaranteedOutput());
        for (FarmChanceOutput chanceOutput : output.getChanceOutputs()) {
            if (chanceOutput.getChance() >= 1) {
                guaranteed.add(chanceOutput.getOutput());
            }
        }
        if (exposedOutputs.size() > guaranteed.size()) {
            return false;
        }
        for (int index = 0; index < exposedOutputs.size(); index++) {
            ItemStack expected = AERecipeStacks.scale(guaranteed.get(index), operations);
            if (!AEItemRecipeAdapters.outputsMatch(exposedOutputs.get(index), expected)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canFitFarmOutputs(List<? extends IInventorySlot> outputSlots, FarmOutput output, int operations) {
        if (outputSlots.isEmpty() || output == null || !output.isValid() || operations <= 0) {
            return false;
        }
        List<ItemStack> virtualSlots = new ArrayList<>(outputSlots.size());
        for (IInventorySlot slot : outputSlots) {
            virtualSlots.add(slot == null ? ItemStack.EMPTY : slot.getStack().copy());
        }
        List<ItemStack> worstCaseOutputs = mergeOutputs(output.getMaxOutputs());
        for (int operation = 0; operation < operations; operation++) {
            for (ItemStack outputStack : worstCaseOutputs) {
                if (!insertVirtual(outputSlots, virtualSlots, outputStack)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<ItemStack> mergeOutputs(List<ItemStack> outputs) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack output : outputs) {
            if (output == null || output.isEmpty()) {
                continue;
            }
            ItemStack matching = null;
            for (ItemStack existing : merged) {
                if (ItemHandlerHelper.canItemStacksStack(existing, output)) {
                    matching = existing;
                    break;
                }
            }
            if (matching == null) {
                merged.add(output.copy());
            } else {
                matching.grow(output.getCount());
            }
        }
        return merged;
    }

    private static boolean insertVirtual(List<? extends IInventorySlot> outputSlots, List<ItemStack> virtualSlots, ItemStack output) {
        int remaining = output.getCount();
        remaining = insertVirtual(outputSlots, virtualSlots, output, remaining, false);
        remaining = insertVirtual(outputSlots, virtualSlots, output, remaining, true);
        return remaining == 0;
    }

    private static int insertVirtual(List<? extends IInventorySlot> outputSlots, List<ItemStack> virtualSlots,
          ItemStack output, int remaining, boolean emptySlots) {
        for (int index = 0; index < outputSlots.size() && remaining > 0; index++) {
            IInventorySlot slot = outputSlots.get(index);
            if (slot == null) {
                continue;
            }
            ItemStack stored = virtualSlots.get(index);
            if (stored.isEmpty() != emptySlots || !slot.isItemValid(output) ||
                !stored.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stored, output)) {
                continue;
            }
            int accepted = Math.min(remaining, Math.max(0, slot.getLimit(output) - stored.getCount()));
            if (accepted <= 0) {
                continue;
            }
            if (stored.isEmpty()) {
                ItemStack inserted = output.copy();
                inserted.setCount(accepted);
                virtualSlots.set(index, inserted);
            } else {
                stored.grow(accepted);
            }
            remaining -= accepted;
        }
        return remaining;
    }

    private static boolean canInsertGas(IExtendedGasTank tank, @Nullable GasStack stack) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0) {
            return false;
        }
        GasStack remainder = tank.insert(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }

    private static boolean canInsertFluid(IExtendedFluidTank tank, @Nullable FluidStack stack) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0) {
            return false;
        }
        FluidStack remainder = tank.insert(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }

    private static boolean hasFarmGasRecipe(Map<FarmInput, ?> recipes, mekanism.api.gas.Gas gas) {
        if (gas == null) {
            return false;
        }
        for (FarmInput input : recipes.keySet()) {
            if (input.isGasInput() && input.gasInput.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static boolean drainOutputs(AEUpgradeNode node, @Nullable List<? extends IInventorySlot> outputSlots) {
        if (outputSlots == null) {
            return false;
        }
        boolean drained = false;
        for (IInventorySlot outputSlot : outputSlots) {
            if (outputSlot != null) {
                drained |= AERecipePort.drainAll(node, AERecipePort.item("item_output", outputSlot));
            }
        }
        return drained;
    }

    private static final class FarmTarget {

        private final IInventorySlot inputSlot;
        private final AERecipeStackKind mediumKind;

        private FarmTarget(IInventorySlot inputSlot, AERecipeStackKind mediumKind) {
            this.inputSlot = inputSlot;
            this.mediumKind = mediumKind;
        }
    }
}
