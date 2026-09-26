package tpa;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static tpa.tools.*;

public class rtp {

    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(Commands.literal("rtp")
                .requires(source -> source.getPlayer() != null
                        && ConfigManager.CONFIG != null
                        && ConfigManager.CONFIG.rtp.isEnabled())
                .executes(context -> {
                    final ServerPlayer player = context.getSource().getPlayerOrException();
                    try {
                        randomTeleport(player, (ServerLevel) player.level());
                    } catch (Exception e) {
                        Constants.LOGGER.error("Error in /rtp => ", e);
                        return 1;
                    }
                    return 0;
                })
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(context -> {
                            final ServerPlayer player = context.getSource().getPlayerOrException();
                            final ServerLevel dimension = DimensionArgument.getDimension(context, "dimension");
                            try {
                                randomTeleport(player, dimension);
                            } catch (Exception e) {
                                Constants.LOGGER.error("Error in /rtp => ", e);
                                return 1;
                            }
                            return 0;
                        })));
    }

    private static void randomTeleport(ServerPlayer player, ServerLevel targetWorld) throws Exception {
        int minRange = ConfigManager.CONFIG.rtp.minRange;
        int maxRange = ConfigManager.CONFIG.rtp.maxRange;

        // 最多尝试 10 次，提高在海洋/特殊地形时的成功率
        Optional<BlockPos> safePos = Optional.empty();
        for (int attempt = 0; attempt < 10 && safePos.isEmpty(); attempt++) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double angle = rng.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt((double) minRange * minRange
                    + rng.nextDouble() * ((double) maxRange * maxRange - (double) minRange * minRange));
            int randomX = (int) Math.round(Math.cos(angle) * radius);
            int randomZ = (int) Math.round(Math.sin(angle) * radius);
            safePos = getRandomSafeBlockPos(randomX, randomZ, targetWorld);
        }

        if (safePos.isEmpty()) {
            sendPlayerMessage(player,
                    getTranslatedText("commands.teleport_commands.rtp.noSafeLocation", player)
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        BlockPos teleportBlockPos = safePos.get();
        Vec3 teleportPos = new Vec3(teleportBlockPos.getX() + 0.5, teleportBlockPos.getY(), teleportBlockPos.getZ() + 0.5);

        sendPlayerMessage(player,
                getTranslatedText("commands.teleport_commands.rtp.teleporting", player)
                        .withStyle(ChatFormatting.AQUA), true);

        Teleporter(player, targetWorld, teleportPos);
    }
}
