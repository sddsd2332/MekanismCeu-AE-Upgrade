package mekceuaeupgrade.mixin.mekanism;

import mekanism.common.tile.factory.TileEntityFactory;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAERecipeMachineHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityFactory.class, remap = false)
public abstract class MixinTileEntityFactory implements IAERecipeMachineHost, IAEUpgradeHostBridge {

    // Current factories expose their lanes and recipes through the core Provider contract.
    @Unique
    private static final IAERecipeMachineAdapter mekceuaeupgrade$unavailableProvider = new IAERecipeMachineAdapter() {};
    @Unique
    private volatile AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;

    @Unique
    private volatile AEUpgradeNode mekceuaeupgrade$cachedNode;

    @Override
    public IAERecipeMachineAdapter getAERecipeMachineAdapter() {
        return mekceuaeupgrade$unavailableProvider;
    }

    @Override
    public AEUpgradeNode getAEUpgradeNode() {
        AEUpgradeNode node = mekceuaeupgrade$cachedNode;
        if (node == null) {
            node = mekceuaeupgrade$getAEUpgradeDelegate().getNode();
            mekceuaeupgrade$cachedNode = node;
        }
        return node;
    }

    @Override
    public AEUpgradeHostDelegate mekceuaeupgrade$getAEUpgradeDelegate() {
        AEUpgradeHostDelegate delegate = mekceuaeupgrade$aeUpgrade;
        if (delegate == null) {
            synchronized (this) {
                delegate = mekceuaeupgrade$aeUpgrade;
                if (delegate == null) {
                    delegate = new AEUpgradeHostDelegate(this);
                    mekceuaeupgrade$aeUpgrade = delegate;
                }
            }
        }
        return delegate;
    }

    @Inject(method = "setRecipeType(Lmekanism/common/base/IFactory$RecipeType;Z)V", at = @At("TAIL"), require = 1)
    private void mekceuaeupgrade$recipeTypeChanged(CallbackInfo ci) {
        if (mekceuaeupgrade$aeUpgrade != null) {
            mekceuaeupgrade$onAERecipePortsChanged();
        }
    }

    @Inject(method = "getConfigurationData", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$getConfigurationData(NBTTagCompound nbtTags, CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.setReturnValue(AERecipeProfileManager.writeConfigCardData((TileEntityFactory) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "setConfigurationData", at = @At("TAIL"))
    private void mekceuaeupgrade$setConfigurationData(NBTTagCompound nbtTags, CallbackInfo ci) {
        AERecipeProfileManager.readConfigCardData((TileEntityFactory) (Object) this, nbtTags);
    }
}
