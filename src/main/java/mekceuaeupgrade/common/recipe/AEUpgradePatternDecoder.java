package mekceuaeupgrade.common.recipe;

import mekceuaeupgrade.common.config.AERecipeKey;
import mekceuaeupgrade.common.config.AERecipeStackNBT;
import mekceuaeupgrade.common.registries.MEKCeuAEUpgradeItems;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.IPatternDetailsDecoder;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.AEItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class AEUpgradePatternDecoder implements IPatternDetailsDecoder {

    public static final AEUpgradePatternDecoder INSTANCE = new AEUpgradePatternDecoder();

    private static boolean registered;

    private AEUpgradePatternDecoder() {
    }

    public static void register() {
        if (!registered) {
            PatternDetailsHelper.registerDecoder(INSTANCE);
            registered = true;
        }
    }

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return isUpgradePatternStack(stack) && isPatternTag(stack.getTagCompound());
    }

    @Nullable
    @Override
    public IPatternDetails decodePattern(AEItemKey what, World level) {
        if (what == null || !what.is(MEKCeuAEUpgradeItems.AECraftingUpgrade)) {
            return null;
        }
        ItemStack stack = what.getReadOnlyStack();
        if (!isUpgradePatternStack(stack) || !isPatternTag(stack.getTagCompound())) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        List<ItemStack> inputs = readStacks(tag, AEExposedRecipe.PATTERN_INPUTS_TAG);
        List<ItemStack> outputs = readStacks(tag, AEExposedRecipe.PATTERN_OUTPUTS_TAG);
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return null;
        }
        AERecipeKey recipeKey = readRecipeKey(tag, inputs, outputs);
        int craftAmount = readCraftAmount(tag);
        return recipeKey == null ? null : AEExposedRecipe.fromPatternDefinition(inputs, outputs, recipeKey, craftAmount);
    }

    private static boolean isUpgradePatternStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == MEKCeuAEUpgradeItems.AECraftingUpgrade && stack.hasTagCompound();
    }

    @Nullable
    private static AERecipeKey readRecipeKey(NBTTagCompound tag, List<ItemStack> inputs, List<ItemStack> outputs) {
        if (tag.hasKey(AEExposedRecipe.PATTERN_RECIPE_KEY_TAG, NBT.TAG_COMPOUND)) {
            return AERecipeKey.fromNBT(tag.getCompoundTag(AEExposedRecipe.PATTERN_RECIPE_KEY_TAG));
        }
        if (tag.hasKey(AEExposedRecipe.PATTERN_ROUTE_KEY_TAG, NBT.TAG_STRING)) {
            String routeKey = tag.getString(AEExposedRecipe.PATTERN_ROUTE_KEY_TAG);
            AERecipeKey baseKey = AERecipeKey.of(inputs, outputs);
            if (routeKey.equals(baseKey.getRouteKey())) {
                return baseKey;
            }
            String prefix = baseKey.getInputKey() + ".";
            String suffix = "." + baseKey.getOutputKey();
            if (routeKey.length() >= prefix.length() + suffix.length() && routeKey.startsWith(prefix) && routeKey.endsWith(suffix)) {
                return AERecipeKey.of(inputs, outputs, routeKey.substring(prefix.length(), routeKey.length() - suffix.length()));
            }
        }
        return AERecipeKey.of(inputs, outputs);
    }

    private static boolean isPatternTag(@Nullable NBTTagCompound tag) {
        return tag != null && tag.hasKey(AEExposedRecipe.PATTERN_INPUTS_TAG, NBT.TAG_LIST) &&
              tag.hasKey(AEExposedRecipe.PATTERN_OUTPUTS_TAG, NBT.TAG_LIST);
    }

    private static int readCraftAmount(NBTTagCompound tag) {
        if (!tag.hasKey(AEExposedRecipe.PATTERN_CRAFT_AMOUNT_TAG, NBT.TAG_INT)) {
            return 1;
        }
        return Math.max(1, tag.getInteger(AEExposedRecipe.PATTERN_CRAFT_AMOUNT_TAG));
    }

    private static List<ItemStack> readStacks(NBTTagCompound tag, String key) {
        NBTTagList list = tag.getTagList(key, NBT.TAG_COMPOUND);
        List<ItemStack> stacks = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack stack = AERecipeStackNBT.read(list.getCompoundTagAt(i));
            if (stack.isEmpty()) {
                return List.of();
            }
            stacks.add(stack);
        }
        return stacks;
    }
}
