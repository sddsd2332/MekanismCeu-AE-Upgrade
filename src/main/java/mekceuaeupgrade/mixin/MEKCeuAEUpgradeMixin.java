package mekceuaeupgrade.mixin;

import net.minecraftforge.fml.common.Loader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

@SuppressWarnings({"unused", "SameParameterValue"})
public class MEKCeuAEUpgradeMixin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("mekceuaeupgrade.mixin.mekceumoremachine.")) {
            return Loader.isModLoaded("mekceumoremachine");
        }
        if (mixinClassName.startsWith("mekceuaeupgrade.mixin.multiblockmachine.")) {
            return Loader.isModLoaded("mekanismmultiblockmachine");
        }
        if (mixinClassName.startsWith("mekceuaeupgrade.mixin.mekanism.")) {
            return Loader.isModLoaded("mekanism");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
