package mekceuaeupgrade.common.adapter;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEFactoryRecipeHost;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AERecipeStacks;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteLegacyIO;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;
import mekceuaeupgrade.common.transfer.AERecipePort;
import mekceuaeupgrade.common.transfer.AERecipeTransferPlan;
import mekceuaeupgrade.common.util.AEUpgradeDebug;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.MathUtils;
import mekanism.common.InfuseStorage;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.recipe.machines.Chance2MachineRecipe;
import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.recipe.outputs.ChanceOutput2;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.recipe.outputs.PressurizedOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class AEFactoryRecipeAdapter implements IAERecipeMachineAdapter {

    public static final AEFactoryRecipeAdapter INSTANCE = new AEFactoryRecipeAdapter();

    private AEFactoryRecipeAdapter() {
    }

    @Override
    public Object getRecipeSourceKey(IAEItemRecipeHost host) {
        IAEFactoryRecipeHost factory = asFactory(host);
        return factory == null ? null : Arrays.asList(factory.getAEFactoryRecipeType(), factory.getAEFactoryGasUsagePerOperation());
    }

    @Override
    public List<AEExposedRecipe> getExposedItemRecipes(IAEItemRecipeHost host) {
        IAEFactoryRecipeHost factory = asFactory(host);
        if (factory == null) {
            return Collections.emptyList();
        }
        return switch (factory.getAEFactoryRecipeType()) {
            case SMELTING -> AEUpgradeRecipeCache.collectBasicItemRecipes(RecipeHandler.Recipe.ENERGIZED_SMELTER.get());
            case ENRICHING -> AEUpgradeRecipeCache.collectBasicItemRecipes(RecipeHandler.Recipe.ENRICHMENT_CHAMBER.get());
            case CRUSHING -> AEUpgradeRecipeCache.collectBasicItemRecipes(RecipeHandler.Recipe.CRUSHER.get());
            case STAMPING -> AEUpgradeRecipeCache.collectBasicItemRecipes(RecipeHandler.Recipe.STAMPING.get());
            case ROLLING -> AEUpgradeRecipeCache.collectBasicItemRecipes(RecipeHandler.Recipe.ROLLING.get());
            case BRUSHED -> AEUpgradeRecipeCache.collectBasicItemRecipes(RecipeHandler.Recipe.BRUSHED.get());
            case TURNING -> AEUpgradeRecipeCache.collectBasicItemRecipes(RecipeHandler.Recipe.TURNING.get());
            case COMPRESSING -> AEUpgradeRecipeCache.collectAdvancedGasItemRecipes(RecipeHandler.Recipe.OSMIUM_COMPRESSOR.get(),
                  factory.getAEFactoryGasUsagePerOperation());
            case PURIFYING -> AEUpgradeRecipeCache.collectAdvancedGasItemRecipes(RecipeHandler.Recipe.PURIFICATION_CHAMBER.get(),
                  factory.getAEFactoryGasUsagePerOperation());
            case INJECTING -> AEUpgradeRecipeCache.collectAdvancedGasItemRecipes(RecipeHandler.Recipe.CHEMICAL_INJECTION_CHAMBER.get(),
                  factory.getAEFactoryGasUsagePerOperation());
            case COMBINING -> AEUpgradeRecipeCache.collectDoubleItemRecipes(RecipeHandler.Recipe.COMBINER.get());
            case AllOY -> AEUpgradeRecipeCache.collectDoubleItemRecipes(RecipeHandler.Recipe.ALLOY.get());
            case INFUSING -> AEUpgradeRecipeCache.collectInfusionItemRecipes(RecipeHandler.Recipe.METALLURGIC_INFUSER.get());
            case SAWING -> AEUpgradeRecipeCache.collectChanceItemRecipes(RecipeHandler.Recipe.PRECISION_SAWMILL.get());
            case EXTRACTOR -> AEUpgradeRecipeCache.collectChanceItemRecipes(RecipeHandler.Recipe.CELL_EXTRACTOR.get());
            case SEPARATOR -> AEUpgradeRecipeCache.collectChanceItemRecipes(RecipeHandler.Recipe.CELL_SEPARATOR.get());
            case FARM -> AEUpgradeRecipeCache.collectFarmGasItemRecipes(RecipeHandler.Recipe.ORGANIC_FARM.get(),
                  factory.getAEFactoryGasUsagePerOperation());
            case RECYCLER -> AEUpgradeRecipeCache.collectGuaranteedChance2ItemRecipes(RecipeHandler.Recipe.RECYCLER.get());
            case NUCLEOSYNTHESIZER -> AEUpgradeRecipeCache.collectNucleosynthesizerGasItemRecipes(
                  RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.get());
            case PRC -> AEUpgradeRecipeCache.collectPressurizedRecipes(RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get());
            default -> Collections.emptyList();
        };
    }

    @Override
    public boolean canAcceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
        IAEFactoryRecipeHost factory = asFactory(host);
        AERecipeTransferPlan plan = factory == null ? null : createFactoryInputPlan(factory, recipe, Collections.singletonList(stack));
        return plan != null && plan.canExecute();
    }

    @Override
    public boolean acceptItemInput(IAEItemRecipeHost host, AEExposedRecipe recipe, ItemStack stack) {
        IAEFactoryRecipeHost factory = asFactory(host);
        AERecipeTransferPlan plan = factory == null ? null : createFactoryInputPlan(factory, recipe, Collections.singletonList(stack));
        return plan != null && plan.execute();
    }

    @Override
    public boolean canAcceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
        IAEFactoryRecipeHost factory = asFactory(host);
        AERecipeTransferPlan plan = factory == null ? null : createFactoryInputPlan(factory, recipe, stacks);
        return plan != null && plan.canExecute();
    }

    @Override
    public boolean acceptItemInputs(IAEItemRecipeHost host, AEExposedRecipe recipe, List<ItemStack> stacks) {
        IAEFactoryRecipeHost factory = asFactory(host);
        AERecipeTransferPlan plan = factory == null ? null : createFactoryInputPlan(factory, recipe, stacks);
        return plan != null && plan.execute();
    }

    @Override
    public boolean canAcceptAnyItemInput(IAEItemRecipeHost host) {
        IAEFactoryRecipeHost factory = asFactory(host);
        if (factory == null || factory.getAEFactoryProcesses() == null || !supportsAEItemRecipeType(factory)) {
            return false;
        }
        if (isAEPressurizedRecipeType(factory)) {
            return canAcceptAnyAEPressurizedItemInput(factory);
        }
        if (isAEDoubleItemRecipeType(factory)) {
            return canAcceptAnyAEDoubleItemInput(factory);
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : factory.getAEFactoryProcesses()) {
            if (processInfo.inputSlot().isEmpty()) {
                return true;
            }
            MachineRecipe<?, ?, ?> recipe = factory.getAEFactoryRecipeForProcessInput(processInfo, processInfo.inputSlot().getStack(), false);
            if (recipe != null && isAERecipeSafe(recipe) && canAEOutputsToSlots(factory, recipe, processInfo) &&
                processInfo.inputSlot().getCount() < processInfo.inputSlot().getLimit(processInfo.inputSlot().getStack())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void observeInputContainers(IAEItemRecipeHost host, Consumer<Object> observer) {
        IAEFactoryRecipeHost factory = asFactory(host);
        if (factory == null || observer == null) {
            return;
        }
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (processes != null) {
            for (IAEFactoryRecipeHost.ProcessView process : processes) {
                if (process != null) {
                    observer.accept(process.inputSlot());
                }
            }
        }
        observer.accept(factory.getAEFactoryExtraSlot());
        observer.accept(factory.getAEFactoryGasTank());
        observer.accept(factory.getAEFactoryFluidTank());
    }

    @Override
    public boolean drainItemOutputs(IAEItemRecipeHost host, AEUpgradeNode node) {
        IAEFactoryRecipeHost factory = asFactory(host);
        if (factory == null || factory.getAEFactoryProcesses() == null) {
            return false;
        }
        boolean drained = false;
        for (IAEFactoryRecipeHost.ProcessView processInfo : factory.getAEFactoryProcesses()) {
            drained |= AERecipePort.drainAll(node, AERecipePort.item("item_output", processInfo.outputSlot()),
                  AERecipePort.item("secondary_item_output", processInfo.secondaryOutputSlot()));
        }
        if (isAEPressurizedRecipeType(factory)) {
            drained |= AERecipePort.drainAll(node, AERecipePort.gas("gas_output", factory.getAEFactoryGasOutputTank()));
        }
        return drained;
    }

    @Nullable
    private AERecipeTransferPlan createFactoryInputPlan(IAEFactoryRecipeHost factory, AEExposedRecipe exposedRecipe, List<ItemStack> stacks) {
        if (exposedRecipe == null || stacks == null || stacks.isEmpty()
              || !supportsAEItemRecipeType(factory) || !AERecipeRouteLegacyIO.matchesInputs(exposedRecipe, stacks)) {
            return null;
        }
        FactoryBatch batch = resolveFactoryBatch(factory, exposedRecipe, stacks);
        if (batch == null) {
            return null;
        }
        List<ProcessAllocation> allocations = allocateFactoryProcesses(factory, batch);
        if (allocations == null) {
            return rejectAEItemInputPlan(factory, "factory process slots cannot accept complete batch of {} operations", batch.operations);
        }
        AERecipeTransferPlan plan = AERecipeTransferPlan.create();
        for (ProcessAllocation allocation : allocations) {
            if (allocation.operations <= 0) {
                continue;
            }
            ItemStack portion = scaledStack(batch.inputPerOperation, allocation.operations);
            if (portion.isEmpty()) {
                return rejectAEItemInputPlan(factory, "factory primary input portion overflowed for process {}", allocation.process.process());
            }
            String portId = "item_input_" + allocation.process.process();
            plan.add(AERecipePort.item(portId, allocation.process.inputSlot()), AERecipeRouteStack.item(portId, portion));
        }
        for (SharedTransfer shared : batch.sharedInputs) {
            plan.add(shared.port, shared.stack);
        }
        return plan;
    }

    @Nullable
    private FactoryBatch resolveFactoryBatch(IAEFactoryRecipeHost factory, AEExposedRecipe exposedRecipe, List<ItemStack> stacks) {
        RecipeType type = factory.getAEFactoryRecipeType();
        if (isAEPressurizedRecipeType(factory)) {
            return resolvePressurizedBatch(factory, exposedRecipe, stacks);
        }
        if (stacks.get(0).isEmpty()) {
            return null;
        }
        ItemStack primaryInput = stacks.get(0);
        MachineRecipe<?, ?, ?> machineRecipe;
        List<SharedTransfer> sharedInputs = new ArrayList<>();
        if (type == RecipeType.INFUSING) {
            if (stacks.size() != 2) {
                return null;
            }
            machineRecipe = findInfusionBatchRecipe(factory, exposedRecipe, primaryInput, stacks.get(1));
            BasicInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
            if (machineRecipe == null || extraSlot == null) {
                return null;
            }
            sharedInputs.add(new SharedTransfer(AERecipePort.item("extra_input", extraSlot),
                  AERecipeRouteStack.item("extra_input", stacks.get(1))));
        } else if (isAEAdvancedGasItemRecipeType(factory) || isAEFarmGasItemRecipeType(factory)
              || isAENucleosynthesizerGasItemRecipeType(factory)) {
            if (stacks.size() != 2) {
                return null;
            }
            GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, factory::isAEFactoryInputGasValid);
            if (gasInput == null || gasInput.getGas() == null || gasInput.amount <= 0) {
                return null;
            }
            if (isAENucleosynthesizerGasItemRecipeType(factory)) {
                machineRecipe = RecipeHandler.getNucleosynthesizerRecipe(new NucleosynthesizerInput(primaryInput.copy(), gasInput.copy()));
            } else {
                machineRecipe = factory.getAEFactoryRecipe(new AdvancedMachineInput(primaryInput.copy(), gasInput.getGas()));
            }
            if (machineRecipe == null || !addFactoryGasSharedInput(factory, stacks.get(1), gasInput, sharedInputs)) {
                return null;
            }
        } else if (type == RecipeType.COMBINING || type == RecipeType.AllOY) {
            if (stacks.size() != 2) {
                return null;
            }
            machineRecipe = factory.getAEFactoryRecipe(new DoubleMachineInput(primaryInput.copy(), stacks.get(1).copy()));
            BasicInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
            if (machineRecipe == null || extraSlot == null) {
                return null;
            }
            sharedInputs.add(new SharedTransfer(AERecipePort.item("extra_input", extraSlot),
                  AERecipeRouteStack.item("extra_input", stacks.get(1))));
        } else {
            if (stacks.size() != 1) {
                return null;
            }
            machineRecipe = factory.getAEFactoryRecipe(new ItemStackInput(primaryInput.copy()));
        }
        if (machineRecipe == null || !isAERecipeSafe(machineRecipe) || !factory.isAEFactoryRecipeCurrent(machineRecipe)) {
            return null;
        }
        ItemStack inputPerOperation = getRecipeInput(machineRecipe);
        int operations = getItemOperations(inputPerOperation, primaryInput);
        if (operations <= 0 || !validateFactoryBatchInputs(factory, machineRecipe, stacks, exposedRecipe, operations)) {
            return null;
        }
        ItemStack primaryOutput = getAEPrimaryRecipeOutput(factory, machineRecipe);
        ItemStack totalPrimaryOutput = scaledStack(primaryOutput, operations);
        if (primaryOutput.isEmpty() || totalPrimaryOutput.isEmpty() || exposedRecipe.getOutputStacks().size() != 1
              || !outputsMatch(exposedRecipe.getOutputStack(), totalPrimaryOutput)) {
            return null;
        }
        ItemStack concreteInput = concreteInputPerOperation(primaryInput, inputPerOperation);
        return concreteInput.isEmpty() ? null : new FactoryBatch(machineRecipe, concreteInput, operations, sharedInputs, null);
    }

    @Nullable
    private FactoryBatch resolvePressurizedBatch(IAEFactoryRecipeHost factory, AEExposedRecipe exposedRecipe, List<ItemStack> stacks) {
        if (stacks.size() != 3 || !AEUpgradeFakeFluid.isAvailable() || !AEUpgradeFakeGas.isAvailable()) {
            return null;
        }
        BasicFluidTank fluidTank = factory.getAEFactoryFluidTank();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        if (fluidTank == null || gasTank == null || factory.getAEFactoryGasOutputTank() == null) {
            return null;
        }
        ItemStack primaryInput = stacks.get(0);
        FluidStack fluidInput = AERecipeRouteLegacyIO.getFluidInput(exposedRecipe, stacks, 1);
        GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 2, factory::isAEFactoryInputGasValid);
        PressurizedRecipe machineRecipe = getAEPressurizedRecipe(primaryInput, fluidInput, gasInput);
        if (machineRecipe == null || fluidInput == null || gasInput == null || !factory.isAEFactoryRecipeCurrent(machineRecipe)) {
            return null;
        }
        int operations = getAEPressurizedOperations(machineRecipe.getInput(), primaryInput, fluidInput, gasInput);
        if (operations <= 0 || !outputsMatch(exposedRecipe, machineRecipe.getOutput(), operations)) {
            return null;
        }
        List<SharedTransfer> sharedInputs = new ArrayList<>();
        sharedInputs.add(new SharedTransfer(AERecipePort.fluid("fluid_input", fluidTank),
              AERecipeRouteStack.fluid("fluid_input", fluidInput)));
        sharedInputs.add(new SharedTransfer(AERecipePort.gas("gas_input", gasTank),
              AERecipeRouteStack.gas("gas_input", gasInput)));
        ItemStack concreteInput = concreteInputPerOperation(primaryInput, machineRecipe.getInput().getSolid());
        return concreteInput.isEmpty() ? null : new FactoryBatch(machineRecipe, concreteInput, operations, sharedInputs, machineRecipe.getOutput());
    }

    private boolean addFactoryGasSharedInput(IAEFactoryRecipeHost factory, ItemStack legacyInput, GasStack gasInput,
          List<SharedTransfer> sharedInputs) {
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        if (gasTank == null || !canAEGasTankAccept(gasTank, gasInput)) {
            return false;
        }
        GasStack fakeGasInput = getAEFakeGasInput(factory, legacyInput);
        if (fakeGasInput != null) {
            sharedInputs.add(new SharedTransfer(AERecipePort.gas("gas_input", gasTank),
                  AERecipeRouteStack.gas("gas_input", gasInput)));
            return true;
        }
        BasicInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        if (extraSlot == null) {
            return false;
        }
        sharedInputs.add(new SharedTransfer(AERecipePort.item("extra_input", extraSlot),
              AERecipeRouteStack.item("extra_input", legacyInput)));
        return true;
    }

    @Nullable
    private MetallurgicInfuserRecipe findInfusionBatchRecipe(IAEFactoryRecipeHost factory, AEExposedRecipe exposedRecipe,
          ItemStack primaryInput, ItemStack extraInput) {
        InfuseObject object = InfuseRegistry.getObject(extraInput);
        if (object == null || !factory.getAEFactoryInfuseStorage().canReceive(object)) {
            return null;
        }
        InfuseStorage simulatedInfuse = getSimulatedInfuseStorage(factory, object, extraInput.getCount());
        return simulatedInfuse == null ? null : findMatchingAEInfusionRecipe(factory, exposedRecipe, primaryInput, simulatedInfuse);
    }

    private boolean validateFactoryBatchInputs(IAEFactoryRecipeHost factory, MachineRecipe<?, ?, ?> machineRecipe, List<ItemStack> stacks,
          AEExposedRecipe exposedRecipe, int operations) {
        RecipeType type = factory.getAEFactoryRecipeType();
        if (type == RecipeType.COMBINING || type == RecipeType.AllOY) {
            ItemStack extraPerOperation = getRecipeExtraInput(machineRecipe);
            return stacks.size() == 2 && getItemOperations(extraPerOperation, stacks.get(1)) == operations;
        }
        if (type == RecipeType.INFUSING) {
            if (!(machineRecipe instanceof MetallurgicInfuserRecipe infuserRecipe) || stacks.size() != 2) {
                return false;
            }
            InfuseObject object = InfuseRegistry.getObject(stacks.get(1));
            if (object == null || object.type != infuserRecipe.getInput().infuse.getType()) {
                return false;
            }
            long provided = (long) object.stored * stacks.get(1).getCount();
            long required = (long) infuserRecipe.getInput().infuse.getAmount() * operations;
            return provided == required;
        }
        if (isAEAdvancedGasItemRecipeType(factory) || isAEFarmGasItemRecipeType(factory)) {
            GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, factory::isAEFactoryInputGasValid);
            long required = (long) factory.getAEFactoryGasUsagePerOperation() * operations;
            return gasInput != null && gasInput.getGas() != null && gasInput.amount == required;
        }
        if (isAENucleosynthesizerGasItemRecipeType(factory)) {
            if (!(machineRecipe instanceof NucleosynthesizerRecipe nucleosynthesizerRecipe)) {
                return false;
            }
            GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(exposedRecipe, stacks, 1, factory::isAEFactoryInputGasValid);
            GasStack requiredGas = nucleosynthesizerRecipe.getInput().getGas();
            return gasInput != null && requiredGas != null && gasInput.isGasEqual(requiredGas)
                  && (long) requiredGas.amount * operations == gasInput.amount;
        }
        return stacks.size() == 1;
    }

    private int getItemOperations(ItemStack required, ItemStack provided) {
        if (required.isEmpty() || provided.isEmpty() || !MachineInput.inputContains(provided, required)) {
            return -1;
        }
        return getSingleOperations(required.getCount(), provided.getCount());
    }

    private ItemStack concreteInputPerOperation(ItemStack provided, ItemStack required) {
        if (getItemOperations(required, provided) <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack concrete = provided.copy();
        concrete.setCount(required.getCount());
        return concrete;
    }

    @Nullable
    private List<ProcessAllocation> allocateFactoryProcesses(IAEFactoryRecipeHost factory, FactoryBatch batch) {
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (processes == null || processes.length == 0) {
            return null;
        }
        List<ProcessAllocation> allocations = new ArrayList<>();
        long totalCapacity = 0;
        for (IAEFactoryRecipeHost.ProcessView process : processes) {
            int capacity = getFactoryProcessCapacity(factory, batch, process);
            if (capacity > 0) {
                allocations.add(new ProcessAllocation(process, capacity));
                totalCapacity += capacity;
            }
        }
        if (totalCapacity < batch.operations) {
            return null;
        }
        allocations.sort((left, right) -> {
            int byCapacity = Integer.compare(left.capacity, right.capacity);
            return byCapacity == 0 ? Integer.compare(left.process.process(), right.process.process()) : byCapacity;
        });
        int remaining = batch.operations;
        for (int i = 0; i < allocations.size(); i++) {
            ProcessAllocation allocation = allocations.get(i);
            int remainingSlots = allocations.size() - i;
            int fairShare = (int) (((long) remaining + remainingSlots - 1) / remainingSlots);
            allocation.operations = Math.min(allocation.capacity, fairShare);
            remaining -= allocation.operations;
        }
        return remaining == 0 ? allocations : null;
    }

    private int getFactoryProcessCapacity(IAEFactoryRecipeHost factory, FactoryBatch batch, IAEFactoryRecipeHost.ProcessView process) {
        BasicInventorySlot inputSlot = process.inputSlot();
        ItemStack current = inputSlot.getStack();
        if (!current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, batch.inputPerOperation)) {
            return 0;
        }
        ItemStack remainder = inputSlot.insertItem(batch.inputPerOperation.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        if (!remainder.isEmpty() || !canFactoryProcessProduce(factory, batch, process)) {
            return 0;
        }
        int room = inputSlot.getLimit(batch.inputPerOperation) - inputSlot.getCount();
        return room <= 0 ? 0 : room / batch.inputPerOperation.getCount();
    }

    private boolean canFactoryProcessProduce(IAEFactoryRecipeHost factory, FactoryBatch batch,
          IAEFactoryRecipeHost.ProcessView process) {
        if (batch.pressurizedOutput != null) {
            return canAEPressurizedOutputAccept(factory, process, batch.pressurizedOutput, 1);
        }
        return canAEOutputsToSlots(factory, batch.machineRecipe, process);
    }

    @Nullable
    private IAEFactoryRecipeHost.ProcessView findAEInputProcess(IAEFactoryRecipeHost factory, AEExposedRecipe recipe, ItemStack stack) {
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (processes == null) {
            return rejectAEItemInputProcess(factory, "process slots are not initialized");
        }
        if (!supportsAEItemRecipeType(factory)) {
            return rejectAEItemInputProcess(factory, "factory recipe type {} is not exposed to AE", factory.getAEFactoryRecipeType());
        }
        if (isAEPressurizedRecipeType(factory)) {
            return rejectAEItemInputProcess(factory, "factory recipe type {} requires item, generic fluid, and generic gas AE inputs",
                  factory.getAEFactoryRecipeType());
        }
        if (stack.isEmpty()) {
            return rejectAEItemInputProcess(factory, "AE supplied an empty input stack");
        }
        if (!AERecipeRouteLegacyIO.matchesInput(recipe, stack)) {
            return rejectAEItemInputProcess(factory, "AE supplied {} but recipe expects {}", AEUpgradeDebug.stack(stack),
                  AEUpgradeDebug.inputStack(recipe));
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            if (canAcceptAEItemInput(factory, processInfo, recipe, stack)) {
                return processInfo;
            }
        }
        return rejectAEItemInputProcess(factory, "no process can accept {} for output {}", AEUpgradeDebug.stack(stack),
              AEUpgradeDebug.outputStack(recipe));
    }

    @Nullable
    private IAEFactoryRecipeHost.ProcessView findAEDoubleInputProcess(IAEFactoryRecipeHost factory, AEExposedRecipe recipe, List<ItemStack> stacks) {
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (processes == null || factory.getAEFactoryExtraSlot() == null) {
            return rejectAEItemInputProcess(factory, "process or extra slot is not initialized");
        }
        if (!isAEDoubleItemRecipeType(factory)) {
            return rejectAEItemInputProcess(factory, "factory recipe type {} is not exposed as a double-input AE route",
                  factory.getAEFactoryRecipeType());
        }
        if (stacks.size() != 2) {
            return rejectAEItemInputProcess(factory, "AE supplied {} inputs but recipe expects 2", stacks.size());
        }
        if (!AERecipeRouteLegacyIO.matchesInputs(recipe, stacks)) {
            return rejectAEItemInputProcess(factory, "AE supplied inputs that do not match the exposed recipe");
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            if (canAcceptAEDoubleItemInput(factory, processInfo, recipe, stacks.get(0), stacks.get(1))) {
                return processInfo;
            }
        }
        return rejectAEItemInputProcess(factory, "no process can accept AE double inputs for output {}", AEUpgradeDebug.outputStack(recipe));
    }

    private boolean canAcceptAEItemInput(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, AEExposedRecipe recipe,
          ItemStack stack) {
        ItemStack simulatedInput = getSimulatedStackWithInsert(processInfo.inputSlot(), stack);
        MachineRecipe<?, ?, ?> machineRecipe = factory.getAEFactoryRecipeForProcessInput(processInfo, simulatedInput, false);
        if (machineRecipe == null || !isAERecipeSafe(machineRecipe)) {
            return false;
        }
        ItemStack recipeInput = getRecipeInput(machineRecipe);
        if (!MachineInput.inputContains(simulatedInput, recipeInput)) {
            return false;
        }
        ItemStack primaryOutput = getAEPrimaryRecipeOutput(factory, machineRecipe);
        if (!outputsMatch(recipe, primaryOutput)) {
            return false;
        }
        if (!canAEOutputsToSlots(factory, machineRecipe, processInfo)) {
            return false;
        }
        ItemStack remainder = processInfo.inputSlot().insertItem(stack.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return remainder.isEmpty();
    }

    private boolean canAcceptAEDoubleItemInput(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, AEExposedRecipe recipe,
          ItemStack input, ItemStack extra) {
        if (factory.getAEFactoryRecipeType() == RecipeType.INFUSING) {
            return canAcceptAEInfusionItemInput(factory, processInfo, recipe, input, extra);
        }
        if (isAEAdvancedGasItemRecipeType(factory)) {
            return canAcceptAEAdvancedGasItemInput(factory, processInfo, recipe, input, extra);
        }
        if (isAEFarmGasItemRecipeType(factory)) {
            return canAcceptAEFarmGasItemInput(factory, processInfo, recipe, input, extra);
        }
        if (isAENucleosynthesizerGasItemRecipeType(factory)) {
            return canAcceptAENucleosynthesizerGasItemInput(factory, processInfo, recipe, input, extra);
        }
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        if (extraSlot == null) {
            return false;
        }
        ItemStack simulatedInput = getSimulatedStackWithInsert(processInfo.inputSlot(), input);
        ItemStack simulatedExtra = getSimulatedStackWithInsert(extraSlot, extra);
        MachineRecipe<?, ?, ?> machineRecipe = factory.findAEFactoryRecipeForInput(simulatedInput, simulatedExtra);
        if (machineRecipe == null || !isAERecipeSafe(machineRecipe)) {
            return false;
        }
        if (!MachineInput.inputContains(simulatedInput, getRecipeInput(machineRecipe)) ||
            !MachineInput.inputContains(simulatedExtra, getRecipeExtraInput(machineRecipe))) {
            return false;
        }
        ItemStack primaryOutput = getAEPrimaryRecipeOutput(factory, machineRecipe);
        if (!outputsMatch(recipe, primaryOutput)) {
            return false;
        }
        if (!canAEOutputsToSlots(factory, machineRecipe, processInfo)) {
            return false;
        }
        ItemStack inputRemainder = processInfo.inputSlot().insertItem(input.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        ItemStack extraRemainder = extraSlot.insertItem(extra.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return inputRemainder.isEmpty() && extraRemainder.isEmpty();
    }

    private boolean supportsAEItemRecipeType(IAEFactoryRecipeHost factory) {
        return switch (factory.getAEFactoryRecipeType()) {
            case SMELTING, ENRICHING, CRUSHING, STAMPING, ROLLING, BRUSHED, TURNING, COMPRESSING, PURIFYING, INJECTING, COMBINING, AllOY, INFUSING, SAWING, EXTRACTOR, SEPARATOR, FARM, RECYCLER, PRC, NUCLEOSYNTHESIZER -> true;
            default -> false;
        };
    }

    private boolean isAEDoubleItemRecipeType(IAEFactoryRecipeHost factory) {
        return factory.getAEFactoryRecipeType() == RecipeType.COMBINING || factory.getAEFactoryRecipeType() == RecipeType.AllOY ||
              factory.getAEFactoryRecipeType() == RecipeType.INFUSING || isAEAdvancedGasItemRecipeType(factory) || isAEFarmGasItemRecipeType(factory) ||
              isAENucleosynthesizerGasItemRecipeType(factory);
    }

    private boolean isAEAdvancedGasItemRecipeType(IAEFactoryRecipeHost factory) {
        return factory.getAEFactoryRecipeType() == RecipeType.COMPRESSING || factory.getAEFactoryRecipeType() == RecipeType.PURIFYING ||
              factory.getAEFactoryRecipeType() == RecipeType.INJECTING;
    }

    private boolean isAEFarmGasItemRecipeType(IAEFactoryRecipeHost factory) {
        return factory.getAEFactoryRecipeType() == RecipeType.FARM;
    }

    private boolean isAENucleosynthesizerGasItemRecipeType(IAEFactoryRecipeHost factory) {
        return factory.getAEFactoryRecipeType() == RecipeType.NUCLEOSYNTHESIZER;
    }

    private boolean isAEDirectGasInputRecipeType(IAEFactoryRecipeHost factory) {
        return isAEAdvancedGasItemRecipeType(factory) || isAEFarmGasItemRecipeType(factory) || isAENucleosynthesizerGasItemRecipeType(factory);
    }

    private boolean isAEPressurizedRecipeType(IAEFactoryRecipeHost factory) {
        return factory.getAEFactoryRecipeType() == RecipeType.PRC;
    }

    private boolean isAERecipeSafe(MachineRecipe<?, ?, ?> recipe) {
        return recipe instanceof BasicMachineRecipe<?> || recipe instanceof DoubleMachineRecipe<?> || recipe instanceof ChanceMachineRecipe<?> ||
              recipe instanceof AdvancedMachineRecipe<?> ||
              recipe instanceof FarmMachineRecipe<?> ||
              recipe instanceof PressurizedRecipe ||
              recipe instanceof NucleosynthesizerRecipe ||
              recipe instanceof MetallurgicInfuserRecipe ||
              recipe instanceof Chance2MachineRecipe<?> chanceRecipe && chanceRecipe.getOutput().primaryChance >= 1;
    }

    private boolean canAcceptAnyAEDoubleItemInput(IAEFactoryRecipeHost factory) {
        if (factory.getAEFactoryRecipeType() == RecipeType.INFUSING) {
            return canAcceptAnyAEInfusionItemInput(factory);
        }
        if (isAEAdvancedGasItemRecipeType(factory)) {
            return canAcceptAnyAEAdvancedGasItemInput(factory);
        }
        if (isAEFarmGasItemRecipeType(factory)) {
            return canAcceptAnyAEFarmGasItemInput(factory);
        }
        if (isAENucleosynthesizerGasItemRecipeType(factory)) {
            return canAcceptAnyAENucleosynthesizerGasItemInput(factory);
        }
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (extraSlot == null || processes == null) {
            return false;
        }
        ItemStack currentExtra = extraSlot.getStack();
        if (currentExtra.isEmpty()) {
            for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
                if (hasAEInputRoom(processInfo.inputSlot(), processInfo.inputSlot().getStack())) {
                    return true;
                }
            }
            return false;
        }
        if (extraSlot.getCount() >= extraSlot.getLimit(currentExtra)) {
            return false;
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            ItemStack currentInput = processInfo.inputSlot().getStack();
            if (currentInput.isEmpty()) {
                return factory.hasAEFactoryRecipeForExtra(currentExtra);
            }
            MachineRecipe<?, ?, ?> recipe = factory.findAEFactoryRecipeForInput(currentInput, currentExtra);
            if (recipe != null && isAERecipeSafe(recipe) && canAEOutputsToSlots(factory, recipe, processInfo) &&
                hasAEInputRoom(processInfo.inputSlot(), currentInput)) {
                return true;
            }
        }
        return false;
    }

    private boolean canAcceptAEAdvancedGasItemInput(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, AEExposedRecipe recipe,
          ItemStack input, ItemStack extra) {
        if (!isAEAdvancedGasItemRecipeType(factory)) {
            return false;
        }
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        if (extraSlot == null || gasTank == null) {
            return false;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(recipe, Arrays.asList(input, extra), 1, factory::isAEFactoryInputGasValid);
        if (gasStack == null || !canAEGasTankAccept(gasTank, gasStack)) {
            return false;
        }
        ItemStack simulatedInput = getSimulatedStackWithInsert(processInfo.inputSlot(), input);
        MachineRecipe<?, ?, ?> machineRecipe = factory.getAEFactoryRecipe(new AdvancedMachineInput(simulatedInput, gasStack.getGas()));
        if (!(machineRecipe instanceof AdvancedMachineRecipe<?> advancedRecipe)) {
            return false;
        }
        int operations = getOutputOperations(advancedRecipe.getOutput().output, recipe.getOutputStack());
        if (operations <= 0) {
            return false;
        }
        ItemStack requiredInput = scaledStack(advancedRecipe.getInput().itemStack, operations);
        if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
            return false;
        }
        int requiredGas = MathUtils.clampToInt((long) factory.getAEFactoryGasUsagePerOperation() * operations);
        if (gasStack.amount != requiredGas || !canAEOutputToSlot(processInfo.outputSlot(), recipe.getOutputStack())) {
            return false;
        }
        ItemStack inputRemainder = processInfo.inputSlot().insertItem(input.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        GasStack fakeGasInput = getAEFakeGasInput(factory, extra);
        if (fakeGasInput != null) {
            GasStack gasRemainder = gasTank.insert(fakeGasInput.copy(), Action.SIMULATE, AutomationType.INTERNAL);
            return inputRemainder.isEmpty() && (gasRemainder == null || gasRemainder.amount <= 0);
        }
        return inputRemainder.isEmpty() && extraSlot.insertItem(extra.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    private boolean canAcceptAEFarmGasItemInput(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, AEExposedRecipe recipe,
          ItemStack input, ItemStack extra) {
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        if (!isAEFarmGasItemRecipeType(factory) || extraSlot == null || gasTank == null) {
            return false;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(recipe, Arrays.asList(input, extra), 1, factory::isAEFactoryInputGasValid);
        if (gasStack == null || !canAEGasTankAccept(gasTank, gasStack)) {
            return false;
        }
        ItemStack simulatedInput = getSimulatedStackWithInsert(processInfo.inputSlot(), input);
        MachineRecipe<?, ?, ?> machineRecipe = factory.getAEFactoryRecipe(new AdvancedMachineInput(simulatedInput, gasStack.getGas()));
        if (!(machineRecipe instanceof FarmMachineRecipe<?> farmRecipe)) {
            return false;
        }
        int operations = getOutputOperations(farmRecipe.getOutput().getMainOutput(), recipe.getOutputStack());
        if (operations <= 0) {
            return false;
        }
        ItemStack requiredInput = scaledStack(farmRecipe.getInput().itemStack, operations);
        if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
            return false;
        }
        int requiredGas = MathUtils.clampToInt((long) factory.getAEFactoryGasUsagePerOperation() * operations);
        if (gasStack.amount != requiredGas || !canAEFarmOutputsToSlots(farmRecipe, processInfo, operations)) {
            return false;
        }
        ItemStack inputRemainder = processInfo.inputSlot().insertItem(input.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        GasStack fakeGasInput = getAEFakeGasInput(factory, extra);
        if (fakeGasInput != null) {
            GasStack gasRemainder = gasTank.insert(fakeGasInput.copy(), Action.SIMULATE, AutomationType.INTERNAL);
            return inputRemainder.isEmpty() && (gasRemainder == null || gasRemainder.amount <= 0);
        }
        return inputRemainder.isEmpty() && extraSlot.insertItem(extra.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    private boolean canAcceptAENucleosynthesizerGasItemInput(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo,
          AEExposedRecipe recipe, ItemStack input, ItemStack extra) {
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        if (!isAENucleosynthesizerGasItemRecipeType(factory) || extraSlot == null || gasTank == null) {
            return false;
        }
        GasStack gasStack = AERecipeRouteLegacyIO.getGasInput(recipe, Arrays.asList(input, extra), 1, factory::isAEFactoryInputGasValid);
        if (gasStack == null || !canAEGasTankAccept(gasTank, gasStack)) {
            return false;
        }
        ItemStack simulatedInput = getSimulatedStackWithInsert(processInfo.inputSlot(), input);
        NucleosynthesizerRecipe machineRecipe = RecipeHandler.getNucleosynthesizerRecipe(new NucleosynthesizerInput(simulatedInput, gasStack));
        if (machineRecipe == null) {
            return false;
        }
        int operations = getOutputOperations(machineRecipe.getOutput().output, recipe.getOutputStack());
        if (operations <= 0) {
            return false;
        }
        ItemStack requiredInput = scaledStack(machineRecipe.getInput().getSolid(), operations);
        if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
            return false;
        }
        GasStack requiredGas = machineRecipe.getInput().getGas();
        if (requiredGas == null || (long) requiredGas.amount * operations != gasStack.amount ||
            !canAEOutputToSlot(processInfo.outputSlot(), recipe.getOutputStack())) {
            return false;
        }
        ItemStack inputRemainder = processInfo.inputSlot().insertItem(input.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        GasStack fakeGasInput = getAEFakeGasInput(factory, extra);
        if (fakeGasInput != null) {
            GasStack gasRemainder = gasTank.insert(fakeGasInput.copy(), Action.SIMULATE, AutomationType.INTERNAL);
            return inputRemainder.isEmpty() && (gasRemainder == null || gasRemainder.amount <= 0);
        }
        return inputRemainder.isEmpty() && extraSlot.insertItem(extra.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    private boolean canAcceptAnyAEAdvancedGasItemInput(IAEFactoryRecipeHost factory) {
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (extraSlot == null || gasTank == null || processes == null) {
            return false;
        }
        ItemStack currentExtra = extraSlot.getStack();
        if (currentExtra.isEmpty()) {
            for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
                if (hasAEInputRoom(processInfo.inputSlot(), processInfo.inputSlot().getStack())) {
                    return true;
                }
            }
            return false;
        }
        if (extraSlot.getCount() >= extraSlot.getLimit(currentExtra)) {
            return false;
        }
        GasStack gasStack = getAEGasFromSource(factory, currentExtra);
        if (gasStack == null || !canAEGasTankAccept(gasTank, gasStack)) {
            return false;
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            ItemStack currentInput = processInfo.inputSlot().getStack();
            if (currentInput.isEmpty()) {
                return true;
            }
            MachineRecipe<?, ?, ?> recipe = factory.getAEFactoryRecipe(new AdvancedMachineInput(currentInput, gasStack.getGas()));
            if (recipe != null && canAEOutputToSlot(processInfo.outputSlot(), getAEPrimaryRecipeOutput(factory, recipe)) &&
                hasAEInputRoom(processInfo.inputSlot(), currentInput)) {
                return true;
            }
        }
        return false;
    }

    private boolean canAcceptAnyAEFarmGasItemInput(IAEFactoryRecipeHost factory) {
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (extraSlot == null || gasTank == null || processes == null) {
            return false;
        }
        ItemStack currentExtra = extraSlot.getStack();
        if (currentExtra.isEmpty()) {
            for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
                if (hasAEInputRoom(processInfo.inputSlot(), processInfo.inputSlot().getStack())) {
                    return true;
                }
            }
            return false;
        }
        if (extraSlot.getCount() >= extraSlot.getLimit(currentExtra)) {
            return false;
        }
        GasStack gasStack = getAEGasFromSource(factory, currentExtra);
        if (gasStack == null || !canAEGasTankAccept(gasTank, gasStack)) {
            return false;
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            ItemStack currentInput = processInfo.inputSlot().getStack();
            if (currentInput.isEmpty()) {
                return true;
            }
            MachineRecipe<?, ?, ?> recipe = factory.getAEFactoryRecipe(new AdvancedMachineInput(currentInput, gasStack.getGas()));
            if (recipe instanceof FarmMachineRecipe<?> farmRecipe && canAEFarmOutputsToSlots(farmRecipe, processInfo, 1) &&
                hasAEInputRoom(processInfo.inputSlot(), currentInput)) {
                return true;
            }
        }
        return false;
    }

    private boolean canAcceptAnyAENucleosynthesizerGasItemInput(IAEFactoryRecipeHost factory) {
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (extraSlot == null || gasTank == null || processes == null) {
            return false;
        }
        ItemStack currentExtra = extraSlot.getStack();
        if (currentExtra.isEmpty()) {
            for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
                if (hasAEInputRoom(processInfo.inputSlot(), processInfo.inputSlot().getStack())) {
                    return true;
                }
            }
            return false;
        }
        if (extraSlot.getCount() >= extraSlot.getLimit(currentExtra)) {
            return false;
        }
        GasStack gasStack = getAEGasFromSource(factory, currentExtra);
        if (gasStack == null || !canAEGasTankAccept(gasTank, gasStack)) {
            return false;
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            ItemStack currentInput = processInfo.inputSlot().getStack();
            if (currentInput.isEmpty()) {
                return true;
            }
            NucleosynthesizerRecipe recipe = RecipeHandler.getNucleosynthesizerRecipe(new NucleosynthesizerInput(currentInput, gasStack));
            if (recipe != null && canAEOutputToSlot(processInfo.outputSlot(), recipe.getOutput().output) &&
                hasAEInputRoom(processInfo.inputSlot(), currentInput)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private IAEFactoryRecipeHost.ProcessView findAEPressurizedInputProcess(IAEFactoryRecipeHost factory, AEExposedRecipe recipe,
          List<ItemStack> stacks) {
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (processes == null || factory.getAEFactoryFluidTank() == null || factory.getAEFactoryGasTank() == null ||
            factory.getAEFactoryGasOutputTank() == null) {
            return rejectAEItemInputProcess(factory, "pressurized process slots or tanks are not initialized");
        }
        if (!isAEPressurizedRecipeType(factory)) {
            return rejectAEItemInputProcess(factory, "factory recipe type {} is not exposed as a pressurized AE route",
                  factory.getAEFactoryRecipeType());
        }
        if (!AEUpgradeFakeFluid.isAvailable() || !AEUpgradeFakeGas.isAvailable()) {
            return rejectAEItemInputProcess(factory, "AE generic fluid/gas support is not available");
        }
        if (stacks.size() != 3) {
            return rejectAEItemInputProcess(factory, "AE supplied {} inputs but pressurized factory requires 3", stacks.size());
        }
        if (!AERecipeRouteLegacyIO.matchesInputs(recipe, stacks)) {
            return rejectAEItemInputProcess(factory, "AE supplied inputs that do not match the exposed pressurized factory route");
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            if (canAcceptAEPressurizedItemInput(factory, processInfo, recipe, stacks)) {
                return processInfo;
            }
        }
        return rejectAEItemInputProcess(factory, "no process can accept AE pressurized inputs for output {}",
              AEUpgradeDebug.outputStacks(recipe));
    }

    private boolean canAcceptAEPressurizedItemInput(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo,
          AEExposedRecipe recipe, List<ItemStack> stacks) {
        FluidStack fluidInput = AERecipeRouteLegacyIO.getFluidInput(recipe, stacks, 1);
        GasStack gasInput = AERecipeRouteLegacyIO.getGasInput(recipe, stacks, 2, factory::isAEFactoryInputGasValid);
        if (fluidInput == null || gasInput == null) {
            return false;
        }
        ItemStack simulatedItem = getSimulatedStackWithInsert(processInfo.inputSlot(), stacks.get(0));
        PressurizedRecipe machineRecipe = getAEPressurizedRecipe(simulatedItem, fluidInput, gasInput);
        if (machineRecipe == null) {
            return false;
        }
        if (!machineRecipe.getInput().meets(new PressurizedInput(simulatedItem, fluidInput, gasInput))) {
            return false;
        }
        int operations = getAEPressurizedOperations(machineRecipe.getInput(), stacks.get(0), fluidInput, gasInput);
        if (operations <= 0 || !outputsMatch(recipe, machineRecipe.getOutput(), operations)) {
            return false;
        }
        return canAEPressurizedInputsAccept(factory, processInfo, stacks.get(0), fluidInput, gasInput) &&
              canAEPressurizedOutputAccept(factory, processInfo, machineRecipe.getOutput(), operations);
    }

    private boolean canAcceptAnyAEPressurizedItemInput(IAEFactoryRecipeHost factory) {
        BasicFluidTank fluidTank = factory.getAEFactoryFluidTank();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        BasicGasTank gasOutTank = factory.getAEFactoryGasOutputTank();
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (fluidTank == null || gasTank == null || gasOutTank == null || processes == null || !AEUpgradeFakeFluid.isAvailable() ||
            !AEUpgradeFakeGas.isAvailable()) {
            return false;
        }
        if (fluidTank.getNeeded() <= 0 || gasTank.getNeeded() <= 0 || RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get().isEmpty()) {
            return false;
        }
        FluidStack currentFluid = fluidTank.getFluid();
        GasStack currentGas = gasTank.getGas();
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            ItemStack currentInput = processInfo.inputSlot().getStack();
            if (currentInput.isEmpty()) {
                return true;
            }
            if (!hasAEInputRoom(processInfo.inputSlot(), currentInput)) {
                continue;
            }
            if (currentFluid == null || currentGas == null) {
                if (factory.hasAEFactoryPartialPressurizedRecipeInput(currentInput, currentFluid, currentGas)) {
                    return true;
                }
                continue;
            }
            PressurizedRecipe recipe = getAEPressurizedRecipe(currentInput, currentFluid, currentGas);
            if (recipe != null && canAEPressurizedOutputAccept(factory, processInfo, recipe.getOutput())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private PressurizedRecipe getAEPressurizedRecipe(ItemStack item, FluidStack fluid, GasStack gas) {
        if (item.isEmpty() || fluid == null || fluid.getFluid() == null || fluid.amount <= 0 || gas == null || gas.getGas() == null || gas.amount <= 0) {
            return null;
        }
        return RecipeHandler.getPRCRecipe(new PressurizedInput(item, fluid, gas));
    }

    private boolean canAEPressurizedInputsAccept(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, ItemStack item,
          FluidStack fluid, GasStack gas) {
        BasicFluidTank fluidTank = factory.getAEFactoryFluidTank();
        BasicGasTank gasTank = factory.getAEFactoryGasTank();
        if (fluidTank == null || gasTank == null) {
            return false;
        }
        ItemStack itemRemainder = processInfo.inputSlot().insertItem(item.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        FluidStack fluidRemainder = fluidTank.insert(fluid.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        GasStack gasRemainder = gasTank.insert(gas.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return itemRemainder.isEmpty() && (fluidRemainder == null || fluidRemainder.amount <= 0) && (gasRemainder == null || gasRemainder.amount <= 0);
    }

    private boolean canAEPressurizedOutputAccept(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, PressurizedOutput output) {
        return canAEPressurizedOutputAccept(factory, processInfo, output, 1);
    }

    private boolean canAEPressurizedOutputAccept(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, PressurizedOutput output,
          int operations) {
        ItemStack baseItemOutput = output.getItemOutput();
        ItemStack itemOutput = scaledStack(baseItemOutput, operations);
        if (!baseItemOutput.isEmpty() && itemOutput.isEmpty()) {
            return false;
        }
        if (!itemOutput.isEmpty() && !canAEOutputToSlot(processInfo.outputSlot(), itemOutput)) {
            return false;
        }
        GasStack baseGasOutput = output.getGasOutput();
        GasStack gasOutput = scaledGasStack(baseGasOutput, operations);
        if (baseGasOutput != null && gasOutput == null) {
            return false;
        }
        if (gasOutput != null && gasOutput.getGas() != null && gasOutput.amount > 0) {
            BasicGasTank gasOutTank = factory.getAEFactoryGasOutputTank();
            if (gasOutTank == null) {
                return false;
            }
            GasStack gasRemainder = gasOutTank.insert(gasOutput.copy(), Action.SIMULATE, AutomationType.INTERNAL);
            return gasRemainder == null || gasRemainder.amount <= 0;
        }
        return true;
    }

    private int getAEPressurizedOperations(PressurizedInput input, ItemStack item, FluidStack fluid, GasStack gas) {
        if (input == null || !input.isValid() || item.isEmpty() || fluid == null || gas == null ||
            !MachineInput.inputContains(item, input.getSolid()) || !fluid.isFluidEqual(input.getFluid()) || !gas.isGasEqual(input.getGas())) {
            return -1;
        }
        int itemOperations = getSingleOperations(input.getSolid().getCount(), item.getCount());
        int fluidOperations = getSingleOperations(input.getFluid().amount, fluid.amount);
        int gasOperations = getSingleOperations(input.getGas().amount, gas.amount);
        return itemOperations > 0 && itemOperations == fluidOperations && itemOperations == gasOperations ? itemOperations : -1;
    }

    private int getSingleOperations(int required, int provided) {
        return required > 0 && provided > 0 && provided % required == 0 ? provided / required : -1;
    }

    @Nullable
    private GasStack getAEGasFromSource(IAEFactoryRecipeHost factory, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        GasStack fakeGasInput = getAEFakeGasInput(factory, stack);
        if (fakeGasInput != null) {
            return fakeGasInput;
        }
        if (GasInventorySlot.isGasContainerItem(stack)) {
            return null;
        }
        GasStack perItem = GasConversionHandler.getConversionGas(stack, factory::isAEFactoryInputGasValid);
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
    private GasStack getAEFakeGasInput(IAEFactoryRecipeHost factory, ItemStack stack) {
        GasStack fakeGasInput = AEUpgradeFakeGas.unpackInput(stack);
        return fakeGasInput != null && factory.isAEFactoryInputGasValid(fakeGasInput.getGas()) ? fakeGasInput.copy() : null;
    }

    private boolean canAEGasTankAccept(BasicGasTank gasTank, GasStack stack) {
        return stack != null && stack.getGas() != null && gasTank.canReceiveType(stack.getGas());
    }

    private boolean canAcceptAEInfusionItemInput(IAEFactoryRecipeHost factory, IAEFactoryRecipeHost.ProcessView processInfo, AEExposedRecipe recipe,
          ItemStack input, ItemStack extra) {
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        if (extraSlot == null) {
            return false;
        }
        ItemStack simulatedInput = getSimulatedStackWithInsert(processInfo.inputSlot(), input);
        ItemStack simulatedExtra = getSimulatedStackWithInsert(extraSlot, extra);
        InfuseObject object = InfuseRegistry.getObject(simulatedExtra);
        if (object == null || !factory.getAEFactoryInfuseStorage().canReceive(object)) {
            return false;
        }
        InfuseStorage simulatedInfuse = getSimulatedInfuseStorage(factory, object, simulatedExtra.getCount());
        if (simulatedInfuse == null) {
            return false;
        }
        MetallurgicInfuserRecipe machineRecipe = findMatchingAEInfusionRecipe(factory, recipe, simulatedInput, simulatedInfuse);
        if (machineRecipe == null || !canAEOutputToSlot(processInfo.outputSlot(), recipe.getOutputStack())) {
            return false;
        }
        ItemStack inputRemainder = processInfo.inputSlot().insertItem(input.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        ItemStack extraRemainder = extraSlot.insertItem(extra.copy(), Action.SIMULATE, AutomationType.INTERNAL);
        return inputRemainder.isEmpty() && extraRemainder.isEmpty();
    }

    private boolean canAcceptAnyAEInfusionItemInput(IAEFactoryRecipeHost factory) {
        IInventorySlot extraSlot = factory.getAEFactoryExtraSlot();
        IAEFactoryRecipeHost.ProcessView[] processes = factory.getAEFactoryProcesses();
        if (extraSlot == null || processes == null) {
            return false;
        }
        ItemStack currentExtra = extraSlot.getStack();
        if (currentExtra.isEmpty()) {
            return true;
        }
        InfuseObject object = InfuseRegistry.getObject(currentExtra);
        if (object == null || !factory.getAEFactoryInfuseStorage().canReceive(object)) {
            return false;
        }
        InfuseStorage simulatedInfuse = getSimulatedInfuseStorage(factory, object, currentExtra.getCount());
        if (simulatedInfuse == null) {
            return false;
        }
        for (IAEFactoryRecipeHost.ProcessView processInfo : processes) {
            ItemStack currentInput = processInfo.inputSlot().getStack();
            if (currentInput.isEmpty()) {
                return true;
            }
            MetallurgicInfuserRecipe recipe = RecipeHandler.getMetallurgicInfuserRecipe(new InfusionInput(simulatedInfuse, currentInput));
            if (recipe != null && canAEOutputsToSlots(factory, recipe, processInfo) &&
                processInfo.inputSlot().getCount() < processInfo.inputSlot().getLimit(currentInput) &&
                extraSlot.getCount() < extraSlot.getLimit(currentExtra)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private InfuseStorage getSimulatedInfuseStorage(IAEFactoryRecipeHost factory, InfuseObject object, int sourceCount) {
        if (object == null || sourceCount <= 0 || !factory.getAEFactoryInfuseStorage().canReceive(object)) {
            return null;
        }
        long added = (long) object.stored * sourceCount;
        if ((long) factory.getAEFactoryInfuseStorage().getAmount() + added > factory.getAEFactoryMaxInfuse()) {
            return null;
        }
        InfuseStorage simulated = new InfuseStorage();
        simulated.copyFrom(factory.getAEFactoryInfuseStorage());
        simulated.increase(object, sourceCount);
        return simulated;
    }

    @Nullable
    private MetallurgicInfuserRecipe findMatchingAEInfusionRecipe(IAEFactoryRecipeHost factory, AEExposedRecipe exposedRecipe,
          ItemStack simulatedInput, InfuseStorage simulatedInfuse) {
        for (MetallurgicInfuserRecipe recipe : RecipeHandler.Recipe.METALLURGIC_INFUSER.get().values()) {
            int operations = getOutputOperations(recipe.getOutput().output, exposedRecipe.getOutputStack());
            if (operations <= 0) {
                continue;
            }
            ItemStack requiredInput = scaledStack(recipe.getInput().inputStack, operations);
            if (requiredInput.isEmpty() || !MachineInput.inputContains(simulatedInput, requiredInput)) {
                continue;
            }
            InfuseStorage requiredInfuse = new InfuseStorage(recipe.getInput().infuse.getType(), recipe.getInput().infuse.getAmount() * operations);
            if (simulatedInfuse.contains(requiredInfuse) && factory.isAEFactoryRecipeCurrent(recipe)) {
                return recipe;
            }
        }
        return null;
    }

    private int getOutputOperations(ItemStack recipeOutput, ItemStack exposedOutput) {
        if (recipeOutput.isEmpty() || exposedOutput.isEmpty() || !ItemHandlerHelper.canItemStacksStack(recipeOutput, exposedOutput)) {
            return -1;
        }
        int recipeCount = recipeOutput.getCount();
        return recipeCount > 0 && exposedOutput.getCount() % recipeCount == 0 ? exposedOutput.getCount() / recipeCount : -1;
    }

    private ItemStack scaledStack(ItemStack stack, int multiplier) {
        return AERecipeStacks.scale(stack, multiplier);
    }

    @Nullable
    private GasStack scaledGasStack(@Nullable GasStack stack, int multiplier) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0 || multiplier <= 0) {
            return null;
        }
        long amount = (long) stack.amount * multiplier;
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        return stack.copy().withAmount((int) amount);
    }

    private boolean hasAEInputRoom(IInventorySlot slot, ItemStack current) {
        return current.isEmpty() || slot.getCount() < slot.getLimit(current);
    }

    private boolean canAEOutputsToSlots(IAEFactoryRecipeHost factory, MachineRecipe<?, ?, ?> recipe, IAEFactoryRecipeHost.ProcessView processInfo) {
        ItemStack primaryOutput = getAEPrimaryRecipeOutput(factory, recipe);
        if (!primaryOutput.isEmpty() && !canAEOutputToSlot(processInfo.outputSlot(), primaryOutput)) {
            return false;
        }
        ItemStack secondaryOutput = getAESecondaryRecipeOutput(factory, recipe);
        return secondaryOutput.isEmpty() || canAEOutputToSlot(processInfo.secondaryOutputSlot(), secondaryOutput);
    }

    private boolean canAEFarmOutputsToSlots(FarmMachineRecipe<?> recipe, IAEFactoryRecipeHost.ProcessView processInfo, int operations) {
        ItemStack primaryOutput = scaledStack(recipe.getOutput().getMainOutput(), operations);
        if (!primaryOutput.isEmpty() && !canAEOutputToSlot(processInfo.outputSlot(), primaryOutput)) {
            return false;
        }
        ItemStack secondaryOutput = scaledStack(recipe.getOutput().getMaxSecondaryOutput(), operations);
        return secondaryOutput.isEmpty() || canAEOutputToSlot(processInfo.secondaryOutputSlot(), secondaryOutput);
    }

    private boolean canAEOutputToSlot(@Nullable IInventorySlot slot, ItemStack output) {
        return slot != null && slot.insertItem(output.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    private ItemStack getAEPrimaryRecipeOutput(IAEFactoryRecipeHost factory, MachineRecipe<?, ?, ?> recipe) {
        return factory.isAEFactoryRecipeCurrent(recipe) ? factory.getAEFactoryPrimaryRecipeOutput(recipe) : ItemStack.EMPTY;
    }

    private ItemStack getAESecondaryRecipeOutput(IAEFactoryRecipeHost factory, MachineRecipe<?, ?, ?> recipe) {
        return factory.isAEFactoryRecipeCurrent(recipe) ? factory.getAEFactorySecondaryRecipeOutput(recipe) : ItemStack.EMPTY;
    }

    private ItemStack getRecipeInput(MachineRecipe<?, ?, ?> recipe) {
        if (recipe.recipeInput instanceof ItemStackInput input) {
            return input.ingredient;
        } else if (recipe.recipeInput instanceof AdvancedMachineInput advancedInput) {
            return advancedInput.itemStack;
        } else if (recipe.recipeInput instanceof DoubleMachineInput doubleMachineInput) {
            return doubleMachineInput.itemStack;
        } else if (recipe.recipeInput instanceof InfusionInput infusionInput) {
            return infusionInput.inputStack;
        } else if (recipe.recipeInput instanceof PressurizedInput pressurizedInput) {
            return pressurizedInput.getSolid();
        } else if (recipe.recipeInput instanceof NucleosynthesizerInput input) {
            return input.getSolid();
        }
        return ItemStack.EMPTY;
    }

    private ItemStack getRecipeExtraInput(MachineRecipe<?, ?, ?> recipe) {
        return recipe.recipeInput instanceof DoubleMachineInput input ? input.extraStack : ItemStack.EMPTY;
    }

    private ItemStack getSimulatedStackWithInsert(IInventorySlot slot, ItemStack stack) {
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

    private boolean outputsMatch(ItemStack expected, ItemStack actual) {
        if (expected.isEmpty() || actual.isEmpty() || expected.getCount() != actual.getCount()) {
            return false;
        }
        return ItemHandlerHelper.canItemStacksStack(expected, actual);
    }

    private boolean outputsMatch(AEExposedRecipe recipe, ItemStack actual) {
        return AEItemRecipeAdapters.outputsMatch(recipe, actual);
    }

    private boolean outputsMatch(AEExposedRecipe exposedRecipe, PressurizedOutput output) {
        return outputsMatch(exposedRecipe, output, exposedRecipe.getCraftAmount());
    }

    private boolean outputsMatch(AEExposedRecipe exposedRecipe, PressurizedOutput output, int operations) {
        List<ItemStack> exposedOutputs = exposedRecipe.getOutputStacks();
        int outputIndex = 0;
        ItemStack baseItemOutput = output.getItemOutput();
        ItemStack itemOutput = scaledStack(baseItemOutput, operations);
        if (!baseItemOutput.isEmpty() && itemOutput.isEmpty()) {
            return false;
        }
        if (!itemOutput.isEmpty()) {
            if (outputIndex >= exposedOutputs.size() || !AEItemRecipeAdapters.outputsMatch(exposedOutputs.get(outputIndex), itemOutput, 1)) {
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

    @Nullable
    private IAEFactoryRecipeHost.ProcessView rejectAEItemInputProcess(IAEFactoryRecipeHost factory, String reason, Object... args) {
        rejectAEItemInput(factory, reason, args);
        return null;
    }

    private boolean rejectAEItemInput(IAEFactoryRecipeHost factory, String reason, Object... args) {
        AEUpgradeDebug.log(factory, "rejecting AE item input: " + reason, args);
        return false;
    }

    @Nullable
    private AERecipeTransferPlan rejectAEItemInputPlan(IAEFactoryRecipeHost factory, String reason, Object... args) {
        rejectAEItemInput(factory, reason, args);
        return null;
    }

    private static final class FactoryBatch {

        private final MachineRecipe<?, ?, ?> machineRecipe;
        private final ItemStack inputPerOperation;
        private final int operations;
        private final List<SharedTransfer> sharedInputs;
        @Nullable
        private final PressurizedOutput pressurizedOutput;

        private FactoryBatch(MachineRecipe<?, ?, ?> machineRecipe, ItemStack inputPerOperation, int operations,
              List<SharedTransfer> sharedInputs, @Nullable PressurizedOutput pressurizedOutput) {
            this.machineRecipe = machineRecipe;
            this.inputPerOperation = inputPerOperation.copy();
            this.operations = operations;
            this.sharedInputs = sharedInputs;
            this.pressurizedOutput = pressurizedOutput;
        }
    }

    private static final class SharedTransfer {

        @Nullable
        private final AERecipePort port;
        private final AERecipeRouteStack stack;

        private SharedTransfer(@Nullable AERecipePort port, AERecipeRouteStack stack) {
            this.port = port;
            this.stack = stack;
        }
    }

    private static final class ProcessAllocation {

        private final IAEFactoryRecipeHost.ProcessView process;
        private final int capacity;
        private int operations;

        private ProcessAllocation(IAEFactoryRecipeHost.ProcessView process, int capacity) {
            this.process = process;
            this.capacity = capacity;
        }
    }

    @Nullable
    private IAEFactoryRecipeHost asFactory(IAEItemRecipeHost host) {
        return host instanceof IAEFactoryRecipeHost factory ? factory : null;
    }
}
