package mekceuaeupgrade.common.recipe;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.recipe.route.AERecipeStackKind;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;
import mekceuaeupgrade.common.transfer.AERecipeNetworkTransferPlan;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import mekceuaeupgrade.common.config.AERecipeKey;
import mekceuaeupgrade.common.config.AERecipeProfile;
import mekceuaeupgrade.common.config.AERecipeStackNBT;
import mekanism.api.gas.GasStack;
import mekanism.common.recipe.inputs.MachineInput;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AEExposedRecipe implements ICraftingPatternDetails {

    @Nullable
    private static IItemStorageChannel itemStorageChannel;

    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;
    private final ItemStack output;
    private final IAEItemStack[] aeInputs;
    private final IAEItemStack[] aeOutputs;
    private final IAEItemStack[] aeCondensedInputs;
    private final IAEItemStack[] aeCondensedOutputs;
    private final AEPatternInput patternInput;
    private final ItemStack patternStack;
    private final AERecipeKey recipeKey;
    @Nullable
    private final AERecipeRoute recipeRoute;
    private final boolean selfReferentialOutput;
    private final int craftAmount;
    private int priority;
    private boolean autoProcessingPlanResolved;
    @Nullable
    private AERecipeNetworkTransferPlan autoProcessingPlan;

    public AEExposedRecipe(ItemStack input, ItemStack output) {
        this(Collections.singletonList(input), output);
    }

    public AEExposedRecipe(ItemStack input, List<ItemStack> outputs) {
        this(Collections.singletonList(input), outputs);
    }

    public AEExposedRecipe(List<ItemStack> inputs, ItemStack output) {
        this(inputs, Collections.singletonList(output));
    }

    public AEExposedRecipe(List<ItemStack> inputs, List<ItemStack> outputs) {
        this(inputs, outputs, AERecipeKey.of(inputs, outputs), null, 1);
    }

    public AEExposedRecipe(List<ItemStack> inputs, List<ItemStack> outputs, String routeDiscriminator) {
        this(inputs, outputs, AERecipeKey.of(inputs, outputs, routeDiscriminator), null, 1);
    }

    public AEExposedRecipe(List<ItemStack> inputs, List<ItemStack> outputs, AERecipeRoute route, String routeDiscriminator) {
        this(inputs, outputs, AERecipeKey.of(inputs, outputs, routeDiscriminator), route, 1);
    }

    AEExposedRecipe(List<ItemStack> inputs, List<ItemStack> outputs, AERecipeKey recipeKey, @Nullable AERecipeRoute recipeRoute,
          int craftAmount) {
        List<ItemStack> copiedInputs = new ArrayList<>(inputs.size());
        for (ItemStack input : inputs) {
            copiedInputs.add(input.copy());
        }
        this.inputs = Collections.unmodifiableList(copiedInputs);
        List<ItemStack> copiedOutputs = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            copiedOutputs.add(output.copy());
        }
        this.outputs = Collections.unmodifiableList(copiedOutputs);
        output = this.outputs.isEmpty() ? ItemStack.EMPTY : this.outputs.get(0);
        aeInputs = new IAEItemStack[this.inputs.size()];
        for (int i = 0; i < this.inputs.size(); i++) {
            aeInputs[i] = toAEStack(this.inputs.get(i));
        }
        aeOutputs = new IAEItemStack[this.outputs.size()];
        for (int i = 0; i < this.outputs.size(); i++) {
            aeOutputs[i] = toAEStack(this.outputs.get(i));
        }
        aeCondensedInputs = condenseStacks(aeInputs);
        aeCondensedOutputs = condenseStacks(aeOutputs);
        patternInput = new AEPatternInput(this.inputs);
        patternStack = createPatternStack(this.inputs, this.outputs, recipeKey);
        this.recipeKey = recipeKey;
        this.recipeRoute = recipeRoute;
        selfReferentialOutput = hasSelfReferentialOutput(this.inputs, this.outputs, recipeRoute);
        this.craftAmount = Math.max(1, craftAmount);
    }

    public AEExposedRecipe withCraftAmount(int amount) {
        amount = AERecipeProfile.clampCraftAmount(amount, getMaxCraftAmount());
        if (amount == craftAmount) {
            return this;
        }
        if (recipeRoute != null) {
            List<ItemStack> routeInputs = recipeRoute.toLegacyInputStacks(amount);
            List<ItemStack> routeOutputs = recipeRoute.toLegacyOutputStacks(amount);
            if (routeInputs != null && routeOutputs != null) {
                AEExposedRecipe routeRecipe = new AEExposedRecipe(routeInputs, routeOutputs, recipeKey, recipeRoute, amount);
                routeRecipe.setPriority(priority);
                return routeRecipe;
            }
        }
        return new AEExposedRecipe(scaleStacks(recipeKey.getInputStacks(), amount), scaleStacks(recipeKey.getOutputStacks(), amount),
              recipeKey, recipeRoute, amount);
    }

    public int getMaxCraftAmount() {
        if (recipeRoute != null) {
            return recipeRoute.getMaxCraftAmount();
        }
        return AERecipeProfile.getMaxCraftAmount(recipeKey.getInputStacks(), recipeKey.getOutputStacks());
    }

    private static List<ItemStack> scaleStacks(List<ItemStack> stacks, int amount) {
        List<ItemStack> scaled = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            long count = (long) copy.getCount() * amount;
            copy.setCount(count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count);
            scaled.add(copy);
        }
        return scaled;
    }

    private static IAEItemStack toAEStack(ItemStack stack) {
        IItemStorageChannel channel = itemStorageChannel;
        if (channel == null) {
            channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            itemStorageChannel = channel;
        }
        return channel.createStack(stack);
    }

    private static ItemStack createPatternStack(List<ItemStack> inputs, List<ItemStack> outputs, AERecipeKey recipeKey) {
        ItemStack stack = new ItemStack(MEKCeuAEUpgradeItems.AECraftingUpgrade);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList inputList = new NBTTagList();
        for (ItemStack input : inputs) {
            inputList.appendTag(AERecipeStackNBT.write(input));
        }
        NBTTagList outputList = new NBTTagList();
        for (ItemStack output : outputs) {
            outputList.appendTag(AERecipeStackNBT.write(output));
        }
        tag.setTag("inputs", inputList);
        tag.setTag("outputs", outputList);
        tag.setString("routeKey", recipeKey.getRouteKey());
        stack.setTagCompound(tag);
        return stack;
    }

    public AEPatternInput getPatternInput() {
        return patternInput;
    }

    public ItemStack getInputStack() {
        return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0).copy();
    }

    public List<ItemStack> getInputStacks() {
        List<ItemStack> copied = new ArrayList<>(inputs.size());
        for (ItemStack input : inputs) {
            copied.add(input.copy());
        }
        return copied;
    }

    public ItemStack getOutputStack() {
        return output.copy();
    }

    public List<ItemStack> getOutputStacks() {
        List<ItemStack> copied = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            copied.add(output.copy());
        }
        return copied;
    }

    public AERecipeKey getRecipeKey() {
        return recipeKey;
    }

    @Nullable
    public AERecipeRoute getRecipeRoute() {
        return recipeRoute;
    }

    public int getCraftAmount() {
        return craftAmount;
    }

    /**
     * @return 输入与输出是否包含相同的物品、气体或流体资源
     */
    public boolean hasSelfReferentialOutput() {
        return selfReferentialOutput;
    }

    /**
     * 自引用处理样板会被 AE 合成规划器视为循环，因此不能注册为 AE 合成配方。
     *
     * @return 该路线能否注册为 AE 合成配方
     */
    public boolean isExposableToCrafting() {
        return !selfReferentialOutput;
    }

    @Nullable
    public AERecipeNetworkTransferPlan getAutoProcessingPlan() {
        if (!autoProcessingPlanResolved) {
            autoProcessingPlan = AERecipeNetworkTransferPlan.fromRecipe(this);
            autoProcessingPlanResolved = true;
        }
        return autoProcessingPlan;
    }

    public boolean matchesInput(ItemStack stack) {
        return inputs.size() == 1 && inputContains(stack, inputs.get(0));
    }

    public boolean matchesInputs(List<ItemStack> stacks) {
        if (stacks.size() != inputs.size()) {
            return false;
        }
        for (int i = 0; i < inputs.size(); i++) {
            if (!inputContains(stacks.get(i), inputs.get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(ICraftingPatternDetails details) {
        if (details == this) {
            return true;
        }
        if (!(details instanceof AEExposedRecipe other)) {
            return false;
        }
        return recipeKey.getRouteKey().equals(other.recipeKey.getRouteKey()) &&
              inputsMatch(inputs, other.inputs) && inputsMatch(outputs, other.outputs);
    }

    @Override
    public ItemStack getPattern() {
        return patternStack.copy();
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        return slotIndex >= 0 && slotIndex < inputs.size() && inputContains(itemStack, inputs.get(slotIndex));
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return copyStacks(aeInputs);
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return copyStacks(aeCondensedInputs);
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return copyStacks(aeCondensedOutputs);
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return copyStacks(aeOutputs);
    }

    private static IAEItemStack[] copyStacks(IAEItemStack[] stacks) {
        IAEItemStack[] copy = new IAEItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            copy[i] = stacks[i] == null ? null : stacks[i].copy();
        }
        return copy;
    }

    /**
     * 合并相同 AE 资源，避免合成 CPU 对重复输入分别模拟后低估实际需求量。
     */
    private static IAEItemStack[] condenseStacks(IAEItemStack[] stacks) {
        Map<IAEItemStack, IAEItemStack> condensed = new LinkedHashMap<>();
        for (IAEItemStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            IAEItemStack existing = condensed.get(stack);
            if (existing == null) {
                condensed.put(stack, stack.copy());
            } else {
                existing.add(stack);
            }
        }
        return condensed.values().toArray(new IAEItemStack[condensed.size()]);
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
        return output.copy();
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof AEExposedRecipe other)) {
            return false;
        }
        return recipeKey.getRouteKey().equals(other.recipeKey.getRouteKey()) &&
              inputsMatch(inputs, other.inputs) && inputsMatch(outputs, other.outputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeKey.getRouteKey(), inputsHash(inputs), inputsHash(outputs));
    }

    private static boolean inputsMatch(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!ItemStack.areItemStacksEqual(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean inputContains(ItemStack supplied, ItemStack expected) {
        return MachineInput.inputContains(supplied, expected) ||
              AEUpgradeFakeFluid.inputContains(supplied, expected) ||
              AEUpgradeFakeGas.inputContains(supplied, expected);
    }

    private static boolean hasSelfReferentialOutput(List<ItemStack> inputs, List<ItemStack> outputs,
          @Nullable AERecipeRoute route) {
        if (route != null) {
            for (AERecipeRouteStack input : route.inputs()) {
                for (AERecipeRouteStack output : route.outputs()) {
                    if (sameResource(input, output)) {
                        return true;
                    }
                }
            }
            return false;
        }
        for (ItemStack input : inputs) {
            for (ItemStack output : outputs) {
                if (sameItem(input, output)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 比较 route 的真实输入和输出资源类型，忽略数量以及 AE 1.12 使用的旧物品载体。
     */
    private static boolean sameResource(AERecipeRouteStack input, AERecipeRouteStack output) {
        if (input == null || output == null) {
            return false;
        }
        if (input.kind() != output.kind()) {
            return false;
        }
        return switch (input.kind()) {
            case ITEM -> sameItem(input.itemStack(), output.itemStack());
            case GAS -> sameGas(input.gasStack(), output.gasStack());
            case FLUID -> sameFluid(input.fluidStack(), output.fluidStack());
        };
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return left != null && right != null && !left.isEmpty() && !right.isEmpty() &&
              ItemStack.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static boolean sameGas(@Nullable GasStack left, @Nullable GasStack right) {
        return left != null && right != null && left.getGas() != null && right.getGas() != null && left.amount > 0 && right.amount > 0 &&
              left.isGasEqual(right);
    }

    private static boolean sameFluid(@Nullable FluidStack left, @Nullable FluidStack right) {
        return left != null && right != null && left.getFluid() != null && right.getFluid() != null && left.amount > 0 && right.amount > 0 &&
              left.isFluidEqual(right);
    }

    private static int inputsHash(List<ItemStack> stacks) {
        Object[] hashes = new Object[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            hashes[i] = Arrays.hashCode(new Object[]{stack.getItem(), stack.getMetadata(), stack.getTagCompound(), stack.getCount()});
        }
        return Arrays.hashCode(hashes);
    }
}
