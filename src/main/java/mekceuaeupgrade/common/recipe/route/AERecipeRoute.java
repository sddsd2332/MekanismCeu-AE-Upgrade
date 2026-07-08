package mekceuaeupgrade.common.recipe.route;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import com.github.bsideup.jabel.Desugar;
import mekanism.api.gas.GasStack;
import mekceuaeupgrade.common.config.AEItemStackKey;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 一条可暴露给 AE 的 Mekanism 配方路线。
 *
 * <p>route 保存 typed 输入和 typed 输出，并在暴露到 AE 1.12 时转换成旧的物品 pattern 表达。
 * routeId、输入输出顺序、端口 ID 和栈内容会一起参与 route key 计算，用于区分同一产物下的不同路线。</p>
 *
 * @param routeId 路线类型 ID，例如 item_gas_to_item 或 gas_fluid_to_gas
 * @param inputs 该路线需要的 typed 输入
 * @param outputs 该路线产出的 typed 输出
 */
@Desugar
public record AERecipeRoute(String routeId, List<AERecipeRouteStack> inputs, List<AERecipeRouteStack> outputs) {

    /**
     * 规范化 route 数据，并为输入输出栈补上稳定顺序。
     */
    public AERecipeRoute {
        routeId = routeId == null ? "" : routeId;
        inputs = copyWithOrder(inputs);
        outputs = copyWithOrder(outputs);
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

    /**
     * 转换成 AE 1.12 可注册的旧物品配方。
     *
     * @return 转换成功时返回暴露配方，否则返回 null
     */
    @Nullable
    public AEExposedRecipe toLegacyRecipe() {
        List<ItemStack> legacyInputs = toLegacyStacks(inputs);
        List<ItemStack> legacyOutputs = toLegacyStacks(outputs);
        if (legacyInputs == null || legacyOutputs == null || legacyInputs.isEmpty() || legacyOutputs.isEmpty()) {
            return null;
        }
        return new AEExposedRecipe(legacyInputs, legacyOutputs, this, getRouteDiscriminator());
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
    private static List<ItemStack> toLegacyStacks(List<AERecipeRouteStack> routeStacks) {
        List<ItemStack> stacks = new ArrayList<>(routeStacks.size());
        for (AERecipeRouteStack routeStack : routeStacks) {
            ItemStack stack = routeStack.toLegacyStack();
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
        StringBuilder builder = new StringBuilder(routeId);
        appendStacks(builder.append("|in"), inputs);
        appendStacks(builder.append("|out"), outputs);
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
        private final List<AERecipeRouteStack> inputs = new ArrayList<>();
        private final List<AERecipeRouteStack> outputs = new ArrayList<>();

        /**
         * @param routeId 路线类型 ID
         */
        private Builder(String routeId) {
            this.routeId = routeId;
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

        /**
         * @return 构建完成的 route
         */
        public AERecipeRoute build() {
            return new AERecipeRoute(routeId, inputs, outputs);
        }
    }
}
