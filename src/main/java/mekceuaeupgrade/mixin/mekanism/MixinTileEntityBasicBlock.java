package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;

import mekanism.common.concurrent.TaskExecutor;
import mekanism.common.tile.base.TileEntityRestrictedTick;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.concurrent.Action;
import mekanism.common.util.concurrent.ActionExecutor;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "onUpdateServer", at = @At("TAIL"))
    private void mekceuaeupgrade$onUpdateServer(CallbackInfo ci) {
        if (((TileEntityBasicBlock) (Object) this).supportsAsync()) {
            return;
        }
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge != null) {
            bridge.mekceuaeupgrade$tickAEUpgradeServer();
        }
    }

    @Redirect(
          method = "doRestrictedTick",
          at = @At(
                value = "INVOKE",
                target = "Lmekanism/common/concurrent/TaskExecutor;addTask(Lmekanism/common/util/concurrent/Action;)Lmekanism/common/util/concurrent/ActionExecutor;"
          )
    )
    private ActionExecutor mekceuaeupgrade$wrapAsyncUpdateServer(TaskExecutor executor, Action action) {
        IAEUpgradeHostBridge bridge = mekceuaeupgrade$getAEBridge();
        if (bridge == null) {
            return executor.addTask(action);
        }
        return executor.addTask(() -> {
            action.doAction();
            executor.addSyncTask(bridge::mekceuaeupgrade$tickAEUpgradeServer);
        });
    }

    @Unique
    @Nullable
    private IAEUpgradeHostBridge mekceuaeupgrade$getAEBridge() {
        Object self = this;
        return self instanceof IAEUpgradeHostBridge hostBridge ? hostBridge : null;
    }
}
