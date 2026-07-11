package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.storage.MEStorage;
import mekanism.api.gas.GasStack;
import me.ramidzkh.mekae2.ae2.AEGasKey;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Atomic AE-side input extraction plan for one exposed machine recipe.
 */
public final class AERecipeNetworkTransferPlan {

    private final List<NetworkInput> inputs;
    private final List<ItemStack> legacyInputs;
    private final List<NetworkStack> outputs;
    @Nullable
    private MEStorage transactionStorage;

    private AERecipeNetworkTransferPlan(List<NetworkInput> inputs, List<NetworkStack> outputs) {
        this.inputs = Collections.unmodifiableList(inputs);
        this.outputs = Collections.unmodifiableList(outputs);
        List<ItemStack> adapterInputs = new ArrayList<>(inputs.size());
        for (NetworkInput input : inputs) {
            adapterInputs.add(input.legacyStack());
        }
        legacyInputs = Collections.unmodifiableList(adapterInputs);
    }

    @Nullable
    public static AERecipeNetworkTransferPlan fromRecipe(AEExposedRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        List<ItemStack> exposedInputs = recipe.getInputStacks();
        List<ItemStack> exposedOutputs = recipe.getOutputStacks();
        AERecipeRoute route = recipe.getRecipeRoute();
        if (exposedInputs.isEmpty() || exposedOutputs.isEmpty()
              || route != null && (route.inputs().size() != exposedInputs.size()
              || route.outputs().size() != exposedOutputs.size())) {
            return null;
        }

        List<NetworkInput> inputs = new ArrayList<>(exposedInputs.size());
        for (int i = 0; i < exposedInputs.size(); i++) {
            NetworkStack networkStack = route == null
                  ? itemStack(exposedInputs.get(i))
                  : routeStack(route.inputs().get(i), exposedInputs.get(i), recipe.getCraftAmount(), true);
            if (networkStack == null) {
                return null;
            }
            inputs.add(new NetworkInput(networkStack, exposedInputs.get(i)));
        }

        List<NetworkStack> outputs = new ArrayList<>(exposedOutputs.size());
        for (int i = 0; i < exposedOutputs.size(); i++) {
            NetworkStack networkStack = route == null
                  ? itemStack(exposedOutputs.get(i))
                  : routeStack(route.outputs().get(i), exposedOutputs.get(i), recipe.getCraftAmount(), false);
            if (networkStack == null) {
                return null;
            }
            outputs.add(networkStack);
        }
        return new AERecipeNetworkTransferPlan(inputs, outputs);
    }

    public boolean isEmpty() {
        return inputs.isEmpty();
    }

    public List<ItemStack> getLegacyInputs() {
        List<ItemStack> copy = new ArrayList<>(legacyInputs.size());
        for (ItemStack stack : legacyInputs) {
            copy.add(stack.copy());
        }
        return copy;
    }

    public boolean canAcceptOutputs(AEUpgradeNode node) {
        MEStorage storage = usableStorage(node);
        if (storage == null) {
            return false;
        }
        List<NetworkStack> aggregatedOutputs = aggregateStacks(outputs);
        if (aggregatedOutputs == null) {
            return false;
        }
        for (NetworkStack output : aggregatedOutputs) {
            long inserted = storage.insert(output.key(), output.amount(), Actionable.SIMULATE, node.getActionSource());
            if (inserted < output.amount()) {
                return false;
            }
        }
        return true;
    }

    public boolean canExtract(AEUpgradeNode node) {
        MEStorage storage = usableStorage(node);
        if (storage == null) {
            return false;
        }
        List<NetworkStack> aggregatedInputs = aggregateInputs(inputs);
        if (aggregatedInputs == null) {
            return false;
        }
        for (NetworkStack input : aggregatedInputs) {
            long extracted = storage.extract(input.key(), input.amount(), Actionable.SIMULATE,
                  node.getActionSource());
            if (extracted < input.amount()) {
                return false;
            }
        }
        return true;
    }

    public boolean extract(AEUpgradeNode node) {
        MEStorage storage = usableStorage(node);
        if (storage == null) {
            return false;
        }
        clearExtracted();
        transactionStorage = storage;
        for (NetworkInput input : inputs) {
            NetworkStack stack = input.stack();
            long extracted = storage.extract(stack.key(), stack.amount(), Actionable.MODULATE, node.getActionSource());
            if (extracted != stack.amount()) {
                if (extracted > 0) {
                    storage.insert(stack.key(), extracted, Actionable.MODULATE, node.getActionSource());
                }
                rollback(node);
                return false;
            }
            input.setExtracted(extracted);
        }
        return true;
    }

    public void rollback(AEUpgradeNode node) {
        MEStorage storage = transactionStorage;
        if (storage != null) {
            for (int i = inputs.size() - 1; i >= 0; i--) {
                NetworkInput input = inputs.get(i);
                if (input.extracted() > 0) {
                    storage.insert(input.stack().key(), input.extracted(), Actionable.MODULATE, node.getActionSource());
                }
            }
        }
        clearExtracted();
        transactionStorage = null;
    }

    public void commit() {
        clearExtracted();
        transactionStorage = null;
    }

    @Nullable
    private static MEStorage usableStorage(AEUpgradeNode node) {
        return node == null || !node.canUseNetwork() ? null : node.getNetworkStorage();
    }

    @Nullable
    private static NetworkStack routeStack(AERecipeRouteStack routeStack, ItemStack exposedStack, int craftAmount,
          boolean input) {
        if (routeStack == null) {
            return null;
        }
        return switch (routeStack.kind()) {
            case ITEM -> itemStack(exposedStack);
            case GAS -> input && routeStack.hasCarrierStack()
                  ? itemStack(exposedStack)
                  : gasStack(routeStack.gasStack(), craftAmount);
            case FLUID -> fluidStack(routeStack.fluidStack(), craftAmount);
        };
    }

    @Nullable
    private static NetworkStack itemStack(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        return key == null || stack.getCount() <= 0 ? null : new NetworkStack(key, stack.getCount());
    }

    @Nullable
    private static NetworkStack gasStack(@Nullable GasStack stack, int multiplier) {
        long amount = scaledAmount(stack == null ? 0 : stack.amount, multiplier);
        AEGasKey key = AEGasKey.of(stack);
        return key == null || amount <= 0 ? null : new NetworkStack(key, amount);
    }

    @Nullable
    private static NetworkStack fluidStack(@Nullable FluidStack stack, int multiplier) {
        long amount = scaledAmount(stack == null ? 0 : stack.amount, multiplier);
        AEFluidKey key = AEFluidKey.of(stack);
        return key == null || amount <= 0 ? null : new NetworkStack(key, amount);
    }

    private static long scaledAmount(int amount, int multiplier) {
        if (amount <= 0 || multiplier <= 0 || amount > Long.MAX_VALUE / multiplier) {
            return 0;
        }
        return (long) amount * multiplier;
    }

    @Nullable
    private static List<NetworkStack> aggregateInputs(List<NetworkInput> inputs) {
        List<NetworkStack> stacks = new ArrayList<>(inputs.size());
        for (NetworkInput input : inputs) {
            stacks.add(input.stack());
        }
        return aggregateStacks(stacks);
    }

    @Nullable
    private static List<NetworkStack> aggregateStacks(List<NetworkStack> stacks) {
        Map<AEKey, Long> amounts = new LinkedHashMap<>();
        for (NetworkStack stack : stacks) {
            long current = amounts.getOrDefault(stack.key(), 0L);
            if (stack.amount() <= 0 || current > Long.MAX_VALUE - stack.amount()) {
                return null;
            }
            amounts.put(stack.key(), current + stack.amount());
        }

        List<NetworkStack> aggregated = new ArrayList<>(amounts.size());
        for (Map.Entry<AEKey, Long> entry : amounts.entrySet()) {
            aggregated.add(new NetworkStack(entry.getKey(), entry.getValue()));
        }
        return aggregated;
    }

    private void clearExtracted() {
        for (NetworkInput input : inputs) {
            input.setExtracted(0);
        }
    }

    private record NetworkStack(AEKey key, long amount) {
    }

    private static final class NetworkInput {

        private final NetworkStack stack;
        private final ItemStack legacyStack;
        private long extracted;

        private NetworkInput(NetworkStack stack, ItemStack legacyStack) {
            this.stack = stack;
            this.legacyStack = legacyStack.copy();
        }

        private NetworkStack stack() {
            return stack;
        }

        private ItemStack legacyStack() {
            return legacyStack.copy();
        }

        private long extracted() {
            return extracted;
        }

        private void setExtracted(long extracted) {
            this.extracted = extracted;
        }
    }
}
