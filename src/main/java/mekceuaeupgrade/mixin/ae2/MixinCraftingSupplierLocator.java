package mekceuaeupgrade.mixin.ae2;

import mekceuaeupgrade.common.host.IAEUpgradeHost;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEKey;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.crafting.execution.CraftingSupplierLocator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Mixin(value = CraftingSupplierLocator.class, remap = false)
public abstract class MixinCraftingSupplierLocator {

    @Inject(
          method = "resolveLocation(Lae2/api/networking/IGrid;Lae2/api/networking/crafting/ICraftingProvider;)Lae2/crafting/execution/CraftingSupplierLocation;",
          at = @At("HEAD"),
          cancellable = true
    )
    private static void mekceuaeupgrade$resolveWirelessProviderLocation(IGrid grid, ICraftingProvider provider,
          CallbackInfoReturnable<CraftingSupplierLocation> cir) {
        CraftingSupplierLocation location = mekceuaeupgrade$getWirelessProviderLocation(grid, provider);
        if (location != null) {
            cir.setReturnValue(location);
        }
    }

    @Inject(
          method = "collectMatchingProviderLocations(Lae2/api/networking/IGrid;Lae2/api/stacks/AEKey;Ljava/lang/Iterable;Ljava/util/function/Function;)Ljava/util/List;",
          at = @At("RETURN"),
          cancellable = true
    )
    private static void mekceuaeupgrade$addWirelessProviderLocations(IGrid grid, AEKey target,
          Iterable<IPatternDetails> patterns,
          Function<IPatternDetails, Iterable<ICraftingProvider>> providersForPattern,
          CallbackInfoReturnable<List<CraftingSupplierLocation>> cir) {
        List<CraftingSupplierLocation> existing = cir.getReturnValue();
        Set<CraftingSupplierLocation> locations = new LinkedHashSet<>();
        if (existing != null) {
            locations.addAll(existing);
        }
        int originalSize = locations.size();
        for (IPatternDetails pattern : patterns) {
            if (!CraftingSupplierLocator.patternProducesTarget(pattern, target)) {
                continue;
            }
            for (ICraftingProvider provider : providersForPattern.apply(pattern)) {
                CraftingSupplierLocation location = mekceuaeupgrade$getWirelessProviderLocation(grid, provider);
                if (location != null) {
                    locations.add(location);
                }
            }
        }
        if (locations.size() != originalSize) {
            cir.setReturnValue(new ArrayList<>(locations));
        }
    }

    @Nullable
    private static CraftingSupplierLocation mekceuaeupgrade$getWirelessProviderLocation(IGrid grid,
          ICraftingProvider provider) {
        if (!(provider instanceof IAEUpgradeHost host) || !(provider instanceof TileEntity tile)
              || !host.getAEUpgradeNode().isWirelessCraftingProviderFor(grid)) {
            return null;
        }
        World world = tile.getWorld();
        if (world == null || tile.isInvalid()) {
            return null;
        }
        BlockPos pos = tile.getPos();
        return new CraftingSupplierLocation(
              world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ());
    }
}
