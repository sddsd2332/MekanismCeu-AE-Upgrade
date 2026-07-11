package mekceuaeupgrade.common.config;

import com.github.bsideup.jabel.Desugar;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.base.IFactory;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.network.PacketAERecipeConfig.AERecipeConfigMessage;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class AERecipeProfileManager {

    public static final String CONFIG_CARD_TAG = "aeRecipeProfile";
    public static final String CONFIG_CARD_AUTO_PROCESSING_TAG = "aeAutoProcessingRecipeProfile";
    private static final String CONFIG_CARD_PROFILE_INDIVIDUAL_TAG = "aeRecipeProfileUseIndividual";
    private static final String CONFIG_CARD_PROFILE_GLOBAL_SLOT_TAG = "aeRecipeProfileGlobalSlot";
    private static final String CONFIG_CARD_PROFILE_FILTER_MODE_TAG = "aeRecipeProfileFilterMode";
    private static final String CONFIG_CARD_AUTO_PROCESSING_PROFILE_INDIVIDUAL_TAG = "aeAutoProcessingRecipeProfileUseIndividual";
    private static final String CONFIG_CARD_AUTO_PROCESSING_PROFILE_GLOBAL_SLOT_TAG = "aeAutoProcessingRecipeProfileGlobalSlot";

    private static final Map<ProfileIdentity, AERecipeProfile> profiles = new HashMap<>();
    private static final Set<IAEUpgradeHost> activeHosts = Collections.newSetFromMap(new WeakHashMap<>());

    private AERecipeProfileManager() {
    }

    @Nullable
    public static AERecipeProfile getProfile(IAEUpgradeHost host) {
        return getProfile(host, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    public static AERecipeProfile getProfile(IAEUpgradeHost host, AERecipeConfigType type) {
        if (!(host instanceof TileEntity tile)) {
            return null;
        }
        return getProfile(tile, null, false, type);
    }

    @Nullable
    public static AERecipeProfile getOrCreateProfile(TileEntity tile, @Nullable EntityPlayer player) {
        return getOrCreateProfile(tile, player, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    public static AERecipeProfile getOrCreateProfile(TileEntity tile, @Nullable EntityPlayer player, AERecipeConfigType type) {
        return getProfile(tile, player, true, type);
    }

    public static void ensureProfileOwner(TileEntity tile, @Nullable EntityPlayer player) {
        if (player != null && tile instanceof IAEUpgradeHost) {
            resolveIdentity(tile, player);
        }
    }

    public static void registerHost(IAEUpgradeHost host) {
        if (host != null) {
            activeHosts.add(host);
        }
    }

    public static void unregisterHost(IAEUpgradeHost host) {
        activeHosts.remove(host);
    }

    @Nullable
    private static AERecipeProfile getProfile(TileEntity tile, @Nullable EntityPlayer player, boolean create) {
        return getProfile(tile, player, create, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    private static AERecipeProfile getProfile(TileEntity tile, @Nullable EntityPlayer player, boolean create, AERecipeConfigType type) {
        ProfileIdentity identity = resolveIdentity(tile, player, type);
        return getProfile(identity, create, type);
    }

    @Nullable
    private static AERecipeProfile getProfile(TileEntity tile, @Nullable EntityPlayer player, boolean create, boolean individual) {
        return getProfile(tile, player, create, individual, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    private static AERecipeProfile getProfile(TileEntity tile, @Nullable EntityPlayer player, boolean create, boolean individual, AERecipeConfigType type) {
        ProfileIdentity identity = resolveIdentity(tile, player, individual, type);
        return getProfile(identity, create, type);
    }

    @Nullable
    private static AERecipeProfile getProfile(@Nullable ProfileIdentity identity, boolean create) {
        return getProfile(identity, create, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    private static AERecipeProfile getProfile(@Nullable ProfileIdentity identity, boolean create, AERecipeConfigType type) {
        if (identity == null) {
            return create ? new AERecipeProfile(type.getDefaultFilterMode()) : null;
        }
        AERecipeProfile profile = profiles.get(identity);
        if (profile == null) {
            profile = load(identity);
            profiles.put(identity, profile);
        }
        return profile;
    }

    public static AERecipeConfigSnapshot buildSnapshot(IAEUpgradeHost host, @Nullable EntityPlayer viewer) {
        return buildSnapshot(host, viewer, AERecipeConfigType.CRAFTING);
    }

    public static AERecipeConfigSnapshot buildSnapshot(IAEUpgradeHost host, @Nullable EntityPlayer viewer, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return AERecipeConfigSnapshot.EMPTY;
        }
        List<AEExposedRecipe> configurableRecipes = AEUpgradeRecipeCache.collectConfigurableRecipes(host);
        if (configurableRecipes.isEmpty()) {
            return AERecipeConfigSnapshot.EMPTY;
        }
        configurableRecipes.sort(Comparator.comparing(recipe -> recipe.getOutputStack().getDisplayName()));
        List<AEExposedRecipe> profileRecipes = new ArrayList<>();
        for (AEExposedRecipe recipe : configurableRecipes) {
            if (type.allowsSelfReferentialRoutes() || recipe.isExposableToCrafting()) {
                profileRecipes.add(recipe);
            }
        }
        AERecipeProfile profile = getOrCreateProfile(tile, viewer, type);
        if (profile != null && profile.prune(profileRecipes)) {
            save(tile, viewer, profile, false, type);
        }
        int craftAmount = profile == null ? AERecipeProfile.DEFAULT_CRAFT_AMOUNT : profile.getCraftAmount();
        Map<String, List<AEExposedRecipe>> grouped = new LinkedHashMap<>();
        for (AEExposedRecipe recipe : configurableRecipes) {
            grouped.computeIfAbsent(recipe.getRecipeKey().getOutputKey(), key -> new ArrayList<>()).add(recipe);
        }
        List<String> defaultProductOrder = new ArrayList<>(grouped.keySet());
        List<String> currentProductOrder = profile == null ? defaultProductOrder : profile.getCurrentProductOrder(defaultProductOrder);
        List<AERecipeConfigSnapshot.Product> products = new ArrayList<>();
        for (String outputKey : currentProductOrder) {
            List<AEExposedRecipe> group = grouped.get(outputKey);
            if (group == null || group.isEmpty()) {
                continue;
            }
            List<String> defaultOrder = new ArrayList<>();
            for (AEExposedRecipe recipe : group) {
                defaultOrder.add(recipe.getRecipeKey().getRouteKey());
            }
            List<String> currentOrder = profile == null ? defaultOrder : profile.getCurrentOrder(outputKey, defaultOrder);
            group.sort(Comparator.comparingInt(recipe -> currentOrder.indexOf(recipe.getRecipeKey().getRouteKey())));
            List<AERecipeConfigSnapshot.Route> routes = new ArrayList<>();
            int order = 0;
            for (AEExposedRecipe recipe : group) {
                boolean modifiable = type.allowsSelfReferentialRoutes() || recipe.isExposableToCrafting();
                routes.add(new AERecipeConfigSnapshot.Route(
                      recipe.getRecipeKey().getRouteKey(),
                      recipe.getInputStacks(),
                      recipe.getOutputStacks(),
                      modifiable && (profile == null ? type.areRoutesEnabledByDefault() : profile.isRouteEnabled(recipe)),
                      order++,
                      modifiable && profile != null ? profile.getEffectiveCraftAmount(recipe) : AERecipeProfile.DEFAULT_CRAFT_AMOUNT,
                      modifiable && profile != null && profile.hasRouteCraftAmount(recipe.getRecipeKey().getRouteKey()),
                      modifiable
                ));
            }
            products.add(new AERecipeConfigSnapshot.Product(
                  outputKey,
                  group.get(0).getOutputStacks(),
                  group.size() > 1,
                  routes
            ));
        }
        return new AERecipeConfigSnapshot(products, host.getAEUpgradeNode().isRecipeProfileIndividual(type),
              host.getAEUpgradeNode().getRecipeProfileGlobalSlot(type), craftAmount,
              host.getAEUpgradeNode().getRecipeProfileFilterMode(type), canEditConfiguration(tile, viewer));
    }

    public static boolean toggleProfileMode(IAEUpgradeHost host, EntityPlayer player) {
        return toggleProfileMode(host, player, AERecipeConfigType.CRAFTING);
    }

    public static boolean toggleProfileMode(IAEUpgradeHost host, EntityPlayer player, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        boolean individual = !host.getAEUpgradeNode().isRecipeProfileIndividual(type);
        boolean changed = host.getAEUpgradeNode().setRecipeProfileIndividual(type, individual);
        if (individual && !host.getAEUpgradeNode().isRecipeProfileIndividualInitialized(type)) {
            AERecipeProfile globalProfile = getProfile(tile, player, true, false, type);
            AERecipeProfile individualProfile = getProfile(tile, player, true, true, type);
            if (individualProfile != null) {
                AERecipeProfile copy = globalProfile == null ? new AERecipeProfile(type.getDefaultFilterMode()) : globalProfile.copy();
                host.getAEUpgradeNode().markRecipeProfileIndividualInitialized(type);
                if (individualProfile.replaceWith(copy)) {
                    save(tile, player, individualProfile, type);
                    return true;
                }
            }
        }
        ProfileIdentity identity = resolveIdentity(tile, player, type);
        if (identity != null) {
            notifyProfileChanged(identity);
        }
        return changed;
    }

    public static boolean cycleGlobalProfileSlot(IAEUpgradeHost host, EntityPlayer player) {
        return cycleGlobalProfileSlot(host, player, 1, AERecipeConfigType.CRAFTING);
    }

    public static boolean cycleGlobalProfileSlot(IAEUpgradeHost host, EntityPlayer player, int direction) {
        return cycleGlobalProfileSlot(host, player, direction, AERecipeConfigType.CRAFTING);
    }

    public static boolean cycleGlobalProfileSlot(IAEUpgradeHost host, EntityPlayer player, int direction, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile) || host.getAEUpgradeNode().isRecipeProfileIndividual(type)) {
            return false;
        }
        boolean changed = host.getAEUpgradeNode().cycleRecipeProfileGlobalSlot(type, direction);
        if (changed) {
            ProfileIdentity identity = resolveIdentity(tile, player, false, type);
            if (identity != null) {
                notifyProfileChanged(identity);
            }
        }
        return changed;
    }

    public static boolean toggleRouteFilterMode(IAEUpgradeHost host, EntityPlayer player) {
        return toggleRouteFilterMode(host, player, AERecipeConfigType.CRAFTING);
    }

    public static boolean toggleRouteFilterMode(IAEUpgradeHost host, EntityPlayer player, AERecipeConfigType type) {
        if (!type.isRouteFilterMutable() || !canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        AERecipeProfile.RouteFilterMode nextMode = host.getAEUpgradeNode().getRecipeProfileFilterMode(type).next();
        if (!host.getAEUpgradeNode().setRecipeProfileFilterMode(type, nextMode)) {
            return false;
        }
        if (host.getAEUpgradeNode().isRecipeProfileIndividual(type) &&
            !host.getAEUpgradeNode().isRecipeProfileIndividualInitialized(type, nextMode)) {
            AERecipeProfile globalProfile = getProfile(tile, player, true, false, type);
            AERecipeProfile individualProfile = getProfile(tile, player, true, true, type);
            host.getAEUpgradeNode().markRecipeProfileIndividualInitialized(type, nextMode);
            if (individualProfile != null) {
                AERecipeProfile copy = globalProfile == null ? new AERecipeProfile(nextMode) : globalProfile.copy();
                copy.setRouteFilterMode(nextMode);
                if (individualProfile.replaceWith(copy)) {
                    save(tile, player, individualProfile, false, type);
                }
            }
        }
        ProfileIdentity identity = resolveIdentity(tile, player, type);
        if (identity != null) {
            notifyProfileChanged(identity);
        }
        return true;
    }

    public static boolean toggleRoute(IAEUpgradeHost host, EntityPlayer player, String routeKey) {
        return toggleRoute(host, player, routeKey, AERecipeConfigType.CRAFTING);
    }

    public static boolean toggleRoute(IAEUpgradeHost host, EntityPlayer player, String routeKey, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        if (!hasRoute(host, routeKey, type)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean enabled = profile.isRouteEnabled(routeKey);
        boolean changed = profile.setRouteEnabled(routeKey, !enabled);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean setAllRoutesEnabled(IAEUpgradeHost host, EntityPlayer player, boolean enabled) {
        return setAllRoutesEnabled(host, player, enabled, AERecipeConfigType.CRAFTING);
    }

    public static boolean setAllRoutesEnabled(IAEUpgradeHost host, EntityPlayer player, boolean enabled, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getAllRouteKeys(host, type);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.setRoutesEnabled(defaultOrder, enabled);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean toggleProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey) {
        return toggleProduct(host, player, outputKey, AERecipeConfigType.CRAFTING);
    }

    public static boolean toggleProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey, type);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean allDisabled = true;
        for (String routeKey : defaultOrder) {
            if (profile.isRouteEnabled(routeKey)) {
                allDisabled = false;
                break;
            }
        }
        boolean changed = profile.setRoutesEnabled(defaultOrder, allDisabled);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean disableProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey) {
        return disableProduct(host, player, outputKey, AERecipeConfigType.CRAFTING);
    }

    public static boolean disableProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey, type);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.setRoutesEnabled(defaultOrder, false);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean moveProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey, int direction) {
        return moveProduct(host, player, outputKey, direction, AERecipeConfigType.CRAFTING);
    }

    public static boolean moveProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey, int direction, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultProductOrder(host, type);
        if (defaultOrder.isEmpty() || !defaultOrder.contains(outputKey)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveProduct(defaultOrder, outputKey, direction);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean moveProductToEdge(IAEUpgradeHost host, EntityPlayer player, String outputKey, boolean top) {
        return moveProductToEdge(host, player, outputKey, top, AERecipeConfigType.CRAFTING);
    }

    public static boolean moveProductToEdge(IAEUpgradeHost host, EntityPlayer player, String outputKey, boolean top, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultProductOrder(host, type);
        if (defaultOrder.isEmpty() || !defaultOrder.contains(outputKey)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveProductToEdge(defaultOrder, outputKey, top);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean moveRoute(IAEUpgradeHost host, EntityPlayer player, String outputKey, String routeKey, int direction) {
        return moveRoute(host, player, outputKey, routeKey, direction, AERecipeConfigType.CRAFTING);
    }

    public static boolean moveRoute(IAEUpgradeHost host, EntityPlayer player, String outputKey, String routeKey, int direction, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey, type);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveRoute(outputKey, defaultOrder, routeKey, direction);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean moveRouteToEdge(IAEUpgradeHost host, EntityPlayer player, String outputKey, String routeKey, boolean top) {
        return moveRouteToEdge(host, player, outputKey, routeKey, top, AERecipeConfigType.CRAFTING);
    }

    public static boolean moveRouteToEdge(IAEUpgradeHost host, EntityPlayer player, String outputKey, String routeKey, boolean top, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey, type);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveRouteToEdge(outputKey, defaultOrder, routeKey, top);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean setGlobalCraftAmount(IAEUpgradeHost host, EntityPlayer player, int amount) {
        return setGlobalCraftAmount(host, player, amount, AERecipeConfigType.CRAFTING);
    }

    public static boolean setGlobalCraftAmount(IAEUpgradeHost host, EntityPlayer player, int amount, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<AEExposedRecipe> defaultRecipes = collectProfileRecipes(host, type);
        if (defaultRecipes.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.prune(defaultRecipes);
        changed |= profile.setCraftAmount(amount, AERecipeProfile.getMaxCraftAmount(defaultRecipes));
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean setRouteCraftAmount(IAEUpgradeHost host, EntityPlayer player, String routeKey, int amount) {
        return setRouteCraftAmount(host, player, routeKey, amount, AERecipeConfigType.CRAFTING);
    }

    public static boolean setRouteCraftAmount(IAEUpgradeHost host, EntityPlayer player, String routeKey, int amount, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<AEExposedRecipe> defaultRecipes = collectProfileRecipes(host, type);
        AEExposedRecipe route = findRoute(defaultRecipes, routeKey);
        if (route == null) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.prune(defaultRecipes);
        changed |= profile.setRouteCraftAmount(routeKey, amount, route.getMaxCraftAmount());
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean clearRouteCraftAmount(IAEUpgradeHost host, EntityPlayer player, String routeKey) {
        return clearRouteCraftAmount(host, player, routeKey, AERecipeConfigType.CRAFTING);
    }

    public static boolean clearRouteCraftAmount(IAEUpgradeHost host, EntityPlayer player, String routeKey, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        if (!hasRoute(host, routeKey, type)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.clearRouteCraftAmount(routeKey);
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean resetProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey) {
        return resetProduct(host, player, outputKey, AERecipeConfigType.CRAFTING);
    }

    public static boolean resetProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey, type);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.resetProduct(outputKey, defaultOrder);
        List<String> defaultProductOrder = getDefaultProductOrder(host, type);
        if (!defaultProductOrder.isEmpty()) {
            changed |= profile.resetProductOrder(outputKey, defaultProductOrder);
        }
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    public static boolean resetAll(IAEUpgradeHost host, EntityPlayer player) {
        return resetAll(host, player, AERecipeConfigType.CRAFTING);
    }

    public static boolean resetAll(IAEUpgradeHost host, EntityPlayer player, AERecipeConfigType type) {
        if (!canConfigure(host, type) || !(host instanceof TileEntity tile)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player, type);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.resetAll();
        if (changed) {
            save(tile, player, profile, type);
        }
        return changed;
    }

    private static boolean hasRoute(IAEUpgradeHost host, String routeKey, AERecipeConfigType type) {
        return findRoute(collectProfileRecipes(host, type), routeKey) != null;
    }

    @Nullable
    private static AEExposedRecipe findRoute(List<AEExposedRecipe> recipes, String routeKey) {
        for (AEExposedRecipe recipe : recipes) {
            if (recipe.getRecipeKey().getRouteKey().equals(routeKey)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean canConfigure(IAEUpgradeHost host) {
        return canConfigure(host, AERecipeConfigType.CRAFTING);
    }

    private static boolean canConfigure(IAEUpgradeHost host, AERecipeConfigType type) {
        return host != null && type.isInstalledIn(host) && host instanceof IAEItemRecipeHost;
    }

    /**
     * 只有机器安全所有者可以修改 AE 配方配置。公开、受信任和管理员访问不会提升为配置所有权。
     */
    public static boolean canEditConfiguration(TileEntity tile, @Nullable EntityPlayer player) {
        if (tile == null || player == null) {
            return false;
        }
        UUID owner = getConfigurationOwner(tile);
        return owner != null && owner.equals(player.getUniqueID());
    }

    /**
     * 非所有者使用配置卡读取或写入时，仅从临时副本中移除 AE 配置，保留 Mekanism 原有配置。
     */
    public static NBTTagCompound filterConfigCardDataForPlayer(TileEntity tile, @Nullable EntityPlayer player, NBTTagCompound nbtTags) {
        if (nbtTags == null || canEditConfiguration(tile, player) ||
            !hasConfigCardProfileData(nbtTags, AERecipeConfigType.CRAFTING) &&
            !hasConfigCardProfileData(nbtTags, AERecipeConfigType.AUTO_PROCESSING)) {
            return nbtTags;
        }
        NBTTagCompound filtered = nbtTags.copy();
        filtered.removeTag(CONFIG_CARD_TAG);
        filtered.removeTag(CONFIG_CARD_AUTO_PROCESSING_TAG);
        filtered.removeTag(CONFIG_CARD_PROFILE_INDIVIDUAL_TAG);
        filtered.removeTag(CONFIG_CARD_PROFILE_GLOBAL_SLOT_TAG);
        filtered.removeTag(CONFIG_CARD_PROFILE_FILTER_MODE_TAG);
        filtered.removeTag(CONFIG_CARD_AUTO_PROCESSING_PROFILE_INDIVIDUAL_TAG);
        filtered.removeTag(CONFIG_CARD_AUTO_PROCESSING_PROFILE_GLOBAL_SLOT_TAG);
        return filtered;
    }

    public static NBTTagCompound writeConfigCardData(TileEntity tile, NBTTagCompound nbtTags) {
        writeConfigCardData(tile, nbtTags, AERecipeConfigType.CRAFTING);
        writeConfigCardData(tile, nbtTags, AERecipeConfigType.AUTO_PROCESSING);
        return nbtTags;
    }

    private static void writeConfigCardData(TileEntity tile, NBTTagCompound nbtTags, AERecipeConfigType type) {
        if (!canUseConfigCardProfile(tile, type)) {
            return;
        }
        IAEUpgradeHost host = (IAEUpgradeHost) tile;
        nbtTags.setBoolean(getConfigCardIndividualTag(type), host.getAEUpgradeNode().isRecipeProfileIndividual(type));
        nbtTags.setInteger(getConfigCardGlobalSlotTag(type), host.getAEUpgradeNode().getRecipeProfileGlobalSlot(type));
        if (type.isRouteFilterMutable()) {
            nbtTags.setString(CONFIG_CARD_PROFILE_FILTER_MODE_TAG, host.getAEUpgradeNode().getRecipeProfileFilterMode(type).name());
        }
        nbtTags.removeTag(getConfigCardTag(type));
        AERecipeProfile profile = getProfile(tile, null, false, type);
        if (profile != null && !profile.isEmpty()) {
            nbtTags.setTag(getConfigCardTag(type), profile.writeToNBT(new NBTTagCompound()));
        }
    }

    public static void clearWorld(World world) {
        if (world == null || world.isRemote || world.getSaveHandler() == null) {
            return;
        }
        String worldPath = world.getSaveHandler().getWorldDirectory().getAbsolutePath();
        profiles.keySet().removeIf(identity -> identity.worldPath().equals(worldPath));
        activeHosts.removeIf(host -> host instanceof TileEntity tile && tile.getWorld() == world);
    }

    public static void readConfigCardData(TileEntity tile, NBTTagCompound nbtTags) {
        if (!(tile instanceof IAEUpgradeHost)) {
            return;
        }
        readConfigCardData(tile, nbtTags, AERecipeConfigType.CRAFTING);
        readConfigCardData(tile, nbtTags, AERecipeConfigType.AUTO_PROCESSING);
    }

    private static void readConfigCardData(TileEntity tile, NBTTagCompound nbtTags, AERecipeConfigType type) {
        if (!canUseConfigCardProfile(tile, type) || !hasConfigCardProfileData(nbtTags, type)) {
            return;
        }
        boolean modeChanged = applyConfigCardFilterMode(tile, nbtTags, type);
        modeChanged |= applyConfigCardProfileMode(tile, nbtTags, type);
        AERecipeProfile profile = getOrCreateProfile(tile, null, type);
        if (profile == null) {
            return;
        }
        String tag = getConfigCardTag(type);
        AERecipeProfile.RouteFilterMode filterMode = ((IAEUpgradeHost) tile).getAEUpgradeNode().getRecipeProfileFilterMode(type);
        AERecipeProfile incoming = nbtTags.hasKey(tag) ? AERecipeProfile.read(nbtTags.getCompoundTag(tag), filterMode) :
              new AERecipeProfile(filterMode);
        incoming.setRouteFilterMode(filterMode);
        if (profile.replaceWith(incoming) || modeChanged) {
            save(tile, null, profile, type);
        }
    }

    private static boolean canUseConfigCardProfile(TileEntity tile, AERecipeConfigType type) {
        return tile instanceof IAEUpgradeHost host && host instanceof IAEItemRecipeHost && type.isSupportedBy(host);
    }

    private static boolean hasConfigCardProfileData(NBTTagCompound nbtTags, AERecipeConfigType type) {
        return nbtTags.hasKey(getConfigCardTag(type)) || nbtTags.hasKey(getConfigCardIndividualTag(type)) || nbtTags.hasKey(getConfigCardGlobalSlotTag(type)) ||
              type.isRouteFilterMutable() && nbtTags.hasKey(CONFIG_CARD_PROFILE_FILTER_MODE_TAG);
    }

    private static boolean applyConfigCardFilterMode(TileEntity tile, NBTTagCompound nbtTags, AERecipeConfigType type) {
        if (!type.isRouteFilterMutable() || !(tile instanceof IAEUpgradeHost host) || !nbtTags.hasKey(CONFIG_CARD_PROFILE_FILTER_MODE_TAG)) {
            return false;
        }
        AERecipeProfile.RouteFilterMode filterMode = AERecipeProfile.RouteFilterMode.fromName(nbtTags.getString(CONFIG_CARD_PROFILE_FILTER_MODE_TAG));
        return host.getAEUpgradeNode().setRecipeProfileFilterMode(type, filterMode);
    }

    private static boolean applyConfigCardProfileMode(TileEntity tile, NBTTagCompound nbtTags, AERecipeConfigType type) {
        if (!(tile instanceof IAEUpgradeHost host) || !nbtTags.hasKey(getConfigCardIndividualTag(type))) {
            return false;
        }
        boolean changed = false;
        boolean individual = nbtTags.getBoolean(getConfigCardIndividualTag(type));
        if (individual) {
            changed |= host.getAEUpgradeNode().setRecipeProfileIndividual(type, true);
            if (!host.getAEUpgradeNode().isRecipeProfileIndividualInitialized(type)) {
                host.getAEUpgradeNode().markRecipeProfileIndividualInitialized(type);
                changed = true;
            }
        } else {
            changed |= host.getAEUpgradeNode().setRecipeProfileIndividual(type, false);
            if (nbtTags.hasKey(getConfigCardGlobalSlotTag(type))) {
                changed |= host.getAEUpgradeNode().setRecipeProfileGlobalSlot(type, nbtTags.getInteger(getConfigCardGlobalSlotTag(type)));
            }
        }
        return changed;
    }

    private static String getConfigCardTag(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING ? CONFIG_CARD_AUTO_PROCESSING_TAG : CONFIG_CARD_TAG;
    }

    private static String getConfigCardIndividualTag(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING ? CONFIG_CARD_AUTO_PROCESSING_PROFILE_INDIVIDUAL_TAG : CONFIG_CARD_PROFILE_INDIVIDUAL_TAG;
    }

    private static String getConfigCardGlobalSlotTag(AERecipeConfigType type) {
        return type == AERecipeConfigType.AUTO_PROCESSING ? CONFIG_CARD_AUTO_PROCESSING_PROFILE_GLOBAL_SLOT_TAG : CONFIG_CARD_PROFILE_GLOBAL_SLOT_TAG;
    }

    private static List<String> getDefaultRouteOrder(IAEUpgradeHost host, String outputKey, AERecipeConfigType type) {
        List<String> routeKeys = new ArrayList<>();
        for (AEExposedRecipe recipe : collectProfileRecipes(host, type)) {
            if (recipe.getRecipeKey().getOutputKey().equals(outputKey)) {
                routeKeys.add(recipe.getRecipeKey().getRouteKey());
            }
        }
        return routeKeys;
    }

    private static List<String> getAllRouteKeys(IAEUpgradeHost host, AERecipeConfigType type) {
        List<String> routeKeys = new ArrayList<>();
        for (AEExposedRecipe recipe : collectProfileRecipes(host, type)) {
            String routeKey = recipe.getRecipeKey().getRouteKey();
            if (!routeKeys.contains(routeKey)) {
                routeKeys.add(routeKey);
            }
        }
        return routeKeys;
    }

    private static List<String> getDefaultProductOrder(IAEUpgradeHost host, AERecipeConfigType type) {
        List<AEExposedRecipe> recipes = collectProfileRecipes(host, type);
        recipes.sort(Comparator.comparing(recipe -> recipe.getOutputStack().getDisplayName()));
        List<String> outputKeys = new ArrayList<>();
        for (AEExposedRecipe recipe : recipes) {
            String outputKey = recipe.getRecipeKey().getOutputKey();
            if (!outputKeys.contains(outputKey)) {
                outputKeys.add(outputKey);
            }
        }
        return outputKeys;
    }

    private static List<AEExposedRecipe> collectProfileRecipes(IAEUpgradeHost host, AERecipeConfigType type) {
        return type.allowsSelfReferentialRoutes() ? AEUpgradeRecipeCache.collectConfigurableRecipes(host) :
              AEUpgradeRecipeCache.collectCraftingRecipes(host);
    }

    private static AERecipeProfile load(ProfileIdentity identity) {
        AERecipeProfile profile = new AERecipeProfile(identity.filterMode());
        File file = identity.file();
        if (!file.isFile()) {
            return profile;
        }
        try {
            profile.readFromNBT(CompressedStreamTools.read(file));
            profile.setRouteFilterMode(identity.filterMode());
        } catch (IOException | RuntimeException e) {
            MEKCeuAEUpgrade.logger.error("Failed to load AE recipe profile {}", file, e);
        }
        return profile;
    }

    private static void save(TileEntity tile, @Nullable EntityPlayer player, AERecipeProfile profile) {
        save(tile, player, profile, true, AERecipeConfigType.CRAFTING);
    }

    private static void save(TileEntity tile, @Nullable EntityPlayer player, AERecipeProfile profile, boolean notify) {
        save(tile, player, profile, notify, AERecipeConfigType.CRAFTING);
    }

    private static void save(TileEntity tile, @Nullable EntityPlayer player, AERecipeProfile profile, AERecipeConfigType type) {
        save(tile, player, profile, true, type);
    }

    private static void save(TileEntity tile, @Nullable EntityPlayer player, AERecipeProfile profile, boolean notify, AERecipeConfigType type) {
        ProfileIdentity identity = resolveIdentity(tile, player, type);
        if (identity == null) {
            return;
        }
        profiles.put(identity, profile);
        profile.setRouteFilterMode(identity.filterMode());
        File file = identity.file();
        if (profile.isEmpty()) {
            if (file.isFile() && !file.delete()) {
                MEKCeuAEUpgrade.logger.warn("Failed to delete empty AE recipe profile {}", file);
            }
            if (notify) {
                notifyProfileChanged(identity);
            }
            return;
        }
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            MEKCeuAEUpgrade.logger.warn("Failed to create AE recipe profile directory {}", parent);
            if (notify) {
                notifyProfileChanged(identity);
            }
            return;
        }
        try {
            CompressedStreamTools.safeWrite(profile.writeToNBT(new NBTTagCompound()), file);
        } catch (IOException | RuntimeException e) {
            MEKCeuAEUpgrade.logger.error("Failed to save AE recipe profile {}", file, e);
        }
        if (notify) {
            notifyProfileChanged(identity);
        }
    }

    private static void notifyProfileChanged(ProfileIdentity identity) {
        for (IAEUpgradeHost host : new ArrayList<>(activeHosts)) {
            if (!(host instanceof TileEntity tile) || tile.isInvalid() || tile.getWorld() == null || tile.getWorld().isRemote) {
                activeHosts.remove(host);
                continue;
            }
            ProfileIdentity hostIdentity = resolveIdentity(tile, null, identity.type());
            if (identity.equals(hostIdentity)) {
                host.getAEUpgradeNode().invalidateRecipeCache();
                syncOpenConfigWindows(host, tile, identity.type());
            }
        }
    }

    private static void syncOpenConfigWindows(IAEUpgradeHost host, TileEntity tile, AERecipeConfigType type) {
        if (!(tile instanceof TileEntityBasicBlock basic) || basic.playersUsing.isEmpty()) {
            return;
        }
        for (EntityPlayer user : basic.playersUsing) {
            if (user instanceof EntityPlayerMP userMP && PacketHandler.canAccessTile(user, tile, true)) {
                AERecipeConfigSnapshot snapshot = buildSnapshot(host, user, type);
                MEKCeuAEUpgrade.packetHandler.sendTo(AERecipeConfigMessage.snapshot(Coord4D.get(tile), snapshot, type), userMP);
            }
        }
    }

    @Nullable
    private static ProfileIdentity resolveIdentity(TileEntity tile, @Nullable EntityPlayer player) {
        return resolveIdentity(tile, player, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    private static ProfileIdentity resolveIdentity(TileEntity tile, @Nullable EntityPlayer player, AERecipeConfigType type) {
        boolean individual = tile instanceof IAEUpgradeHost host && host.getAEUpgradeNode().isRecipeProfileIndividual(type);
        return resolveIdentity(tile, player, individual, type);
    }

    @Nullable
    private static ProfileIdentity resolveIdentity(TileEntity tile, @Nullable EntityPlayer player, boolean individual) {
        return resolveIdentity(tile, player, individual, AERecipeConfigType.CRAFTING);
    }

    @Nullable
    private static ProfileIdentity resolveIdentity(TileEntity tile, @Nullable EntityPlayer player, boolean individual, AERecipeConfigType type) {
        World world = tile.getWorld();
        if (world == null || world.isRemote || world.getSaveHandler() == null) {
            return null;
        }
        UUID owner = resolveOwner(tile, player);
        if (owner == null) {
            return null;
        }
        File worldDirectory = world.getSaveHandler().getWorldDirectory();
        String machineId = resolveMachineId(tile);
        File ownerDirectory = new File(new File(new File(worldDirectory, "data"), "mek"), owner.toString());
        String machineDirectoryName = sanitize(machineId).toLowerCase(Locale.ROOT) + "_" + digest(machineId).substring(0, 12);
        File machineDirectory = new File(ownerDirectory, machineDirectoryName);
        File typeDirectory = new File(machineDirectory, type.getId());
        File directory = new File(typeDirectory, individual ? "single" : "global");
        AERecipeProfile.RouteFilterMode filterMode = tile instanceof IAEUpgradeHost host ? host.getAEUpgradeNode().getRecipeProfileFilterMode(type) :
              type.getDefaultFilterMode();
        if (type.isRouteFilterMutable()) {
            directory = new File(directory, filterMode.name().toLowerCase(Locale.ROOT));
        }
        String profileId = machineId + "/" + type.getId() + "/" + (individual ? "single" : "global") + "/" +
              (type.isRouteFilterMutable() ? filterMode.name().toLowerCase(Locale.ROOT) + "/" : "");
        String fileName;
        if (individual) {
            if (!(tile instanceof IAEUpgradeHost host)) {
                return null;
            }
            UUID instance = host.getAEUpgradeNode().getOrCreateRecipeProfileInstance(type);
            profileId += instance;
            fileName = instance + ".dat";
        } else if (tile instanceof IAEUpgradeHost host) {
            int slot = Math.max(AEUpgradeNode.MIN_GLOBAL_PROFILE_SLOT,
                  Math.min(AEUpgradeNode.MAX_GLOBAL_PROFILE_SLOT, host.getAEUpgradeNode().getRecipeProfileGlobalSlot(type)));
            profileId += slot;
            fileName = String.format(Locale.ROOT, "slot_%02d.dat", slot);
        } else {
            return null;
        }
        File file = new File(directory, fileName);
        return new ProfileIdentity(worldDirectory.getAbsolutePath(), owner, profileId, individual, type, filterMode, file);
    }

    @Nullable
    private static UUID resolveOwner(TileEntity tile, @Nullable EntityPlayer player) {
        if (tile instanceof ISecurityTile securityTile && securityTile.getSecurity().getOwnerUUID() != null) {
            return securityTile.getSecurity().getOwnerUUID();
        }
        if (tile instanceof IAEUpgradeHost host) {
            UUID fallbackOwner = host.getAEUpgradeNode().getRecipeProfileOwner();
            if (fallbackOwner != null) {
                return fallbackOwner;
            }
            if (player != null) {
                UUID playerId = player.getUniqueID();
                host.getAEUpgradeNode().setRecipeProfileOwner(playerId);
                return playerId;
            }
        }
        return player == null ? null : player.getUniqueID();
    }

    @Nullable
    private static UUID getConfigurationOwner(TileEntity tile) {
        if (tile instanceof ISecurityTile securityTile) {
            return securityTile.getSecurity().getOwnerUUID();
        }
        if (tile instanceof IAEUpgradeHost host) {
            return host.getAEUpgradeNode().getRecipeProfileOwner();
        }
        return null;
    }

    private static String resolveMachineId(TileEntity tile) {
        Block block = tile.getBlockType();
        ResourceLocation registryName = block == null ? null : block.getRegistryName();
        String base = registryName == null ? tile.getClass().getName() : registryName.toString();
        MachineType machineType = MachineType.get(block, tile.getBlockMetadata());
        if (machineType != null) {
            base += "/" + machineType.getName();
        } else {
            base += "/" + tile.getBlockMetadata();
        }
        if (tile instanceof TileEntityFactory factory) {
            IFactory.RecipeType recipeType = factory.getRecipeType();
            base += "/" + recipeType.getName();
        }
        return base;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >>> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash AE recipe profile id.", e);
        }
    }

    @Desugar
    private record ProfileIdentity(String worldPath, UUID owner, String profileId, boolean individual, AERecipeConfigType type,
                                   AERecipeProfile.RouteFilterMode filterMode, File file) {
    }
}
