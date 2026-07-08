package mekceuaeupgrade.common.recipe;

import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeFluid;
import mekceuaeupgrade.common.transfer.AEUpgradeFakeGas;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import mekceuaeupgrade.common.config.AERecipeKey;
import mekceuaeupgrade.common.config.AERecipeProfile;
import mekceuaeupgrade.common.config.AERecipeStackNBT;
import mekanism.common.recipe.inputs.MachineInput;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AEExposedRecipe implements IPatternDetails {

    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;
    private final ItemStack output;
    private final IInput[] patternInputs;
    private final List<GenericStack> patternOutputs;
    private final AEPatternInput patternInput;
    private final ItemStack patternStack;
    private final AEItemKey patternDefinition;
    private final AERecipeKey recipeKey;
    @Nullable
    private final AERecipeRoute recipeRoute;
    private final int craftAmount;
    private int priority;

    static final String PATTERN_INPUTS_TAG = "inputs";
    static final String PATTERN_OUTPUTS_TAG = "outputs";
    static final String PATTERN_RECIPE_KEY_TAG = "recipeKey";
    static final String PATTERN_ROUTE_KEY_TAG = "routeKey";
    static final String PATTERN_CRAFT_AMOUNT_TAG = "craftAmount";

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

    static AEExposedRecipe fromPatternDefinition(List<ItemStack> inputs, List<ItemStack> outputs, AERecipeKey recipeKey, int craftAmount) {
        return new AEExposedRecipe(inputs, outputs, recipeKey, null, craftAmount);
    }

    private AEExposedRecipe(List<ItemStack> inputs, List<ItemStack> outputs, AERecipeKey recipeKey, @Nullable AERecipeRoute recipeRoute,
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
        this.craftAmount = Math.max(1, craftAmount);
        output = this.outputs.isEmpty() ? ItemStack.EMPTY : this.outputs.get(0);
        patternInputs = new IInput[this.inputs.size()];
        for (int i = 0; i < this.inputs.size(); i++) {
            patternInputs[i] = new Input(this.inputs.get(i));
        }
        List<GenericStack> genericOutputs = new ArrayList<>(this.outputs.size());
        for (ItemStack stack : this.outputs) {
            GenericStack genericStack = GenericStack.fromItemStack(stack);
            if (genericStack != null) {
                genericOutputs.add(genericStack);
            }
        }
        patternOutputs = Collections.unmodifiableList(genericOutputs);
        patternInput = new AEPatternInput(this.inputs);
        patternStack = createPatternStack(this.inputs, this.outputs, recipeKey, this.craftAmount);
        patternDefinition = Objects.requireNonNull(AEItemKey.of(patternStack), "pattern definition");
        this.recipeKey = recipeKey;
        this.recipeRoute = recipeRoute;
    }

    public AEExposedRecipe withCraftAmount(int amount) {
        amount = AERecipeProfile.clampCraftAmount(amount, getMaxCraftAmount());
        if (amount == craftAmount) {
            return this;
        }
        return new AEExposedRecipe(scaleStacks(recipeKey.getInputStacks(), amount), scaleStacks(recipeKey.getOutputStacks(), amount),
              recipeKey, recipeRoute, amount);
    }

    public int getMaxCraftAmount() {
        return AERecipeProfile.getMaxCraftAmount(recipeKey.getInputStacks(), recipeKey.getOutputStacks());
    }

    private static List<ItemStack> scaleStacks(List<ItemStack> stacks, int amount) {
        List<ItemStack> scaled = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            scaled.add(AERecipeStacks.scale(stack, amount));
        }
        return scaled;
    }

    private static ItemStack createPatternStack(List<ItemStack> inputs, List<ItemStack> outputs, AERecipeKey recipeKey, int craftAmount) {
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
        tag.setTag(PATTERN_INPUTS_TAG, inputList);
        tag.setTag(PATTERN_OUTPUTS_TAG, outputList);
        tag.setTag(PATTERN_RECIPE_KEY_TAG, recipeKey.write(new NBTTagCompound()));
        tag.setString(PATTERN_ROUTE_KEY_TAG, recipeKey.getRouteKey());
        tag.setInteger(PATTERN_CRAFT_AMOUNT_TAG, Math.max(1, craftAmount));
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

    public boolean matches(IPatternDetails details) {
        if (details == this) {
            return true;
        }
        if (details != null && patternDefinition.equals(details.getDefinition())) {
            return true;
        }
        if (!(details instanceof AEExposedRecipe other)) {
            return false;
        }
        return recipeKey.getRouteKey().equals(other.recipeKey.getRouteKey()) &&
              inputsMatch(inputs, other.inputs) && inputsMatch(outputs, other.outputs);
    }

    public ItemStack getPattern() {
        return patternStack.copy();
    }

    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        return slotIndex >= 0 && slotIndex < inputs.size() && inputContains(itemStack, inputs.get(slotIndex));
    }

    public boolean isCraftable() {
        return false;
    }

    @Override
    public AEItemKey getDefinition() {
        return patternDefinition;
    }

    @Override
    public IInput[] getInputs() {
        return patternInputs.clone();
    }

    public IInput[] getCondensedInputs() {
        return getInputs();
    }

    public List<GenericStack> getCondensedOutputs() {
        return getOutputs();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return patternOutputs;
    }

    public boolean canSubstitute() {
        return false;
    }

    public ItemStack getOutput(World world) {
        return output.copy();
    }

    public int getPriority() {
        return priority;
    }

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

    private static int inputsHash(List<ItemStack> stacks) {
        Object[] hashes = new Object[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            hashes[i] = Arrays.hashCode(new Object[]{stack.getItem(), stack.getMetadata(), stack.getTagCompound(), stack.getCount()});
        }
        return Arrays.hashCode(hashes);
    }

    private static final class Input implements IInput {

        private final GenericStack genericStack;
        private final long multiplier;

        private Input(ItemStack stack) {
            GenericStack stackTemplate = Objects.requireNonNull(GenericStack.fromItemStack(stack), "pattern input");
            this.genericStack = new GenericStack(stackTemplate.what(), 1);
            this.multiplier = stackTemplate.amount();
        }

        @Override
        public GenericStack[] possibleInputs() {
            return new GenericStack[]{genericStack};
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, World level) {
            return input.matches(genericStack);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
