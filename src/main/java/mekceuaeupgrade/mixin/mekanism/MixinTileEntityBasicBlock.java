package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;

import ae2.api.AECapabilities;
import ae2.api.networking.IInWorldGridNodeHost;
import mekanism.common.tile.base.TileEntityRestrictedTick;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(value = TileEntityBasicBlock.class, remap = false)
public abstract class MixinTileEntityBasicBlock extends TileEntityRestrictedTick {

    @Inject(method = "onLoad", at = @At("TAIL"))
    private void mekceuaeupgrade$onLoad(CallbackInfo ci) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$onAEUpgradeLoad();
        }
    }

    @Inject(method = "validate", at = @At("TAIL"))
    private void mekceuaeupgrade$validate(CallbackInfo ci) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$validateAEUpgrade();
        }
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void mekceuaeupgrade$invalidate(CallbackInfo ci) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$invalidateAEUpgrade();
        }
    }

    @Override
    public void onChunkUnload() {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$onAEUpgradeChunkUnload();
        }
        super.onChunkUnload();
    }

    @Inject(method = "onNeighborChange", at = @At("TAIL"))
    private void mekceuaeupgrade$onNeighborChange(Block block, CallbackInfo ci) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$onAENeighborChanged();
        }
    }

    @Inject(method = "readCustomNBT", at = @At("TAIL"))
    private void mekceuaeupgrade$readCustomNBT(NBTTagCompound nbtTags, CallbackInfo ci) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$readAEUpgrade(nbtTags);
        }
    }

    @Inject(method = "writeCustomNBT", at = @At("TAIL"))
    private void mekceuaeupgrade$writeCustomNBT(NBTTagCompound nbtTags, CallbackInfo ci) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$writeAEUpgrade(nbtTags);
        }
    }

    @Inject(method = "hasCapability", at = @At("HEAD"), cancellable = true)
    private void mekceuaeupgrade$hasCapability(Capability<?> capability, EnumFacing side, CallbackInfoReturnable<Boolean> cir) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null && capability == AECapabilities.IN_WORLD_GRID_NODE_HOST) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private <T> void mekceuaeupgrade$getCapability(Capability<T> capability, EnumFacing side, CallbackInfoReturnable<T> cir) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null && capability == AECapabilities.IN_WORLD_GRID_NODE_HOST) {
            cir.setReturnValue(AECapabilities.IN_WORLD_GRID_NODE_HOST.cast((IInWorldGridNodeHost) bridge));
        }
    }

    @Unique
    @Nullable
    private IAEUpgradeHostBridge mekceuaeupgrade$getAEBridge() {
        Object self = this;
        return self instanceof IAEUpgradeHostBridge hostBridge ? hostBridge : null;
    }
}
