package tpa.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import tpa.Constants;
import tpa.config.ConfigManager;
import tpa.rtp.RtpManager;

public class RtpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(
                Commands.literal("rtp")
                        .requires(
                                source ->
                                        source.getPlayer() != null
                                                && ConfigManager.CONFIG != null
                                                && ConfigManager.CONFIG.rtp.isEnabled())
                        .executes(
                                context -> {
                                    final ServerPlayer player =
                                            context.getSource().getPlayerOrException();
                                    try {
                                        randomTeleport(player, (ServerLevel) player.level());
                                    } catch (Exception e) {
                                        Constants.LOGGER.error("Error in /rtp => ", e);
                                        return 1;
                                    }
                                    return 0;
                                })
                        .then(
                                Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(
                                                context -> {
                                                    final ServerPlayer player =
                                                            context.getSource()
                                                                    .getPlayerOrException();
                                                    final ServerLevel dimension =
                                                            DimensionArgument.getDimension(
                                                                    context, "dimension");
                                                    try {
                                                        randomTeleport(player, dimension);
                                                    } catch (Exception e) {
                                                        Constants.LOGGER.error(
                                                                "Error in /rtp => ", e);
                                                        return 1;
                                                    }
                                                    return 0;
                                                })));
    }

    private static void randomTeleport(ServerPlayer player, ServerLevel targetWorld)
            throws Exception {
        RtpManager.request(player, targetWorld);
    }
}
