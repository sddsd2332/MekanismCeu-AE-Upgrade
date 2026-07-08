package mekceuaeupgrade.mixin.mekanism;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEFactoryProcessView;
import mekceuaeupgrade.common.host.AEUpgradeHostDelegate;
import mekceuaeupgrade.common.host.IAEFactoryRecipeHost;
import mekceuaeupgrade.common.host.IAEUpgradeHostBridge;
import mekceuaeupgrade.common.item.AEUpgrade;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.common.InfuseStorage;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.tile.factory.TileEntityFactory;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(value = TileEntityFactory.class, remap = false)
public abstract class MixinTileEntityFactory implements IAEFactoryRecipeHost, IAEUpgradeHostBridge {

    @Shadow
    private RecipeType recipeType;
    @Shadow
    private InputInventorySlot extraSlot;
    @Shadow
    private BasicGasTank gasTank;
    @Shadow
    private BasicGasTank gasOutTank;
    @Shadow
    private BasicFluidTank fluidTank;
    @Shadow
    @Final
    private InfuseStorage infuseStored;
    @Shadow
    private int maxInfuse;
    @Shadow
    private long baseTotalUsage;
    @Shadow
    private double secondaryEnergyPerTick;

    @Unique
    private AEUpgradeHostDelegate mekceuaeupgrade$aeUpgrade;
    @Unique
    private Object[] mekceuaeupgrade$cachedRawProcesses;
    @Unique
    private IAEFactoryRecipeHost.ProcessView[] mekceuaeupgrade$cachedProcesses;

    @Override
    public AEUpgradeHostDelegate mekceuaeupgrade$getAEUpgradeDelegate() {
        if (mekceuaeupgrade$aeUpgrade == null) {
            mekceuaeupgrade$aeUpgrade = new AEUpgradeHostDelegate(this);
        }
        return mekceuaeupgrade$aeUpgrade;
    }

    public void onRecipeCacheInvalidated(int cacheIndex) {
        mekceuaeupgrade$invalidateAERecipeCache();
    }

    @Inject(method = "getConfigurationData", at = @At("RETURN"), cancellable = true)
    private void mekceuaeupgrade$getConfigurationData(NBTTagCompound nbtTags, CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.setReturnValue(AERecipeProfileManager.writeConfigCardData(mekceuaeupgrade$self(), cir.getReturnValue()));
    }

    @Inject(method = "setConfigurationData", at = @At("TAIL"))
    private void mekceuaeupgrade$setConfigurationData(NBTTagCompound nbtTags, CallbackInfo ci) {
        AERecipeProfileManager.readConfigCardData(mekceuaeupgrade$self(), nbtTags);
    }

    @Override
    @Nonnull
    public RecipeType getAEFactoryRecipeType() {
        return recipeType;
    }

    @Override
    @Nullable
    public IAEFactoryRecipeHost.ProcessView[] getAEFactoryProcesses() {
        Object[] rawProcesses = mekceuaeupgrade$getRawProcessInfoSlots();
        if (rawProcesses == null) {
            return null;
        }
        if (rawProcesses == mekceuaeupgrade$cachedRawProcesses && mekceuaeupgrade$cachedProcesses != null) {
            return mekceuaeupgrade$cachedProcesses;
        }
        IAEFactoryRecipeHost.ProcessView[] views = new IAEFactoryRecipeHost.ProcessView[rawProcesses.length];
        for (int i = 0; i < rawProcesses.length; i++) {
            Object rawProcess = rawProcesses[i];
            if (rawProcess == null) {
                return null;
            }
            IAEFactoryRecipeHost.ProcessView view = mekceuaeupgrade$wrapProcess(rawProcess);
            if (view == null) {
                return null;
            }
            views[i] = view;
        }
        mekceuaeupgrade$cachedRawProcesses = rawProcesses;
        mekceuaeupgrade$cachedProcesses = views;
        return views;
    }

    @Override
    @Nullable
    public BasicInventorySlot getAEFactoryExtraSlot() {
        return extraSlot;
    }

    @Override
    @Nullable
    public BasicGasTank getAEFactoryGasTank() {
        return gasTank;
    }

    @Override
    @Nullable
    public BasicGasTank getAEFactoryGasOutputTank() {
        return gasOutTank;
    }

    @Override
    @Nullable
    public BasicFluidTank getAEFactoryFluidTank() {
        return fluidTank;
    }

    @Override
    @Nonnull
    public InfuseStorage getAEFactoryInfuseStorage() {
        return infuseStored;
    }

    @Override
    public int getAEFactoryMaxInfuse() {
        return maxInfuse;
    }

    @Override
    public int getAEFactoryGasUsagePerOperation() {
        long usage = mekceuaeupgrade$usesStatisticalSecondaryFuel() ? mekceuaeupgrade$getStatisticalGasUsageLimit() : baseTotalUsage;
        return Math.max(1, MathUtils.clampToInt(usage));
    }

    @Override
    @Nullable
    public MachineRecipe<?, ?, ?> getAEFactoryRecipeForProcessInput(IAEFactoryRecipeHost.ProcessView processInfo, ItemStack fallbackInput,
          boolean updateCache) {
        Object rawProcess = processInfo instanceof AEFactoryProcessView ? ((AEFactoryProcessView) processInfo).rawProcess() : processInfo;
        return mekceuaeupgrade$invokeFactoryRecipeForProcess(rawProcess, fallbackInput, updateCache);
    }

    @Override
    @Nullable
    public MachineRecipe<?, ?, ?> findAEFactoryRecipeForInput(ItemStack fallbackInput, ItemStack extra) {
        return mekceuaeupgrade$invokeFactoryMethod("findRecipeForInput",
              new Class<?>[]{ItemStack.class, ItemStack.class}, fallbackInput, extra);
    }

    @Override
    @Nullable
    public MachineRecipe<?, ?, ?> getAEFactoryRecipe(MachineInput<?> input) {
        return mekceuaeupgrade$invokeRecipeHandler("getFactoryRecipe",
              new Class<?>[]{TileEntityFactory.class, MachineInput.class}, mekceuaeupgrade$self(), input);
    }

    @Override
    public boolean hasAEFactoryRecipeForExtra(@Nonnull ItemStack stack) {
        Boolean result = mekceuaeupgrade$invokeRecipeHandler("hasRecipeForExtra",
              new Class<?>[]{TileEntityFactory.class, ItemStack.class}, mekceuaeupgrade$self(), stack);
        return result != null && result;
    }

    @Override
    public boolean hasAEFactoryPartialPressurizedRecipeInput(ItemStack itemStack, @Nullable FluidStack fluidStack, @Nullable GasStack gasStack) {
        Boolean result = mekceuaeupgrade$invokeRecipeHandler("hasPartialPressurizedRecipeInput",
              new Class<?>[]{TileEntityFactory.class, ItemStack.class, FluidStack.class, GasStack.class}, mekceuaeupgrade$self(), itemStack, fluidStack,
              gasStack);
        return result != null && result;
    }

    @Override
    public boolean isAEFactoryInputGasValid(Gas gas) {
        Boolean result = mekceuaeupgrade$invokeRecipeHandler("isValidInputGas",
              new Class<?>[]{TileEntityFactory.class, Gas.class}, mekceuaeupgrade$self(), gas);
        return result != null && result;
    }

    @Override
    public boolean isAEFactoryRecipeCurrent(MachineRecipe<?, ?, ?> recipe) {
        Boolean result = mekceuaeupgrade$invokeRecipeHandler("matchesRecipe", new Class<?>[]{MachineRecipe.class}, recipe);
        return result != null && result;
    }

    @Override
    public ItemStack getAEFactoryPrimaryRecipeOutput(MachineRecipe<?, ?, ?> recipe) {
        ItemStack result = mekceuaeupgrade$invokeRecipeHandler("getPrimaryRecipeOutput", new Class<?>[]{MachineRecipe.class}, recipe);
        return result == null ? ItemStack.EMPTY : result;
    }

    @Override
    public ItemStack getAEFactorySecondaryRecipeOutput(MachineRecipe<?, ?, ?> recipe) {
        ItemStack result = mekceuaeupgrade$invokeRecipeHandler("getSecondaryRecipeOutput", new Class<?>[]{MachineRecipe.class}, recipe);
        return result == null ? ItemStack.EMPTY : result;
    }

    @Unique
    private long mekceuaeupgrade$getStatisticalGasUsageLimit() {
        int ticks = Math.max(1, mekceuaeupgrade$invokeGetTicksRequired());
        long maxPerTick = 3L * Math.max(1, (long) Math.ceil(Math.max(secondaryEnergyPerTick, 0)));
        return maxPerTick * ticks;
    }

    @Unique
    private int mekceuaeupgrade$invokeGetTicksRequired() {
        Integer ticks = mekceuaeupgrade$invokeFactoryMethod("getTicksRequired", new Class<?>[]{MachineRecipe.class}, new Object[]{null});
        return ticks == null ? 1 : ticks;
    }

    @Unique
    private boolean mekceuaeupgrade$usesStatisticalSecondaryFuel() {
        Boolean result = mekceuaeupgrade$invokeFactoryMethod("usesStatisticalSecondaryFuel", new Class<?>[0]);
        return result != null && result;
    }

    @Unique
    @Nullable
    private Object[] mekceuaeupgrade$getRawProcessInfoSlots() {
        try {
            Field field = TileEntityFactory.class.getDeclaredField("processInfoSlots");
            field.setAccessible(true);
            Object raw = field.get(this);
            return raw instanceof Object[] ? (Object[]) raw : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Unique
    @Nullable
    private IAEFactoryRecipeHost.ProcessView mekceuaeupgrade$wrapProcess(Object rawProcess) {
        try {
            return new AEFactoryProcessView(rawProcess);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Unique
    @Nullable
    private MachineRecipe<?, ?, ?> mekceuaeupgrade$invokeFactoryRecipeForProcess(Object rawProcess, ItemStack fallbackInput, boolean updateCache) {
        if (rawProcess == null) {
            return null;
        }
        return mekceuaeupgrade$invokeFactoryMethod("getRecipeForInput",
              new Class<?>[]{rawProcess.getClass(), ItemStack.class, boolean.class}, rawProcess, fallbackInput, updateCache);
    }

    @Unique
    @Nullable
    private Object mekceuaeupgrade$getRecipeHandler() {
        try {
            Field field = TileEntityFactory.class.getDeclaredField("recipeHandler");
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Unique
    @Nullable
    @SuppressWarnings("unchecked")
    private <T> T mekceuaeupgrade$invokeRecipeHandler(String methodName, Class<?>[] parameterTypes, Object... args) {
        Object handler = mekceuaeupgrade$getRecipeHandler();
        return handler == null ? null : (T) mekceuaeupgrade$invoke(methodName, handler.getClass(), handler, parameterTypes, args);
    }

    @Unique
    @Nullable
    @SuppressWarnings("unchecked")
    private <T> T mekceuaeupgrade$invokeFactoryMethod(String methodName, Class<?>[] parameterTypes, Object... args) {
        return (T) mekceuaeupgrade$invoke(methodName, TileEntityFactory.class, this, parameterTypes, args);
    }

    @Unique
    @Nullable
    private Object mekceuaeupgrade$invoke(String methodName, Class<?> owner, Object target, Class<?>[] parameterTypes, Object... args) {
        Method method = mekceuaeupgrade$findMethod(owner, methodName, parameterTypes);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    @Unique
    @Nullable
    private Method mekceuaeupgrade$findMethod(Class<?> owner, String methodName, Class<?>[] parameterTypes) {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Unique
    private TileEntityFactory mekceuaeupgrade$self() {
        return (TileEntityFactory) (Object) this;
    }

}
