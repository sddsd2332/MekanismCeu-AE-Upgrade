package mekceuaeupgrade.mixin.appeng;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.CraftingGridCache;
import appeng.me.helpers.BaseActionSource;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import mekceuaeupgrade.common.host.AEWirelessCraftingProviderRegistry;
import mekceuaeupgrade.common.integration.appeng.AEIncrementalCraftingIndex;
import mekceuaeupgrade.common.integration.appeng.IAEIncrementalCraftingGridCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class MixinCraftingGridCache implements ICraftingProviderHelper, IAEIncrementalCraftingGridCache {

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private Object2ObjectMap<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItems;

    @Shadow
    @Final
    private Set<IAEItemStack> emitableItems;

    @Shadow
    private IStorageGrid storageGrid;

    @Unique
    private final AEIncrementalCraftingIndex mekceuaeupgrade$incrementalCrafting = new AEIncrementalCraftingIndex();
    @Unique
    private boolean mekceuaeupgrade$recalculatingPatterns;
    @Unique
    private final Set<IAEItemStack> mekceuaeupgrade$pendingCraftabilityChanges = new LinkedHashSet<>();

    @Override
    @Unique
    public IGrid mekceuaeupgrade$getGrid() {
        return grid;
    }

    @Override
    @Unique
    public void mekceuaeupgrade$setProviderRecipes(ICraftingProvider provider, ICraftingMedium medium,
          Collection<? extends ICraftingPatternDetails> recipes) {
        mekceuaeupgrade$incrementalCrafting.setProviderRecipes(provider, medium, recipes);
    }

    @Override
    @Unique
    public List<IAEItemStack> mekceuaeupgrade$removeProvider(ICraftingProvider provider) {
        List<IAEItemStack> changed = mekceuaeupgrade$incrementalCrafting.removeProvider(provider);
        if (!changed.isEmpty()) {
            if (mekceuaeupgrade$recalculatingPatterns) {
                for (IAEItemStack stack : changed) {
                    mekceuaeupgrade$queuePendingCraftabilityChange(stack);
                }
            } else {
                mekceuaeupgrade$postCraftabilityChanges(changed);
            }
        }
        return changed;
    }

    /**
     * AE removes providers before the next crafting-cache tick. Keep the sidecar index in the
     * same lifecycle transition so a provider cannot survive an empty-grid transition.
     */
    @Inject(method = "removeNode", at = @At("RETURN"))
    private void mekceuaeupgrade$removeIncrementalProvider(IGridNode gridNode, IGridHost machine,
          CallbackInfo ci) {
        if (machine instanceof ICraftingProvider) {
            mekceuaeupgrade$removeProvider((ICraftingProvider) machine);
        }
    }

    @Inject(method = "recalculateCraftingPatterns", at = @At("HEAD"))
    private void mekceuaeupgrade$beginPatternRecalculation(CallbackInfo ci) {
        mekceuaeupgrade$recalculatingPatterns = true;
    }

    @Inject(method = "recalculateCraftingPatterns", at = @At("RETURN"))
    private void mekceuaeupgrade$finishPatternRecalculation(CallbackInfo ci) {
        mekceuaeupgrade$recalculatingPatterns = false;
        mekceuaeupgrade$flushPendingCraftabilityChanges();
    }


    @Inject(method = "onUpdateTick", at = @At("RETURN"))
    private void mekceuaeupgrade$publishIncrementalCrafting(CallbackInfo ci) {
        List<IAEItemStack> changed = mekceuaeupgrade$incrementalCrafting.processPendingChanges(
              stack -> craftableItems.containsKey(stack) || emitableItems.contains(stack));
        if (!changed.isEmpty()) {
            mekceuaeupgrade$postCraftabilityChanges(changed);
        }
        mekceuaeupgrade$flushPendingCraftabilityChanges();
    }

    @Unique
    private void mekceuaeupgrade$postCraftabilityChanges(Iterable<IAEItemStack> changes) {
        if (changes == null) {
            return;
        }
        if (storageGrid == null) {
            for (IAEItemStack stack : changes) {
                mekceuaeupgrade$queuePendingCraftabilityChange(stack);
            }
            return;
        }
        List<IAEItemStack> filtered = new ArrayList<>();
        for (IAEItemStack stack : changes) {
            if (stack == null) {
                continue;
            }
            // A native recipe/emitable item still owns the output after the sidecar provider is
            // removed, so do not publish a spurious craftability=false transition.
            if (!stack.isCraftable() && (craftableItems.containsKey(stack) || emitableItems.contains(stack))) {
                continue;
            }
            filtered.add(stack);
        }
        if (!filtered.isEmpty()) {
            storageGrid.postCraftablesChanges(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class),
                  filtered, new BaseActionSource());
        }
    }

    @Unique
    private void mekceuaeupgrade$flushPendingCraftabilityChanges() {
        if (mekceuaeupgrade$pendingCraftabilityChanges.isEmpty()) {
            return;
        }
        List<IAEItemStack> changes = new ArrayList<>(mekceuaeupgrade$pendingCraftabilityChanges);
        mekceuaeupgrade$pendingCraftabilityChanges.clear();
        mekceuaeupgrade$postCraftabilityChanges(changes);
    }

    @Unique
    private void mekceuaeupgrade$queuePendingCraftabilityChange(IAEItemStack stack) {
        if (stack == null) {
            return;
        }
        // AE item-stack equality ignores the craftable bit; replace an older transition so a
        // reconnect that ends in craftable=true cannot be overwritten by a stale false update.
        mekceuaeupgrade$pendingCraftabilityChanges.remove(stack);
        mekceuaeupgrade$pendingCraftabilityChanges.add(stack.copy());
    }

    @ModifyArg(
          method = "recalculateCraftingPatterns",
          at = @At(
                value = "INVOKE",
                target = "Lappeng/api/networking/storage/IStorageGrid;postCraftablesChanges(Lappeng/api/storage/IStorageChannel;Ljava/lang/Iterable;Lappeng/api/networking/security/IActionSource;)V"
          ),
          index = 1
    )
    private Iterable<IAEItemStack> mekceuaeupgrade$preserveIncrementalCraftables(Iterable<IAEItemStack> changes) {
        return mekceuaeupgrade$incrementalCrafting.filterNativeCraftabilityChanges(changes);
    }


    @Inject(method = "getAvailableItems", at = @At("RETURN"))
    private void mekceuaeupgrade$addIncrementalCraftables(IItemList<IAEItemStack> out,
          CallbackInfoReturnable<IItemList<IAEItemStack>> cir) {
        IItemList<IAEItemStack> result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        for (IAEItemStack stack : mekceuaeupgrade$incrementalCrafting.getCraftableOutputs()) {
            result.addCrafting(stack);
        }
    }

    @Inject(method = "getCraftingFor", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$addIncrementalPatterns(IAEItemStack whatToCraft, ICraftingPatternDetails details,
          int slotIndex, World world, CallbackInfoReturnable<ImmutableCollection<ICraftingPatternDetails>> cir) {
        cir.setReturnValue(mekceuaeupgrade$incrementalCrafting.mergeCraftingFor(whatToCraft, cir.getReturnValue()));
    }

    @Inject(method = "getMediums", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$addIncrementalMediums(ICraftingPatternDetails details,
          CallbackInfoReturnable<List<ICraftingMedium>> cir) {
        cir.setReturnValue(AEIncrementalCraftingIndex.mergeMediums(cir.getReturnValue(),
              mekceuaeupgrade$incrementalCrafting.getMediums(details)));
    }

    @Inject(
          method = "recalculateCraftingPatterns",
          at = @At(
                value = "NEW",
                target = "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap",
                ordinal = 1,
                shift = At.Shift.BEFORE
          )
    )
    private void mekceuaeupgrade$provideWirelessCrafting(CallbackInfo ci) {
        AEWirelessCraftingProviderRegistry.provideCrafting(grid, this);
    }

    @ModifyArg(
          method = "recalculateCraftingPatterns",
          at = @At(
                value = "INVOKE",
                target = "Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;containsKey(Ljava/lang/Object;)Z",
                ordinal = 1
          ),
          index = 0
    )
    private Object mekceuaeupgrade$useCraftableStackAsLookupKey(Object key) {
        return key instanceof Map.Entry ? ((Map.Entry<?, ?>) key).getKey() : key;
    }
}
