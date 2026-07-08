package mekceuaeupgrade.common.transfer;

import mekceuaeupgrade.common.host.AEUpgradeNode;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEFluidKey;
import ae2.api.storage.MEStorage;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Forge 流体与 AE2 流体存储频道之间的桥接工具。
 */
public final class AEUpgradeFluidBridge {

    /**
     * 工具类不允许实例化。
     */
    private AEUpgradeFluidBridge() {
    }

    /**
     * @return 当前运行环境是否能访问 AE2 流体频道
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * 向 AE 流体网络插入流体。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param stack 要插入的流体栈
     * @param action AE 插入动作，模拟或真实执行
     * @return AE 未接受的剩余流体；完全接受时返回 null
     */
    @Nullable
    public static FluidStack inject(AEUpgradeNode node, FluidStack stack, Actionable action) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0 || !node.canUseNetwork()) {
            return stack;
        }
        MEStorage storage = node.getNetworkStorage();
        AEFluidKey key = AEFluidKey.of(stack);
        if (storage == null || key == null) {
            return stack;
        }
        long inserted = storage.insert(key, stack.amount, action, node.getActionSource());
        if (inserted >= stack.amount) {
            return null;
        }
        FluidStack remainder = stack.copy();
        remainder.amount = (int) Math.max(0, stack.amount - inserted);
        return remainder;
    }

    /**
     * 从 AE 流体网络提取流体。
     *
     * @param node 负责访问 AE 网络的升级节点
     * @param request 请求提取的流体栈
     * @param action AE 提取动作，模拟或真实执行
     * @return 实际提取到的流体，失败时返回 null
     */
    @Nullable
    public static FluidStack extract(AEUpgradeNode node, FluidStack request, Actionable action) {
        if (request == null || request.getFluid() == null || request.amount <= 0 || !node.canUseNetwork()) {
            return null;
        }
        MEStorage storage = node.getNetworkStorage();
        AEFluidKey key = AEFluidKey.of(request);
        if (storage == null || key == null) {
            return null;
        }
        long extracted = storage.extract(key, request.amount, action, node.getActionSource());
        return extracted <= 0 ? null : key.toStack((int) Math.min(extracted, Integer.MAX_VALUE));
    }
}
