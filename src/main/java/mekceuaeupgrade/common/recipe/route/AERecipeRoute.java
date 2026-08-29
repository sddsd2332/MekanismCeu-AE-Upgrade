package mekceuaeupgrade.common.recipe.route;

import com.github.bsideup.jabel.Desugar;
import mekanism.api.gas.GasStack;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekceuaeupgrade.common.config.AEItemStackKey;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 一条可暴露给 AE 的 Mekanism 配方路线。
 *
 * <p>route 保存 typed 输入和 typed 输出，并在暴露到 AE 1.12 时转换成旧的物品 pattern 表达。
 * routeId、输入输出顺序、端口 ID 和栈内容会一起参与 route key 计算，用于区分同一产物下的不同路线。</p>
 *
 * @param routeId 路线类型 ID，例如 item_gas_to_item 或 gas_fluid_to_gas
 * @param recipeKey Provider 分配的物理路线键
 * @param logicalRecipeKey factory lane 之间共享的逻辑配方键
 * @param configurationInputs 机器保留且不按批量消耗的配置输入
 * @param inputs 该路线需要的 typed 输入
 * @param guaranteedOutputs 该路线保证产出的 typed 输出
 * @param optionalOutputs 该路线可能产出的 typed 输出
 */
@Desugar
public record AERecipeRoute(
      String routeId,
      String recipeKey,
      String logicalRecipeKey,
      List<AERecipeRouteStack> configurationInputs,
      List<AERecipeRouteStack> inputs,
      List<AERecipeRouteStack> guaranteedOutputs,
      List<AERecipeRouteStack> optionalOutputs) {

    /**
     * 保留旧 adapter 使用的最小构造入口。
     */
    public AERecipeRoute(String routeId, List<AERecipeRouteStack> inputs, List<AERecipeRouteStack> outputs) {
        this(routeId, "", "", Collections.emptyList(), inputs, outputs, Collections.emptyList());
    }

    /**
     * 规范化 route 数据，并为输入输出栈补上稳定顺序。
     */
    public AERecipeRoute {
        routeId = routeId == null ? "" : routeId;
        recipeKey = recipeKey == null ? "" : recipeKey;
        logicalRecipeKey = logicalRecipeKey == null || logicalRecipeKey.isEmpty() ? recipeKey : logicalRecipeKey;
        configurationInputs = copyWithOrder(configurationInputs);
        inputs = copyWithOrder(inputs);
        guaranteedOutputs = copyWithOrder(guaranteedOutputs);
        optionalOutputs = copyWithOrder(optionalOutputs);
    }

    /**
     * 旧调用方所说的 outputs 始终是 AE 能承诺给合成规划器的 guaranteed outputs。
     */
    public List<AERecipeRouteStack> outputs() {
        return guaranteedOutputs;
    }

    /**
     * 创建 route 构建器。
     *
     * @param routeId 路线类型 ID
     * @return 新的构建器
     */
    public static Builder builder(String routeId) {
        return new Builder(routeId);
    }

    @Nullable
    public MachineRecipeRoute toMachineRecipeRoute() {
        try {
            MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder(routeId);
            if (!recipeKey.isEmpty()) {
                builder.recipeKey(recipeKey);
            }
            if (!logicalRecipeKey.isEmpty()) {
                builder.logicalRecipeKey(logicalRecipeKey);
            }
            for (AERecipeRouteStack input : configurationInputs) {
                MachineResourceStack converted = input.toMachineResourceStack();
                if (converted == null) {
                    return null;
                }
                builder.configurationInput(converted);
            }
            for (AERecipeRouteStack input : inputs) {
                MachineResourceStack converted = input.toMachineResourceStack();
                if (converted == null) {
                    return null;
                }
                builder.input(converted);
            }
            for (AERecipeRouteStack output : guaranteedOutputs) {
                MachineResourceStack converted = output.toMachineResourceStack();
                if (converted == null) {
                    return null;
                }
                builder.output(converted);
            }
            for (AERecipeRouteStack output : optionalOutputs) {
                MachineResourceStack converted = output.toMachineResourceStack();
                if (converted == null) {
                    return null;
                }
                builder.optionalOutput(converted);
            }
            return builder.build();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    public static AERecipeRoute fromMachineRecipeRoute(@Nullable MachineRecipeRoute route) {
        if (route == null) {
            return null;
        }
        List<AERecipeRouteStack> configurationInputs = fromMachineStacks(route.configurationInputs());
        List<AERecipeRouteStack> inputs = fromMachineStacks(route.inputs());
        List<AERecipeRouteStack> guaranteedOutputs = fromMachineStacks(route.guaranteedOutputs());
        List<AERecipeRouteStack> optionalOutputs = fromMachineStacks(route.optionalOutputs());
        if (configurationInputs == null || inputs == null || guaranteedOutputs == null || optionalOutputs == null) {
            return null;
        }
        return new AERecipeRoute(route.routeId(), route.recipeKey(), route.logicalRecipeKey(), configurationInputs,
              inputs, guaranteedOutputs, optionalOutputs);
    }

    @Nullable
    private static List<AERecipeRouteStack> fromMachineStacks(List<MachineResourceStack> stacks) {
        List<AERecipeRouteStack> convertedStacks = new ArrayList<>(stacks.size());
        for (MachineResourceStack stack : stacks) {
            AERecipeRouteStack converted = AERecipeRouteStack.fromMachineResourceStack(stack);
            if (converted == null) {
                return null;
            }
            convertedStacks.add(converted);
        }
        return convertedStacks;
    }

    public AERecipeRoute withRouteId(String mappedRouteId) {
        return new AERecipeRoute(mappedRouteId, recipeKey, logicalRecipeKey, configurationInputs, inputs,
              guaranteedOutputs, optionalOutputs);
    }

    /**
     * 比较 Provider 当前返回的完整路线，防止旧 pattern 被绑定到同名但内容已变化的 route。
     */
    public boolean matchesMachineRecipeRoute(@Nullable MachineRecipeRoute route) {
        if (route == null || !routeId.equals(route.routeId()) || !recipeKey.equals(route.recipeKey()) ||
            !logicalRecipeKey.equals(route.logicalRecipeKey())) {
            return false;
        }
        MachineRecipeRoute converted = toMachineRecipeRoute();
        return converted != null && converted.configurationInputs().equals(route.configurationInputs()) &&
              converted.inputs().equals(route.inputs()) &&
              converted.guaranteedOutputs().equals(route.guaranteedOutputs()) &&
              converted.optionalOutputs().equals(route.optionalOutputs());
    }

    /**
     * 转换成 AE 1.12 可注册的旧物品配方。
     *
     * @return 转换成功时返回暴露配方，否则返回 null
     */
    @Nullable
    public AEExposedRecipe toLegacyRecipe() {
        List<ItemStack> legacyInputs = toLegacyStacks(inputs, 1);
        List<ItemStack> legacyOutputs = toLegacyStacks(guaranteedOutputs, 1);
        if (legacyInputs == null || legacyOutputs == null || legacyInputs.isEmpty() || legacyOutputs.isEmpty()) {
            return null;
        }
        return new AEExposedRecipe(legacyInputs, legacyOutputs, this, getRouteDiscriminator());
    }

    /**
     * @return 该 route 在不溢出的前提下允许的最大批量倍数
     */
    public int getMaxCraftAmount() {
        int max = Integer.MAX_VALUE;
        for (AERecipeRouteStack stack : inputs) {
            max = Math.min(max, stack.getMaxCraftAmount());
        }
        for (AERecipeRouteStack stack : guaranteedOutputs) {
            max = Math.min(max, stack.getMaxCraftAmount());
        }
        for (AERecipeRouteStack stack : optionalOutputs) {
            max = Math.min(max, stack.getMaxCraftAmount());
        }
        return Math.max(1, max);
    }

    /**
     * 按批量倍数生成 AE 旧物品输入栈。
     *
     * @param craftAmount 批量倍数
     * @return 旧物品输入栈，无法转换时返回 null
     */
    @Nullable
    public List<ItemStack> toLegacyInputStacks(int craftAmount) {
        return toLegacyStacks(inputs, Math.max(1, Math.min(craftAmount, getMaxCraftAmount())));
    }

    /**
     * 按批量倍数生成 AE 旧物品输出栈。
     *
     * @param craftAmount 批量倍数
     * @return 旧物品输出栈，无法转换时返回 null
     */
    @Nullable
    public List<ItemStack> toLegacyOutputStacks(int craftAmount) {
        return toLegacyStacks(guaranteedOutputs, Math.max(1, Math.min(craftAmount, getMaxCraftAmount())));
    }

    /**
     * 批量转换 route。
     *
     * @param routes 待暴露的 route 集合
     * @return 可注册到 AE 的旧物品配方列表
     */
    public static List<AEExposedRecipe> toLegacyRecipes(Collection<AERecipeRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<AEExposedRecipe> exposed = new ArrayList<>(routes.size());
        for (AERecipeRoute route : routes) {
            AEExposedRecipe recipe = route.toLegacyRecipe();
            if (recipe != null) {
                exposed.add(recipe);
            }
        }
        return exposed;
    }

    /**
     * 将 typed 栈列表转换成 AE 旧物品栈列表。
     *
     * @param routeStacks typed route 栈列表
     * @return 全部可转换时返回旧物品栈列表，否则返回 null
     */
    @Nullable
    private static List<ItemStack> toLegacyStacks(List<AERecipeRouteStack> routeStacks, int craftAmount) {
        List<ItemStack> stacks = new ArrayList<>(routeStacks.size());
        for (AERecipeRouteStack routeStack : routeStacks) {
            ItemStack stack = routeStack.toLegacyStack(craftAmount);
            if (stack.isEmpty()) {
                return null;
            }
            stacks.add(stack);
        }
        return stacks;
    }

    /**
     * 复制 route 栈列表，并用列表下标写入顺序。
     *
     * @param routeStacks 原始 route 栈列表
     * @return 不可变 route 栈列表
     */
    private static List<AERecipeRouteStack> copyWithOrder(List<AERecipeRouteStack> routeStacks) {
        if (routeStacks == null || routeStacks.isEmpty()) {
            return Collections.emptyList();
        }
        List<AERecipeRouteStack> copy = new ArrayList<>(routeStacks.size());
        for (int i = 0; i < routeStacks.size(); i++) {
            copy.add(routeStacks.get(i).withOrder(i));
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * @return 用于区分同类配方不同 route 的稳定摘要
     */
    private String getRouteDiscriminator() {
        StringBuilder builder = new StringBuilder(routeId)
              .append("|recipe=").append(recipeKey)
              .append("|logical=").append(logicalRecipeKey);
        appendStacks(builder.append("|configuration"), configurationInputs);
        appendStacks(builder.append("|in"), inputs);
        appendStacks(builder.append("|out"), guaranteedOutputs);
        appendStacks(builder.append("|optional"), optionalOutputs);
        return hash(builder.toString());
    }

    /**
     * 将 route 栈身份写入摘要构造器。
     *
     * @param builder 摘要文本构造器
     * @param stacks 需要写入的 route 栈列表
     */
    private static void appendStacks(StringBuilder builder, List<AERecipeRouteStack> stacks) {
        for (AERecipeRouteStack stack : stacks) {
            builder.append('|')
                  .append(stack.order())
                  .append(':')
                  .append(stack.kind())
                  .append(':')
                  .append(stack.portId());
            appendStackIdentity(builder, stack);
        }
    }

    /**
     * 写入单个 route 栈的身份信息。
     *
     * @param builder 摘要文本构造器
     * @param stack route 栈
     */
    private static void appendStackIdentity(StringBuilder builder, AERecipeRouteStack stack) {
        switch (stack.kind()) {
            case ITEM -> appendItemIdentity(builder.append(":item="), stack.itemStack());
            case GAS -> {
                GasStack gas = stack.gasStack();
                builder.append(":gas=");
                if (gas != null && gas.getGas() != null) {
                    builder.append(gas.getGas().getName()).append('@').append(gas.amount);
                }
                if (!stack.legacyItemStack().isEmpty()) {
                    appendItemIdentity(builder.append(":legacy="), stack.legacyItemStack());
                }
            }
            case FLUID -> {
                FluidStack fluid = stack.fluidStack();
                builder.append(":fluid=");
                if (fluid != null && fluid.getFluid() != null) {
                    builder.append(fluid.getFluid().getName()).append('@').append(fluid.amount);
                    if (fluid.tag != null) {
                        builder.append(":tag=").append(hash(fluid.tag.toString()));
                    }
                }
            }
        }
    }

    /**
     * 写入物品栈身份。
     *
     * @param builder 摘要文本构造器
     * @param stack 物品栈
     */
    private static void appendItemIdentity(StringBuilder builder, ItemStack stack) {
        if (!stack.isEmpty()) {
            builder.append(AEItemStackKey.fromStack(stack).getEncoded());
        }
    }

    /**
     * 计算稳定短摘要。
     *
     * @param value 原始身份文本
     * @return Base64URL 编码后的 SHA-256 摘要
     */
    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to encode AE recipe route.", e);
        }
    }

    /**
     * AE 配方 route 构建器。
     */
    public static final class Builder {

        private final String routeId;
        private String recipeKey = "";
        private String logicalRecipeKey = "";
        private final List<AERecipeRouteStack> configurationInputs = new ArrayList<>();
        private final List<AERecipeRouteStack> inputs = new ArrayList<>();
        private final List<AERecipeRouteStack> outputs = new ArrayList<>();
        private final List<AERecipeRouteStack> optionalOutputs = new ArrayList<>();

        /**
         * @param routeId 路线类型 ID
         */
        private Builder(String routeId) {
            this.routeId = routeId;
        }

        public Builder recipeKey(String recipeKey) {
            this.recipeKey = recipeKey;
            return this;
        }

        public Builder logicalRecipeKey(String logicalRecipeKey) {
            this.logicalRecipeKey = logicalRecipeKey;
            return this;
        }

        public Builder configurationItem(String portId, ItemStack stack) {
            configurationInputs.add(AERecipeRouteStack.item(portId, stack));
            return this;
        }

        public Builder configurationGas(String portId, GasStack stack) {
            configurationInputs.add(AERecipeRouteStack.gas(portId, stack));
            return this;
        }

        public Builder configurationFluid(String portId, FluidStack stack) {
            configurationInputs.add(AERecipeRouteStack.fluid(portId, stack));
            return this;
        }

        /**
         * 追加物品输入。
         *
         * @param portId 绑定机器输入端口的 ID
         * @param stack 真实物品输入
         * @return 当前构建器
         */
        public Builder inputItem(String portId, ItemStack stack) {
            inputs.add(AERecipeRouteStack.item(portId, stack));
            return this;
        }

        /**
         * 追加气体输入。
         *
         * @param portId 绑定机器输入端口的 ID
         * @param stack 真实气体输入
         * @return 当前构建器
         */
        public Builder inputGas(String portId, GasStack stack) {
            inputs.add(AERecipeRouteStack.gas(portId, stack));
            return this;
        }

        /**
         * 追加气体输入，并指定 AE pattern 中使用的旧物品代表。
         *
         * @param portId 绑定机器输入端口的 ID
         * @param stack 真实气体输入
         * @param legacyItemStack AE 1.12 pattern 中代表该气体输入的物品
         * @return 当前构建器
         */
        public Builder inputGas(String portId, GasStack stack, ItemStack legacyItemStack) {
            inputs.add(AERecipeRouteStack.gas(portId, stack, legacyItemStack));
            return this;
        }

        /**
         * 追加流体输入。
         *
         * @param portId 绑定机器输入端口的 ID
         * @param stack 真实流体输入
         * @return 当前构建器
         */
        public Builder inputFluid(String portId, FluidStack stack) {
            inputs.add(AERecipeRouteStack.fluid(portId, stack));
            return this;
        }

        /**
         * 追加物品输出。
         *
         * @param portId 绑定机器输出端口的 ID
         * @param stack 真实物品输出
         * @return 当前构建器
         */
        public Builder outputItem(String portId, ItemStack stack) {
            outputs.add(AERecipeRouteStack.item(portId, stack));
            return this;
        }

        /**
         * 追加气体输出。
         *
         * @param portId 绑定机器输出端口的 ID
         * @param stack 真实气体输出
         * @return 当前构建器
         */
        public Builder outputGas(String portId, GasStack stack) {
            outputs.add(AERecipeRouteStack.gas(portId, stack));
            return this;
        }

        /**
         * 追加流体输出。
         *
         * @param portId 绑定机器输出端口的 ID
         * @param stack 真实流体输出
         * @return 当前构建器
         */
        public Builder outputFluid(String portId, FluidStack stack) {
            outputs.add(AERecipeRouteStack.fluid(portId, stack));
            return this;
        }

        public Builder optionalOutputItem(String portId, ItemStack stack) {
            optionalOutputs.add(AERecipeRouteStack.item(portId, stack));
            return this;
        }

        public Builder optionalOutputGas(String portId, GasStack stack) {
            optionalOutputs.add(AERecipeRouteStack.gas(portId, stack));
            return this;
        }

        public Builder optionalOutputFluid(String portId, FluidStack stack) {
            optionalOutputs.add(AERecipeRouteStack.fluid(portId, stack));
            return this;
        }

        /**
         * @return 构建完成的 route
         */
        public AERecipeRoute build() {
            return new AERecipeRoute(routeId, recipeKey, logicalRecipeKey, configurationInputs, inputs, outputs,
                  optionalOutputs);
        }
    }
}
