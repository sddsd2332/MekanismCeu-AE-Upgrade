package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import mekanism.common.item.ItemConfigurationCard;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemConfigurationCard.class, remap = false)
public abstract class MixinItemConfigurationCard {

    @Inject(
          method = "onItemUseFirst",
          at = @At(
                value = "INVOKE_ASSIGN",
                target = "Lmekanism/api/IConfigCardAccess$ISpecialConfigData;getConfigurationData(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;"
          )
    )
    private void mekceuaeupgrade$filterAEConfigCardDataOnRead(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY,
          float hitZ, EnumHand hand, CallbackInfoReturnable<EnumActionResult> cir, @Local TileEntity tileEntity,
          @Local LocalRef<NBTTagCompound> data) {
        mekceuaeupgrade$filterAEConfigCardData(tileEntity, player, data);
    }

    @Inject(
          method = "onItemUseFirst",
          at = @At(
                value = "INVOKE",
                target = "Lmekanism/api/IConfigCardAccess$ISpecialConfigData;setConfigurationData(Lnet/minecraft/nbt/NBTTagCompound;)V"
          )
    )
    private void mekceuaeupgrade$filterAEConfigCardDataOnWrite(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY,
          float hitZ, EnumHand hand, CallbackInfoReturnable<EnumActionResult> cir, @Local TileEntity tileEntity,
          @Local LocalRef<NBTTagCompound> data) {
        mekceuaeupgrade$filterAEConfigCardData(tileEntity, player, data);
    }

    @Inject(method = "onItemUseFirst", at = @At(value = "INVOKE", target = "Lmekanism/common/util/CapabilityUtils;getCapability(Lnet/minecraftforge/common/capabilities/ICapabilityProvider;Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/util/EnumFacing;)Ljava/lang/Object;"))
    private void mekceuaeupgrade$ensureAEProfileOwner(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand, CallbackInfoReturnable<EnumActionResult> cir, @Local TileEntity tileEntity) {
        mekceuaeupgrade$ensureProfileOwner(tileEntity, player);
    }

    @Unique
    private void mekceuaeupgrade$ensureProfileOwner(TileEntity tileEntity, EntityPlayer player) {
        AERecipeProfileManager.ensureProfileOwner(tileEntity, player);
        TileEntity profileTile = mekceuaeupgrade$getProfileTile(tileEntity);
        if (profileTile != tileEntity) {
            AERecipeProfileManager.ensureProfileOwner(profileTile, player);
        }
    }

    @Unique
    private void mekceuaeupgrade$filterAEConfigCardData(TileEntity tileEntity, EntityPlayer player, LocalRef<NBTTagCompound> data) {
        TileEntity profileTile = mekceuaeupgrade$getProfileTile(tileEntity);
        data.set(AERecipeProfileManager.filterConfigCardDataForPlayer(profileTile, player, data.get()));
    }

    @Unique
    private TileEntity mekceuaeupgrade$getProfileTile(TileEntity tileEntity) {
        if (tileEntity instanceof TileEntityBoundingBlock boundingBlock && boundingBlock.getMainTile() != null) {
            return boundingBlock.getMainTile();
        }
        return tileEntity;
    }
}
