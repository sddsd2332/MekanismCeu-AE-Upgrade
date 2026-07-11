package mekceuaeupgrade.common.host;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.InfuseStorage;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekceuaeupgrade.common.adapter.AEFactoryRecipeAdapter;
import mekceuaeupgrade.common.adapter.IAERecipeMachineAdapter;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IAEFactoryRecipeHost extends IAERecipeMachineHost {

    @Override
    default IAERecipeMachineAdapter getAERecipeMachineAdapter() {
        return AEFactoryRecipeAdapter.INSTANCE;
    }

    @Nonnull
    RecipeType getAEFactoryRecipeType();

    @Nullable
    ProcessView[] getAEFactoryProcesses();

    @Nullable
    BasicInventorySlot getAEFactoryExtraSlot();

    @Nullable
    BasicGasTank getAEFactoryGasTank();

    @Nullable
    BasicGasTank getAEFactoryGasOutputTank();

    @Nullable
    BasicFluidTank getAEFactoryFluidTank();

    @Nonnull
    InfuseStorage getAEFactoryInfuseStorage();

    int getAEFactoryMaxInfuse();

    int getAEFactoryGasUsagePerOperation();

    @Nullable
    MachineRecipe<?, ?, ?> getAEFactoryRecipeForProcessInput(ProcessView processInfo, ItemStack fallbackInput, boolean updateCache);

    @Nullable
    MachineRecipe<?, ?, ?> findAEFactoryRecipeForInput(ItemStack fallbackInput, ItemStack extra);

    @Nullable
    MachineRecipe<?, ?, ?> getAEFactoryRecipe(MachineInput<?> input);

    boolean hasAEFactoryRecipeForExtra(@Nonnull ItemStack stack);

    boolean hasAEFactoryPartialPressurizedRecipeInput(ItemStack itemStack, @Nullable FluidStack fluidStack, @Nullable GasStack gasStack);

    boolean isAEFactoryInputGasValid(Gas gas);

    boolean isAEFactoryRecipeCurrent(MachineRecipe<?, ?, ?> recipe);

    ItemStack getAEFactoryPrimaryRecipeOutput(MachineRecipe<?, ?, ?> recipe);

    ItemStack getAEFactorySecondaryRecipeOutput(MachineRecipe<?, ?, ?> recipe);

    interface ProcessView {

        int process();

        @Nonnull
        BasicInventorySlot inputSlot();

        @Nonnull
        IInventorySlot outputSlot();

        @Nullable
        IInventorySlot secondaryOutputSlot();
    }
}
