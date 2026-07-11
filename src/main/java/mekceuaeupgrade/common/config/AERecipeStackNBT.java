package mekceuaeupgrade.common.config;

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
        if (!tag.hasKey(COUNT, NBT.TAG_INT)) {
            return new ItemStack(tag);
        }
        int count = tag.getInteger(COUNT);
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        NBTTagCompound itemTag = tag.copy();
        itemTag.setByte("Count", (byte) 1);
        ItemStack stack = new ItemStack(itemTag);
        if (!stack.isEmpty()) {
            stack.setCount(count);
        }
        return stack;
    }
}
