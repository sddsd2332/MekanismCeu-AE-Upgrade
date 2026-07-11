package mekceuaeupgrade.common.host;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;

public record AEWirelessLink(int dimension, BlockPos position) {

    private static final String DIMENSION_TAG = "dimension";
    private static final String X_TAG = "x";
    private static final String Y_TAG = "y";
    private static final String Z_TAG = "z";

    @Nullable
    public static AEWirelessLink read(NBTTagCompound parent, String key) {
        if (!parent.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound link = parent.getCompoundTag(key);
        if (!link.hasKey(DIMENSION_TAG, Constants.NBT.TAG_INT)
              || !link.hasKey(X_TAG, Constants.NBT.TAG_INT)
              || !link.hasKey(Y_TAG, Constants.NBT.TAG_INT)
              || !link.hasKey(Z_TAG, Constants.NBT.TAG_INT)) {
            return null;
        }
        return new AEWirelessLink(link.getInteger(DIMENSION_TAG), new BlockPos(
              link.getInteger(X_TAG), link.getInteger(Y_TAG), link.getInteger(Z_TAG)));
    }

    public void write(NBTTagCompound parent, String key) {
        NBTTagCompound link = new NBTTagCompound();
        link.setInteger(DIMENSION_TAG, dimension);
        link.setInteger(X_TAG, position.getX());
        link.setInteger(Y_TAG, position.getY());
        link.setInteger(Z_TAG, position.getZ());
        parent.setTag(key, link);
    }

    public String formatLocation() {
        return "dim " + dimension + " @ " + position.getX() + ", " + position.getY() + ", " + position.getZ();
    }
}
