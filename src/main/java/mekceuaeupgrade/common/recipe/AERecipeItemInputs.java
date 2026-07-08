package mekceuaeupgrade.common.recipe;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 将 Mekanism 配方里的物品输入转换成 AE 可以实际下单的具体物品栈。
 *
 * <p>Mekanism/Forge 1.12 经常用 {@link OreDictionary#WILDCARD_VALUE} 表示矿辞或多 meta 输入；
 * AE pattern 不能安全保存这种泛输入，所以暴露配方前需要展开成具体 meta，并由调用方反查 Mek 配方确认该候选真实可用。</p>
 */
public final class AERecipeItemInputs {

    private AERecipeItemInputs() {
    }

    /**
     * 展开单个物品输入。
     *
     * @param input Mek 配方中的原始输入
     * @param accepts 调用方的真实配方校验
     * @return 可暴露给 AE 的具体输入
     */
    public static List<ItemStack> expand(ItemStack input, Predicate<ItemStack> accepts) {
        if (input == null || input.isEmpty() || input.getCount() <= 0) {
            return Collections.emptyList();
        }
        Predicate<ItemStack> filter = accepts == null ? stack -> true : accepts;
        List<ItemStack> rawCandidates = rawCandidates(input);
        List<ItemStack> expanded = new ArrayList<>(rawCandidates.size());
        Set<String> seen = new LinkedHashSet<>();
        for (ItemStack candidate : rawCandidates) {
            ItemStack normalized = normalizeCandidate(input, candidate);
            if (!isConcreteInput(normalized) || !filter.test(normalized)) {
                continue;
            }
            if (seen.add(identity(normalized))) {
                expanded.add(normalized);
            }
        }
        return expanded;
    }

    /**
     * 展开多个物品输入的笛卡尔组合。
     *
     * @param inputs 原始输入列表
     * @param accepts 组合级真实配方校验
     * @return 可暴露给 AE 的具体输入组合
     */
    public static List<List<ItemStack>> expandCombinations(List<ItemStack> inputs, Predicate<List<ItemStack>> accepts) {
        if (inputs == null || inputs.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<ItemStack>> candidateGroups = new ArrayList<>(inputs.size());
        for (ItemStack input : inputs) {
            List<ItemStack> candidates = expand(input, stack -> true);
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            candidateGroups.add(candidates);
        }
        Predicate<List<ItemStack>> filter = accepts == null ? stacks -> true : accepts;
        List<List<ItemStack>> combinations = new ArrayList<>();
        addCombinations(candidateGroups, 0, new ArrayList<>(inputs.size()), filter, combinations);
        return combinations;
    }

    /**
     * @return 物品栈是否已经是 AE 可保存的具体输入。
     */
    public static boolean isConcreteInput(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0 && stack.getMetadata() != OreDictionary.WILDCARD_VALUE;
    }

    private static void addCombinations(List<List<ItemStack>> groups, int index, List<ItemStack> current,
          Predicate<List<ItemStack>> accepts, List<List<ItemStack>> output) {
        if (index >= groups.size()) {
            List<ItemStack> copied = copyStacks(current);
            if (accepts.test(copied)) {
                output.add(copied);
            }
            return;
        }
        for (ItemStack candidate : groups.get(index)) {
            current.add(candidate);
            addCombinations(groups, index + 1, current, accepts, output);
            current.remove(current.size() - 1);
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copied = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copied.add(stack.copy());
        }
        return copied;
    }

    private static List<ItemStack> rawCandidates(ItemStack input) {
        if (input.getMetadata() != OreDictionary.WILDCARD_VALUE) {
            return Collections.singletonList(input.copy());
        }
        List<ItemStack> candidates = new ArrayList<>();
        addCreativeSubItems(input, candidates);
        addFallbackMetaZero(input, candidates);
        addOreDictionaryCandidates(input, candidates);
        List<ItemStack> directCandidates = new ArrayList<>(candidates);
        for (ItemStack candidate : directCandidates) {
            addOreDictionaryCandidates(candidate, candidates);
        }
        return candidates;
    }

    private static void addOreDictionaryCandidates(ItemStack input, List<ItemStack> candidates) {
        int[] oreIds;
        try {
            oreIds = OreDictionary.getOreIDs(input);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName == null || oreName.isEmpty()) {
                continue;
            }
            for (ItemStack oreStack : OreDictionary.getOres(oreName, false)) {
                if (oreStack.isEmpty()) {
                    continue;
                }
                if (oreStack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                    addCreativeSubItems(oreStack, candidates);
                    addFallbackMetaZero(oreStack, candidates);
                } else {
                    candidates.add(oreStack.copy());
                }
            }
        }
    }

    private static void addCreativeSubItems(ItemStack input, List<ItemStack> candidates) {
        NonNullList<ItemStack> subItems = NonNullList.create();
        try {
            input.getItem().getSubItems(CreativeTabs.SEARCH, subItems);
        } catch (RuntimeException | LinkageError ignored) {
            return;
        }
        for (ItemStack subItem : subItems) {
            if (subItem.isEmpty() || subItem.getItem() != input.getItem() || subItem.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                continue;
            }
            candidates.add(subItem.copy());
        }
    }

    private static void addFallbackMetaZero(ItemStack input, List<ItemStack> candidates) {
        candidates.add(new ItemStack(input.getItem(), 1, 0));
    }

    private static ItemStack normalizeCandidate(ItemStack recipeInput, ItemStack candidate) {
        if (candidate.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack normalized = candidate.copy();
        normalized.setCount(recipeInput.getCount());
        if (recipeInput.hasTagCompound()) {
            NBTTagCompound tag = recipeInput.getTagCompound();
            normalized.setTagCompound(tag == null ? null : tag.copy());
        }
        return normalized;
    }

    private static String identity(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        return (name == null ? stack.getItem().getClass().getName() : name.toString()) + '@' + stack.getMetadata() + 'x' + stack.getCount() +
              (stack.hasTagCompound() ? '#' + stack.getTagCompound().toString() : "");
    }
}
