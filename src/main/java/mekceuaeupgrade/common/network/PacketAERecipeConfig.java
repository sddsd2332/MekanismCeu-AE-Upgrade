package mekceuaeupgrade.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismUtils;
import mekceuaeupgrade.common.config.AERecipeConfigClientCache;
import mekceuaeupgrade.common.config.AERecipeConfigSnapshot;
import mekceuaeupgrade.common.config.AERecipeConfigType;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHost;
import mekceuaeupgrade.common.network.PacketAERecipeConfig.AERecipeConfigMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketAERecipeConfig implements IMessageHandler<AERecipeConfigMessage, IMessage> {

    @Override
    public IMessage onMessage(AERecipeConfigMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player.world.isRemote) {
                handleClient(message);
                return;
            }
            if (message.coord4D == null || message.coord4D.dimensionId != player.world.provider.getDimension()) {
                return;
            }
            TileEntity tile = message.coord4D.getTileEntity(player.world);
            if (!(tile instanceof IAEUpgradeHost host) || !PacketHandler.canAccessTile(player, tile, true)) {
                return;
            }
            if (!message.configType.isInstalledIn(host)) {
                sendSnapshot(host, player instanceof EntityPlayerMP playerMP ? playerMP : null, message.configType);
                return;
            }
            if (message.packetType == RecipeConfigPacket.SNAPSHOT) {
                return;
            }
            if (message.packetType != RecipeConfigPacket.REQUEST && !AERecipeProfileManager.canEditConfiguration(tile, player)) {
                sendSnapshot(host, player instanceof EntityPlayerMP playerMP ? playerMP : null, message.configType);
                return;
            }
            switch (message.packetType) {
                case REQUEST -> {
                }
                case TOGGLE_ROUTE -> AERecipeProfileManager.toggleRoute(host, player, message.routeKey, message.configType);
                case TOGGLE_PRODUCT -> AERecipeProfileManager.toggleProduct(host, player, message.outputKey, message.configType);
                case DISABLE_PRODUCT -> AERecipeProfileManager.disableProduct(host, player, message.outputKey, message.configType);
                case MOVE_PRODUCT -> AERecipeProfileManager.moveProduct(host, player, message.outputKey, message.direction, message.configType);
                case MOVE_ROUTE -> AERecipeProfileManager.moveRoute(host, player, message.outputKey, message.routeKey, message.direction, message.configType);
                case MOVE_PRODUCT_TO_TOP -> AERecipeProfileManager.moveProductToEdge(host, player, message.outputKey, true, message.configType);
                case MOVE_PRODUCT_TO_BOTTOM -> AERecipeProfileManager.moveProductToEdge(host, player, message.outputKey, false, message.configType);
                case MOVE_ROUTE_TO_TOP -> AERecipeProfileManager.moveRouteToEdge(host, player, message.outputKey, message.routeKey, true, message.configType);
                case MOVE_ROUTE_TO_BOTTOM -> AERecipeProfileManager.moveRouteToEdge(host, player, message.outputKey, message.routeKey, false, message.configType);
                case SET_GLOBAL_CRAFT_AMOUNT -> AERecipeProfileManager.setGlobalCraftAmount(host, player, message.amount, message.configType);
                case SET_ROUTE_CRAFT_AMOUNT -> AERecipeProfileManager.setRouteCraftAmount(host, player, message.routeKey, message.amount, message.configType);
                case CLEAR_ROUTE_CRAFT_AMOUNT -> AERecipeProfileManager.clearRouteCraftAmount(host, player, message.routeKey, message.configType);
                case RESET_PRODUCT -> AERecipeProfileManager.resetProduct(host, player, message.outputKey, message.configType);
                case RESET_ALL -> AERecipeProfileManager.resetAll(host, player, message.configType);
                case TOGGLE_PROFILE_MODE -> AERecipeProfileManager.toggleProfileMode(host, player, message.configType);
                case CYCLE_GLOBAL_PROFILE -> AERecipeProfileManager.cycleGlobalProfileSlot(host, player, message.direction, message.configType);
                case TOGGLE_ROUTE_FILTER_MODE -> AERecipeProfileManager.toggleRouteFilterMode(host, player, message.configType);
                case SET_ALL_ROUTES_ENABLED -> AERecipeProfileManager.setAllRoutesEnabled(host, player, message.direction > 0, message.configType);
                case SNAPSHOT -> {
                }
            }
            sendSnapshots(tile, host, player, message.configType);
        }, player);
        return null;
    }

    private void sendSnapshots(TileEntity tile, IAEUpgradeHost host, EntityPlayer fallbackPlayer, AERecipeConfigType type) {
        if (tile instanceof TileEntityBasicBlock basic && !basic.playersUsing.isEmpty()) {
            for (EntityPlayer user : basic.playersUsing) {
                if (user instanceof EntityPlayerMP userMP && PacketHandler.canAccessTile(user, tile, true)) {
                    sendSnapshot(host, userMP, type);
                }
            }
        } else if (fallbackPlayer instanceof EntityPlayerMP playerMP) {
            sendSnapshot(host, playerMP, type);
        }
    }

    private void sendSnapshot(IAEUpgradeHost host, EntityPlayerMP player) {
        sendSnapshot(host, player, AERecipeConfigType.CRAFTING);
    }

    private void sendSnapshot(IAEUpgradeHost host, EntityPlayerMP player, AERecipeConfigType type) {
        if (player == null) {
            return;
        }
        TileEntity tile = (TileEntity) host;
        AERecipeConfigSnapshot snapshot = AERecipeProfileManager.buildSnapshot(host, player, type);
        MEKCeuAEUpgrade.packetHandler.sendTo(AERecipeConfigMessage.snapshot(Coord4D.get(tile), snapshot, type), player);
    }

    private void handleClient(AERecipeConfigMessage message) {
        if (message.packetType == RecipeConfigPacket.SNAPSHOT && message.coord4D != null) {
            AERecipeConfigClientCache.setSnapshot(message.coord4D, message.configType, AERecipeConfigSnapshot.read(message.payload));
        }
    }

    public enum RecipeConfigPacket {
        REQUEST,
        SNAPSHOT,
        TOGGLE_ROUTE,
        TOGGLE_PRODUCT,
        DISABLE_PRODUCT,
        MOVE_PRODUCT,
        MOVE_ROUTE,
        MOVE_PRODUCT_TO_TOP,
        MOVE_PRODUCT_TO_BOTTOM,
        MOVE_ROUTE_TO_TOP,
        MOVE_ROUTE_TO_BOTTOM,
        SET_GLOBAL_CRAFT_AMOUNT,
        SET_ROUTE_CRAFT_AMOUNT,
        CLEAR_ROUTE_CRAFT_AMOUNT,
        RESET_PRODUCT,
        RESET_ALL,
        TOGGLE_PROFILE_MODE,
        CYCLE_GLOBAL_PROFILE,
        TOGGLE_ROUTE_FILTER_MODE,
        SET_ALL_ROUTES_ENABLED
    }

    public static class AERecipeConfigMessage implements IMessage {

        public RecipeConfigPacket packetType = RecipeConfigPacket.REQUEST;
        public Coord4D coord4D;
        public String outputKey = "";
        public String routeKey = "";
        public int direction;
        public int amount;
        public AERecipeConfigType configType = AERecipeConfigType.CRAFTING;
        public NBTTagCompound payload = new NBTTagCompound();

        public AERecipeConfigMessage() {
        }

        public AERecipeConfigMessage(Coord4D coord) {
            this(coord, AERecipeConfigType.CRAFTING);
        }

        public AERecipeConfigMessage(Coord4D coord, AERecipeConfigType type) {
            coord4D = coord;
            configType = type == null ? AERecipeConfigType.CRAFTING : type;
        }

        public AERecipeConfigMessage(Coord4D coord, RecipeConfigPacket packetType, String outputKey, String routeKey, int direction) {
            this(coord, AERecipeConfigType.CRAFTING, packetType, outputKey, routeKey, direction, 0);
        }

        public AERecipeConfigMessage(Coord4D coord, RecipeConfigPacket packetType, String outputKey, String routeKey, int direction, int amount) {
            this(coord, AERecipeConfigType.CRAFTING, packetType, outputKey, routeKey, direction, amount);
        }

        public AERecipeConfigMessage(Coord4D coord, AERecipeConfigType type, RecipeConfigPacket packetType, String outputKey, String routeKey, int direction) {
            this(coord, type, packetType, outputKey, routeKey, direction, 0);
        }

        public AERecipeConfigMessage(Coord4D coord, AERecipeConfigType type, RecipeConfigPacket packetType, String outputKey, String routeKey, int direction,
              int amount) {
            coord4D = coord;
            configType = type == null ? AERecipeConfigType.CRAFTING : type;
            this.packetType = packetType;
            this.outputKey = outputKey == null ? "" : outputKey;
            this.routeKey = routeKey == null ? "" : routeKey;
            this.direction = direction;
            this.amount = amount;
        }

        public static AERecipeConfigMessage snapshot(Coord4D coord, AERecipeConfigSnapshot snapshot) {
            return snapshot(coord, snapshot, AERecipeConfigType.CRAFTING);
        }

        public static AERecipeConfigMessage snapshot(Coord4D coord, AERecipeConfigSnapshot snapshot, AERecipeConfigType type) {
            AERecipeConfigMessage message = new AERecipeConfigMessage(coord);
            message.configType = type == null ? AERecipeConfigType.CRAFTING : type;
            message.packetType = RecipeConfigPacket.SNAPSHOT;
            message.payload = snapshot.write(new NBTTagCompound());
            return message;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(packetType.ordinal());
            dataStream.writeInt(configType.ordinal());
            coord4D.write(dataStream);
            PacketHandler.writeString(dataStream, outputKey);
            PacketHandler.writeString(dataStream, routeKey);
            dataStream.writeInt(direction);
            dataStream.writeInt(amount);
            PacketHandler.writeNBT(dataStream, payload);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            packetType = MekanismUtils.getByIndex(RecipeConfigPacket.values(), dataStream.readInt(), RecipeConfigPacket.REQUEST);
            configType = AERecipeConfigType.byIndex(dataStream.readInt());
            coord4D = Coord4D.read(dataStream);
            outputKey = PacketHandler.readString(dataStream);
            routeKey = PacketHandler.readString(dataStream);
            direction = dataStream.readInt();
            amount = dataStream.readInt();
            payload = PacketHandler.readNBT(dataStream);
            if (payload == null) {
                payload = new NBTTagCompound();
            }
        }
    }
}
