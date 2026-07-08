package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AEItemStackKey {

    private final NBTTagCompound stackTag;
    private final String encoded;

    private AEItemStackKey(NBTTagCompound stackTag, String encoded) {
        this.stackTag = stackTag;
        this.encoded = encoded;
    }

    public static AEItemStackKey fromStack(@Nonnull ItemStack stack) {
        ItemStack copy = stack.copy();
        NBTTagCompound tag = AERecipeStackNBT.write(copy);
        return new AEItemStackKey(tag, encode(tag));
    }

    public static AEItemStackKey fromNBT(NBTTagCompound tag) {
        NBTTagCompound stackTag = tag.getCompoundTag("stack");
        return new AEItemStackKey(stackTag, encode(stackTag));
    }

    public NBTTagCompound write(NBTTagCompound tag) {
        tag.setTag("stack", stackTag.copy());
        return tag;
    }

    public ItemStack getStack() {
        return AERecipeStackNBT.read(stackTag.copy());
    }

    public String getEncoded() {
        return encoded;
    }

    private static String encode(NBTTagCompound tag) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            updateDigest(messageDigest, tag);
            byte[] digest = messageDigest.digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to encode AE recipe stack key.", e);
        }
    }

    private static void updateDigest(MessageDigest digest, NBTBase tag) {
        digest.update(tag.getId());
        if (tag instanceof NBTTagCompound compound) {
            List<String> keys = new ArrayList<>(compound.getKeySet());
            Collections.sort(keys);
            updateInt(digest, keys.size());
            for (String key : keys) {
                updateString(digest, key);
                updateDigest(digest, compound.getTag(key));
            }
        } else if (tag instanceof NBTTagList list) {
            updateInt(digest, list.tagCount());
            for (NBTBase value : list) {
                updateDigest(digest, value);
            }
        } else {
            updateString(digest, tag.toString());
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof AEItemStackKey other && encoded.equals(other.encoded);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encoded);
    }

    @Override
    public String toString() {
        return encoded;
    }
}
