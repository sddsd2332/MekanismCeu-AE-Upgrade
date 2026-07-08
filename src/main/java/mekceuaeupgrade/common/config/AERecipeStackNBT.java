package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

public final class AERecipeStackNBT {

    private static final String COUNT = "mekCount";

    private AERecipeStackNBT() {
    }

    public static NBTTagCompound write(ItemStack stack) {
        NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
        tag.setInteger(COUNT, stack.getCount());
        return tag;
    }

    public static ItemStack read(NBTTagCompound tag) {
        ItemStack stack = new ItemStack(tag);
        if (!stack.isEmpty() && tag.hasKey(COUNT, NBT.TAG_INT)) {
            stack.setCount(tag.getInteger(COUNT));
        }
        return stack;
    }
}
