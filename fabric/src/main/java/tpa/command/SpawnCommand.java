package tpa.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.world.level.Level.OVERWORLD;

import static tpa.language.LanguageManager.getTranslatedText;
import static tpa.teleport.TeleportSafety.getSafeBlockPos;
import static tpa.teleport.TeleportService.teleport;
import static tpa.util.MessageService.sendPlayerMessage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import tpa.Constants;
import tpa.TpaMod;
import tpa.config.ConfigManager;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class SpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(
                Commands.literal("spawn")
                        .requires(
                                source ->
                                        source.getPlayer() != null
                                                && ConfigManager.CONFIG != null
                                                && ConfigManager.CONFIG.spawn.isEnabled())
                        .executes(
                                context -> {
                                    final ServerPlayer player =
                                            context.getSource().getPlayerOrException();

                                    try {
                                        toWorldSpawn(player, false);

                                    } catch (Exception error) {
                                        Constants.LOGGER.error(
                                                "Error while going to the worldspawn! => ", error);
                                        sendPlayerMessage(
                                                player,
                                                getTranslatedText(
                                                                "commands.teleport_commands.common.error",
                                                                player)
                                                        .withStyle(
                                                                ChatFormatting.RED,
                                                                ChatFormatting.BOLD),
                                                true);
                                        return 1;
                                    }
                                    return 0;
                                })
                        .then(
                                argument("Disable Safety", BoolArgumentType.bool())
                                        .executes(
                                                context -> {
                                                    final boolean safety =
                                                            BoolArgumentType.getBool(
                                                                    context, "Disable Safety");
                                                    final ServerPlayer player =
                                                            context.getSource()
                                                                    .getPlayerOrException();

                                                    try {
                                                        toWorldSpawn(player, safety);

                                                    } catch (Exception error) {
                                                        Constants.LOGGER.error(
                                                                "Error while going to the"
                                                                        + " worldspawn! => ",
                                                                error);
                                                        sendPlayerMessage(
                                                                player,
                                                                getTranslatedText(
                                                                                "commands.teleport_commands.common.error",
                                                                                player)
                                                                        .withStyle(
                                                                                ChatFormatting.RED,
                                                                                ChatFormatting
                                                                                        .BOLD),
                                                                true);
                                                        return 1;
                                                    }
                                                    return 0;
                                                })));
    }

    private static void toWorldSpawn(ServerPlayer player, boolean safetyDisabled)
            throws NullPointerException {
        // 使用配置中的 world_id，默认回退到主世界
        String configWorldId = ConfigManager.CONFIG.spawn.getWorldId();
        ServerLevel world =
                StreamSupport.stream(TpaMod.SERVER.getAllLevels().spliterator(), false)
                        .filter(l -> l.dimension().identifier().toString().equals(configWorldId))
                        .findFirst()
                        .orElseGet(() -> TpaMod.SERVER.getLevel(OVERWORLD));
        BlockPos worldSpawn =
                Objects.requireNonNull(world, "Spawn world cannot be null!")
                        .getLevelData()
                        .getRespawnData()
                        .pos();

        if (!safetyDisabled) {
            Optional<BlockPos> teleportData = getSafeBlockPos(worldSpawn, world);

            if (teleportData.isPresent()) {
                BlockPos safeBlockPos = teleportData.get();

                // check if the player is already at this location
                if (player.blockPosition().equals(safeBlockPos) && player.level() == world) {

                    sendPlayerMessage(
                            player,
                            getTranslatedText("commands.teleport_commands.spawn.same", player)
                                    .withStyle(ChatFormatting.AQUA),
                            true);
                } else {
                    Vec3 teleportPos =
                            new Vec3(
                                    safeBlockPos.getX() + 0.5,
                                    safeBlockPos.getY(),
                                    safeBlockPos.getZ() + 0.5);

                    sendPlayerMessage(
                            player,
                            getTranslatedText("commands.teleport_commands.spawn.go", player),
                            true);
                    teleport(player, world, teleportPos);
                }

            } else {

                sendPlayerMessage(
                        player,
                        Component.empty()
                                .append(
                                        getTranslatedText(
                                                        "commands.teleport_commands.common.noSafeLocation",
                                                        player)
                                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                                .append("\n")
                                .append(
                                        getTranslatedText(
                                                        "commands.teleport_commands.common.safetyIsForLosers",
                                                        player)
                                                .withStyle(ChatFormatting.WHITE))
                                .append("\n")
                                .append(
                                        getTranslatedText(
                                                        "commands.teleport_commands.common.forceTeleport",
                                                        player)
                                                .withStyle(
                                                        ChatFormatting.DARK_AQUA,
                                                        ChatFormatting.BOLD)
                                                .withStyle(
                                                        style ->
                                                                style.withClickEvent(
                                                                        new ClickEvent.RunCommand(
                                                                                "/spawn true"))))
                                .append("\n"),
                        false);
            }

        } else {

            if (player.blockPosition().equals(worldSpawn) && player.level() == world) {

                sendPlayerMessage(
                        player,
                        getTranslatedText("commands.teleport_commands.spawn.same", player)
                                .withStyle(ChatFormatting.AQUA),
                        true);
            } else {

                sendPlayerMessage(
                        player,
                        getTranslatedText("commands.teleport_commands.spawn.go", player),
                        true);
                teleport(
                        player,
                        world,
                        new Vec3(
                                worldSpawn.getX() + 0.5,
                                worldSpawn.getY(),
                                worldSpawn.getZ() + 0.5));
            }
        }
    }
}
