package mekceuaeupgrade.common.recipe.route;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.AERecipeItemInputs;
import mekceuaeupgrade.common.recipe.AERecipeStacks;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.ChemicalGasInput;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.inputs.RotaryInput;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.machines.RotaryRecipe;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import mekanism.common.recipe.outputs.FluidOutput;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.recipe.outputs.PressurizedOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 从 Mekanism 配方表收集可暴露给 AE 的通用 route。
 *
 * <p>每个公开方法只负责一种输入输出拓扑，例如 item + gas -> item 或 gas + fluid -> gas。
 * 具体机器 adapter 只需要选择合适的收集方法，后续输入写入会由 route 的端口 ID 和 {@code AERecipeTransferPlan} 统一处理。</p>
 */
public final class AERecipeRouteCollectors {

    /**
     * 工具类不允许实例化。
     */
    private AERecipeRouteCollectors() {
    }

    /**
     * 收集物品输入、气体输出路线。
     *
     * @param recipes Mekanism item -> gas 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectItemToGas(
          Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ItemStackInput, GasOutput, ?> recipe : recipes.values()) {
            ItemStack input = recipe.getInput().ingredient;
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(output)) {
                continue;
            }
            for (ItemStack expandedInput : AERecipeItemInputs.expand(input, candidate -> itemToGasRecipeMatches(recipes, candidate, output))) {
                routes.add(AERecipeRoute.builder("route:item_to_gas")
                      .inputItem("item_input", expandedInput)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    /**
     * 收集气体输入、物品输出路线，并额外生成气体转换物品路线。
     *
     * @param recipes Mekanism gas -> item 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectGasToItem(Map<GasInput, ? extends CrystallizerRecipe> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (CrystallizerRecipe recipe : recipes.values()) {
            GasStack requiredGas = recipe.getInput().ingredient;
            ItemStack output = recipe.getOutput().output;
            if (!isPositiveGas(requiredGas) || !isExposableOutput(output)) {
                continue;
            }
            routes.add(AERecipeRoute.builder("route:gas_to_item.fake")
                  .inputGas("gas_input", requiredGas)
                  .outputItem("item_output", output)
                  .build());
            for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(requiredGas.getGas())) {
                addGasConversionToItemRoute(routes, "route:gas_to_item.conversion", requiredGas, source, output);
            }
        }
        return routes;
    }

    /**
     * 收集气体输入、气体输出路线，并额外生成气体转换物品路线。
     *
     * @param recipes Mekanism gas -> gas 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectGasToGas(
          Map<GasInput, ? extends MachineRecipe<GasInput, GasOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasInput, GasOutput, ?> recipe : recipes.values()) {
            GasStack requiredGas = recipe.getInput().ingredient;
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(requiredGas) || !isPositiveGas(output)) {
                continue;
            }
            routes.add(AERecipeRoute.builder("route:gas_to_gas.fake")
                  .inputGas("gas_input", requiredGas)
                  .outputGas("gas_output", output)
                  .build());
            for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(requiredGas.getGas())) {
                addGasOnlyConversionToGasRoute(routes, requiredGas, source, output);
            }
        }
        return routes;
    }

    /**
     * 收集物品加固定气体输入、气体输出路线。
     *
     * @param recipes Mekanism item -> gas 配方表
     * @param gasType 机器需要的共享气体类型
     * @param gasPerOperation 每次操作消耗的气体量
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectItemGasToGas(
          Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipes, @Nullable Gas gasType, int gasPerOperation) {
        if (gasType == null || gasPerOperation <= 0) {
            return Collections.emptyList();
        }
        List<AERecipeRoute> routes = new ArrayList<>();
        GasStack requiredGas = new GasStack(gasType, gasPerOperation);
        for (MachineRecipe<ItemStackInput, GasOutput, ?> recipe : recipes.values()) {
            ItemStack itemInput = recipe.getInput().ingredient;
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(output)) {
                continue;
            }
            for (ItemStack expandedInput : AERecipeItemInputs.expand(itemInput, candidate -> itemToGasRecipeMatches(recipes, candidate, output))) {
                routes.add(AERecipeRoute.builder("route:item_gas_to_gas.fake")
                      .inputItem("item_input", expandedInput)
                      .inputGas("gas_input", requiredGas)
                      .outputGas("gas_output", output)
                      .build());
                for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(gasType)) {
                    addItemGasConversionToGasRoute(routes, expandedInput, requiredGas, source, output);
                }
            }
        }
        return routes;
    }

    /**
     * 收集高级机器的物品加气体输入、物品输出路线。
     *
     * @param recipes Mekanism advanced gas 配方表
     * @param gasPerOperation 每次操作消耗的气体量
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectAdvancedGasToItem(Map<AdvancedMachineInput, ? extends AdvancedMachineRecipe<?>> recipes,
          int gasPerOperation) {
        if (gasPerOperation <= 0) {
            return Collections.emptyList();
        }
        List<AERecipeRoute> routes = new ArrayList<>();
        for (AdvancedMachineRecipe<?> recipe : recipes.values()) {
            AdvancedMachineInput input = recipe.getInput();
            ItemStack output = recipe.getOutput().output;
            addItemGasToItemRoutes(routes, input.itemStack, input.gasType, gasPerOperation, output,
                  candidate -> advancedGasToItemRecipeMatches(recipes, candidate, input.gasType, output));
        }
        return routes;
    }

    /**
     * 收集种植机的物品加气体输入、物品输出路线。
     *
     * @param recipes Mekanism farm 配方表
     * @param gasPerOperation 每次操作消耗的气体量
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectFarmGasToItem(Map<AdvancedMachineInput, ? extends FarmMachineRecipe<?>> recipes,
          int gasPerOperation) {
        if (gasPerOperation <= 0) {
            return Collections.emptyList();
        }
        List<AERecipeRoute> routes = new ArrayList<>();
        for (FarmMachineRecipe<?> recipe : recipes.values()) {
            AdvancedMachineInput input = recipe.getInput();
            ItemStack output = recipe.getOutput().getMainOutput();
            addItemGasToItemRoutes(routes, input.itemStack, input.gasType, gasPerOperation, output,
                  candidate -> farmGasToItemRecipeMatches(recipes, candidate, input.gasType, output));
        }
        return routes;
    }

    /**
     * 收集核合成机的物品加气体输入、物品输出路线。
     *
     * @param recipes Mekanism nucleosynthesizer 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectNucleosynthesizerGasToItem(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?> recipe : recipes.values()) {
            NucleosynthesizerInput input = recipe.getInput();
            GasStack requiredGas = input.getGas();
            ItemStack solidInput = input.getSolid();
            ItemStack output = recipe.getOutput().output;
            if (!isPositiveGas(requiredGas) || !isExposableOutput(output)) {
                continue;
            }
            for (ItemStack expandedSolid : AERecipeItemInputs.expand(solidInput,
                  candidate -> nucleosynthesizerRecipeMatches(recipes, candidate, requiredGas, output))) {
                routes.add(AERecipeRoute.builder("route:item_gas_to_item.fake")
                      .inputItem("item_input", expandedSolid)
                      .inputGas("gas_input", requiredGas)
                      .outputItem("item_output", output)
                      .build());
                for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(requiredGas.getGas())) {
                    addItemGasConversionToItemRoute(routes, expandedSolid, requiredGas, source, output);
                }
            }
        }
        return routes;
    }

    /**
     * 收集两个气体输入、气体输出路线，并额外生成两侧气体转换物品路线。
     *
     * @param recipes Mekanism chemical gas 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectChemicalGasToGas(
          Map<ChemicalGasInput, ? extends MachineRecipe<ChemicalGasInput, GasOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ChemicalGasInput, GasOutput, ?> recipe : recipes.values()) {
            ChemicalGasInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(input.input) || !isPositiveGas(input.uu) || !isPositiveGas(output)) {
                continue;
            }
            routes.add(AERecipeRoute.builder("route:gas_gas_to_gas.fake")
                  .inputGas("left_gas", input.input)
                  .inputGas("right_gas", input.uu)
                  .outputGas("gas_output", output)
                  .build());
            for (GasConversionHandler.GasConversionSource leftSource : GasConversionHandler.getConversionSourcesForGas(getGas(input.input))) {
                for (GasConversionHandler.GasConversionSource rightSource : GasConversionHandler.getConversionSourcesForGas(getGas(input.uu))) {
                    addGasPairConversionToGasRoute(routes, input.input, leftSource, input.uu, rightSource, output);
                }
            }
        }
        return routes;
    }

    /**
     * 收集化学对输入、气体输出路线，并额外生成两侧气体转换物品路线。
     *
     * @param recipes Mekanism chemical pair 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectChemicalPairToGas(
          Map<ChemicalPairInput, ? extends MachineRecipe<ChemicalPairInput, GasOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<ChemicalPairInput, GasOutput, ?> recipe : recipes.values()) {
            ChemicalPairInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(input.leftGas) || !isPositiveGas(input.rightGas) || !isPositiveGas(output)) {
                continue;
            }
            routes.add(AERecipeRoute.builder("route:gas_pair_to_gas.fake")
                  .inputGas("left_gas", input.leftGas)
                  .inputGas("right_gas", input.rightGas)
                  .outputGas("gas_output", output)
                  .build());
            for (GasConversionHandler.GasConversionSource leftSource : GasConversionHandler.getConversionSourcesForGas(input.leftGas.getGas())) {
                for (GasConversionHandler.GasConversionSource rightSource : GasConversionHandler.getConversionSourcesForGas(input.rightGas.getGas())) {
                    addGasPairConversionToGasRoute(routes, input.leftGas, leftSource, input.rightGas, rightSource, output);
                }
            }
        }
        return routes;
    }

    /**
     * 收集气体加流体输入、气体输出路线。
     *
     * @param recipes Mekanism gas + fluid -> gas 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectGasFluidToGas(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, GasOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasAndFluidInput, GasOutput, ?> recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            GasStack output = recipe.getOutput().output;
            if (!isPositiveGas(input.ingredientGas) || !isPositiveFluid(input.ingredientFluid) || !isPositiveGas(output)) {
                continue;
            }
            routes.add(AERecipeRoute.builder("route:gas_fluid_to_gas.fake")
                  .inputGas("gas_input", input.ingredientGas)
                  .inputFluid("fluid_input", input.ingredientFluid)
                  .outputGas("gas_output", output)
                  .build());
            for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(getGas(input.ingredientGas))) {
                addGasConversionFluidToGasRoute(routes, input.ingredientGas, source, input.ingredientFluid, output);
            }
        }
        return routes;
    }

    /**
     * 收集流体输入、双气体输出路线。
     *
     * @param recipes Mekanism fluid -> gas pair 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectFluidToGasPair(
          Map<FluidInput, ? extends MachineRecipe<FluidInput, ChemicalPairOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<FluidInput, ChemicalPairOutput, ?> recipe : recipes.values()) {
            FluidStack input = recipe.getInput().ingredient;
            ChemicalPairOutput output = recipe.getOutput();
            if (!isPositiveFluid(input) || output == null || !output.isValid() ||
                !isPositiveGas(output.leftGas) || !isPositiveGas(output.rightGas)) {
                continue;
            }
            routes.add(AERecipeRoute.builder("route:fluid_to_gas_pair")
                  .inputFluid("fluid_input", input)
                  .outputGas("left_gas_output", output.leftGas)
                  .outputGas("right_gas_output", output.rightGas)
                  .build());
        }
        return routes;
    }

    /**
     * 收集加压反应室的物品、流体、气体输入路线。
     *
     * @param recipes Mekanism pressurized 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectPressurized(
          Map<PressurizedInput, ? extends MachineRecipe<PressurizedInput, PressurizedOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<PressurizedInput, PressurizedOutput, ?> recipe : recipes.values()) {
            PressurizedInput input = recipe.getInput();
            PressurizedOutput output = recipe.getOutput();
            if (input == null || !input.isValid() || output == null ||
                !isPositiveFluid(input.getFluid()) || !isPositiveGas(input.getGas())) {
                continue;
            }
            for (ItemStack expandedSolid : AERecipeItemInputs.expand(input.getSolid(),
                  candidate -> pressurizedRecipeMatches(recipes, candidate, input.getFluid(), input.getGas(), output))) {
                addPressurizedRoute(routes, "route:pressurized.fake", expandedSolid, input.getFluid(), input.getGas(), ItemStack.EMPTY, output);
                for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(getGas(input.getGas()))) {
                    addPressurizedGasConversionRoute(routes, input, expandedSolid, source, output);
                }
            }
        }
        return routes;
    }

    /**
     * 收集旋转冷凝机气体输入、流体输出路线。
     *
     * @param recipes Mekanism rotary 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectRotaryGasToFluid(Map<RotaryInput, ? extends RotaryRecipe> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (RotaryRecipe recipe : recipes.values()) {
            if (!recipe.hasGasToFluid()) {
                continue;
            }
            GasStack input = recipe.getGasInput();
            FluidStack output = recipe.getFluidOutput(input);
            if (isPositiveGas(input) && isPositiveFluid(output)) {
                routes.add(AERecipeRoute.builder("route:gas_to_fluid")
                      .inputGas("gas_input", input)
                      .outputFluid("fluid_output", output)
                      .build());
                for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(getGas(input))) {
                    addGasConversionToFluidRoute(routes, input, source, output);
                }
            }
        }
        return routes;
    }

    /**
     * 收集旋转冷凝机流体输入、气体输出路线。
     *
     * @param recipes Mekanism rotary 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectRotaryFluidToGas(Map<RotaryInput, ? extends RotaryRecipe> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (RotaryRecipe recipe : recipes.values()) {
            if (!recipe.hasFluidToGas()) {
                continue;
            }
            FluidStack input = recipe.getFluidInput();
            GasStack output = recipe.getGasOutput(input);
            if (isPositiveFluid(input) && isPositiveGas(output)) {
                routes.add(AERecipeRoute.builder("route:fluid_to_gas")
                      .inputFluid("fluid_input", input)
                      .outputGas("gas_output", output)
                      .build());
            }
        }
        return routes;
    }

    /**
     * 收集气体加流体输入、流体输出路线。
     *
     * @param recipes Mekanism gas + fluid -> fluid 配方表
     * @return 可暴露给 AE 的 route 列表
     */
    public static List<AERecipeRoute> collectGasFluidToFluid(
          Map<GasAndFluidInput, ? extends MachineRecipe<GasAndFluidInput, FluidOutput, ?>> recipes) {
        List<AERecipeRoute> routes = new ArrayList<>();
        for (MachineRecipe<GasAndFluidInput, FluidOutput, ?> recipe : recipes.values()) {
            GasAndFluidInput input = recipe.getInput();
            FluidStack output = recipe.getOutput().output;
            if (!isPositiveGas(input.ingredientGas) || !isPositiveFluid(input.ingredientFluid) || !isPositiveFluid(output)) {
                continue;
            }
            routes.add(AERecipeRoute.builder("route:gas_fluid_to_fluid.fake")
                  .inputGas("gas_input", input.ingredientGas)
                  .inputFluid("fluid_input", input.ingredientFluid)
                  .outputFluid("fluid_output", output)
                  .build());
            for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(getGas(input.ingredientGas))) {
                addGasConversionFluidToFluidRoute(routes, input.ingredientGas, source, input.ingredientFluid, output);
            }
        }
        return routes;
    }

    private static void addItemGasToItemRoutes(List<AERecipeRoute> routes, ItemStack itemInput, @Nullable Gas gasType, int gasPerOperation,
          ItemStack output, Predicate<ItemStack> acceptsItemInput) {
        GasStack requiredGas = gasType == null || gasPerOperation <= 0 ? null : new GasStack(gasType, gasPerOperation);
        if (!isPositiveGas(requiredGas) || !isExposableOutput(output)) {
            return;
        }
        for (ItemStack expandedInput : AERecipeItemInputs.expand(itemInput, acceptsItemInput)) {
            routes.add(AERecipeRoute.builder("route:item_gas_to_item.fake")
                  .inputItem("item_input", expandedInput)
                  .inputGas("gas_input", requiredGas)
                  .outputItem("item_output", output)
                  .build());
            for (GasConversionHandler.GasConversionSource source : GasConversionHandler.getConversionSourcesForGas(gasType)) {
                addItemGasConversionToItemRoute(routes, expandedInput, requiredGas, source, output);
            }
        }
    }

    private static void addGasConversionToItemRoute(List<AERecipeRoute> routes, String routeId, GasStack requiredGas,
          GasConversionHandler.GasConversionSource source, ItemStack recipeOutput) {
        GasStack sourceGas = source.getGasStack();
        if (!isMatchingPositiveGas(requiredGas, sourceGas) || !isExposableOutput(recipeOutput)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        GasStack gas = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        ItemStack output = scaledStack(recipeOutput, operations);
        if (gas != null && !carrier.isEmpty() && !output.isEmpty()) {
            routes.add(AERecipeRoute.builder(routeId)
                  .inputGas("gas_input", gas, carrier)
                  .outputItem("item_output", output)
                  .build());
        }
    }

    private static void addItemGasConversionToGasRoute(List<AERecipeRoute> routes, ItemStack recipeInput, GasStack requiredGas,
          GasConversionHandler.GasConversionSource source, GasStack recipeOutput) {
        GasStack sourceGas = source.getGasStack();
        if (!isExposableInput(recipeInput) || !isMatchingPositiveGas(requiredGas, sourceGas) || !isPositiveGas(recipeOutput)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        ItemStack itemInput = scaledStack(recipeInput, operations);
        GasStack gasInput = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        GasStack output = scaledGasStack(recipeOutput, operations);
        if (!itemInput.isEmpty() && gasInput != null && !carrier.isEmpty() && output != null) {
            routes.add(AERecipeRoute.builder("route:item_gas_to_gas.conversion")
                  .inputItem("item_input", itemInput)
                  .inputGas("gas_input", gasInput, carrier)
                  .outputGas("gas_output", output)
                  .build());
        }
    }

    private static void addGasOnlyConversionToGasRoute(List<AERecipeRoute> routes, GasStack requiredGas,
          GasConversionHandler.GasConversionSource source, GasStack recipeOutput) {
        GasStack sourceGas = source.getGasStack();
        if (!isMatchingPositiveGas(requiredGas, sourceGas) || !isPositiveGas(recipeOutput)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        GasStack gasInput = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        GasStack output = scaledGasStack(recipeOutput, operations);
        if (gasInput != null && !carrier.isEmpty() && output != null) {
            routes.add(AERecipeRoute.builder("route:gas_to_gas.conversion")
                  .inputGas("gas_input", gasInput, carrier)
                  .outputGas("gas_output", output)
                  .build());
        }
    }

    private static void addItemGasConversionToItemRoute(List<AERecipeRoute> routes, ItemStack recipeInput, GasStack requiredGas,
          GasConversionHandler.GasConversionSource source, ItemStack recipeOutput) {
        GasStack sourceGas = source.getGasStack();
        if (!isExposableInput(recipeInput) || !isMatchingPositiveGas(requiredGas, sourceGas) || !isExposableOutput(recipeOutput)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        ItemStack itemInput = scaledStack(recipeInput, operations);
        GasStack gasInput = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        ItemStack output = scaledStack(recipeOutput, operations);
        if (!itemInput.isEmpty() && gasInput != null && !carrier.isEmpty() && !output.isEmpty()) {
            routes.add(AERecipeRoute.builder("route:item_gas_to_item.conversion")
                  .inputItem("item_input", itemInput)
                  .inputGas("gas_input", gasInput, carrier)
                  .outputItem("item_output", output)
                  .build());
        }
    }

    private static void addGasPairConversionToGasRoute(List<AERecipeRoute> routes, GasStack leftRequired,
          GasConversionHandler.GasConversionSource leftSource, GasStack rightRequired, GasConversionHandler.GasConversionSource rightSource,
          GasStack output) {
        GasStack leftSourceGas = leftSource.getGasStack();
        GasStack rightSourceGas = rightSource.getGasStack();
        if (!isMatchingPositiveGas(leftRequired, leftSourceGas) || !isMatchingPositiveGas(rightRequired, rightSourceGas) ||
            !isPositiveGas(output)) {
            return;
        }
        int leftOperationUnit = leftSourceGas.amount / gcd(leftRequired.amount, leftSourceGas.amount);
        int rightOperationUnit = rightSourceGas.amount / gcd(rightRequired.amount, rightSourceGas.amount);
        int operations = lcm(leftOperationUnit, rightOperationUnit);
        if (operations <= 0) {
            return;
        }
        long leftSourceCount = (long) operations * leftRequired.amount / leftSourceGas.amount;
        long rightSourceCount = (long) operations * rightRequired.amount / rightSourceGas.amount;
        GasStack leftGas = scaledGasStack(leftRequired, operations);
        GasStack rightGas = scaledGasStack(rightRequired, operations);
        ItemStack leftCarrier = scaledStack(leftSource.getStack(), clampPositiveInt(leftSourceCount));
        ItemStack rightCarrier = scaledStack(rightSource.getStack(), clampPositiveInt(rightSourceCount));
        GasStack gasOutput = scaledGasStack(output, operations);
        if (leftGas != null && rightGas != null && !leftCarrier.isEmpty() && !rightCarrier.isEmpty() && gasOutput != null) {
            routes.add(AERecipeRoute.builder("route:gas_gas_to_gas.conversion")
                  .inputGas("left_gas", leftGas, leftCarrier)
                  .inputGas("right_gas", rightGas, rightCarrier)
                  .outputGas("gas_output", gasOutput)
                  .build());
        }
    }

    private static void addGasConversionFluidToFluidRoute(List<AERecipeRoute> routes, GasStack requiredGas,
          GasConversionHandler.GasConversionSource source, FluidStack requiredFluid, FluidStack output) {
        GasStack sourceGas = source.getGasStack();
        if (!isMatchingPositiveGas(requiredGas, sourceGas) || !isPositiveFluid(requiredFluid) || !isPositiveFluid(output)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        GasStack gas = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        FluidStack fluid = scaledFluidStack(requiredFluid, operations);
        FluidStack fluidOutput = scaledFluidStack(output, operations);
        if (gas != null && !carrier.isEmpty() && fluid != null && fluidOutput != null) {
            routes.add(AERecipeRoute.builder("route:gas_fluid_to_fluid.conversion")
                  .inputGas("gas_input", gas, carrier)
                  .inputFluid("fluid_input", fluid)
                  .outputFluid("fluid_output", fluidOutput)
                  .build());
        }
    }

    private static void addGasConversionFluidToGasRoute(List<AERecipeRoute> routes, GasStack requiredGas,
          GasConversionHandler.GasConversionSource source, FluidStack requiredFluid, GasStack output) {
        GasStack sourceGas = source.getGasStack();
        if (!isMatchingPositiveGas(requiredGas, sourceGas) || !isPositiveFluid(requiredFluid) || !isPositiveGas(output)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        GasStack gas = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        FluidStack fluid = scaledFluidStack(requiredFluid, operations);
        GasStack gasOutput = scaledGasStack(output, operations);
        if (gas != null && !carrier.isEmpty() && fluid != null && gasOutput != null) {
            routes.add(AERecipeRoute.builder("route:gas_fluid_to_gas.conversion")
                  .inputGas("gas_input", gas, carrier)
                  .inputFluid("fluid_input", fluid)
                  .outputGas("gas_output", gasOutput)
                  .build());
        }
    }

    private static void addGasConversionToFluidRoute(List<AERecipeRoute> routes, GasStack requiredGas,
          GasConversionHandler.GasConversionSource source, FluidStack output) {
        GasStack sourceGas = source.getGasStack();
        if (!isMatchingPositiveGas(requiredGas, sourceGas) || !isPositiveFluid(output)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        GasStack gas = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        FluidStack fluidOutput = scaledFluidStack(output, operations);
        if (gas != null && !carrier.isEmpty() && fluidOutput != null) {
            routes.add(AERecipeRoute.builder("route:gas_to_fluid.conversion")
                  .inputGas("gas_input", gas, carrier)
                  .outputFluid("fluid_output", fluidOutput)
                  .build());
        }
    }

    private static void addPressurizedGasConversionRoute(List<AERecipeRoute> routes, PressurizedInput input, ItemStack solidInput,
          GasConversionHandler.GasConversionSource source, PressurizedOutput output) {
        GasStack requiredGas = input.getGas();
        GasStack sourceGas = source.getGasStack();
        if (!isExposableInput(solidInput) || !isPositiveFluid(input.getFluid()) || !isMatchingPositiveGas(requiredGas, sourceGas)) {
            return;
        }
        int gcd = gcd(requiredGas.amount, sourceGas.amount);
        int operations = sourceGas.amount / gcd;
        int sourceCount = requiredGas.amount / gcd;
        ItemStack solid = scaledStack(solidInput, operations);
        FluidStack fluid = scaledFluidStack(input.getFluid(), operations);
        GasStack gas = scaledGasStack(requiredGas, operations);
        ItemStack carrier = scaledStack(source.getStack(), sourceCount);
        if (!solid.isEmpty() && fluid != null && gas != null && !carrier.isEmpty()) {
            addPressurizedRoute(routes, "route:pressurized.conversion", solid, fluid, gas, carrier, output, operations);
        }
    }

    private static void addPressurizedRoute(List<AERecipeRoute> routes, String routeId, ItemStack solidInput, FluidStack fluidInput,
          GasStack gasInput, ItemStack gasLegacyInput, PressurizedOutput output) {
        addPressurizedRoute(routes, routeId, solidInput, fluidInput, gasInput, gasLegacyInput, output, 1);
    }

    private static void addPressurizedRoute(List<AERecipeRoute> routes, String routeId, ItemStack solidInput, FluidStack fluidInput,
          GasStack gasInput, ItemStack gasLegacyInput, PressurizedOutput output, int operations) {
        AERecipeRoute.Builder builder = AERecipeRoute.builder(routeId)
              .inputItem("item_input", solidInput)
              .inputFluid("fluid_input", fluidInput);
        if (gasLegacyInput.isEmpty()) {
            builder.inputGas("gas_input", gasInput);
        } else {
            builder.inputGas("gas_input", gasInput, gasLegacyInput);
        }
        boolean hasOutput = false;
        ItemStack baseItemOutput = output.getItemOutput();
        ItemStack itemOutput = scaledStack(baseItemOutput, operations);
        if (!baseItemOutput.isEmpty() && itemOutput.isEmpty()) {
            return;
        }
        if (!itemOutput.isEmpty()) {
            builder.outputItem("item_output", itemOutput);
            hasOutput = true;
        }
        GasStack baseGasOutput = output.getGasOutput();
        GasStack gasOutput = scaledGasStack(baseGasOutput, operations);
        if (isPositiveGas(baseGasOutput) && gasOutput == null) {
            return;
        }
        if (gasOutput != null) {
            builder.outputGas("gas_output", gasOutput);
            hasOutput = true;
        }
        if (hasOutput) {
            routes.add(builder.build());
        }
    }

    @Nullable
    private static Gas getGas(@Nullable GasStack stack) {
        return stack == null ? null : stack.getGas();
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

    private static boolean isMatchingPositiveGas(GasStack required, GasStack provided) {
        return required != null && required.getGas() != null && required.amount > 0 && provided != null &&
              provided.isGasEqual(required) && provided.amount > 0;
    }

    private static boolean isPositiveGas(@Nullable GasStack stack) {
        return stack != null && stack.getGas() != null && stack.amount > 0;
    }

    private static boolean isPositiveFluid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() != null && stack.amount > 0;
    }

    private static boolean isExposableInput(ItemStack stack) {
        return AERecipeItemInputs.isConcreteInput(stack);
    }

    private static boolean isExposableOutput(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0;
    }

    private static boolean itemToGasRecipeMatches(Map<ItemStackInput, ? extends MachineRecipe<ItemStackInput, GasOutput, ?>> recipes,
          ItemStack input, GasStack output) {
        Object matched = getRecipe(recipes, new ItemStackInput(input.copy()));
        if (matched instanceof MachineRecipe<?, ?, ?> recipe && recipe.getOutput() instanceof GasOutput matchedOutput) {
            return gasStacksEqual(matchedOutput.output, output);
        }
        return false;
    }

    private static boolean advancedGasToItemRecipeMatches(Map<AdvancedMachineInput, ? extends AdvancedMachineRecipe<?>> recipes,
          ItemStack itemInput, @Nullable Gas gasType, ItemStack output) {
        if (gasType == null) {
            return false;
        }
        Object matched = getRecipe(recipes, new AdvancedMachineInput(itemInput.copy(), gasType));
        return matched instanceof AdvancedMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().output, output);
    }

    private static boolean farmGasToItemRecipeMatches(Map<AdvancedMachineInput, ? extends FarmMachineRecipe<?>> recipes,
          ItemStack itemInput, @Nullable Gas gasType, ItemStack output) {
        if (gasType == null) {
            return false;
        }
        Object matched = getRecipe(recipes, new AdvancedMachineInput(itemInput.copy(), gasType));
        return matched instanceof FarmMachineRecipe<?> recipe && ItemStack.areItemStacksEqual(recipe.getOutput().getMainOutput(), output);
    }

    private static boolean nucleosynthesizerRecipeMatches(
          Map<NucleosynthesizerInput, ? extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ?>> recipes,
          ItemStack solidInput, GasStack gasInput, ItemStack output) {
        if (!isPositiveGas(gasInput)) {
            return false;
        }
        Object matched = getRecipe(recipes, new NucleosynthesizerInput(solidInput.copy(), gasInput.copy()));
        if (matched instanceof MachineRecipe<?, ?, ?> recipe && recipe.getOutput() instanceof ItemStackOutput matchedOutput) {
            return ItemStack.areItemStacksEqual(matchedOutput.output, output);
        }
        return false;
    }

    private static boolean pressurizedRecipeMatches(
          Map<PressurizedInput, ? extends MachineRecipe<PressurizedInput, PressurizedOutput, ?>> recipes,
          ItemStack solidInput, FluidStack fluidInput, GasStack gasInput, PressurizedOutput output) {
        if (!isPositiveFluid(fluidInput) || !isPositiveGas(gasInput) || output == null) {
            return false;
        }
        Object matched = getRecipe(recipes, new PressurizedInput(solidInput.copy(), fluidInput.copy(), gasInput.copy()));
        if (matched instanceof MachineRecipe<?, ?, ?> recipe && recipe.getOutput() instanceof PressurizedOutput matchedOutput) {
            return pressurizedOutputsEqual(matchedOutput, output);
        }
        return false;
    }

    private static boolean pressurizedOutputsEqual(PressurizedOutput left, PressurizedOutput right) {
        return left != null && right != null &&
              ItemStack.areItemStacksEqual(left.getItemOutput(), right.getItemOutput()) &&
              gasStacksEqual(left.getGasOutput(), right.getGasOutput());
    }

    private static boolean gasStacksEqual(@Nullable GasStack left, @Nullable GasStack right) {
        if (left == null || left.getGas() == null || left.amount <= 0) {
            return right == null || right.getGas() == null || right.amount <= 0;
        }
        return right != null && right.getGas() != null && right.amount == left.amount && right.isGasEqual(left);
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getRecipe(Map recipeMap, MachineInput input) {
        return RecipeHandler.getRecipe(input, recipeMap);
    }

    private static int clampPositiveInt(long value) {
        return value <= 0 || value > Integer.MAX_VALUE ? -1 : (int) value;
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

    private static int lcm(int a, int b) {
        if (a <= 0 || b <= 0) {
            return -1;
        }
        long result = (long) a / gcd(a, b) * b;
        return result > Integer.MAX_VALUE ? -1 : (int) result;
    }
}
