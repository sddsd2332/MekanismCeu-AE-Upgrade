package mekceuaeupgrade.common.network;

import mekceuaeupgrade.common.core.MEKCeuAEUpgrade;
import mekceuaeupgrade.common.network.PacketAERecipeConfig.AERecipeConfigMessage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class MEKCeuAEUpgradePacketHandler {

    private int packetId;
    private boolean initialized;
    private final SimpleNetworkWrapper netHandler = NetworkRegistry.INSTANCE.newSimpleChannel(MEKCeuAEUpgrade.MODID);

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        netHandler.registerMessage(PacketAERecipeConfig.class, AERecipeConfigMessage.class, nextPacketId(), Side.SERVER);
        netHandler.registerMessage(PacketAERecipeConfig.class, AERecipeConfigMessage.class, nextPacketId(), Side.CLIENT);
    }

    private int nextPacketId() {
        return packetId++;
    }

    public void sendTo(IMessage message, EntityPlayerMP player) {
        netHandler.sendTo(message, player);
    }

    public void sendToServer(IMessage message) {
        netHandler.sendToServer(message);
    }
}
