package tpa.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

public final class MessageService {

    public static void sendPlayerMessage(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }

    public static void sendPlayerMessage(ServerPlayer player, Component message, boolean overlay) {
        if (overlay) {
            // 26.1 Action Bar: 发送客户端包
            player.connection.send(new ClientboundSetActionBarTextPacket(message));
        } else {
            player.sendSystemMessage(message);
        }
    }
}
