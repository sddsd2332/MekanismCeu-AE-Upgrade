package mekceuaeupgrade.common.host;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class AEFactoryProcessView implements IAEFactoryRecipeHost.ProcessView {

    private final Object rawProcess;
    private final int process;
    private final BasicInventorySlot inputSlot;
    private final IInventorySlot outputSlot;
    @Nullable
    private final IInventorySlot secondaryOutputSlot;

    public AEFactoryProcessView(Object rawProcess) throws ReflectiveOperationException {
        this.rawProcess = rawProcess;
        Class<?> processClass = rawProcess.getClass();
        process = ((Integer) invoke(processClass, rawProcess, "process")).intValue();
        inputSlot = (BasicInventorySlot) invoke(processClass, rawProcess, "inputSlot");
        outputSlot = (IInventorySlot) invoke(processClass, rawProcess, "outputSlot");
        secondaryOutputSlot = (IInventorySlot) invoke(processClass, rawProcess, "secondaryOutputSlot");
    }

    public Object rawProcess() {
        return rawProcess;
    }

    @Override
    public int process() {
        return process;
    }

    @Override
    @Nonnull
    public BasicInventorySlot inputSlot() {
        return inputSlot;
    }

    @Override
    @Nonnull
    public IInventorySlot outputSlot() {
        return outputSlot;
    }

    @Override
    @Nullable
    public IInventorySlot secondaryOutputSlot() {
        return secondaryOutputSlot;
    }

    private static Object invoke(Class<?> owner, Object target, String methodName) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
