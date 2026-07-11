package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AERecipeStacks;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AERecipeProfile {

    public static final int DEFAULT_CRAFT_AMOUNT = 1;
    public static final int MAX_CRAFT_AMOUNT = Integer.MAX_VALUE;

    private static final String ROUTE_FILTER_MODE = "routeFilterMode";
    private static final String ENABLED_ROUTES = "enabledRoutes";
    private static final String DISABLED_ROUTES = "disabledRoutes";
    private static final String ORDERED_PRODUCTS = "orderedProducts";
    private static final String ORDERED_OUTPUTS = "orderedOutputs";
    private static final String CRAFT_AMOUNT = "craftAmount";
    private static final String ROUTE_CRAFT_AMOUNTS = "routeCraftAmounts";
    private static final String OUTPUT_KEY = "outputKey";
    private static final String ROUTES = "routes";
    private static final String ROUTE_KEY = "routeKey";
    private static final String AMOUNT = "amount";

    private final RouteFilterMode defaultRouteFilterMode;
    private RouteFilterMode routeFilterMode;
    private final Set<String> enabledRoutes = new LinkedHashSet<>();
    private final Set<String> disabledRoutes = new LinkedHashSet<>();
    private final Map<String, List<String>> orderedRoutes = new LinkedHashMap<>();
    private final List<String> orderedOutputs = new ArrayList<>();
    private final Map<String, Integer> routeCraftAmounts = new LinkedHashMap<>();
    private int craftAmount = DEFAULT_CRAFT_AMOUNT;
    private int version;

    public AERecipeProfile() {
        this(RouteFilterMode.BLACKLIST);
    }

    public AERecipeProfile(RouteFilterMode defaultRouteFilterMode) {
        this.defaultRouteFilterMode = defaultRouteFilterMode == null ? RouteFilterMode.BLACKLIST : defaultRouteFilterMode;
        routeFilterMode = this.defaultRouteFilterMode;
    }

    public int getVersion() {
        return version;
    }

    public boolean isEmpty() {
        return routeFilterMode == defaultRouteFilterMode && enabledRoutes.isEmpty() && disabledRoutes.isEmpty() && orderedRoutes.isEmpty() &&
               orderedOutputs.isEmpty() && routeCraftAmounts.isEmpty() && craftAmount == DEFAULT_CRAFT_AMOUNT;
    }

    public RouteFilterMode getRouteFilterMode() {
        return routeFilterMode;
    }

    public boolean toggleRouteFilterMode() {
        return setRouteFilterMode(routeFilterMode.next());
    }

    public boolean setRouteFilterMode(RouteFilterMode filterMode) {
        RouteFilterMode normalized = filterMode == null ? defaultRouteFilterMode : filterMode;
        if (routeFilterMode == normalized) {
            return false;
        }
        routeFilterMode = normalized;
        version++;
        return true;
    }

    public boolean isRouteEnabled(String routeKey) {
        return routeFilterMode == RouteFilterMode.WHITELIST ? enabledRoutes.contains(routeKey) : !disabledRoutes.contains(routeKey);
    }

    public boolean isRouteEnabled(AEExposedRecipe recipe) {
        return isRouteEnabled(recipe.getRecipeKey().getRouteKey());
    }

    public boolean setRouteEnabled(String routeKey, boolean enabled) {
        boolean changed = routeFilterMode == RouteFilterMode.WHITELIST
              ? enabled ? enabledRoutes.add(routeKey) : enabledRoutes.remove(routeKey)
              : enabled ? disabledRoutes.remove(routeKey) : disabledRoutes.add(routeKey);
        if (changed) {
            version++;
        }
        return changed;
    }

    public boolean setRoutesEnabled(Collection<String> routeKeys, boolean enabled) {
        boolean changed = false;
        for (String routeKey : routeKeys) {
            changed |= routeFilterMode == RouteFilterMode.WHITELIST
                  ? enabled ? enabledRoutes.add(routeKey) : enabledRoutes.remove(routeKey)
                  : enabled ? disabledRoutes.remove(routeKey) : disabledRoutes.add(routeKey);
        }
        if (changed) {
            version++;
        }
        return changed;
    }

    public int getCraftAmount() {
        return craftAmount;
    }

    public int getEffectiveCraftAmount(AEExposedRecipe recipe) {
        return getEffectiveCraftAmount(recipe.getRecipeKey().getRouteKey());
    }

    public int getEffectiveCraftAmount(String routeKey) {
        return routeCraftAmounts.getOrDefault(routeKey, craftAmount);
    }

    private int getEffectiveCraftAmount(AEExposedRecipe recipe, int globalCraftAmount) {
        Integer routeCraftAmount = routeCraftAmounts.get(recipe.getRecipeKey().getRouteKey());
        return routeCraftAmount == null ? globalCraftAmount : clampCraftAmount(routeCraftAmount, getMaxCraftAmount(recipe));
    }

    public boolean hasRouteCraftAmount(String routeKey) {
        return routeCraftAmounts.containsKey(routeKey);
    }

    public int getRouteCraftAmount(String routeKey) {
        return routeCraftAmounts.getOrDefault(routeKey, craftAmount);
    }

    public boolean setCraftAmount(int amount) {
        return setCraftAmount(amount, MAX_CRAFT_AMOUNT);
    }

    public boolean setCraftAmount(int amount, int maxCraftAmount) {
        amount = clampCraftAmount(amount, maxCraftAmount);
        if (craftAmount == amount) {
            return false;
        }
        craftAmount = amount;
        routeCraftAmounts.entrySet().removeIf(entry -> entry.getValue() == craftAmount);
        version++;
        return true;
    }

    public boolean setRouteCraftAmount(String routeKey, int amount) {
        return setRouteCraftAmount(routeKey, amount, MAX_CRAFT_AMOUNT);
    }

    public boolean setRouteCraftAmount(String routeKey, int amount, int maxCraftAmount) {
        amount = clampCraftAmount(amount, maxCraftAmount);
        Integer previous = routeCraftAmounts.get(routeKey);
        if (amount == craftAmount) {
            if (previous != null) {
                routeCraftAmounts.remove(routeKey);
                version++;
                return true;
            }
            return false;
        }
        if (previous != null && previous == amount) {
            return false;
        }
        routeCraftAmounts.put(routeKey, amount);
        version++;
        return true;
    }

    public boolean clearRouteCraftAmount(String routeKey) {
        if (routeCraftAmounts.remove(routeKey) != null) {
            version++;
            return true;
        }
        return false;
    }

    public static int clampCraftAmount(int amount) {
        return clampCraftAmount(amount, MAX_CRAFT_AMOUNT);
    }

    public static int clampCraftAmount(int amount, int maxCraftAmount) {
        return Math.max(DEFAULT_CRAFT_AMOUNT, Math.min(Math.max(DEFAULT_CRAFT_AMOUNT, maxCraftAmount), amount));
    }

    public static int getMaxCraftAmount(AEExposedRecipe recipe) {
        return getMaxCraftAmount(recipe.getRecipeKey().getInputStacks(), recipe.getRecipeKey().getOutputStacks());
    }

    public static int getMaxCraftAmount(Collection<AEExposedRecipe> recipes) {
        int maxCraftAmount = MAX_CRAFT_AMOUNT;
        for (AEExposedRecipe recipe : recipes) {
            maxCraftAmount = Math.min(maxCraftAmount, getMaxCraftAmount(recipe));
        }
        return maxCraftAmount;
    }

    public static int getMaxCraftAmount(List<ItemStack> inputStacks, List<ItemStack> outputStacks) {
        int maxCraftAmount = MAX_CRAFT_AMOUNT;
        for (ItemStack inputStack : inputStacks) {
            maxCraftAmount = getMaxCraftAmount(inputStack, maxCraftAmount);
        }
        for (ItemStack outputStack : outputStacks) {
            maxCraftAmount = getMaxCraftAmount(outputStack, maxCraftAmount);
        }
        return maxCraftAmount;
    }

    private static int getMaxCraftAmount(ItemStack stack, int currentMax) {
        long amount = AERecipeStacks.getAmount(stack);
        if (stack.isEmpty() || amount <= 0) {
            return currentMax;
        }
        return Math.min(currentMax, (int) Math.min(Integer.MAX_VALUE, MAX_CRAFT_AMOUNT / amount));
    }

    public boolean moveRoute(String outputKey, Collection<String> defaultOrder, String routeKey, int direction) {
        if (direction == 0 || !defaultOrder.contains(routeKey)) {
            return false;
        }
        List<String> currentOrder = getCurrentOrder(outputKey, defaultOrder);
        int index = currentOrder.indexOf(routeKey);
        int target = index + direction;
        if (index == -1 || target < 0 || target >= currentOrder.size()) {
            return false;
        }
        Collections.swap(currentOrder, index, target);
        return setOrder(outputKey, defaultOrder, currentOrder);
    }

    public boolean moveRouteToEdge(String outputKey, Collection<String> defaultOrder, String routeKey, boolean top) {
        if (!defaultOrder.contains(routeKey)) {
            return false;
        }
        List<String> currentOrder = getCurrentOrder(outputKey, defaultOrder);
        int index = currentOrder.indexOf(routeKey);
        if (index == -1 || top && index == 0 || !top && index == currentOrder.size() - 1) {
            return false;
        }
        currentOrder.remove(index);
        currentOrder.add(top ? 0 : currentOrder.size(), routeKey);
        return setOrder(outputKey, defaultOrder, currentOrder);
    }

    public boolean moveProduct(Collection<String> defaultOrder, String outputKey, int direction) {
        if (direction == 0 || !defaultOrder.contains(outputKey)) {
            return false;
        }
        List<String> currentOrder = getCurrentProductOrder(defaultOrder);
        int index = currentOrder.indexOf(outputKey);
        int target = index + direction;
        if (index == -1 || target < 0 || target >= currentOrder.size()) {
            return false;
        }
        Collections.swap(currentOrder, index, target);
        return setProductOrder(defaultOrder, currentOrder);
    }

    public boolean moveProductToEdge(Collection<String> defaultOrder, String outputKey, boolean top) {
        if (!defaultOrder.contains(outputKey)) {
            return false;
        }
        List<String> currentOrder = getCurrentProductOrder(defaultOrder);
        int index = currentOrder.indexOf(outputKey);
        if (index == -1 || top && index == 0 || !top && index == currentOrder.size() - 1) {
            return false;
        }
        currentOrder.remove(index);
        currentOrder.add(top ? 0 : currentOrder.size(), outputKey);
        return setProductOrder(defaultOrder, currentOrder);
    }

    public boolean setProductOrder(Collection<String> defaultOrder, List<String> newOrder) {
        List<String> normalizedDefault = new ArrayList<>(defaultOrder);
        List<String> normalizedNew = new ArrayList<>();
        for (String outputKey : newOrder) {
            if (normalizedDefault.contains(outputKey) && !normalizedNew.contains(outputKey)) {
                normalizedNew.add(outputKey);
            }
        }
        for (String outputKey : normalizedDefault) {
            if (!normalizedNew.contains(outputKey)) {
                normalizedNew.add(outputKey);
            }
        }
        if (normalizedNew.equals(normalizedDefault)) {
            if (!orderedOutputs.isEmpty()) {
                orderedOutputs.clear();
                version++;
                return true;
            }
            return false;
        }
        if (!normalizedNew.equals(orderedOutputs)) {
            orderedOutputs.clear();
            orderedOutputs.addAll(normalizedNew);
            version++;
            return true;
        }
        return false;
    }

    public boolean setOrder(String outputKey, Collection<String> defaultOrder, List<String> newOrder) {
        List<String> normalizedDefault = new ArrayList<>(defaultOrder);
        List<String> normalizedNew = new ArrayList<>();
        for (String routeKey : newOrder) {
            if (normalizedDefault.contains(routeKey) && !normalizedNew.contains(routeKey)) {
                normalizedNew.add(routeKey);
            }
        }
        for (String routeKey : normalizedDefault) {
            if (!normalizedNew.contains(routeKey)) {
                normalizedNew.add(routeKey);
            }
        }
        List<String> previous = orderedRoutes.get(outputKey);
        if (normalizedNew.equals(normalizedDefault)) {
            if (previous != null) {
                orderedRoutes.remove(outputKey);
                version++;
                return true;
            }
            return false;
        }
        if (!normalizedNew.equals(previous)) {
            orderedRoutes.put(outputKey, normalizedNew);
            version++;
            return true;
        }
        return false;
    }

    public boolean resetProduct(String outputKey) {
        return resetProduct(outputKey, Collections.emptyList());
    }

    public boolean resetProduct(String outputKey, Collection<String> defaultOrder) {
        boolean changed = orderedRoutes.remove(outputKey) != null;
        if (defaultOrder == null || defaultOrder.isEmpty()) {
            List<String> enabledForProduct = new ArrayList<>();
            for (String routeKey : enabledRoutes) {
                if (routeKey.endsWith("." + outputKey)) {
                    enabledForProduct.add(routeKey);
                }
            }
            List<String> disabledForProduct = new ArrayList<>();
            for (String routeKey : disabledRoutes) {
                if (routeKey.endsWith("." + outputKey)) {
                    disabledForProduct.add(routeKey);
                }
            }
            changed |= enabledRoutes.removeAll(enabledForProduct);
            changed |= disabledRoutes.removeAll(disabledForProduct);
            changed |= routeCraftAmounts.keySet().removeIf(routeKey -> routeKey.endsWith("." + outputKey));
        } else {
            changed |= enabledRoutes.removeAll(defaultOrder);
            changed |= disabledRoutes.removeAll(defaultOrder);
            changed |= routeCraftAmounts.keySet().removeAll(defaultOrder);
        }
        if (changed) {
            version++;
        }
        return changed;
    }

    public boolean resetProductOrder(String outputKey, Collection<String> defaultOrder) {
        if (!orderedOutputs.contains(outputKey)) {
            return false;
        }
        List<String> currentOrder = getCurrentProductOrder(defaultOrder);
        currentOrder.remove(outputKey);
        List<String> normalizedDefault = new ArrayList<>(defaultOrder);
        int defaultIndex = normalizedDefault.indexOf(outputKey);
        int insertIndex = currentOrder.size();
        for (int i = 0; i < currentOrder.size(); i++) {
            int candidateDefaultIndex = normalizedDefault.indexOf(currentOrder.get(i));
            if (candidateDefaultIndex > defaultIndex) {
                insertIndex = i;
                break;
            }
        }
        currentOrder.add(insertIndex, outputKey);
        return setProductOrder(defaultOrder, currentOrder);
    }

    public boolean resetAll() {
        boolean changed = !enabledRoutes.isEmpty() || !disabledRoutes.isEmpty() || !orderedRoutes.isEmpty() || !orderedOutputs.isEmpty() ||
                          !routeCraftAmounts.isEmpty() || craftAmount != DEFAULT_CRAFT_AMOUNT;
        if (!changed) {
            return false;
        }
        enabledRoutes.clear();
        disabledRoutes.clear();
        orderedRoutes.clear();
        orderedOutputs.clear();
        routeCraftAmounts.clear();
        craftAmount = DEFAULT_CRAFT_AMOUNT;
        version++;
        return true;
    }

    public boolean prune(Collection<AEExposedRecipe> currentRecipes) {
        Map<String, List<String>> defaultRoutes = new LinkedHashMap<>();
        Map<String, Integer> routeMaxCraftAmounts = new LinkedHashMap<>();
        List<String> defaultOutputs = new ArrayList<>();
        Set<String> validOutputs = new HashSet<>();
        Set<String> validRoutes = new HashSet<>();
        for (AEExposedRecipe recipe : currentRecipes) {
            String outputKey = recipe.getRecipeKey().getOutputKey();
            String routeKey = recipe.getRecipeKey().getRouteKey();
            if (validOutputs.add(outputKey)) {
                defaultOutputs.add(outputKey);
            }
            defaultRoutes.computeIfAbsent(outputKey, key -> new ArrayList<>()).add(routeKey);
            routeMaxCraftAmounts.put(routeKey, getMaxCraftAmount(recipe));
            validRoutes.add(routeKey);
        }
        int clampedCraftAmount = clampCraftAmount(craftAmount, getMaxCraftAmount(currentRecipes));
        boolean changed = false;
        if (craftAmount != clampedCraftAmount) {
            craftAmount = clampedCraftAmount;
            changed = true;
        }
        changed |= orderedOutputs.removeIf(outputKey -> !validOutputs.contains(outputKey));
        List<String> normalizedOutputs = getCurrentProductOrder(defaultOutputs);
        if (normalizedOutputs.equals(defaultOutputs)) {
            if (!orderedOutputs.isEmpty()) {
                orderedOutputs.clear();
                changed = true;
            }
        } else if (!normalizedOutputs.equals(orderedOutputs)) {
            orderedOutputs.clear();
            orderedOutputs.addAll(normalizedOutputs);
            changed = true;
        }
        changed |= enabledRoutes.removeIf(routeKey -> !validRoutes.contains(routeKey));
        changed |= disabledRoutes.removeIf(routeKey -> !validRoutes.contains(routeKey));
        Iterator<Map.Entry<String, Integer>> routeAmountIterator = routeCraftAmounts.entrySet().iterator();
        while (routeAmountIterator.hasNext()) {
            Map.Entry<String, Integer> entry = routeAmountIterator.next();
            Integer maxCraftAmount = routeMaxCraftAmounts.get(entry.getKey());
            if (maxCraftAmount == null) {
                routeAmountIterator.remove();
                changed = true;
                continue;
            }
            int clampedAmount = clampCraftAmount(entry.getValue(), maxCraftAmount);
            if (clampedAmount == craftAmount) {
                routeAmountIterator.remove();
                changed = true;
            } else if (clampedAmount != entry.getValue()) {
                entry.setValue(clampedAmount);
                changed = true;
            }
        }
        Iterator<Map.Entry<String, List<String>>> iterator = orderedRoutes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<String>> entry = iterator.next();
            List<String> defaultOrder = defaultRoutes.get(entry.getKey());
            if (defaultOrder == null) {
                iterator.remove();
                changed = true;
                continue;
            }
            List<String> normalized = getCurrentOrder(entry.getKey(), defaultOrder);
            if (normalized.equals(defaultOrder)) {
                iterator.remove();
                changed = true;
            } else if (!normalized.equals(entry.getValue())) {
                entry.setValue(normalized);
                changed = true;
            }
        }
        if (changed) {
            version++;
        }
        return changed;
    }

    public List<AEExposedRecipe> applyTo(List<AEExposedRecipe> defaultOrderedRecipes, boolean filterDisabled) {
        Map<String, List<AEExposedRecipe>> grouped = new LinkedHashMap<>();
        for (AEExposedRecipe recipe : defaultOrderedRecipes) {
            String outputKey = recipe.getRecipeKey().getOutputKey();
            grouped.computeIfAbsent(outputKey, key -> new ArrayList<>()).add(recipe);
        }
        int globalCraftAmount = clampCraftAmount(craftAmount, getMaxCraftAmount(defaultOrderedRecipes));
        List<AEExposedRecipe> ordered = new ArrayList<>();
        for (String outputKey : getCurrentProductOrder(grouped.keySet())) {
            List<AEExposedRecipe> group = new ArrayList<>(grouped.get(outputKey));
            group.sort((left, right) -> {
                int leftIndex = getConfiguredIndex(outputKey, left.getRecipeKey().getRouteKey());
                int rightIndex = getConfiguredIndex(outputKey, right.getRecipeKey().getRouteKey());
                if (leftIndex == rightIndex) {
                    return 0;
                } else if (leftIndex == -1) {
                    return 1;
                } else if (rightIndex == -1) {
                    return -1;
                }
                return Integer.compare(leftIndex, rightIndex);
            });
            for (AEExposedRecipe recipe : group) {
                if (!filterDisabled || isRouteEnabled(recipe)) {
                    ordered.add(recipe.withCraftAmount(getEffectiveCraftAmount(recipe, globalCraftAmount)));
                }
            }
        }
        return ordered;
    }

    public List<String> getCurrentProductOrder(Collection<String> defaultOrder) {
        List<String> currentOrder = new ArrayList<>();
        for (String outputKey : orderedOutputs) {
            if (defaultOrder.contains(outputKey) && !currentOrder.contains(outputKey)) {
                currentOrder.add(outputKey);
            }
        }
        for (String outputKey : defaultOrder) {
            if (!currentOrder.contains(outputKey)) {
                currentOrder.add(outputKey);
            }
        }
        return currentOrder;
    }

    public List<String> getCurrentOrder(String outputKey, Collection<String> defaultOrder) {
        List<String> currentOrder = new ArrayList<>();
        List<String> configuredOrder = orderedRoutes.get(outputKey);
        if (configuredOrder != null) {
            for (String routeKey : configuredOrder) {
                if (defaultOrder.contains(routeKey) && !currentOrder.contains(routeKey)) {
                    currentOrder.add(routeKey);
                }
            }
        }
        for (String routeKey : defaultOrder) {
            if (!currentOrder.contains(routeKey)) {
                currentOrder.add(routeKey);
            }
        }
        return currentOrder;
    }

    private int getConfiguredIndex(String outputKey, String routeKey) {
        List<String> routes = orderedRoutes.get(outputKey);
        return routes == null ? -1 : routes.indexOf(routeKey);
    }

    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setString(ROUTE_FILTER_MODE, routeFilterMode.name());
        if (craftAmount != DEFAULT_CRAFT_AMOUNT) {
            tag.setInteger(CRAFT_AMOUNT, craftAmount);
        }
        NBTTagList enabledList = new NBTTagList();
        for (String routeKey : enabledRoutes) {
            enabledList.appendTag(new NBTTagString(routeKey));
        }
        if (enabledList.tagCount() > 0) {
            tag.setTag(ENABLED_ROUTES, enabledList);
        }
        NBTTagList disabledList = new NBTTagList();
        for (String routeKey : disabledRoutes) {
            disabledList.appendTag(new NBTTagString(routeKey));
        }
        if (disabledList.tagCount() > 0) {
            tag.setTag(DISABLED_ROUTES, disabledList);
        }
        NBTTagList routeAmountList = new NBTTagList();
        for (Map.Entry<String, Integer> entry : routeCraftAmounts.entrySet()) {
            NBTTagCompound routeAmountTag = new NBTTagCompound();
            routeAmountTag.setString(ROUTE_KEY, entry.getKey());
            routeAmountTag.setInteger(AMOUNT, entry.getValue());
            routeAmountList.appendTag(routeAmountTag);
        }
        if (routeAmountList.tagCount() > 0) {
            tag.setTag(ROUTE_CRAFT_AMOUNTS, routeAmountList);
        }
        NBTTagList outputList = new NBTTagList();
        for (String outputKey : orderedOutputs) {
            outputList.appendTag(new NBTTagString(outputKey));
        }
        if (outputList.tagCount() > 0) {
            tag.setTag(ORDERED_OUTPUTS, outputList);
        }
        NBTTagList orderedList = new NBTTagList();
        for (Map.Entry<String, List<String>> entry : orderedRoutes.entrySet()) {
            NBTTagCompound orderTag = new NBTTagCompound();
            orderTag.setString(OUTPUT_KEY, entry.getKey());
            NBTTagList routeList = new NBTTagList();
            for (String routeKey : entry.getValue()) {
                routeList.appendTag(new NBTTagString(routeKey));
            }
            orderTag.setTag(ROUTES, routeList);
            orderedList.appendTag(orderTag);
        }
        if (orderedList.tagCount() > 0) {
            tag.setTag(ORDERED_PRODUCTS, orderedList);
        }
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        routeFilterMode = tag.hasKey(ROUTE_FILTER_MODE, NBT.TAG_STRING) ? RouteFilterMode.fromName(tag.getString(ROUTE_FILTER_MODE)) : defaultRouteFilterMode;
        enabledRoutes.clear();
        disabledRoutes.clear();
        orderedRoutes.clear();
        orderedOutputs.clear();
        routeCraftAmounts.clear();
        craftAmount = tag.hasKey(CRAFT_AMOUNT, NBT.TAG_INT) ? clampCraftAmount(tag.getInteger(CRAFT_AMOUNT)) : DEFAULT_CRAFT_AMOUNT;
        NBTTagList enabledList = tag.getTagList(ENABLED_ROUTES, NBT.TAG_STRING);
        for (int i = 0; i < enabledList.tagCount(); i++) {
            enabledRoutes.add(enabledList.getStringTagAt(i));
        }
        NBTTagList disabledList = tag.getTagList(DISABLED_ROUTES, NBT.TAG_STRING);
        for (int i = 0; i < disabledList.tagCount(); i++) {
            disabledRoutes.add(disabledList.getStringTagAt(i));
        }
        NBTTagList routeAmountList = tag.getTagList(ROUTE_CRAFT_AMOUNTS, NBT.TAG_COMPOUND);
        for (int i = 0; i < routeAmountList.tagCount(); i++) {
            NBTTagCompound routeAmountTag = routeAmountList.getCompoundTagAt(i);
            String routeKey = routeAmountTag.getString(ROUTE_KEY);
            if (!routeKey.isEmpty() && routeAmountTag.hasKey(AMOUNT, NBT.TAG_INT)) {
                int amount = clampCraftAmount(routeAmountTag.getInteger(AMOUNT));
                if (amount != craftAmount) {
                    routeCraftAmounts.put(routeKey, amount);
                }
            }
        }
        NBTTagList orderedList = tag.getTagList(ORDERED_PRODUCTS, NBT.TAG_COMPOUND);
        for (int i = 0; i < orderedList.tagCount(); i++) {
            NBTTagCompound orderTag = orderedList.getCompoundTagAt(i);
            String outputKey = orderTag.getString(OUTPUT_KEY);
            if (outputKey.isEmpty()) {
                continue;
            }
            List<String> routes = new ArrayList<>();
            NBTTagList routeList = orderTag.getTagList(ROUTES, NBT.TAG_STRING);
            for (int routeIndex = 0; routeIndex < routeList.tagCount(); routeIndex++) {
                routes.add(routeList.getStringTagAt(routeIndex));
            }
            if (!routes.isEmpty()) {
                orderedRoutes.put(outputKey, routes);
            }
        }
        NBTTagList outputList = tag.getTagList(ORDERED_OUTPUTS, NBT.TAG_STRING);
        for (int i = 0; i < outputList.tagCount(); i++) {
            String outputKey = outputList.getStringTagAt(i);
            if (!outputKey.isEmpty() && !orderedOutputs.contains(outputKey)) {
                orderedOutputs.add(outputKey);
            }
        }
        version++;
    }

    public AERecipeProfile copy() {
        AERecipeProfile copy = new AERecipeProfile(defaultRouteFilterMode);
        copy.routeFilterMode = routeFilterMode;
        copy.craftAmount = craftAmount;
        copy.enabledRoutes.addAll(enabledRoutes);
        copy.disabledRoutes.addAll(disabledRoutes);
        copy.routeCraftAmounts.putAll(routeCraftAmounts);
        copy.orderedOutputs.addAll(orderedOutputs);
        for (Map.Entry<String, List<String>> entry : orderedRoutes.entrySet()) {
            copy.orderedRoutes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        copy.version = version;
        return copy;
    }

    public boolean replaceWith(AERecipeProfile other) {
        if (routeFilterMode == other.routeFilterMode && craftAmount == other.craftAmount && Objects.equals(enabledRoutes, other.enabledRoutes) &&
            Objects.equals(disabledRoutes, other.disabledRoutes) && Objects.equals(routeCraftAmounts, other.routeCraftAmounts) &&
            Objects.equals(orderedOutputs, other.orderedOutputs) && Objects.equals(orderedRoutes, other.orderedRoutes)) {
            return false;
        }
        routeFilterMode = other.routeFilterMode;
        craftAmount = other.craftAmount;
        enabledRoutes.clear();
        enabledRoutes.addAll(other.enabledRoutes);
        disabledRoutes.clear();
        disabledRoutes.addAll(other.disabledRoutes);
        routeCraftAmounts.clear();
        routeCraftAmounts.putAll(other.routeCraftAmounts);
        orderedOutputs.clear();
        orderedOutputs.addAll(other.orderedOutputs);
        orderedRoutes.clear();
        for (Map.Entry<String, List<String>> entry : other.orderedRoutes.entrySet()) {
            orderedRoutes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        version++;
        return true;
    }

    public static AERecipeProfile read(NBTTagCompound tag) {
        return read(tag, RouteFilterMode.BLACKLIST);
    }

    public static AERecipeProfile read(NBTTagCompound tag, RouteFilterMode defaultRouteFilterMode) {
        AERecipeProfile profile = new AERecipeProfile(defaultRouteFilterMode);
        profile.readFromNBT(tag);
        return profile;
    }

    public enum RouteFilterMode {
        BLACKLIST,
        WHITELIST;

        public RouteFilterMode next() {
            return this == BLACKLIST ? WHITELIST : BLACKLIST;
        }

        public static RouteFilterMode fromName(String name) {
            if (name == null || name.isEmpty()) {
                return BLACKLIST;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                MEKCeuAEUpgrade.logger.warn("Unknown AE recipe filter mode {}, falling back to blacklist.", name);
                return BLACKLIST;
            }
        }
    }
}
