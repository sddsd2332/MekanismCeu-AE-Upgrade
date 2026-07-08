package mekceuaeupgrade.common.util;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import mekanism.api.MekanismAPI;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public final class AEUpgradeDebug {

    private AEUpgradeDebug() {
    }

    public static boolean enabled() {
        return MekanismAPI.debug;
    }

    public static void log(Object source, String message, Object... args) {
        if (enabled()) {
            MEKCeuAEUpgrade.logger.info("[AE Upgrade] " + describeSource(source) + " " + message, args);
        }
    }

    public static String stack(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        String itemName = name == null ? stack.getItem().getClass().getName() : name.toString();
        return stack.getCount() + "x" + itemName + "@" + stack.getMetadata() + (stack.hasTagCompound() ? " " + stack.getTagCompound() : "");
    }

    public static String stacks(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(stack(stacks.get(i)));
        }
        return builder.append(']').toString();
    }

    private static String describeSource(Object source) {
        if (source instanceof TileEntity tile) {
            String world = tile.getWorld() == null ? "no-world" : "dim " + tile.getWorld().provider.getDimension();
            return "[" + world + " " + tile.getPos() + "]";
        }
        return "[" + source.getClass().getSimpleName() + "]";
    }
}
