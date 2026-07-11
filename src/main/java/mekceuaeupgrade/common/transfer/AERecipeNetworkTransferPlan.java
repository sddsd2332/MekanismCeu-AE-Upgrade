package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条配方在 AE 网络侧的事务计划。
 *
 * <p>计划会保留 AE 1.12 pattern 需要看到的旧物品输入，同时按 route 类型从物品、气体或流体频道抽取真实输入。
 * 它还提供 route 输出的 AE 接收能力预检。真实抽取成功后，如果机器写入失败，可以调用
 * {@link #rollback(AEUpgradeNode)} 按原频道退回 AE。</p>
 */
public final class AERecipeNetworkTransferPlan {

    private final List<ExtractedInput> inputs;
    private final List<ItemStack> legacyInputs;
    private final List<NetworkOutput> outputs;

    /**
     * @param inputs 已解析的输入抽取项
     */
    private AERecipeNetworkTransferPlan(List<ExtractedInput> inputs, List<NetworkOutput> outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        List<ItemStack> legacy = new ArrayList<>(inputs.size());
        for (ExtractedInput input : inputs) {
            legacy.add(input.legacyStack());
        }
        legacyInputs = Collections.unmodifiableList(legacy);
    }

    /**
     * 从暴露配方创建 AE 网络抽取计划。
     *
     * @param recipe 暴露给 AE 的配方
     * @return 能完整解析输入时返回计划，否则返回 null
     */
    @Nullable
    public static AERecipeNetworkTransferPlan fromRecipe(AEExposedRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        List<ItemStack> legacyInputs = recipe.getInputStacks();
        AERecipeRoute route = recipe.getRecipeRoute();
        if (legacyInputs.isEmpty() || route != null && route.inputs().size() != legacyInputs.size()) {
            return null;
        }
        List<ExtractedInput> inputs = new ArrayList<>(legacyInputs.size());
        for (int i = 0; i < legacyInputs.size(); i++) {
            ExtractedInput input = route == null ? itemInput(legacyInputs.get(i)) :
                  routeInput(route.inputs().get(i), legacyInputs.get(i), recipe.getCraftAmount());
            if (input == null) {
                return null;
            }
            inputs.add(input);
        }
        List<NetworkOutput> outputs = createOutputs(recipe, route);
        return outputs == null ? null : new AERecipeNetworkTransferPlan(Collections.unmodifiableList(inputs), outputs);
    }

    /**
     * 在拉取输入前预检 AE 是否能接收该配方的主要输出。
     *
     * @param node 当前机器的 AE 网络节点
     * @return 输出可以完整写入 AE 时返回 true
     */
    public boolean canAcceptOutputs(AEUpgradeNode node) {
        if (node == null || !node.canUseNetwork()) {
            return false;
        }
        for (int i = 0; i < outputs.size(); i++) {
            NetworkOutput output = outputs.get(i);
            if (hasEquivalentOutputBefore(i, output)) {
                continue;
            }
            long total = 0;
            for (int j = i; j < outputs.size(); j++) {
                NetworkOutput candidate = outputs.get(j);
                if (output.isSameResource(candidate)) {
                    total = addAmount(total, candidate.amount());
                    if (total < 0) {
                        return false;
                    }
                }
            }
            if (!output.canAccept(node, total)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasEquivalentOutputBefore(int index, NetworkOutput output) {
        for (int i = 0; i < index; i++) {
            if (output.isSameResource(outputs.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 是否没有可抽取输入
     */
    public boolean isEmpty() {
        return inputs.isEmpty();
    }

    /**
     * @return adapter 入口需要看到的旧物品输入列表
     */
    public List<ItemStack> getLegacyInputs() {
        return legacyInputs;
    }

    /**
     * 模拟检查 AE 中是否有足够的全部输入。
     *
     * @param node 当前机器的 AE 网络节点
     * @return 所有输入都可以完整抽取时返回 true
     */
    public boolean canExtract(AEUpgradeNode node) {
        if (node == null || !node.canUseNetwork()) {
            return false;
        }
        for (int i = 0; i < inputs.size(); i++) {
            ExtractedInput input = inputs.get(i);
            if (hasEquivalentInputBefore(i, input)) {
                continue;
            }
            long total = 0;
            for (int j = i; j < inputs.size(); j++) {
                ExtractedInput candidate = inputs.get(j);
                if (input.isSameResource(candidate)) {
                    total = addAmount(total, candidate.amount());
                    if (total < 0) {
                        return false;
                    }
                }
            }
            if (!input.canExtract(node, total)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasEquivalentInputBefore(int index, ExtractedInput input) {
        for (int i = 0; i < index; i++) {
            if (input.isSameResource(inputs.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static long addAmount(long current, long amount) {
        return current < 0 || amount <= 0 || Long.MAX_VALUE - current < amount ? -1 : current + amount;
    }

    /**
     * 从 AE 网络真实抽取全部输入。
     *
     * @param node 当前机器的 AE 网络节点
     * @return 全部输入抽取成功时返回 true，失败会自动退回已抽取内容
     */
    public boolean extract(AEUpgradeNode node) {
        if (node == null || !node.canUseNetwork()) {
            return false;
        }
        for (ExtractedInput input : inputs) {
            input.clearExtracted();
        }
        for (int i = 0; i < inputs.size(); i++) {
            ExtractedInput input = inputs.get(i);
            if (!input.extract(node)) {
                rollback(node, inputs, i);
                return false;
            }
        }
        return true;
    }

    /**
     * 把本计划已经从 AE 抽出的内容按原频道退回。
     *
     * @param node 当前机器的 AE 网络节点
     */
    public void rollback(AEUpgradeNode node) {
        rollback(node, inputs, inputs.size());
    }

    /**
     * 提交成功事务并清理仅用于回滚的抽取状态。
     */
    public void commit() {
        for (ExtractedInput input : inputs) {
            input.clearExtracted();
        }
    }

    @Nullable
    private static List<NetworkOutput> createOutputs(AEExposedRecipe recipe, @Nullable AERecipeRoute route) {
        List<ItemStack> legacyOutputs = recipe.getOutputStacks();
        if (legacyOutputs.isEmpty() || route != null && route.outputs().size() != legacyOutputs.size()) {
            return null;
        }
        List<NetworkOutput> outputs = new ArrayList<>(legacyOutputs.size());
        for (int i = 0; i < legacyOutputs.size(); i++) {
            NetworkOutput output = route == null ? itemOutput(legacyOutputs.get(i)) :
                  routeOutput(route.outputs().get(i), legacyOutputs.get(i), recipe.getCraftAmount());
            if (output == null) {
                return null;
            }
            outputs.add(output);
        }
        return Collections.unmodifiableList(outputs);
    }

    /**
     * 将单个 route 输入转换为对应 AE 频道的抽取请求。
     */
    @Nullable
    private static ExtractedInput routeInput(AERecipeRouteStack routeStack, ItemStack legacyStack, int craftAmount) {
        if (routeStack == null) {
            return null;
        }
        switch (routeStack.kind()) {
            case ITEM:
                return itemInput(legacyStack);
            case GAS:
                if (routeStack.hasLegacyStack()) {
                    return itemInput(legacyStack);
                }
                GasStack gas = scaled(routeStack.gasStack(), craftAmount);
                return gas == null || gas.getGas() == null || gas.amount <= 0 ? null : new GasInput(gas, legacyStack);
            case FLUID:
                FluidStack fluid = scaled(routeStack.fluidStack(), craftAmount);
                return fluid == null || fluid.getFluid() == null || fluid.amount <= 0 ? null : new FluidInput(fluid, legacyStack);
            default:
                return null;
        }
    }

    /**
     * 创建物品频道抽取请求。
     */
    @Nullable
    private static ExtractedInput itemInput(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new ItemInput(stack);
    }

    /**
     * 把已经从 AE 抽出的内容按原频道退回。
     */
    private static void rollback(AEUpgradeNode node, List<ExtractedInput> inputs, int completedInputs) {
        for (int i = completedInputs - 1; i >= 0; i--) {
            inputs.get(i).rollback(node);
        }
    }

    /**
     * 按 route 输出类型检查对应 AE 频道的可接收能力。
     */
    private static NetworkOutput routeOutput(AERecipeRouteStack routeOutput, ItemStack legacyOutput, int craftAmount) {
        if (routeOutput == null) {
            return null;
        }
        switch (routeOutput.kind()) {
            case ITEM:
                return itemOutput(legacyOutput);
            case GAS:
                GasStack gas = scaled(routeOutput.gasStack(), craftAmount);
                return gas == null || gas.getGas() == null || gas.amount <= 0 ? null : new GasOutput(gas);
            case FLUID:
                FluidStack fluid = scaled(routeOutput.fluidStack(), craftAmount);
                return fluid == null || fluid.getFluid() == null || fluid.amount <= 0 ? null : new FluidOutput(fluid);
            default:
                return null;
        }
    }

    /**
     * 检查 AE 物品频道是否能完整接收物品输出。
     */
    private static NetworkOutput itemOutput(ItemStack output) {
        return output == null || output.isEmpty() ? null : new ItemOutput(output);
    }

    private abstract static class NetworkOutput {

        abstract boolean isSameResource(NetworkOutput other);

        abstract long amount();

        abstract boolean canAccept(AEUpgradeNode node, long amount);
    }

    private static final class ItemOutput extends NetworkOutput {

        private final ItemStack output;

        private ItemOutput(ItemStack output) {
            this.output = output.copy();
        }

        @Override
        boolean isSameResource(NetworkOutput other) {
            return other instanceof ItemOutput itemOutput && ItemHandlerHelper.canItemStacksStack(output, itemOutput.output);
        }

        @Override
        long amount() {
            return output.getCount();
        }

        @Override
        boolean canAccept(AEUpgradeNode node, long amount) {
            if (amount <= 0 || amount > Integer.MAX_VALUE) {
                return false;
            }
            ItemStack combined = output.copy();
            combined.setCount((int) amount);
            return node.injectItem(combined, Actionable.SIMULATE).isEmpty();
        }
    }

    private static final class GasOutput extends NetworkOutput {

        private final GasStack output;

        private GasOutput(GasStack output) {
            this.output = output.copy();
        }

        @Override
        boolean isSameResource(NetworkOutput other) {
            return other instanceof GasOutput gasOutput && output.isGasEqual(gasOutput.output);
        }

        @Override
        long amount() {
            return output.amount;
        }

        @Override
        boolean canAccept(AEUpgradeNode node, long amount) {
            if (amount <= 0 || amount > Integer.MAX_VALUE) {
                return false;
            }
            GasStack combined = output.copy();
            combined.amount = (int) amount;
            GasStack remainder = node.injectGas(combined, Actionable.SIMULATE);
            return remainder == null || remainder.amount <= 0;
        }
    }

    private static final class FluidOutput extends NetworkOutput {

        private final FluidStack output;

        private FluidOutput(FluidStack output) {
            this.output = output.copy();
        }

        @Override
        boolean isSameResource(NetworkOutput other) {
            return other instanceof FluidOutput fluidOutput && output.isFluidEqual(fluidOutput.output);
        }

        @Override
        long amount() {
            return output.amount;
        }

        @Override
        boolean canAccept(AEUpgradeNode node, long amount) {
            if (amount <= 0 || amount > Integer.MAX_VALUE) {
                return false;
            }
            FluidStack combined = output.copy();
            combined.amount = (int) amount;
            FluidStack remainder = node.injectFluid(combined, Actionable.SIMULATE);
            return remainder == null || remainder.amount <= 0;
        }
    }

    /**
     * 一项 AE 输入抽取请求。
     */
    private abstract static class ExtractedInput {

        private final ItemStack legacyStack;

        /**
         * @param legacyStack AE pattern 对应的旧物品输入
         */
        private ExtractedInput(ItemStack legacyStack) {
            this.legacyStack = copy(legacyStack);
        }

        /**
         * @return adapter 入口需要看到的旧物品输入
         */
        private ItemStack legacyStack() {
            return legacyStack.copy();
        }

        /**
         * 模拟检查该输入是否可从 AE 抽取。
         */
        abstract boolean isSameResource(ExtractedInput other);

        abstract long amount();

        abstract boolean canExtract(AEUpgradeNode node, long amount);

        /**
         * 真实抽取该输入。
         */
        abstract boolean extract(AEUpgradeNode node);

        /**
         * 将已抽出的内容退回 AE。
         */
        abstract void rollback(AEUpgradeNode node);

        /**
         * 清理上一次事务留下的真实抽取状态。
         */
        abstract void clearExtracted();
    }

    /**
     * AE 物品频道输入。
     */
    private static final class ItemInput extends ExtractedInput {

        private final ItemStack request;
        private final IAEItemStack availabilityRequest;
        private ItemStack extracted = ItemStack.EMPTY;

        /**
         * @param request 请求抽取的物品
         */
        private ItemInput(ItemStack request) {
            super(request);
            this.request = request.copy();
            availabilityRequest = AEItemStack.fromItemStack(request);
        }

        @Override
        boolean isSameResource(ExtractedInput other) {
            return other instanceof ItemInput itemInput && ItemHandlerHelper.canItemStacksStack(request, itemInput.request);
        }

        @Override
        long amount() {
            return request.getCount();
        }

        @Override
        boolean canExtract(AEUpgradeNode node, long amount) {
            if (availabilityRequest == null || amount <= 0) {
                return false;
            }
            IAEItemStack combined = availabilityRequest.copy();
            combined.setStackSize(amount);
            return node.hasAvailableItem(combined);
        }

        @Override
        boolean extract(AEUpgradeNode node) {
            ItemStack result = node.extractItem(request.copy(), Actionable.MODULATE);
            if (!contains(result, request)) {
                if (!result.isEmpty()) {
                    node.injectItem(result, Actionable.MODULATE);
                }
                return false;
            }
            extracted = result.copy();
            return true;
        }

        @Override
        void rollback(AEUpgradeNode node) {
            if (!extracted.isEmpty()) {
                node.injectItem(extracted.copy(), Actionable.MODULATE);
            }
            clearExtracted();
        }

        @Override
        void clearExtracted() {
            extracted = ItemStack.EMPTY;
        }
    }

    /**
     * AE 气体频道输入。
     */
    private static final class GasInput extends ExtractedInput {

        private final GasStack request;
        @Nullable
        private final Object availabilityRequest;
        @Nullable
        private GasStack extracted;

        /**
         * @param request 请求抽取的气体
         * @param legacyStack AE pattern 对应的旧物品输入
         */
        private GasInput(GasStack request, ItemStack legacyStack) {
            super(legacyStack);
            this.request = request.copy();
            availabilityRequest = AEUpgradeGasBridge.createAvailabilityRequest(request);
        }

        @Override
        boolean isSameResource(ExtractedInput other) {
            return other instanceof GasInput gasInput && request.isGasEqual(gasInput.request);
        }

        @Override
        long amount() {
            return request.amount;
        }

        @Override
        @SuppressWarnings("rawtypes")
        boolean canExtract(AEUpgradeNode node, long amount) {
            if (!(availabilityRequest instanceof IAEStack requestStack) || amount <= 0) {
                return false;
            }
            IAEStack combined = requestStack.copy();
            combined.setStackSize(amount);
            return node.hasAvailableGas(combined);
        }

        @Override
        boolean extract(AEUpgradeNode node) {
            GasStack result = node.extractGas(request.copy(), Actionable.MODULATE);
            if (!contains(result, request)) {
                if (result != null && result.amount > 0) {
                    node.injectGas(result, Actionable.MODULATE);
                }
                return false;
            }
            extracted = result.copy();
            return true;
        }

        @Override
        void rollback(AEUpgradeNode node) {
            if (extracted != null && extracted.amount > 0) {
                node.injectGas(extracted.copy(), Actionable.MODULATE);
            }
            clearExtracted();
        }

        @Override
        void clearExtracted() {
            extracted = null;
        }
    }

    /**
     * AE 流体频道输入。
     */
    private static final class FluidInput extends ExtractedInput {

        private final FluidStack request;
        @Nullable
        private final IAEFluidStack availabilityRequest;
        @Nullable
        private FluidStack extracted;

        /**
         * @param request 请求抽取的流体
         * @param legacyStack AE pattern 对应的旧物品输入
         */
        private FluidInput(FluidStack request, ItemStack legacyStack) {
            super(legacyStack);
            this.request = request.copy();
            availabilityRequest = AEFluidStack.fromFluidStack(request);
        }

        @Override
        boolean isSameResource(ExtractedInput other) {
            return other instanceof FluidInput fluidInput && request.isFluidEqual(fluidInput.request);
        }

        @Override
        long amount() {
            return request.amount;
        }

        @Override
        boolean canExtract(AEUpgradeNode node, long amount) {
            if (availabilityRequest == null || amount <= 0) {
                return false;
            }
            IAEFluidStack combined = availabilityRequest.copy();
            combined.setStackSize(amount);
            return node.hasAvailableFluid(combined);
        }

        @Override
        boolean extract(AEUpgradeNode node) {
            FluidStack result = node.extractFluid(request.copy(), Actionable.MODULATE);
            if (!contains(result, request)) {
                if (result != null && result.amount > 0) {
                    node.injectFluid(result, Actionable.MODULATE);
                }
                return false;
            }
            extracted = result.copy();
            return true;
        }

        @Override
        void rollback(AEUpgradeNode node) {
            if (extracted != null && extracted.amount > 0) {
                node.injectFluid(extracted.copy(), Actionable.MODULATE);
            }
            clearExtracted();
        }

        @Override
        void clearExtracted() {
            extracted = null;
        }
    }

    /**
     * 判断 AE 实际抽取的物品是否满足请求。
     */
    private static boolean contains(ItemStack actual, ItemStack expected) {
        return actual != null && !actual.isEmpty() && expected != null && !expected.isEmpty() &&
              actual.getCount() >= expected.getCount() && ItemHandlerHelper.canItemStacksStack(actual, expected);
    }

    /**
     * 判断 AE 实际抽取的气体是否满足请求。
     */
    private static boolean contains(@Nullable GasStack actual, GasStack expected) {
        return actual != null && expected != null && actual.getGas() != null && expected.getGas() != null &&
              actual.amount >= expected.amount && actual.isGasEqual(expected);
    }

    /**
     * 判断 AE 实际抽取的流体是否满足请求。
     */
    private static boolean contains(@Nullable FluidStack actual, FluidStack expected) {
        return actual != null && expected != null && actual.getFluid() != null && expected.getFluid() != null &&
              actual.amount >= expected.amount && actual.isFluidEqual(expected);
    }

    /**
     * 按当前配方批量数量放大气体栈。
     */
    @Nullable
    private static GasStack scaled(@Nullable GasStack stack, int multiplier) {
        if (stack == null || stack.getGas() == null || stack.amount <= 0 || multiplier <= 0) {
            return null;
        }
        long amount = (long) stack.amount * multiplier;
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        GasStack scaled = stack.copy();
        scaled.amount = (int) amount;
        return scaled;
    }

    /**
     * 按当前配方批量数量放大流体栈。
     */
    @Nullable
    private static FluidStack scaled(@Nullable FluidStack stack, int multiplier) {
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

    /**
     * @param stack 需要复制的物品栈
     * @return 安全副本
     */
    private static ItemStack copy(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }
}
