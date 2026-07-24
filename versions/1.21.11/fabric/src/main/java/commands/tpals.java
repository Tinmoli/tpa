package tpa;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import static tpa.tools.getTranslatedText;
import static tpa.tools.sendPlayerMessage;

public class tpals {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpals")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> {
                    final ServerPlayer player = context.getSource().getPlayerOrException();
                    sendPlayerMessage(player, buildCommandList(player), false);
                    return 0;
                }));
    }

    private static MutableComponent buildCommandList(ServerPlayer player) {
        MutableComponent msg = Component.empty();

        // Header
        msg.append(Component.literal("=== tpa v" + Constants.VERSION + " ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
           .append(getTranslatedText("commands.teleport_commands.tpals.title", player).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
           .append(Component.literal(" ===").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

        // All commands
        if (ConfigManager.CONFIG.spawn.isEnabled()) {
            cmd(msg, "/spawn", "commands.teleport_commands.tpals.spawn", player);
        }
        if (ConfigManager.CONFIG.back.isEnabled()) {
            cmd(msg, "/back", "commands.teleport_commands.tpals.back", player);
        }
        if (ConfigManager.CONFIG.home.isEnabled()) {
            cmd(msg, "/sethome <name>", "commands.teleport_commands.tpals.sethome", player);
            cmd(msg, "/home [name]", "commands.teleport_commands.tpals.home", player);
            cmd(msg, "/homes", "commands.teleport_commands.tpals.homes", player);
            cmd(msg, "/delhome <name>", "commands.teleport_commands.tpals.delhome", player);
            cmd(msg, "/renamehome <name> <new>", "commands.teleport_commands.tpals.renamehome", player);
            cmd(msg, "/defaulthome <name>", "commands.teleport_commands.tpals.defaulthome", player);
        }
        if (ConfigManager.CONFIG.warp.isEnabled()) {
            cmd(msg, "/warp <name>", "commands.teleport_commands.tpals.warp", player);
            cmd(msg, "/warps", "commands.teleport_commands.tpals.warps", player);
        }
        if (ConfigManager.CONFIG.tpa.isEnabled()) {
            cmd(msg, "/tpa <player>", "commands.teleport_commands.tpals.tpa", player);
            cmd(msg, "/tpahere <player>", "commands.teleport_commands.tpals.tpahere", player);
            cmd(msg, "/tpaaccept <player>", "commands.teleport_commands.tpals.tpaaccept", player);
            cmd(msg, "/tpadeny <player>", "commands.teleport_commands.tpals.tpadeny", player);
        }
        if (ConfigManager.CONFIG.rtp.isEnabled()) {
            cmd(msg, "/rtp [dimension]", "commands.teleport_commands.tpals.rtp", player);
        }

        if (player.createCommandSourceStack().permissions().hasPermission(
                net.minecraft.server.permissions.Permissions.COMMANDS_OWNER)) {
            if (ConfigManager.CONFIG.warp.isEnabled()) {
                cmd(msg, "/setwarp <name>", "commands.teleport_commands.tpals.setwarp", player);
                cmd(msg, "/delwarp <name>", "commands.teleport_commands.tpals.delwarp", player);
                cmd(msg, "/renamewarp <name> <new>", "commands.teleport_commands.tpals.renamewarp", player);
            }
            cmd(msg, "/tpastorage json-to-sqlite",
                    "commands.teleport_commands.tpals.storage_json_to_sqlite", player);
            cmd(msg, "/tpareload",
                    "commands.teleport_commands.tpals.reload", player);
        }

        return msg;
    }

    private static void cmd(MutableComponent msg, String command, String descKey, ServerPlayer player) {
        msg.append("\n")
           .append(Component.literal("  " + command).withStyle(ChatFormatting.AQUA))
           .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
           .append(getTranslatedText(descKey, player).withStyle(ChatFormatting.WHITE));
    }
}
