package mekceuaeupgrade.common.adapter;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.common.InfuseStorage;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.recipe.outputs.PressurizedOutput;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AERecipeStacks;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteLegacyIO;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.transfer.AERecipePort;
import mekceuaeupgrade.common.transfer.AERecipeTransferPlan;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;
import mekceuaeupgrade.common.util.AEUpgradeDebug;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AEHybridRecipeAdapters {

    private AEHybridRecipeAdapters() {
    }

    public static <RECIPE extends MachineRecipe<InfusionInput, ItemStackOutput, RECIPE>> IAERecipeMachineAdapter infusionItemToItem(
          Supplier<Map<InfusionInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends InputInventorySlot> extraSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<InfuseStorage> infuseStorage,
          IntSupplier maxInfuse,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectInfusionItemRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "infusion machine requires item input and infuse source input");
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "infusion machine requires item input and infuse source input");
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getInfusionRecipe(host, recipe, stacks, recipes, inputSlot.get(), extraSlot.get(), outputSlot.get(), infuseStorage.get(),
                      maxInfuse.getAsInt()) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getInfusionRecipe(host, recipe, stacks, recipes, inputSlot.get(), extraSlot.get(), outputSlot.get(), infuseStorage.get(),
                      maxInfuse.getAsInt()) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.item("item_input", inputSlot.get()), AERecipePort.item("extra_input", extraSlot.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic infusion input execute failed for {}", AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                InputInventorySlot extra = extraSlot.get();
                OutputInventorySlot output = outputSlot.get();
                InfuseStorage storage = infuseStorage.get();
                if (input == null || extra == null || output == null || storage == null) {
                    return false;
                }
                if (input.isEmpty() || extra.isEmpty()) {
                    return true;
                }
                InfuseObject object = InfuseRegistry.getObject(extra.getStack());
                if (object == null || !storage.canReceive(object)) {
                    return false;
                }
                InfuseStorage simulatedInfuse = getSimulatedInfuseStorage(storage, object, extra.getCount(), maxInfuse.getAsInt());
                if (simulatedInfuse == null) {
                    return false;
                }
                RECIPE recipe = getInfusionRecipe(recipes, input.getStack(), simulatedInfuse);
                return recipe != null && AEItemRecipeAdapters.canOutputToSlot(output, recipe.getOutput().output) &&
                      AEItemRecipeAdapters.hasInputRoom(input, input.getStack()) && AEItemRecipeAdapters.hasInputRoom(extra, extra.getStack());
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

    public static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> IAERecipeMachineAdapter itemGasToGas(
          Supplier<Map<ItemStackInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends IExtendedGasTank> outputTank,
          Supplier<Gas> gasType,
          Predicate<Gas> isValidGas,
          IntSupplier gasUsagePerOperation,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return Arrays.asList(recipes.get(), gasUsagePerOperation.getAsInt());
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectItemGasToGasRecipes(recipes.get(), gasType.get(), gasUsagePerOperation.getAsInt());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires item input and gas conversion input", machineName);
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires item input and gas conversion input", machineName);
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getItemGasToGasRecipe(host, recipe, stacks, recipes, inputSlot.get(), gasTank.get(), outputTank.get(), isValidGas,
                      gasUsagePerOperation, refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getItemGasToGasRecipe(host, recipe, stacks, recipes, inputSlot.get(), gasTank.get(), outputTank.get(), isValidGas,
                      gasUsagePerOperation, refreshRecipeLookupCache, machineName) == null) {
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
                InputInventorySlot input = inputSlot.get();
                IExtendedGasTank gas = gasTank.get();
                IExtendedGasTank output = outputTank.get();
                if (input == null || gas == null || output == null || !AEUpgradeFakeGas.isAvailable()) {
                    return false;
                }
                ItemStack currentInput = input.getStack();
                if (currentInput.isEmpty()) {
                    return gas.getNeeded() > 0 && output.getNeeded() > 0;
                }
                RECIPE recipe = getItemToGasRecipe(recipes, refreshRecipeLookupCache, currentInput);
                return recipe != null && AEItemRecipeAdapters.hasInputRoom(input, currentInput) &&
                      canGasStackInsert(output, recipe.getOutput().output);
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
                observer.accept(gasTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainGas(node, outputTank.get());
            }

        };
    }

    public static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> IAERecipeMachineAdapter nucleosynthesizerItemGasToItem(
          Supplier<Map<NucleosynthesizerInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends GasInventorySlot> gasSlot,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<? extends IExtendedGasTank> gasTank,
          Predicate<Gas> isValidGas,
          Runnable refreshRecipeLookupCache) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectNucleosynthesizerGasItemRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "nucleosynthesizer requires item input and gas conversion input");
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "nucleosynthesizer requires item input and gas conversion input");
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getNucleosynthesizerRecipe(host, recipe, stacks, recipes, inputSlot.get(), gasSlot.get(), outputSlot.get(), gasTank.get(),
                      isValidGas, refreshRecipeLookupCache) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getNucleosynthesizerRecipe(host, recipe, stacks, recipes, inputSlot.get(), gasSlot.get(), outputSlot.get(), gasTank.get(),
                      isValidGas, refreshRecipeLookupCache) == null) {
                    return false;
                }
                GasStack fakeGasInput = getFakeGasInput(stacks.get(1), isValidGas);
                if (fakeGasInput != null) {
                    AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                          Arrays.asList(AERecipePort.item("item_input", inputSlot.get()), AERecipePort.gas("gas_input", gasTank.get())));
                    if (plan == null || !plan.execute()) {
                        return AEItemRecipeAdapters.reject(host, "atomic nucleosynthesizer fake gas input execute failed for {}",
                              AEUpgradeDebug.stacks(stacks));
                    }
                    return true;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.create()
                      .add(AERecipePort.item("item_input", inputSlot.get()), AERecipeRouteStack.item("item_input", stacks.get(0)))
                      .add(AERecipePort.item("gas_slot_input", gasSlot.get()), AERecipeRouteStack.item("gas_slot_input", stacks.get(1)));
                if (!plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic nucleosynthesizer input execute failed for {}", AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                GasInventorySlot gas = gasSlot.get();
                OutputInventorySlot output = outputSlot.get();
                IExtendedGasTank tank = gasTank.get();
                if (input == null || gas == null || output == null || tank == null) {
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
                if (conversion == null || !canGasTankReceiveType(tank, conversion)) {
                    return false;
                }
                if (currentInput.isEmpty()) {
                    return true;
                }
                RECIPE recipe = getNucleosynthesizerRecipe(recipes, refreshRecipeLookupCache, currentInput, conversion);
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
                return AERecipePort.drainItem(node, outputSlot.get());
            }

        };
    }

    public static <RECIPE extends MachineRecipe<PressurizedInput, PressurizedOutput, RECIPE>> IAERecipeMachineAdapter pressurizedItemFluidGas(
          Supplier<Map<PressurizedInput, RECIPE>> recipes,
          Supplier<? extends InputInventorySlot> inputSlot,
          Supplier<? extends IExtendedFluidTank> fluidTank,
          Supplier<? extends IExtendedGasTank> gasTank,
          Supplier<? extends OutputInventorySlot> outputSlot,
          Supplier<? extends IExtendedGasTank> outputGasTank,
          Runnable refreshRecipeLookupCache,
          String machineName) {
        return new IAERecipeMachineAdapter() {

            @Override
            public Object getRecipeSourceKey(IAEItemRecipeHost host) {
                return recipes.get();
            }

            @Override
            public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
                return AEUpgradeRecipeCache.collectPressurizedRecipes(recipes.get());
            }

            @Override
            public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires item, fake fluid, and fake gas inputs", machineName);
            }

            @Override
            public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
                return AEItemRecipeAdapters.reject(host, "{} requires item, fake fluid, and fake gas inputs", machineName);
            }

            @Override
            public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                return getPressurizedRecipe(host, recipe, stacks, recipes, inputSlot.get(), fluidTank.get(), gasTank.get(), outputSlot.get(),
                      outputGasTank.get(), refreshRecipeLookupCache, machineName) != null;
            }

            @Override
            public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
                if (getPressurizedRecipe(host, recipe, stacks, recipes, inputSlot.get(), fluidTank.get(), gasTank.get(), outputSlot.get(),
                      outputGasTank.get(), refreshRecipeLookupCache, machineName) == null) {
                    return false;
                }
                AERecipeTransferPlan plan = AERecipeTransferPlan.fromLegacyInputsByPortId(recipe, stacks,
                      Arrays.asList(AERecipePort.item("item_input", inputSlot.get()), AERecipePort.fluid("fluid_input", fluidTank.get()),
                            AERecipePort.gas("gas_input", gasTank.get())));
                if (plan == null || !plan.execute()) {
                    return AEItemRecipeAdapters.reject(host, "atomic {} input execute failed for {}", machineName, AEUpgradeDebug.stacks(stacks));
                }
                return true;
            }

            @Override
            public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
                InputInventorySlot input = inputSlot.get();
                IExtendedFluidTank fluid = fluidTank.get();
                IExtendedGasTank gas = gasTank.get();
                OutputInventorySlot output = outputSlot.get();
                IExtendedGasTank outputGas = outputGasTank.get();
                if (input == null || fluid == null || gas == null || output == null || outputGas == null ||
                    !AEUpgradeFakeFluid.isAvailable() || !AEUpgradeFakeGas.isAvailable()) {
                    return false;
                }
                ItemStack currentInput = input.getStack();
                if (!AEItemRecipeAdapters.hasInputRoom(input, currentInput) || fluid.getNeeded() <= 0 || gas.getNeeded() <= 0) {
                    return false;
                }
                if (currentInput.isEmpty() || fluid.getFluid() == null || gas.getGas() == null) {
                    return !getExposedItemRecipes(host).isEmpty();
                }
                RECIPE recipe = getPressurizedRecipe(recipes, refreshRecipeLookupCache, currentInput, fluid.getFluid(), gas.getGas());
                return recipe != null && canPressurizedOutputAccept(output, outputGas, recipe.getOutput());
            }

            @Override
            public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
                observer.accept(inputSlot.get());
                observer.accept(fluidTank.get());
                observer.accept(gasTank.get());
            }

            @Override
            public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
                return AERecipePort.drainItemAndGas(node, outputSlot.get(), outputGasTank.get());
            }

        };
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<InfusionInput, ItemStackOutput, RECIPE>> RECIPE getInfusionRecipe(IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<InfusionInput, RECIPE>> recipes, @Nullable InputInventorySlot inputSlot,
          @Nullable InputInventorySlot extraSlot, @Nullable OutputInventorySlot outputSlot, @Nullable InfuseStorage infuseStorage, int maxInfuse) {
        if (inputSlot == null || extraSlot == null || outputSlot == null || infuseStorage == null) {
            AEItemRecipeAdapters.reject(host, "input, extra, output slot, or infuse storage is not initialized");
            return null;
        }
        if (stacks.size() != 2) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} inputs but infusion machine requires 2", stacks.size());
            return null;
        }
        if (!exposedRecipe.matchesInputs(stacks)) {
            AEItemRecipeAdapters.reject(host, "AE supplied inputs that do not match the exposed infusion route");
            return null;
        }
        ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(inputSlot, stacks.get(0));
        ItemStack simulatedExtra = AEItemRecipeAdapters.getSimulatedStackWithInsert(extraSlot, stacks.get(1));
        InfuseObject object = InfuseRegistry.getObject(simulatedExtra);
        if (object == null || !infuseStorage.canReceive(object)) {
            AEItemRecipeAdapters.reject(host, "extra input {} cannot provide compatible infuse", AEUpgradeDebug.stack(simulatedExtra));
            return null;
        }
        InfuseStorage simulatedInfuse = getSimulatedInfuseStorage(infuseStorage, object, simulatedExtra.getCount(), maxInfuse);
        if (simulatedInfuse == null) {
            AEItemRecipeAdapters.reject(host, "extra input {} would exceed infuse capacity", AEUpgradeDebug.stack(simulatedExtra));
            return null;
        }
        RECIPE machineRecipe = findMatchingInfusionRecipe(recipes, exposedRecipe, simulatedInput, simulatedInfuse);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism infusion recipe for simulated inputs {} and {}",
                  AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(simulatedExtra));
            return null;
        }
        if (!AEItemRecipeAdapters.canOutputToSlot(outputSlot, exposedRecipe.getOutputStack())) {
            AEItemRecipeAdapters.reject(host, "output slot cannot accept {}", AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        ItemStack inputRemainder = inputSlot.insertItem(stacks.get(0).copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        ItemStack extraRemainder = extraSlot.insertItem(stacks.get(1).copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        if (!inputRemainder.isEmpty() || !extraRemainder.isEmpty()) {
            AEItemRecipeAdapters.reject(host, "input simulation left remainders {} and {}", AEUpgradeDebug.stack(inputRemainder),
                  AEUpgradeDebug.stack(extraRemainder));
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static InfuseStorage getSimulatedInfuseStorage(InfuseStorage infuseStorage, InfuseObject object, int sourceCount, int maxInfuse) {
        if (object == null || sourceCount <= 0 || !infuseStorage.canReceive(object)) {
            return null;
        }
        long added = (long) object.stored * sourceCount;
        if ((long) infuseStorage.getAmount() + added > maxInfuse) {
            return null;
        }
        InfuseStorage simulated = new InfuseStorage();
        simulated.copyFrom(infuseStorage);
        simulated.increase(object, sourceCount);
        return simulated;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<InfusionInput, ItemStackOutput, RECIPE>> RECIPE getInfusionRecipe(
          Supplier<Map<InfusionInput, RECIPE>> recipes, ItemStack input, InfuseStorage infuseStorage) {
        if (input.isEmpty()) {
            return null;
        }
        return RecipeHandler.getRecipe(new InfusionInput(infuseStorage, input), recipes.get());
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<InfusionInput, ItemStackOutput, RECIPE>> RECIPE findMatchingInfusionRecipe(
          Supplier<Map<InfusionInput, RECIPE>> recipes, AEExposedRecipe exposedRecipe, ItemStack simulatedInput, InfuseStorage simulatedInfuse) {
        for (RECIPE recipe : recipes.get().values()) {
            int operations = getOutputOperations(recipe.getOutput().output, exposedRecipe.getOutputStack());
            if (operations <= 0) {
                continue;
            }
            ItemStack requiredInput = scaledStack(recipe.getInput().inputStack, operations);
            if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
                continue;
            }
            InfuseStorage requiredInfuse = new InfuseStorage(recipe.getInput().infuse.getType(), recipe.getInput().infuse.getAmount() * operations);
            if (simulatedInfuse.contains(requiredInfuse)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<ItemStackInput, GasOutput, RECIPE>> RECIPE getItemGasToGasRecipe(IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<ItemStackInput, RECIPE>> recipes,
          @Nullable InputInventorySlot inputSlot, @Nullable IExtendedGasTank gasTank, @Nullable IExtendedGasTank outputTank,
          Predicate<Gas> isValidGas, IntSupplier gasUsagePerOperation, Runnable refreshRecipeLookupCache, String machineName) {
        if (inputSlot == null || gasTank == null || outputTank == null) {
            AEItemRecipeAdapters.reject(host, "input slot, input gas tank, or output gas tank is not initialized");
            return null;
        }
        if (!AEUpgradeFakeGas.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE2FC fake gas output is not available");
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
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, isValidGas);
        if (gasStack == null) {
            AEItemRecipeAdapters.reject(host, "gas source {} cannot provide a supported conversion gas", AEUpgradeDebug.stack(stacks.get(1)));
            return null;
        }
        int gasPerOperation = gasUsagePerOperation.getAsInt();
        if (gasPerOperation <= 0 || gasStack.amount % gasPerOperation != 0) {
            AEItemRecipeAdapters.reject(host, "gas source {} provides {} but gas usage per operation is {}",
                  AEUpgradeDebug.stack(stacks.get(1)), gasStack.amount, gasPerOperation);
            return null;
        }
        int operations = gasStack.amount / gasPerOperation;
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "gas source {} cannot run any operation", AEUpgradeDebug.stack(stacks.get(1)));
            return null;
        }
        if (!canGasStackInsert(gasTank, gasStack)) {
            AEItemRecipeAdapters.reject(host, "input gas tank cannot accept {}", gasStack);
            return null;
        }
        ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(inputSlot, stacks.get(0));
        RECIPE machineRecipe = getItemToGasRecipe(recipes, refreshRecipeLookupCache, simulatedInput);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for simulated input {}", machineName, AEUpgradeDebug.stack(simulatedInput));
            return null;
        }
        ItemStack requiredInput = scaledStack(machineRecipe.getInput().ingredient, operations);
        if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
            AEItemRecipeAdapters.reject(host, "simulated input {} does not contain Mekanism recipe ingredient {}",
                  AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(requiredInput));
            return null;
        }
        GasStack requiredOutput = scaledGasStack(machineRecipe.getOutput().output, operations);
        if (!AEUpgradeFakeGas.outputMatches(exposedRecipe.getOutputStack(), requiredOutput)) {
            AEItemRecipeAdapters.reject(host, "machine gas output {} does not match exposed output {}",
                  requiredOutput, AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        if (!canGasStackInsert(outputTank, requiredOutput)) {
            AEItemRecipeAdapters.reject(host, "output gas tank cannot accept {}", requiredOutput);
            return null;
        }
        ItemStack inputRemainder = inputSlot.insertItem(stacks.get(0).copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        GasStack gasRemainder = gasTank.insert(gasStack.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        if (!inputRemainder.isEmpty() || gasRemainder != null && gasRemainder.amount > 0) {
            AEItemRecipeAdapters.reject(host, "input simulation left item remainder {} and gas remainder {}",
                  AEUpgradeDebug.stack(inputRemainder), gasRemainder);
            return null;
        }
        return machineRecipe;
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

    @Nullable
    private static <RECIPE extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, RECIPE>> RECIPE getNucleosynthesizerRecipe(
          IAEItemRecipeHost host, AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<NucleosynthesizerInput, RECIPE>> recipes,
          @Nullable InputInventorySlot inputSlot, @Nullable GasInventorySlot gasSlot, @Nullable OutputInventorySlot outputSlot,
          @Nullable IExtendedGasTank gasTank, Predicate<Gas> isValidGas, Runnable refreshRecipeLookupCache) {
        if (inputSlot == null || gasSlot == null || outputSlot == null || gasTank == null) {
            AEItemRecipeAdapters.reject(host, "input, gas, output slot, or gas tank is not initialized");
            return null;
        }
        if (stacks.size() != 2) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} inputs but nucleosynthesizer requires 2", stacks.size());
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "AE supplied inputs that do not match the exposed nucleosynthesizer route");
            return null;
        }
        ItemStack gasSource = stacks.get(1);
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, isValidGas);
        if (gasStack == null) {
            AEItemRecipeAdapters.reject(host, "gas source {} cannot provide a supported conversion gas", AEUpgradeDebug.stack(gasSource));
            return null;
        }
        if (!canGasTankReceiveType(gasTank, gasStack)) {
            AEItemRecipeAdapters.reject(host, "gas tank cannot accept {}", gasStack);
            return null;
        }
        ItemStack simulatedInput = AEItemRecipeAdapters.getSimulatedStackWithInsert(inputSlot, stacks.get(0));
        RECIPE machineRecipe = getNucleosynthesizerRecipe(recipes, refreshRecipeLookupCache, simulatedInput, gasStack);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism nucleosynthesizer recipe for simulated input {} and gas {}",
                  AEUpgradeDebug.stack(simulatedInput), gasStack);
            return null;
        }
        int operations = getOutputOperations(machineRecipe.getOutput().output, exposedRecipe.getOutputStack());
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "machine output {} does not match exposed output {}",
                  AEUpgradeDebug.stack(machineRecipe.getOutput().output), AEUpgradeDebug.outputStack(exposedRecipe));
            return null;
        }
        ItemStack requiredInput = scaledStack(machineRecipe.getInput().getSolid(), operations);
        if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
            AEItemRecipeAdapters.reject(host, "simulated input {} does not contain Mekanism recipe ingredient {}",
                  AEUpgradeDebug.stack(simulatedInput), AEUpgradeDebug.stack(requiredInput));
            return null;
        }
        GasStack requiredGas = machineRecipe.getInput().getGas();
        if (requiredGas == null || (long) requiredGas.amount * operations != gasStack.amount) {
            AEItemRecipeAdapters.reject(host, "gas source {} provides {} but route requires {}",
                  AEUpgradeDebug.stack(gasSource), gasStack.amount, requiredGas == null ? 0 : (long) requiredGas.amount * operations);
            return null;
        }
        if (!AEItemRecipeAdapters.canOutputToSlot(outputSlot, exposedRecipe.getOutputStack())) {
            AEItemRecipeAdapters.reject(host, "output slot cannot accept {}", AEUpgradeDebug.outputStack(exposedRecipe));
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
    private static <RECIPE extends MachineRecipe<PressurizedInput, PressurizedOutput, RECIPE>> RECIPE getPressurizedRecipe(IAEItemRecipeHost host,
          AEExposedRecipe exposedRecipe, List<ItemStack> stacks, Supplier<Map<PressurizedInput, RECIPE>> recipes,
          @Nullable InputInventorySlot inputSlot, @Nullable IExtendedFluidTank fluidTank, @Nullable IExtendedGasTank gasTank,
          @Nullable OutputInventorySlot outputSlot, @Nullable IExtendedGasTank outputGasTank, Runnable refreshRecipeLookupCache,
          String machineName) {
        if (inputSlot == null || fluidTank == null || gasTank == null || outputSlot == null || outputGasTank == null) {
            AEItemRecipeAdapters.reject(host, "input/output slots or tanks are not initialized");
            return null;
        }
        if (!AEUpgradeFakeFluid.isAvailable() || !AEUpgradeFakeGas.isAvailable()) {
            AEItemRecipeAdapters.reject(host, "AE2FC fake fluid/gas support is not available");
            return null;
        }
        if (stacks.size() != 3) {
            AEItemRecipeAdapters.reject(host, "AE supplied {} inputs but {} requires 3", stacks.size(), machineName);
            return null;
        }
        if (!AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            AEItemRecipeAdapters.reject(host, "AE supplied inputs that do not match the exposed {} route", machineName);
            return null;
        }
        FluidStack fluidInput = AERecipeRouteLegacyIO.getFluidInput(exposedRecipe, stacks, 1);
        GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 2, gas -> hasMatchingPressurizedGas(recipes.get(), gas));
        if (fluidInput == null || gasInput == null) {
            AEItemRecipeAdapters.reject(host, "{} inputs {} and {} are not fake fluid/gas stacks",
                  machineName, AEUpgradeDebug.stack(stacks.get(1)), AEUpgradeDebug.stack(stacks.get(2)));
            return null;
        }
        ItemStack simulatedItem = AEItemRecipeAdapters.getSimulatedStackWithInsert(inputSlot, stacks.get(0));
        RECIPE machineRecipe = getPressurizedRecipe(recipes, refreshRecipeLookupCache, simulatedItem, fluidInput, gasInput);
        if (machineRecipe == null) {
            AEItemRecipeAdapters.reject(host, "no Mekanism {} recipe for item {}, fluid {}, gas {}",
                  machineName, AEUpgradeDebug.stack(simulatedItem), fluidInput, gasInput);
            return null;
        }
        if (!machineRecipe.getInput().meets(new PressurizedInput(simulatedItem, fluidInput, gasInput))) {
            AEItemRecipeAdapters.reject(host, "{} inputs do not contain recipe requirements", machineName);
            return null;
        }
        int operations = getPressurizedOperations(machineRecipe.getInput(), stacks.get(0), fluidInput, gasInput);
        if (operations <= 0) {
            AEItemRecipeAdapters.reject(host, "{} inputs do not provide a whole number of recipe operations", machineName);
            return null;
        }
        if (!outputsMatch(exposedRecipe, machineRecipe.getOutput(), operations)) {
            AEItemRecipeAdapters.reject(host, "machine outputs do not match exposed outputs {}", AEUpgradeDebug.outputStacks(exposedRecipe));
            return null;
        }
        if (!canPressurizedInputsAccept(inputSlot, fluidTank, gasTank, stacks.get(0), fluidInput, gasInput)) {
            AEItemRecipeAdapters.reject(host, "input slot/tanks cannot accept supplied {} inputs", machineName);
            return null;
        }
        if (!canPressurizedOutputAccept(outputSlot, outputGasTank, machineRecipe.getOutput(), operations)) {
            AEItemRecipeAdapters.reject(host, "output slot/tank cannot accept exposed {} outputs", machineName);
            return null;
        }
        return machineRecipe;
    }

    @Nullable
    private static <RECIPE extends MachineRecipe<PressurizedInput, PressurizedOutput, RECIPE>> RECIPE getPressurizedRecipe(
          Supplier<Map<PressurizedInput, RECIPE>> recipes, Runnable refreshRecipeLookupCache, ItemStack item, FluidStack fluid, GasStack gas) {
        if (item.isEmpty() || fluid == null || fluid.getFluid() == null || fluid.amount <= 0 || gas == null || gas.getGas() == null || gas.amount <= 0) {
            return null;
        }
        refreshRecipeLookupCache.run();
        return RecipeHandler.getRecipe(new PressurizedInput(item, fluid, gas), recipes.get());
    }

    private static boolean outputsMatch(AEExposedRecipe exposedRecipe, PressurizedOutput output, int operations) {
        List<ItemStack> exposedOutputs = exposedRecipe.getOutputStacks();
        int outputIndex = 0;
        ItemStack baseItemOutput = output.getItemOutput();
        ItemStack itemOutput = scaledStack(baseItemOutput, operations);
        if (!baseItemOutput.isEmpty() && itemOutput.isEmpty()) {
            return false;
        }
        if (!itemOutput.isEmpty()) {
            if (outputIndex >= exposedOutputs.size() || !itemOutputsMatch(exposedOutputs.get(outputIndex), itemOutput)) {
                return false;
            }
            outputIndex++;
        }
        GasStack baseGasOutput = output.getGasOutput();
        GasStack gasOutput = scaledGasStack(baseGasOutput, operations);
        if (baseGasOutput != null && gasOutput == null) {
            return false;
        }
        if (gasOutput != null && gasOutput.getGas() != null && gasOutput.amount > 0) {
            if (outputIndex >= exposedOutputs.size() || !AEUpgradeFakeGas.outputMatches(exposedOutputs.get(outputIndex), gasOutput)) {
                return false;
            }
            outputIndex++;
        }
        return outputIndex == exposedOutputs.size();
    }

    private static boolean outputsMatch(AEExposedRecipe exposedRecipe, PressurizedOutput output) {
        return outputsMatch(exposedRecipe, output, exposedRecipe.getCraftAmount());
    }

    private static boolean itemOutputsMatch(ItemStack expected, ItemStack actual) {
        return AEItemRecipeAdapters.outputsMatch(expected, actual, 1);
    }

    private static boolean itemOutputsMatch(ItemStack expected, ItemStack actual, int multiplier) {
        return AEItemRecipeAdapters.outputsMatch(expected, actual, multiplier);
    }

    private static boolean canPressurizedInputsAccept(InputInventorySlot inputSlot, IExtendedFluidTank fluidTank, IExtendedGasTank gasTank,
          ItemStack item, FluidStack fluid, GasStack gas) {
        ItemStack itemRemainder = inputSlot.insertItem(item.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        FluidStack fluidRemainder = fluidTank.insert(fluid.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        GasStack gasRemainder = gasTank.insert(gas.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL);
        return itemRemainder.isEmpty() && (fluidRemainder == null || fluidRemainder.amount <= 0) && (gasRemainder == null || gasRemainder.amount <= 0);
    }

    private static boolean canPressurizedOutputAccept(OutputInventorySlot outputSlot, IExtendedGasTank outputGasTank, PressurizedOutput output) {
        return canPressurizedOutputAccept(outputSlot, outputGasTank, output, 1);
    }

    private static boolean canPressurizedOutputAccept(OutputInventorySlot outputSlot, IExtendedGasTank outputGasTank, PressurizedOutput output,
          int operations) {
        ItemStack baseItemOutput = output.getItemOutput();
        ItemStack itemOutput = scaledStack(baseItemOutput, operations);
        if (!baseItemOutput.isEmpty() && itemOutput.isEmpty()) {
            return false;
        }
        if (!itemOutput.isEmpty() && !outputSlot.insertItem(itemOutput.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL).isEmpty()) {
            return false;
        }
        GasStack baseGasOutput = output.getGasOutput();
        GasStack gasOutput = scaledGasStack(baseGasOutput, operations);
        if (baseGasOutput != null && gasOutput == null) {
            return false;
        }
        if (gasOutput != null && gasOutput.getGas() != null && gasOutput.amount > 0) {
            return canGasStackInsert(outputGasTank, gasOutput);
        }
        return true;
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

    private static boolean canGasTankReceiveType(IExtendedGasTank gasTank, GasStack stack) {
        return stack != null && stack.getGas() != null && gasTank.canReceiveType(stack.getGas());
    }

    private static boolean canGasStackInsert(IExtendedGasTank gasTank, GasStack stack) {
        return stack != null && stack.getGas() != null && stack.amount > 0 &&
              gasTank.insert(stack.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL) == null;
    }

    private static boolean hasMatchingPressurizedGas(Map<PressurizedInput, ?> recipes, @Nullable Gas gas) {
        if (gas == null) {
            return false;
        }
        for (PressurizedInput input : recipes.keySet()) {
            GasStack stack = input.getGas();
            if (stack != null && stack.getGas() == gas) {
                return true;
            }
        }
        return false;
    }

    private static int getPressurizedOperations(PressurizedInput input, ItemStack item, FluidStack fluid, GasStack gas) {
        if (input == null || !input.isValid() || item.isEmpty() || fluid == null || gas == null ||
            !MachineInput.inputContains(item, input.getSolid()) || !fluid.isFluidEqual(input.getFluid()) || !gas.isGasEqual(input.getGas())) {
            return -1;
        }
        int itemOperations = getSingleOperations(input.getSolid().getCount(), item.getCount());
        int fluidOperations = getSingleOperations(input.getFluid().amount, fluid.amount);
        int gasOperations = getSingleOperations(input.getGas().amount, gas.amount);
        return itemOperations > 0 && itemOperations == fluidOperations && itemOperations == gasOperations ? itemOperations : -1;
    }

    private static int getSingleOperations(int required, int provided) {
        return required > 0 && provided > 0 && provided % required == 0 ? provided / required : -1;
    }

    private static int getOutputOperations(ItemStack recipeOutput, ItemStack exposedOutput) {
        if (recipeOutput.isEmpty() || exposedOutput.isEmpty() || !ItemHandlerHelper.canItemStacksStack(recipeOutput, exposedOutput)) {
            return -1;
        }
        int recipeCount = recipeOutput.getCount();
        return recipeCount > 0 && exposedOutput.getCount() % recipeCount == 0 ? exposedOutput.getCount() / recipeCount : -1;
    }

    private static ItemStack scaledStack(ItemStack stack, int multiplier) {
        return AERecipeStacks.scale(stack, multiplier);
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
}
