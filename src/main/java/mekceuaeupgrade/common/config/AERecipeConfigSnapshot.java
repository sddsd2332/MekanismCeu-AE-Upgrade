package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AERecipeConfigSnapshot {

    private static final String PRODUCTS = "products";
    private static final String OUTPUT_KEY = "outputKey";
    private static final String OUTPUTS = "outputs";
    private static final String ROUTES = "routes";
    private static final String ROUTE_KEY = "routeKey";
    private static final String INPUTS = "inputs";
    private static final String ENABLED = "enabled";
    private static final String ORDER = "order";
    private static final String MULTIPLE = "multiple";
    private static final String INDIVIDUAL_PROFILE = "individualProfile";
    private static final String GLOBAL_PROFILE_SLOT = "globalProfileSlot";
    private static final String ROUTE_FILTER_MODE = "routeFilterMode";
    private static final String CRAFT_AMOUNT = "craftAmount";
    private static final String ROUTE_CRAFT_AMOUNT = "routeCraftAmount";
    private static final String ROUTE_CRAFT_AMOUNT_OVERRIDE = "routeCraftAmountOverride";

    public static final AERecipeConfigSnapshot EMPTY = new AERecipeConfigSnapshot(Collections.emptyList(), false, 1, AERecipeProfile.DEFAULT_CRAFT_AMOUNT,
          AERecipeProfile.RouteFilterMode.BLACKLIST);

    private final List<Product> products;
    private final boolean individualProfile;
    private final int globalProfileSlot;
    private final AERecipeProfile.RouteFilterMode routeFilterMode;
    private final int craftAmount;

    public AERecipeConfigSnapshot(List<Product> products) {
        this(products, false, 1, AERecipeProfile.DEFAULT_CRAFT_AMOUNT);
    }

    public AERecipeConfigSnapshot(List<Product> products, boolean individualProfile) {
        this(products, individualProfile, 1, AERecipeProfile.DEFAULT_CRAFT_AMOUNT);
    }

    public AERecipeConfigSnapshot(List<Product> products, boolean individualProfile, int craftAmount) {
        this(products, individualProfile, 1, craftAmount);
    }

    public AERecipeConfigSnapshot(List<Product> products, boolean individualProfile, int globalProfileSlot, int craftAmount) {
        this(products, individualProfile, globalProfileSlot, craftAmount, AERecipeProfile.RouteFilterMode.BLACKLIST);
    }

    public AERecipeConfigSnapshot(List<Product> products, boolean individualProfile, int globalProfileSlot, int craftAmount,
          AERecipeProfile.RouteFilterMode routeFilterMode) {
        this.products = Collections.unmodifiableList(new ArrayList<>(products));
        this.individualProfile = individualProfile;
        this.globalProfileSlot = Math.max(1, Math.min(10, globalProfileSlot));
        this.routeFilterMode = routeFilterMode == null ? AERecipeProfile.RouteFilterMode.BLACKLIST : routeFilterMode;
        this.craftAmount = AERecipeProfile.clampCraftAmount(craftAmount, getMaxCraftAmount());
    }

    public List<Product> getProducts() {
        return products;
    }

    public boolean isEmpty() {
        return products.isEmpty();
    }

    public boolean isIndividualProfile() {
        return individualProfile;
    }

    public int getGlobalProfileSlot() {
        return globalProfileSlot;
    }

    public AERecipeProfile.RouteFilterMode getRouteFilterMode() {
        return routeFilterMode;
    }

    public int getCraftAmount() {
        return craftAmount;
    }

    public int getMaxCraftAmount() {
        int maxCraftAmount = AERecipeProfile.MAX_CRAFT_AMOUNT;
        for (Product product : products) {
            maxCraftAmount = Math.min(maxCraftAmount, product.getMaxCraftAmount());
        }
        return maxCraftAmount;
    }

    public NBTTagCompound write(NBTTagCompound tag) {
        tag.setBoolean(INDIVIDUAL_PROFILE, individualProfile);
        tag.setInteger(GLOBAL_PROFILE_SLOT, globalProfileSlot);
        tag.setString(ROUTE_FILTER_MODE, routeFilterMode.name());
        tag.setInteger(CRAFT_AMOUNT, craftAmount);
        NBTTagList productList = new NBTTagList();
        for (Product product : products) {
            NBTTagCompound productTag = new NBTTagCompound();
            productTag.setString(OUTPUT_KEY, product.getOutputKey());
            productTag.setTag(OUTPUTS, writeStacks(product.getOutputStacks()));
            productTag.setBoolean(MULTIPLE, product.hasMultipleRoutes());
            NBTTagList routeList = new NBTTagList();
            for (Route route : product.getRoutes()) {
                NBTTagCompound routeTag = new NBTTagCompound();
                routeTag.setString(ROUTE_KEY, route.getRouteKey());
                NBTTagList inputList = new NBTTagList();
                for (ItemStack input : route.getInputStacks()) {
                    inputList.appendTag(AERecipeStackNBT.write(input));
                }
                routeTag.setTag(INPUTS, inputList);
                routeTag.setTag(OUTPUTS, writeStacks(route.getOutputStacks()));
                routeTag.setBoolean(ENABLED, route.isEnabled());
                routeTag.setInteger(ORDER, route.getOrder());
                routeTag.setInteger(ROUTE_CRAFT_AMOUNT, route.getCraftAmount());
                routeTag.setBoolean(ROUTE_CRAFT_AMOUNT_OVERRIDE, route.hasCraftAmountOverride());
                routeList.appendTag(routeTag);
            }
            productTag.setTag(ROUTES, routeList);
            productList.appendTag(productTag);
        }
        tag.setTag(PRODUCTS, productList);
        return tag;
    }

    public static AERecipeConfigSnapshot read(NBTTagCompound tag) {
        NBTTagList productList = tag.getTagList(PRODUCTS, NBT.TAG_COMPOUND);
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < productList.tagCount(); i++) {
            NBTTagCompound productTag = productList.getCompoundTagAt(i);
            String outputKey = productTag.getString(OUTPUT_KEY);
            if (outputKey.isEmpty()) {
                continue;
            }
            List<ItemStack> outputs = readStacks(productTag, OUTPUTS);
            if (outputs.isEmpty()) {
                continue;
            }
            boolean multiple = productTag.getBoolean(MULTIPLE);
            NBTTagList routeList = productTag.getTagList(ROUTES, NBT.TAG_COMPOUND);
            List<Route> routes = new ArrayList<>();
            for (int routeIndex = 0; routeIndex < routeList.tagCount(); routeIndex++) {
                NBTTagCompound routeTag = routeList.getCompoundTagAt(routeIndex);
                String routeKey = routeTag.getString(ROUTE_KEY);
                if (routeKey.isEmpty()) {
                    continue;
                }
                List<ItemStack> inputs = new ArrayList<>();
                NBTTagList inputList = routeTag.getTagList(INPUTS, NBT.TAG_COMPOUND);
                for (int inputIndex = 0; inputIndex < inputList.tagCount(); inputIndex++) {
                    addStackIfValid(inputs, AERecipeStackNBT.read(inputList.getCompoundTagAt(inputIndex)));
                }
                List<ItemStack> routeOutputs = readStacks(routeTag, OUTPUTS);
                if (inputs.isEmpty() || routeOutputs.isEmpty()) {
                    continue;
                }
                routes.add(new Route(
                      routeKey,
                      inputs,
                      routeOutputs,
                      routeTag.getBoolean(ENABLED),
                      routeTag.getInteger(ORDER),
                      routeTag.hasKey(ROUTE_CRAFT_AMOUNT, NBT.TAG_INT) ? routeTag.getInteger(ROUTE_CRAFT_AMOUNT) : AERecipeProfile.DEFAULT_CRAFT_AMOUNT,
                      routeTag.getBoolean(ROUTE_CRAFT_AMOUNT_OVERRIDE)
                ));
            }
            if (routes.isEmpty()) {
                continue;
            }
            products.add(new Product(outputKey, outputs, multiple, routes));
        }
        boolean individualProfile = tag.getBoolean(INDIVIDUAL_PROFILE);
        int globalProfileSlot = tag.hasKey(GLOBAL_PROFILE_SLOT, NBT.TAG_INT) ? tag.getInteger(GLOBAL_PROFILE_SLOT) : 1;
        AERecipeProfile.RouteFilterMode routeFilterMode = AERecipeProfile.RouteFilterMode.fromName(tag.getString(ROUTE_FILTER_MODE));
        int craftAmount = tag.hasKey(CRAFT_AMOUNT, NBT.TAG_INT) ? tag.getInteger(CRAFT_AMOUNT) : AERecipeProfile.DEFAULT_CRAFT_AMOUNT;
        return products.isEmpty() && !individualProfile && globalProfileSlot == 1 && routeFilterMode == AERecipeProfile.RouteFilterMode.BLACKLIST &&
               craftAmount == AERecipeProfile.DEFAULT_CRAFT_AMOUNT ? EMPTY :
              new AERecipeConfigSnapshot(products, individualProfile, globalProfileSlot, craftAmount, routeFilterMode);
    }

    private static NBTTagList writeStacks(List<ItemStack> stacks) {
        NBTTagList list = new NBTTagList();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                list.appendTag(AERecipeStackNBT.write(stack));
            }
        }
        return list;
    }

    private static List<ItemStack> readStacks(NBTTagCompound tag, String key) {
        NBTTagList list = tag.getTagList(key, NBT.TAG_COMPOUND);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            addStackIfValid(stacks, AERecipeStackNBT.read(list.getCompoundTagAt(i)));
        }
        return stacks;
    }

    private static void addStackIfValid(List<ItemStack> stacks, ItemStack stack) {
        if (!stack.isEmpty()) {
            stacks.add(stack);
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copied = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copied.add(stack.copy());
            }
        }
        return copied;
    }

    public static class Product {

        private final String outputKey;
        private final ItemStack outputStack;
        private final List<ItemStack> outputStacks;
        private final boolean multipleRoutes;
        private final List<Route> routes;

        public Product(String outputKey, ItemStack outputStack, boolean multipleRoutes, List<Route> routes) {
            this(outputKey, Collections.singletonList(outputStack), multipleRoutes, routes);
        }

        public Product(String outputKey, List<ItemStack> outputStacks, boolean multipleRoutes, List<Route> routes) {
            this.outputKey = outputKey;
            this.outputStacks = Collections.unmodifiableList(copyStacks(outputStacks));
            this.outputStack = this.outputStacks.isEmpty() ? ItemStack.EMPTY : this.outputStacks.get(0);
            this.multipleRoutes = multipleRoutes;
            this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        }

        public String getOutputKey() {
            return outputKey;
        }

        public ItemStack getOutputStack() {
            return outputStack.copy();
        }

        public List<ItemStack> getOutputStacks() {
            return copyStacks(outputStacks);
        }

        public boolean hasMultipleRoutes() {
            return multipleRoutes;
        }

        public List<Route> getRoutes() {
            return routes;
        }

        public int getMaxCraftAmount() {
            int maxCraftAmount = AERecipeProfile.MAX_CRAFT_AMOUNT;
            for (Route route : routes) {
                maxCraftAmount = Math.min(maxCraftAmount, route.getMaxCraftAmount());
            }
            return maxCraftAmount;
        }
    }

    public static class Route {

        private final String routeKey;
        private final List<ItemStack> inputStacks;
        private final ItemStack outputStack;
        private final List<ItemStack> outputStacks;
        private final boolean enabled;
        private final int order;
        private final int craftAmount;
        private final boolean craftAmountOverride;

        public Route(String routeKey, ItemStack inputStack, ItemStack outputStack, boolean enabled, int order) {
            this(routeKey, Collections.singletonList(inputStack), outputStack, enabled, order);
        }

        public Route(String routeKey, List<ItemStack> inputStacks, ItemStack outputStack, boolean enabled, int order) {
            this(routeKey, inputStacks, Collections.singletonList(outputStack), enabled, order);
        }

        public Route(String routeKey, List<ItemStack> inputStacks, List<ItemStack> outputStacks, boolean enabled, int order) {
            this(routeKey, inputStacks, outputStacks, enabled, order, AERecipeProfile.DEFAULT_CRAFT_AMOUNT, false);
        }

        public Route(String routeKey, List<ItemStack> inputStacks, List<ItemStack> outputStacks, boolean enabled, int order, int craftAmount,
              boolean craftAmountOverride) {
            this.routeKey = routeKey;
            this.inputStacks = Collections.unmodifiableList(copyStacks(inputStacks));
            this.outputStacks = Collections.unmodifiableList(copyStacks(outputStacks));
            this.outputStack = this.outputStacks.isEmpty() ? ItemStack.EMPTY : this.outputStacks.get(0);
            this.enabled = enabled;
            this.order = order;
            this.craftAmount = AERecipeProfile.clampCraftAmount(craftAmount, getMaxCraftAmount());
            this.craftAmountOverride = craftAmountOverride;
        }

        public String getRouteKey() {
            return routeKey;
        }

        public ItemStack getInputStack() {
            return inputStacks.isEmpty() ? ItemStack.EMPTY : inputStacks.get(0).copy();
        }

        public List<ItemStack> getInputStacks() {
            List<ItemStack> copied = new ArrayList<>(inputStacks.size());
            for (ItemStack inputStack : inputStacks) {
                copied.add(inputStack.copy());
            }
            return copied;
        }

        public ItemStack getOutputStack() {
            return outputStack.copy();
        }

        public List<ItemStack> getOutputStacks() {
            return copyStacks(outputStacks);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getOrder() {
            return order;
        }

        public int getCraftAmount() {
            return craftAmount;
        }

        public boolean hasCraftAmountOverride() {
            return craftAmountOverride;
        }

        public int getMaxCraftAmount() {
            return AERecipeProfile.getMaxCraftAmount(inputStacks, outputStacks);
        }
    }
}
