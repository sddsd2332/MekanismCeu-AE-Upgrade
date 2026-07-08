package mekceuaeupgrade.common.network;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.host.IAEUpgradeHost;

import io.netty.buffer.ByteBuf;
import mekceuaeupgrade.common.config.AERecipeConfigClientCache;
import mekceuaeupgrade.common.config.AERecipeConfigSnapshot;
import mekceuaeupgrade.common.config.AERecipeProfileManager;
import mekceuaeupgrade.common.network.PacketAERecipeConfig.AERecipeConfigMessage;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismUtils;
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
            if (!host.hasAEUpgrade()) {
                sendSnapshot(host, player instanceof EntityPlayerMP playerMP ? playerMP : null);
                return;
            }
            switch (message.packetType) {
                case REQUEST -> {
                }
                case TOGGLE_ROUTE -> AERecipeProfileManager.toggleRoute(host, player, message.routeKey);
                case TOGGLE_PRODUCT -> AERecipeProfileManager.toggleProduct(host, player, message.outputKey);
                case DISABLE_PRODUCT -> AERecipeProfileManager.disableProduct(host, player, message.outputKey);
                case MOVE_PRODUCT -> AERecipeProfileManager.moveProduct(host, player, message.outputKey, message.direction);
                case MOVE_ROUTE -> AERecipeProfileManager.moveRoute(host, player, message.outputKey, message.routeKey, message.direction);
                case MOVE_PRODUCT_TO_TOP -> AERecipeProfileManager.moveProductToEdge(host, player, message.outputKey, true);
                case MOVE_PRODUCT_TO_BOTTOM -> AERecipeProfileManager.moveProductToEdge(host, player, message.outputKey, false);
                case MOVE_ROUTE_TO_TOP -> AERecipeProfileManager.moveRouteToEdge(host, player, message.outputKey, message.routeKey, true);
                case MOVE_ROUTE_TO_BOTTOM -> AERecipeProfileManager.moveRouteToEdge(host, player, message.outputKey, message.routeKey, false);
                case SET_GLOBAL_CRAFT_AMOUNT -> AERecipeProfileManager.setGlobalCraftAmount(host, player, message.amount);
                case SET_ROUTE_CRAFT_AMOUNT -> AERecipeProfileManager.setRouteCraftAmount(host, player, message.routeKey, message.amount);
                case CLEAR_ROUTE_CRAFT_AMOUNT -> AERecipeProfileManager.clearRouteCraftAmount(host, player, message.routeKey);
                case RESET_PRODUCT -> AERecipeProfileManager.resetProduct(host, player, message.outputKey);
                case RESET_ALL -> AERecipeProfileManager.resetAll(host, player);
                case TOGGLE_PROFILE_MODE -> AERecipeProfileManager.toggleProfileMode(host, player);
                case CYCLE_GLOBAL_PROFILE -> AERecipeProfileManager.cycleGlobalProfileSlot(host, player, message.direction);
                case TOGGLE_ROUTE_FILTER_MODE -> AERecipeProfileManager.toggleRouteFilterMode(host, player);
                case SET_ALL_ROUTES_ENABLED -> AERecipeProfileManager.setAllRoutesEnabled(host, player, message.direction > 0);
                case SNAPSHOT -> {
                    return;
                }
            }
            sendSnapshots(tile, host, player);
        }, player);
        return null;
    }

    private void sendSnapshots(TileEntity tile, IAEUpgradeHost host, EntityPlayer fallbackPlayer) {
        if (tile instanceof TileEntityBasicBlock basic && !basic.playersUsing.isEmpty()) {
            for (EntityPlayer user : basic.playersUsing) {
                if (user instanceof EntityPlayerMP userMP && PacketHandler.canAccessTile(user, tile, true)) {
                    sendSnapshot(host, userMP);
                }
            }
        } else if (fallbackPlayer instanceof EntityPlayerMP playerMP) {
            sendSnapshot(host, playerMP);
        }
    }

    private void sendSnapshot(IAEUpgradeHost host, EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        TileEntity tile = (TileEntity) host;
        AERecipeConfigSnapshot snapshot = AERecipeProfileManager.buildSnapshot(host, player);
        MEKCeuAEUpgrade.packetHandler.sendTo(AERecipeConfigMessage.snapshot(Coord4D.get(tile), snapshot), player);
    }

    private void handleClient(AERecipeConfigMessage message) {
        if (message.packetType == RecipeConfigPacket.SNAPSHOT && message.coord4D != null) {
            AERecipeConfigClientCache.setSnapshot(message.coord4D, AERecipeConfigSnapshot.read(message.payload));
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
        public NBTTagCompound payload = new NBTTagCompound();

        public AERecipeConfigMessage() {
        }

        public AERecipeConfigMessage(Coord4D coord) {
            coord4D = coord;
        }

        public AERecipeConfigMessage(Coord4D coord, RecipeConfigPacket packetType, String outputKey, String routeKey, int direction) {
            this(coord, packetType, outputKey, routeKey, direction, 0);
        }

        public AERecipeConfigMessage(Coord4D coord, RecipeConfigPacket packetType, String outputKey, String routeKey, int direction, int amount) {
            coord4D = coord;
            this.packetType = packetType;
            this.outputKey = outputKey == null ? "" : outputKey;
            this.routeKey = routeKey == null ? "" : routeKey;
            this.direction = direction;
            this.amount = amount;
        }

        public static AERecipeConfigMessage snapshot(Coord4D coord, AERecipeConfigSnapshot snapshot) {
            AERecipeConfigMessage message = new AERecipeConfigMessage(coord);
            message.packetType = RecipeConfigPacket.SNAPSHOT;
            message.payload = snapshot.write(new NBTTagCompound());
            return message;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(packetType.ordinal());
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
