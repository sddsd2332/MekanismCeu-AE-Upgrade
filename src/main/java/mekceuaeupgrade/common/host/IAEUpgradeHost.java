package mekceuaeupgrade.common.host;

import mekceuaeupgrade.common.item.AEUpgrade;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;

import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.util.AECableType;
import ae2.text.TextComponentItemStack;
import mekanism.common.base.IUpgradeTile;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IWorldNameable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public interface IAEUpgradeHost extends IInWorldGridNodeHost, IActionHost, ICraftingProvider {

    AEUpgradeNode getAEUpgradeNode();

    default boolean supportsAEUpgrade() {
        return supportsAECraftingUpgrade() || supportsAEOutputUpgrade() || supportsAEAutoProcessingUpgrade();
    }

    default boolean hasAEUpgrade() {
        return hasAECraftingUpgrade() || hasAEOutputUpgrade() || hasAEAutoProcessingUpgrade();
    }

    default boolean supportsAECraftingUpgrade() {
        return supportsAEWiredCraftingUpgrade() || supportsAEWirelessCraftingUpgrade();
    }

    default boolean hasAECraftingUpgrade() {
        return hasAEWiredCraftingUpgrade() || hasAEWirelessCraftingUpgrade();
    }

    default boolean supportsAEWiredCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_CRAFTING);
    }

    default boolean hasAEWiredCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_CRAFTING);
    }

    default boolean supportsAEWirelessCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_WIRELESS_CRAFTING);
    }

    default boolean hasAEWirelessCraftingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_WIRELESS_CRAFTING);
    }

    default boolean supportsAEOutputUpgrade() {
        return supportsAEWiredOutputUpgrade() || supportsAEWirelessOutputUpgrade();
    }

    default boolean hasAEOutputUpgrade() {
        return hasAEWiredOutputUpgrade() || hasAEWirelessOutputUpgrade();
    }

    default boolean supportsAEWiredOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_OUTPUT);
    }

    default boolean hasAEWiredOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_OUTPUT);
    }

    default boolean supportsAEWirelessOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_WIRELESS_OUTPUT);
    }

    default boolean hasAEWirelessOutputUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_WIRELESS_OUTPUT);
    }

    default boolean supportsAEAutoProcessingUpgrade() {
        return supportsAEWiredAutoProcessingUpgrade() || supportsAEWirelessAutoProcessingUpgrade();
    }

    default boolean hasAEAutoProcessingUpgrade() {
        return hasAEWiredAutoProcessingUpgrade() || hasAEWirelessAutoProcessingUpgrade();
    }

    default boolean supportsAEWiredAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_AUTO_PROCESSING);
    }

    default boolean hasAEWiredAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_AUTO_PROCESSING);
    }

    default boolean supportsAEWirelessAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.supportsUpgrade(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING);
    }

    default boolean hasAEWirelessAutoProcessingUpgrade() {
        return this instanceof IUpgradeTile tile && tile.isUpgradeInstalled(AEUpgrade.AE_WIRELESS_AUTO_PROCESSING);
    }

    default boolean shouldExposeAE() {
        if (!(this instanceof TileEntity tile)) {
            return false;
        }
        return supportsAEUpgrade() && hasAEUpgrade() && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid();
    }

    default boolean shouldExposeAECrafting() {
        return shouldExposeAE() && supportsAECraftingUpgrade() && hasAECraftingUpgrade();
    }

    default boolean shouldExposeAEWiredCrafting() {
        return shouldExposeAE() && supportsAEWiredCraftingUpgrade() && hasAEWiredCraftingUpgrade();
    }

    default boolean shouldExposeAEWirelessCrafting() {
        return shouldExposeAE() && supportsAEWirelessCraftingUpgrade() && hasAEWirelessCraftingUpgrade();
    }

    default boolean shouldExposeAEOutput() {
        return shouldExposeAE() && supportsAEOutputUpgrade() && hasAEOutputUpgrade();
    }

    default boolean shouldExposeAEWiredOutput() {
        return shouldExposeAE() && supportsAEWiredOutputUpgrade() && hasAEWiredOutputUpgrade();
    }

    default boolean shouldExposeAEWirelessOutput() {
        return shouldExposeAE() && supportsAEWirelessOutputUpgrade() && hasAEWirelessOutputUpgrade();
    }

    default boolean shouldExposeAEAutoProcessing() {
        return shouldExposeAE() && supportsAEAutoProcessingUpgrade() && hasAEAutoProcessingUpgrade();
    }

    default boolean shouldExposeAEWiredAutoProcessing() {
        return shouldExposeAE() && supportsAEWiredAutoProcessingUpgrade() && hasAEWiredAutoProcessingUpgrade();
    }

    default boolean shouldExposeAEWirelessAutoProcessing() {
        return shouldExposeAE() && supportsAEWirelessAutoProcessingUpgrade() && hasAEWirelessAutoProcessingUpgrade();
    }

    default void onAEUpgradeInstalled() {
        getAEUpgradeNode().activate();
    }

    default void onAEUpgradeRemoved() {
        getAEUpgradeNode().deactivate();
    }

    @Override
    @Nullable
    default IGridNode getGridNode(@Nonnull EnumFacing dir) {
        return getAEUpgradeNode().getGridNode(dir);
    }

    @Override
    @Nonnull
    default AECableType getCableConnectionType(@Nonnull EnumFacing dir) {
        return getAEUpgradeNode().getCableConnectionType(dir);
    }

    default void securityBreak() {
        if (this instanceof TileEntity tile && tile.getWorld() != null) {
            tile.getWorld().destroyBlock(tile.getPos(), true);
        }
    }

    @Override
    @Nullable
    default IGridNode getActionableNode() {
        return getAEUpgradeNode().getActionableNode();
    }

    @Override
    default List<AEExposedRecipe> getAvailablePatterns() {
        return shouldExposeAECrafting() ? getAEUpgradeNode().getAvailablePatterns() : List.of();
    }

    @Override
    default boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        return shouldExposeAECrafting() && getAEUpgradeNode().pushPattern(patternDetails, inputHolder, multiplier);
    }

    @Override
    default boolean canMergePatternPush(IPatternDetails patternDetails) {
        return false;
    }

    @Override
    default int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int maxMultiplier) {
        return 1;
    }

    @Override
    default boolean isBusy() {
        return shouldExposeAECrafting() ? getAEUpgradeNode().isBusy() : true;
    }

    @Override
    default PatternContainerGroup getTerminalGroup() {
        if (!(this instanceof TileEntity tile)) {
            return PatternContainerGroup.nothing();
        }
        ItemStack displayStack = getAEUpgradeMachineDisplayStack(tile);
        AEItemKey icon = AEItemKey.of(displayStack);
        ITextComponent name = getAEUpgradeMachineDisplayName(tile, displayStack);
        return new PatternContainerGroup(icon, name, Collections.emptyList());
    }

    static ItemStack getAEUpgradeMachineDisplayStack(TileEntity tile) {
        if (tile.getWorld() == null) {
            return ItemStack.EMPTY;
        }
        Item item = Item.getItemFromBlock(tile.getBlockType());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int metadata = 0;
        try {
            metadata = tile.getBlockType().getMetaFromState(tile.getWorld().getBlockState(tile.getPos()));
        } catch (RuntimeException | LinkageError ignored) {
            metadata = 0;
        }
        return new ItemStack(item, 1, metadata);
    }

    static ITextComponent getAEUpgradeMachineDisplayName(TileEntity tile, ItemStack displayStack) {
        if (tile instanceof IWorldNameable nameable) {
            if (nameable.hasCustomName()) {
                ITextComponent displayName = getAEUpgradeSafeDisplayName(nameable);
                if (displayName != null) {
                    return displayName;
                }
            }
            String name = getAEUpgradeSafeName(nameable);
            if (name != null && !name.isEmpty()) {
                return new TextComponentString(name);
            }
        }
        if (!displayStack.isEmpty()) {
            return TextComponentItemStack.of(displayStack);
        }
        try {
            return new TextComponentTranslation(tile.getBlockType().getTranslationKey() + ".name");
        } catch (RuntimeException | LinkageError ignored) {
            return new TextComponentString(tile.getClass().getSimpleName());
        }
    }

    @Nullable
    static ITextComponent getAEUpgradeSafeDisplayName(IWorldNameable nameable) {
        try {
            return nameable.getDisplayName();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static String getAEUpgradeSafeName(IWorldNameable nameable) {
        try {
            return nameable.getName();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
