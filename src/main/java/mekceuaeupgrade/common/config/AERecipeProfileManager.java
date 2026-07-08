package mekceuaeupgrade.common.config;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.AEUpgradeNode;
import mekceuaeupgrade.common.host.IAEItemRecipeHost;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.recipe.AEExposedRecipe;
import mekceuaeupgrade.common.recipe.AEUpgradeRecipeCache;

import mekceuaeupgrade.common.network.PacketAERecipeConfig.AERecipeConfigMessage;
import mekanism.common.PacketHandler;
import mekanism.common.base.IFactory;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import mekanism.api.Coord4D;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class AERecipeProfileManager {

    public static final String CONFIG_CARD_TAG = "aeRecipeProfile";

    private static final Map<ProfileIdentity, AERecipeProfile> profiles = new HashMap<>();
    private static final Set<IAEUpgradeHost> activeHosts = Collections.newSetFromMap(new WeakHashMap<>());

    private AERecipeProfileManager() {
    }

    @Nullable
    public static AERecipeProfile getProfile(IAEUpgradeHost host) {
        if (!(host instanceof TileEntity tile)) {
            return null;
        }
        return getProfile(tile, null, false);
    }

    @Nullable
    public static AERecipeProfile getOrCreateProfile(TileEntity tile, @Nullable EntityPlayer player) {
        return getProfile(tile, player, true);
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
        ProfileIdentity identity = resolveIdentity(tile, player);
        return getProfile(identity, create);
    }

    @Nullable
    private static AERecipeProfile getProfile(TileEntity tile, @Nullable EntityPlayer player, boolean create, boolean individual) {
        ProfileIdentity identity = resolveIdentity(tile, player, individual);
        return getProfile(identity, create);
    }

    @Nullable
    private static AERecipeProfile getProfile(@Nullable ProfileIdentity identity, boolean create) {
        if (identity == null) {
            return create ? new AERecipeProfile() : null;
        }
        AERecipeProfile profile = profiles.get(identity);
        if (profile == null) {
            profile = load(identity);
            profiles.put(identity, profile);
        }
        return profile;
    }

    public static AERecipeConfigSnapshot buildSnapshot(IAEUpgradeHost host, @Nullable EntityPlayer viewer) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return AERecipeConfigSnapshot.EMPTY;
        }
        List<AEExposedRecipe> defaultRecipes = AEUpgradeRecipeCache.collectExposableRecipes(host);
        if (defaultRecipes.isEmpty()) {
            return AERecipeConfigSnapshot.EMPTY;
        }
        defaultRecipes.sort(Comparator.comparing(recipe -> recipe.getOutputStack().getDisplayName()));
        AERecipeProfile profile = getOrCreateProfile(tile, viewer);
        if (profile != null && profile.prune(defaultRecipes)) {
            save(tile, viewer, profile, false);
        }
        int craftAmount = profile == null ? AERecipeProfile.DEFAULT_CRAFT_AMOUNT : profile.getCraftAmount();
        Map<String, List<AEExposedRecipe>> grouped = new LinkedHashMap<>();
        for (AEExposedRecipe recipe : defaultRecipes) {
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
                routes.add(new AERecipeConfigSnapshot.Route(
                      recipe.getRecipeKey().getRouteKey(),
                      recipe.getInputStacks(),
                      recipe.getOutputStacks(),
                      profile == null || profile.isRouteEnabled(recipe),
                      order++,
                      profile == null ? AERecipeProfile.DEFAULT_CRAFT_AMOUNT : profile.getEffectiveCraftAmount(recipe),
                      profile != null && profile.hasRouteCraftAmount(recipe.getRecipeKey().getRouteKey())
                ));
            }
            products.add(new AERecipeConfigSnapshot.Product(
                  outputKey,
                  group.get(0).getOutputStacks(),
                  group.size() > 1,
                  routes
            ));
        }
        return new AERecipeConfigSnapshot(products, host.getAEUpgradeNode().isRecipeProfileIndividual(),
              host.getAEUpgradeNode().getRecipeProfileGlobalSlot(), craftAmount,
              profile == null ? AERecipeProfile.RouteFilterMode.BLACKLIST : profile.getRouteFilterMode());
    }

    public static boolean toggleProfileMode(IAEUpgradeHost host, EntityPlayer player) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        boolean individual = !host.getAEUpgradeNode().isRecipeProfileIndividual();
        boolean changed = host.getAEUpgradeNode().setRecipeProfileIndividual(individual);
        if (individual && !host.getAEUpgradeNode().isRecipeProfileIndividualInitialized()) {
            AERecipeProfile globalProfile = getProfile(tile, player, true, false);
            AERecipeProfile individualProfile = getProfile(tile, player, true, true);
            if (individualProfile != null) {
                AERecipeProfile copy = globalProfile == null ? new AERecipeProfile() : globalProfile.copy();
                host.getAEUpgradeNode().markRecipeProfileIndividualInitialized();
                if (individualProfile.replaceWith(copy)) {
                    save(tile, player, individualProfile);
                    return true;
                }
            }
        }
        ProfileIdentity identity = resolveIdentity(tile, player);
        if (identity != null) {
            notifyProfileChanged(identity);
        }
        return changed;
    }

    public static boolean cycleGlobalProfileSlot(IAEUpgradeHost host, EntityPlayer player) {
        return cycleGlobalProfileSlot(host, player, 1);
    }

    public static boolean cycleGlobalProfileSlot(IAEUpgradeHost host, EntityPlayer player, int direction) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile) || host.getAEUpgradeNode().isRecipeProfileIndividual()) {
            return false;
        }
        boolean changed = host.getAEUpgradeNode().cycleRecipeProfileGlobalSlot(direction);
        if (changed) {
            ProfileIdentity identity = resolveIdentity(tile, player, false);
            if (identity != null) {
                notifyProfileChanged(identity);
            }
        }
        return changed;
    }

    public static boolean toggleRouteFilterMode(IAEUpgradeHost host, EntityPlayer player) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.toggleRouteFilterMode();
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean toggleRoute(IAEUpgradeHost host, EntityPlayer player, String routeKey) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        if (!hasRoute(host, routeKey)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean enabled = profile.isRouteEnabled(routeKey);
        boolean changed = profile.setRouteEnabled(routeKey, !enabled);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean setAllRoutesEnabled(IAEUpgradeHost host, EntityPlayer player, boolean enabled) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getAllRouteKeys(host);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.setRoutesEnabled(defaultOrder, enabled);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean toggleProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
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
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean disableProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.setRoutesEnabled(defaultOrder, false);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean moveProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey, int direction) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultProductOrder(host);
        if (defaultOrder.isEmpty() || !defaultOrder.contains(outputKey)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveProduct(defaultOrder, outputKey, direction);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean moveProductToEdge(IAEUpgradeHost host, EntityPlayer player, String outputKey, boolean top) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultProductOrder(host);
        if (defaultOrder.isEmpty() || !defaultOrder.contains(outputKey)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveProductToEdge(defaultOrder, outputKey, top);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean moveRoute(IAEUpgradeHost host, EntityPlayer player, String outputKey, String routeKey, int direction) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveRoute(outputKey, defaultOrder, routeKey, direction);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean moveRouteToEdge(IAEUpgradeHost host, EntityPlayer player, String outputKey, String routeKey, boolean top) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.moveRouteToEdge(outputKey, defaultOrder, routeKey, top);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean setGlobalCraftAmount(IAEUpgradeHost host, EntityPlayer player, int amount) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<AEExposedRecipe> defaultRecipes = AEUpgradeRecipeCache.collectExposableRecipes(host);
        if (defaultRecipes.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.prune(defaultRecipes);
        changed |= profile.setCraftAmount(amount, AERecipeProfile.getMaxCraftAmount(defaultRecipes));
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean setRouteCraftAmount(IAEUpgradeHost host, EntityPlayer player, String routeKey, int amount) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<AEExposedRecipe> defaultRecipes = AEUpgradeRecipeCache.collectExposableRecipes(host);
        AEExposedRecipe route = findRoute(defaultRecipes, routeKey);
        if (route == null) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.prune(defaultRecipes);
        changed |= profile.setRouteCraftAmount(routeKey, amount, route.getMaxCraftAmount());
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean clearRouteCraftAmount(IAEUpgradeHost host, EntityPlayer player, String routeKey) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        if (!hasRoute(host, routeKey)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.clearRouteCraftAmount(routeKey);
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean resetProduct(IAEUpgradeHost host, EntityPlayer player, String outputKey) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        List<String> defaultOrder = getDefaultRouteOrder(host, outputKey);
        if (defaultOrder.isEmpty()) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.resetProduct(outputKey, defaultOrder);
        List<String> defaultProductOrder = getDefaultProductOrder(host);
        if (!defaultProductOrder.isEmpty()) {
            changed |= profile.resetProductOrder(outputKey, defaultProductOrder);
        }
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    public static boolean resetAll(IAEUpgradeHost host, EntityPlayer player) {
        if (!canConfigure(host) || !(host instanceof TileEntity tile)) {
            return false;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, player);
        if (profile == null) {
            return false;
        }
        boolean changed = profile.resetAll();
        if (changed) {
            save(tile, player, profile);
        }
        return changed;
    }

    private static boolean hasRoute(IAEUpgradeHost host, String routeKey) {
        return findRoute(AEUpgradeRecipeCache.collectExposableRecipes(host), routeKey) != null;
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
        return host.hasAEUpgrade() && host instanceof IAEItemRecipeHost;
    }

    public static NBTTagCompound writeConfigCardData(TileEntity tile, NBTTagCompound nbtTags) {
        AERecipeProfile profile = getProfile(tile, null, false);
        if (profile != null && !profile.isEmpty()) {
            nbtTags.setTag(CONFIG_CARD_TAG, profile.writeToNBT(new NBTTagCompound()));
        }
        return nbtTags;
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
        if (!(tile instanceof IAEUpgradeHost host)) {
            return;
        }
        AERecipeProfile profile = getOrCreateProfile(tile, null);
        if (profile == null) {
            return;
        }
        AERecipeProfile incoming = nbtTags.hasKey(CONFIG_CARD_TAG) ? AERecipeProfile.read(nbtTags.getCompoundTag(CONFIG_CARD_TAG)) : new AERecipeProfile();
        if (profile.replaceWith(incoming)) {
            save(tile, null, profile);
        }
    }

    private static List<String> getDefaultRouteOrder(IAEUpgradeHost host, String outputKey) {
        List<String> routeKeys = new ArrayList<>();
        for (AEExposedRecipe recipe : AEUpgradeRecipeCache.collectExposableRecipes(host)) {
            if (recipe.getRecipeKey().getOutputKey().equals(outputKey)) {
                routeKeys.add(recipe.getRecipeKey().getRouteKey());
            }
        }
        return routeKeys;
    }

    private static List<String> getAllRouteKeys(IAEUpgradeHost host) {
        List<String> routeKeys = new ArrayList<>();
        for (AEExposedRecipe recipe : AEUpgradeRecipeCache.collectExposableRecipes(host)) {
            String routeKey = recipe.getRecipeKey().getRouteKey();
            if (!routeKeys.contains(routeKey)) {
                routeKeys.add(routeKey);
            }
        }
        return routeKeys;
    }

    private static List<String> getDefaultProductOrder(IAEUpgradeHost host) {
        List<AEExposedRecipe> recipes = AEUpgradeRecipeCache.collectExposableRecipes(host);
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

    private static AERecipeProfile load(ProfileIdentity identity) {
        AERecipeProfile profile = new AERecipeProfile();
        File file = identity.file();
        if (!file.isFile()) {
            return profile;
        }
        try {
            profile.readFromNBT(CompressedStreamTools.read(file));
        } catch (IOException | RuntimeException e) {
            MEKCeuAEUpgrade.logger.error("Failed to load AE recipe profile {}", file, e);
        }
        return profile;
    }

    private static void save(TileEntity tile, @Nullable EntityPlayer player, AERecipeProfile profile) {
        save(tile, player, profile, true);
    }

    private static void save(TileEntity tile, @Nullable EntityPlayer player, AERecipeProfile profile, boolean notify) {
        ProfileIdentity identity = resolveIdentity(tile, player);
        if (identity == null) {
            return;
        }
        profiles.put(identity, profile);
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
            ProfileIdentity hostIdentity = resolveIdentity(tile, null);
            if (identity.equals(hostIdentity)) {
                host.getAEUpgradeNode().invalidateRecipeCache();
                syncOpenConfigWindows(host, tile);
            }
        }
    }

    private static void syncOpenConfigWindows(IAEUpgradeHost host, TileEntity tile) {
        if (!(tile instanceof TileEntityBasicBlock basic) || basic.playersUsing.isEmpty()) {
            return;
        }
        for (EntityPlayer user : basic.playersUsing) {
            if (user instanceof EntityPlayerMP userMP && PacketHandler.canAccessTile(user, tile, true)) {
                AERecipeConfigSnapshot snapshot = buildSnapshot(host, user);
                MEKCeuAEUpgrade.packetHandler.sendTo(AERecipeConfigMessage.snapshot(Coord4D.get(tile), snapshot), userMP);
            }
        }
    }

    @Nullable
    private static ProfileIdentity resolveIdentity(TileEntity tile, @Nullable EntityPlayer player) {
        boolean individual = tile instanceof IAEUpgradeHost host && host.getAEUpgradeNode().isRecipeProfileIndividual();
        return resolveIdentity(tile, player, individual);
    }

    @Nullable
    private static ProfileIdentity resolveIdentity(TileEntity tile, @Nullable EntityPlayer player, boolean individual) {
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
        String profileId = machineId;
        File directory = ownerDirectory;
        String filePrefix = sanitize(machineId);
        if (individual) {
            if (!(tile instanceof IAEUpgradeHost host)) {
                return null;
            }
            UUID instance = host.getAEUpgradeNode().getOrCreateRecipeProfileInstance();
            profileId = machineId + "/" + instance;
            directory = new File(ownerDirectory, "single");
        } else if (tile instanceof IAEUpgradeHost host) {
            int slot = Math.max(AEUpgradeNode.MIN_GLOBAL_PROFILE_SLOT,
                  Math.min(AEUpgradeNode.MAX_GLOBAL_PROFILE_SLOT, host.getAEUpgradeNode().getRecipeProfileGlobalSlot()));
            if (slot > AEUpgradeNode.MIN_GLOBAL_PROFILE_SLOT) {
                profileId = machineId + "/global/" + slot;
                directory = new File(ownerDirectory, "global");
                filePrefix += "_global_" + slot;
            }
        }
        File file = new File(directory, filePrefix + "_" + digest(profileId) + ".dat");
        return new ProfileIdentity(worldDirectory.getAbsolutePath(), owner, profileId, individual, file);
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

    private record ProfileIdentity(String worldPath, UUID owner, String profileId, boolean individual, File file) {
    }
}
