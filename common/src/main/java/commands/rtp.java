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
import java.util.Random;

import static tpa.tools.*;

public class rtp {
    private static final Random RANDOM = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(Commands.literal("rtp")
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

        // 生成随机坐标
        int randomX = RANDOM.nextInt(maxRange - minRange + 1) + minRange;
        int randomZ = RANDOM.nextInt(maxRange - minRange + 1) + minRange;

        // 随机方向
        if (RANDOM.nextBoolean()) randomX = -randomX;
        if (RANDOM.nextBoolean()) randomZ = -randomZ;

        // 查找安全位置
        Optional<BlockPos> safePos = getSafeBlockPos(new BlockPos(randomX, 64, randomZ), targetWorld);

        if (safePos.isEmpty()) {
            player.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.rtp.noSafeLocation", player)
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        BlockPos teleportBlockPos = safePos.get();
        Vec3 teleportPos = new Vec3(teleportBlockPos.getX() + 0.5, teleportBlockPos.getY(), teleportBlockPos.getZ() + 0.5);

        player.displayClientMessage(
                getTranslatedText("commands.teleport_commands.rtp.teleporting", player)
                        .withStyle(ChatFormatting.AQUA), true);

        Teleporter(player, targetWorld, teleportPos);
    }
}
