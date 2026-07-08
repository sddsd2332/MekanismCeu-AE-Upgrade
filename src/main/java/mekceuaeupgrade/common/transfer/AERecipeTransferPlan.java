package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.route.AERecipeRoute;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteLegacyIO;
import mekceuaeupgrade.common.recipe.route.AERecipeRouteStack;
import mekceuaeupgrade.common.recipe.route.AERecipeStackKind;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次 AE 配方输入转移的原子执行计划。
 *
 * <p>计划由多个端口和 route 栈组成。执行前会先模拟所有端口，真实写入时如果任意一步失败，
 * 会用端口快照把已经写入的槽位或储罐恢复到执行前状态。</p>
 */
public final class AERecipeTransferPlan {

    private final List<Entry> entries = new ArrayList<>();

    /**
     * 使用 {@link #create()} 创建空计划。
     */
    private AERecipeTransferPlan() {
    }

    /**
     * 创建一个空的转移计划，适合 adapter 手动追加端口。
     *
     * @return 新的空计划
     */
    public static AERecipeTransferPlan create() {
        return new AERecipeTransferPlan();
    }

    /**
     * 从单个 AE 输入载体创建转移计划。
     *
     * @param recipe 暴露给 AE 的配方
     * @param stack AE 实际交付的输入载体
     * @param port 目标机器端口
     * @return 能成功解码时返回计划，否则返回 null
     */
    @Nullable
    public static AERecipeTransferPlan fromLegacyInput(AEExposedRecipe recipe, ItemStack stack, @Nullable AERecipePort port) {
        return fromLegacyInputs(recipe, Collections.singletonList(stack), Collections.singletonList(port));
    }

    /**
     * 按输入顺序从 AE 输入载体创建转移计划。
     *
     * <p>没有 route 的 item-only 配方会按物品输入处理；带 route 的配方会根据 route 的类型把 generic wrapper 解码为真实栈。</p>
     *
     * @param recipe 暴露给 AE 的配方
     * @param stacks AE 实际交付的输入载体列表
     * @param ports 与输入顺序一一对应的目标端口
     * @return 能成功解码并绑定端口时返回计划，否则返回 null
     */
    @Nullable
    public static AERecipeTransferPlan fromLegacyInputs(AEExposedRecipe recipe, List<ItemStack> stacks, List<AERecipePort> ports) {
        if (recipe == null || stacks == null || ports == null || stacks.size() != ports.size() || !recipe.matchesInputs(stacks)) {
            return null;
        }
        AERecipeRoute route = recipe.getRecipeRoute();
        if (route != null && route.inputs().size() != stacks.size()) {
            return null;
        }
        AERecipeTransferPlan plan = create();
        for (int i = 0; i < stacks.size(); i++) {
            AERecipeRouteStack stack = decodeInput(recipe, route, stacks, i, ports.get(i));
            if (stack == null) {
                return null;
            }
            plan.add(ports.get(i), stack);
        }
        return plan;
    }

    /**
     * 按 route 端口 ID 从 AE 输入载体创建转移计划。
     *
     * <p>route 存在时，端口列表不需要和输入顺序一致，会通过 {@link AERecipePort#portId()} 与 route 输入绑定。
     * route 不存在时退回 {@link #fromLegacyInputs(AEExposedRecipe, List, List)} 的顺序绑定逻辑。</p>
     *
     * @param recipe 暴露给 AE 的配方
     * @param stacks AE 实际交付的输入载体列表
     * @param ports 可供 route 绑定的目标端口列表
     * @return 能成功解码并按端口 ID 绑定时返回计划，否则返回 null
     */
    @Nullable
    public static AERecipeTransferPlan fromLegacyInputsByPortId(AEExposedRecipe recipe, List<ItemStack> stacks, List<AERecipePort> ports) {
        AERecipeRoute route = recipe == null ? null : recipe.getRecipeRoute();
        if (route == null) {
            return fromLegacyInputs(recipe, stacks, ports);
        }
        if (stacks == null || ports == null || route.inputs().size() != stacks.size() || !recipe.matchesInputs(stacks)) {
            return null;
        }
        Map<String, AERecipePort> portsById = new HashMap<>();
        for (AERecipePort port : ports) {
            if (port == null || portsById.put(port.portId(), port) != null) {
                return null;
            }
        }
        AERecipeTransferPlan plan = create();
        for (int i = 0; i < stacks.size(); i++) {
            AERecipeRouteStack routeStack = route.inputs().get(i);
            AERecipePort port = portsById.get(routeStack.portId());
            AERecipeRouteStack decodedStack = decodeInput(recipe, route, stacks, i, port);
            if (decodedStack == null) {
                return null;
            }
            plan.add(port, decodedStack);
        }
        return plan;
    }

    /**
     * 手动向计划追加一个输入转移项。
     *
     * @param port 目标机器端口
     * @param stack 要写入该端口的 route 栈
     * @return 当前计划，便于链式调用
     */
    public AERecipeTransferPlan add(@Nullable AERecipePort port, AERecipeRouteStack stack) {
        entries.add(new Entry(port, stack));
        return this;
    }

    /**
     * 模拟检查整个计划是否可执行。
     *
     * @return 所有端口都能完整接收对应栈时返回 true
     */
    public boolean canExecute() {
        if (entries.isEmpty()) {
            return false;
        }
        for (Entry entry : entries) {
            if (entry.port == null || entry.stack == null || entry.port.kind() != entry.stack.kind() || !entry.port.canInsert(entry.stack)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 原子执行整个计划。
     *
     * @return 所有输入真实写入成功时返回 true，失败会恢复已写入端口
     */
    public boolean execute() {
        if (!canExecute()) {
            return false;
        }
        List<AERecipePort.Snapshot> snapshots = snapshotPorts();
        for (Entry entry : entries) {
            if (!entry.port.insert(entry.stack)) {
                restore(snapshots);
                return false;
            }
        }
        return true;
    }

    /**
     * 为计划涉及到的每个端口创建一次快照。
     *
     * @return 去重后的端口快照列表
     */
    private List<AERecipePort.Snapshot> snapshotPorts() {
        Map<AERecipePort, Boolean> seen = new IdentityHashMap<>();
        List<AERecipePort.Snapshot> snapshots = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.port != null && !seen.containsKey(entry.port)) {
                seen.put(entry.port, Boolean.TRUE);
                snapshots.add(entry.port.snapshot());
            }
        }
        return snapshots;
    }

    /**
     * 按反向顺序恢复端口快照。
     *
     * @param snapshots 需要恢复的快照列表
     */
    private static void restore(List<AERecipePort.Snapshot> snapshots) {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            snapshots.get(i).restore();
        }
    }

    /**
     * 将 AE 输入载体解码成端口可写入的 typed route 栈。
     *
     * @param recipe 暴露给 AE 的配方
     * @param route 配方 route，没有 route 时只允许物品输入
     * @param stacks AE 实际交付的输入载体列表
     * @param index 当前输入下标
     * @param port 目标端口
     * @return 解码后的 route 栈，失败时返回 null
     */
    @Nullable
    private static AERecipeRouteStack decodeInput(AEExposedRecipe recipe, @Nullable AERecipeRoute route, List<ItemStack> stacks, int index,
          @Nullable AERecipePort port) {
        if (port == null || index < 0 || index >= stacks.size()) {
            return null;
        }
        AERecipeRouteStack routeStack = route == null ? null : route.inputs().get(index);
        AERecipeStackKind kind = routeStack == null ? AERecipeStackKind.ITEM : routeStack.kind();
        if (port.kind() != kind) {
            return null;
        }
        ItemStack carrierStack = stacks.get(index);
        if (routeStack != null && !AERecipeRouteLegacyIO.matchesInput(routeStack, carrierStack)) {
            return null;
        }
        switch (kind) {
            case ITEM:
                return carrierStack == null || carrierStack.isEmpty() ? null : AERecipeRouteStack.item(port.portId(), carrierStack);
            case GAS:
                if (routeStack == null) {
                    return null;
                }
                GasStack gas = AERecipeRouteLegacyIO.getGasInput(recipe, stacks, index, gasType -> true);
                return gas == null ? null : AERecipeRouteStack.gas(port.portId(), gas);
            case FLUID:
                if (routeStack == null) {
                    return null;
                }
                FluidStack fluid = AERecipeRouteLegacyIO.getFluidInput(recipe, stacks, index);
                return fluid == null ? null : AERecipeRouteStack.fluid(port.portId(), fluid);
            default:
                return null;
        }
    }

    /**
     * 计划中的单个端口写入项。
     */
    private static final class Entry {

        @Nullable
        private final AERecipePort port;
        @Nullable
        private final AERecipeRouteStack stack;

        /**
         * @param port 目标机器端口
         * @param stack 要写入端口的 route 栈
         */
        private Entry(@Nullable AERecipePort port, @Nullable AERecipeRouteStack stack) {
            this.port = port;
            this.stack = stack;
        }
    }
}
