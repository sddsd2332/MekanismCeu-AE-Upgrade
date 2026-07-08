package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AERecipeKey {

    private static final String ROUTE_SEPARATOR = ".";
    private static final String INPUT_SEPARATOR = "+";
    private static final String OUTPUT_SEPARATOR = "+";
    private static final String ROUTE_DISCRIMINATOR = "routeDiscriminator";

    private final List<AEItemStackKey> inputs;
    private final List<AEItemStackKey> outputs;
    private final String inputKey;
    private final String routeDiscriminator;
    private final String routeKey;
    private final String outputKey;

    private AERecipeKey(List<AEItemStackKey> inputs, List<AEItemStackKey> outputs, String routeDiscriminator) {
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
        this.routeDiscriminator = routeDiscriminator == null ? "" : routeDiscriminator;
        inputKey = this.inputs.stream().map(AEItemStackKey::getEncoded).collect(Collectors.joining(INPUT_SEPARATOR));
        outputKey = this.outputs.stream().map(AEItemStackKey::getEncoded).collect(Collectors.joining(OUTPUT_SEPARATOR));
        routeKey = this.routeDiscriminator.isEmpty()
              ? inputKey + ROUTE_SEPARATOR + outputKey
              : inputKey + ROUTE_SEPARATOR + this.routeDiscriminator + ROUTE_SEPARATOR + outputKey;
    }

    public static AERecipeKey of(ItemStack input, ItemStack output) {
        return of(Collections.singletonList(input), output);
    }

    public static AERecipeKey of(List<ItemStack> inputs, ItemStack output) {
        return of(inputs, Collections.singletonList(output));
    }

    public static AERecipeKey of(List<ItemStack> inputs, List<ItemStack> outputs) {
        return of(inputs, outputs, "");
    }

    public static AERecipeKey of(List<ItemStack> inputs, List<ItemStack> outputs, String routeDiscriminator) {
        List<AEItemStackKey> inputKeys = new ArrayList<>(inputs.size());
        for (ItemStack input : inputs) {
            inputKeys.add(AEItemStackKey.fromStack(input));
        }
        List<AEItemStackKey> outputKeys = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            outputKeys.add(AEItemStackKey.fromStack(output));
        }
        return new AERecipeKey(inputKeys, outputKeys, routeDiscriminator);
    }

    public static AERecipeKey of(AEExposedRecipe recipe) {
        return recipe.getRecipeKey();
    }

    public static AERecipeKey fromNBT(NBTTagCompound tag) {
        List<AEItemStackKey> inputKeys = new ArrayList<>();
        NBTTagList inputs = tag.getTagList("inputs", NBT.TAG_COMPOUND);
        for (int i = 0; i < inputs.tagCount(); i++) {
            inputKeys.add(AEItemStackKey.fromNBT(inputs.getCompoundTagAt(i)));
        }
        List<AEItemStackKey> outputKeys = new ArrayList<>();
        NBTTagList outputs = tag.getTagList("outputs", NBT.TAG_COMPOUND);
        for (int i = 0; i < outputs.tagCount(); i++) {
            outputKeys.add(AEItemStackKey.fromNBT(outputs.getCompoundTagAt(i)));
        }
        return new AERecipeKey(inputKeys, outputKeys, tag.getString(ROUTE_DISCRIMINATOR));
    }

    public NBTTagCompound write(NBTTagCompound tag) {
        NBTTagList inputList = new NBTTagList();
        for (AEItemStackKey input : inputs) {
            inputList.appendTag(input.write(new NBTTagCompound()));
        }
        NBTTagList outputList = new NBTTagList();
        for (AEItemStackKey output : outputs) {
            outputList.appendTag(output.write(new NBTTagCompound()));
        }
        tag.setTag("inputs", inputList);
        tag.setTag("outputs", outputList);
        if (!routeDiscriminator.isEmpty()) {
            tag.setString(ROUTE_DISCRIMINATOR, routeDiscriminator);
        }
        return tag;
    }

    public String getInputKey() {
        return inputKey;
    }

    public List<String> getInputKeys() {
        return inputs.stream().map(AEItemStackKey::getEncoded).collect(Collectors.toList());
    }

    public String getOutputKey() {
        return outputKey;
    }

    public List<String> getOutputKeys() {
        return outputs.stream().map(AEItemStackKey::getEncoded).collect(Collectors.toList());
    }

    public String getRouteDiscriminator() {
        return routeDiscriminator;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public ItemStack getInputStack() {
        return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0).getStack();
    }

    public List<ItemStack> getInputStacks() {
        List<ItemStack> stacks = new ArrayList<>(inputs.size());
        for (AEItemStackKey input : inputs) {
            stacks.add(input.getStack());
        }
        return stacks;
    }

    public ItemStack getOutputStack() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).getStack();
    }

    public List<ItemStack> getOutputStacks() {
        List<ItemStack> stacks = new ArrayList<>(outputs.size());
        for (AEItemStackKey output : outputs) {
            stacks.add(output.getStack());
        }
        return stacks;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof AERecipeKey other && routeKey.equals(other.routeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeKey);
    }

    @Override
    public String toString() {
        return routeKey;
    }
}
