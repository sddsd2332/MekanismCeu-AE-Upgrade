package mekceuaeupgrade.common.util;

import mekanism.api.MekanismAPI;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.function.Supplier;

public final class AEUpgradeDebug {

    private static final Object DISABLED_VALUE = new Object() {
        @Override
        public String toString() {
            return "debug-disabled";
        }
    };

    private AEUpgradeDebug() {
    }

    public static boolean enabled() {
        return MekanismAPI.debug;
    }

    public static void log(Object source, String message) {
        if (enabled()) {
            MEKCeuAEUpgrade.logger.info("[AE Upgrade] " + describeSource(source) + " " + message);
        }
    }

    public static void log(Object source, String message, Object arg0) {
        if (enabled()) {
            MEKCeuAEUpgrade.logger.info("[AE Upgrade] " + describeSource(source) + " " + message, arg0);
        }
    }

    public static void log(Object source, String message, Object arg0, Object arg1) {
        if (enabled()) {
            MEKCeuAEUpgrade.logger.info("[AE Upgrade] " + describeSource(source) + " " + message, arg0, arg1);
        }
    }

    public static void log(Object source, String message, Object arg0, Object arg1, Object arg2) {
        if (enabled()) {
            MEKCeuAEUpgrade.logger.info("[AE Upgrade] " + describeSource(source) + " " + message, arg0, arg1, arg2);
        }
    }

    public static void log(Object source, String message, Object arg0, Object arg1, Object arg2, Object arg3) {
        if (enabled()) {
            MEKCeuAEUpgrade.logger.info("[AE Upgrade] " + describeSource(source) + " " + message, arg0, arg1, arg2, arg3);
        }
    }

    public static void log(Object source, String message, Object... args) {
        if (enabled()) {
            MEKCeuAEUpgrade.logger.info("[AE Upgrade] " + describeSource(source) + " " + message, args);
        }
    }

    public static Object stack(ItemStack stack) {
        return enabled() ? new DeferredValue(() -> formatStack(stack)) : DISABLED_VALUE;
    }

    public static Object stack(Supplier<ItemStack> stackSupplier) {
        return enabled() ? new DeferredValue(() -> formatStack(stackSupplier.get())) : DISABLED_VALUE;
    }

    public static Object stacks(List<ItemStack> stacks) {
        return enabled() ? new DeferredValue(() -> formatStacks(stacks)) : DISABLED_VALUE;
    }

    public static Object stacks(Supplier<? extends List<ItemStack>> stacksSupplier) {
        return enabled() ? new DeferredValue(() -> formatStacks(stacksSupplier.get())) : DISABLED_VALUE;
    }

    public static Object inputStack(AEExposedRecipe recipe) {
        return enabled() ? new DeferredValue(() -> formatStack(recipe.getInputStack())) : DISABLED_VALUE;
    }

    public static Object outputStack(AEExposedRecipe recipe) {
        return enabled() ? new DeferredValue(() -> formatStack(recipe.getOutputStack())) : DISABLED_VALUE;
    }

    public static Object inputStacks(AEExposedRecipe recipe) {
        return enabled() ? new DeferredValue(() -> formatStacks(recipe.getInputStacks())) : DISABLED_VALUE;
    }

    public static Object outputStacks(AEExposedRecipe recipe) {
        return enabled() ? new DeferredValue(() -> formatStacks(recipe.getOutputStacks())) : DISABLED_VALUE;
    }

    private static String formatStack(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        String itemName = name == null ? stack.getItem().getClass().getName() : name.toString();
        return stack.getCount() + "x" + itemName + "@" + stack.getMetadata() + (stack.hasTagCompound() ? " " + stack.getTagCompound() : "");
    }

    private static String formatStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(formatStack(stacks.get(i)));
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

    private static final class DeferredValue {

        private final Supplier<String> formatter;

        private DeferredValue(Supplier<String> formatter) {
            this.formatter = formatter;
        }

        @Override
        public String toString() {
            return formatter.get();
        }
    }
}
