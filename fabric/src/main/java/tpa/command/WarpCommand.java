package tpa.command;

import static net.minecraft.commands.Commands.argument;

import static tpa.language.LanguageManager.getTranslatedText;
import static tpa.storage.StorageManager.STORAGE;
import static tpa.teleport.TeleportService.teleport;
import static tpa.util.MessageService.sendPlayerMessage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import tpa.Constants;
import tpa.config.ConfigManager;
import tpa.gui.WarpsGui;
import tpa.storage.NamedLocation;
import tpa.storage.StorageManager;
import tpa.suggestion.WarpSuggestionProvider;
import tpa.util.WorldLookup;

import java.util.Optional;

public class WarpCommand {
    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {

        commandDispatcher.register(
                Commands.literal("setwarp")
                        .requires(
                                source ->
                                        source.getPlayer() != null
                                                && ConfigManager.CONFIG != null
                                                && ConfigManager.CONFIG.warp.isEnabled()
                                                && source.permissions()
                                                        .hasPermission(
                                                                net.minecraft.server.permissions
                                                                        .Permissions
                                                                        .COMMANDS_OWNER))
                        .then(
                                argument("name", StringArgumentType.greedyString())
                                        .executes(
                                                context -> {
                                                    final String name =
                                                            StringArgumentType.getString(
                                                                    context, "name");
                                                    final ServerPlayer player =
                                                            context.getSource()
                                                                    .getPlayerOrException();

                                                    try {
                                                        setWarp(player, name);

                                                    } catch (Exception e) {
                                                        Constants.LOGGER.error(
                                                                "Error while setting the warp!", e);
                                                        sendPlayerMessage(
                                                                player,
                                                                getTranslatedText(
                                                                                "commands.teleport_commands.warp.error",
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

        commandDispatcher.register(
                Commands.literal("warp")
                        .requires(
                                source ->
                                        source.getPlayer() != null
                                                && ConfigManager.CONFIG != null
                                                && ConfigManager.CONFIG.warp.isEnabled())
                        .then(
                                argument("name", StringArgumentType.greedyString())
                                        .suggests(new WarpSuggestionProvider())
                                        .executes(
                                                context -> {
                                                    final String name =
                                                            StringArgumentType.getString(
                                                                    context, "name");
                                                    final ServerPlayer player =
                                                            context.getSource()
                                                                    .getPlayerOrException();

                                                    try {
                                                        goToWarp(player, name);

                                                    } catch (Exception e) {
                                                        Constants.LOGGER.error(
                                                                "Error while going to the warp!",
                                                                e);
                                                        sendPlayerMessage(
                                                                player,
                                                                getTranslatedText(
                                                                                "commands.teleport_commands.warp.error",
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

        commandDispatcher.register(
                Commands.literal("delwarp")
                        .requires(
                                source ->
                                        source.getPlayer() != null
                                                && ConfigManager.CONFIG != null
                                                && ConfigManager.CONFIG.warp.isEnabled()
                                                && source.permissions()
                                                        .hasPermission(
                                                                net.minecraft.server.permissions
                                                                        .Permissions
                                                                        .COMMANDS_OWNER))
                        .then(
                                argument("name", StringArgumentType.greedyString())
                                        .suggests(new WarpSuggestionProvider())
                                        .executes(
                                                context -> {
                                                    final String name =
                                                            StringArgumentType.getString(
                                                                    context, "name");
                                                    final ServerPlayer player =
                                                            context.getSource()
                                                                    .getPlayerOrException();

                                                    try {
                                                        deleteWarp(player, name);

                                                    } catch (Exception e) {
                                                        Constants.LOGGER.error(
                                                                "Error while deleting to the warp!",
                                                                e);
                                                        sendPlayerMessage(
                                                                player,
                                                                getTranslatedText(
                                                                                "commands.teleport_commands.warp.error",
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

        commandDispatcher.register(
                Commands.literal("renamewarp")
                        .requires(
                                source ->
                                        source.getPlayer() != null
                                                && ConfigManager.CONFIG != null
                                                && ConfigManager.CONFIG.warp.isEnabled()
                                                && source.permissions()
                                                        .hasPermission(
                                                                net.minecraft.server.permissions
                                                                        .Permissions
                                                                        .COMMANDS_OWNER))
                        .then(
                                argument("name", StringArgumentType.string())
                                        .suggests(new WarpSuggestionProvider())
                                        .then(
                                                argument("newName", StringArgumentType.string())
                                                        .executes(
                                                                context -> {
                                                                    final String name =
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            context,
                                                                                            "name");
                                                                    final String newName =
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            context,
                                                                                            "newName");
                                                                    final ServerPlayer player =
                                                                            context.getSource()
                                                                                    .getPlayerOrException();

                                                                    try {
                                                                        renameWarp(
                                                                                player, name,
                                                                                newName);

                                                                    } catch (Exception e) {
                                                                        Constants.LOGGER.error(
                                                                                "Error while"
                                                                                    + " renaming"
                                                                                    + " the warp!",
                                                                                e);
                                                                        sendPlayerMessage(
                                                                                player,
                                                                                getTranslatedText(
                                                                                                "commands.teleport_commands.warp.error",
                                                                                                player)
                                                                                        .withStyle(
                                                                                                ChatFormatting
                                                                                                        .RED,
                                                                                                ChatFormatting
                                                                                                        .BOLD),
                                                                                true);
                                                                        return 1;
                                                                    }
                                                                    return 0;
                                                                }))));

        commandDispatcher.register(
                Commands.literal("warps")
                        .requires(
                                source ->
                                        source.getPlayer() != null
                                                && ConfigManager.CONFIG != null
                                                && ConfigManager.CONFIG.warp.isEnabled())
                        .executes(
                                context -> {
                                    final ServerPlayer player =
                                            context.getSource().getPlayerOrException();

                                    try {
                                        java.util.List<NamedLocation> warps =
                                                StorageManager.STORAGE.getWarps();
                                        new WarpsGui(player, new java.util.ArrayList<>(warps))
                                                .open();

                                    } catch (Exception e) {
                                        Constants.LOGGER.error("Error while opening warps GUI!", e);
                                        sendPlayerMessage(
                                                player,
                                                getTranslatedText(
                                                                "commands.teleport_commands.warps.error",
                                                                player)
                                                        .withStyle(
                                                                ChatFormatting.RED,
                                                                ChatFormatting.BOLD),
                                                true);
                                        return 1;
                                    }
                                    return 0;
                                }));
    }

    private static void setWarp(ServerPlayer player, String warpName) throws Exception {
        warpName = warpName.toLowerCase();

        BlockPos blockPos =
                new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        String worldString = player.level().dimension().identifier().toString();

        // Create the NamedLocation
        NamedLocation warp = new NamedLocation(warpName, blockPos, worldString);

        // Adds the warp, returns true if the warp already exists
        boolean warpExists = STORAGE.addWarp(warp);

        if (warpExists) {
            // Display error message that the warp already exists
            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.warp.exists", player)
                            .withStyle(ChatFormatting.RED),
                    true);

        } else {
            // Display message that the home as been set
            sendPlayerMessage(
                    player, getTranslatedText("commands.teleport_commands.warp.set", player), true);
        }
    }

    private static void goToWarp(ServerPlayer player, String warpName) throws Exception {
        warpName = warpName.toLowerCase();

        // Gets warp
        Optional<NamedLocation> optionalWarp = STORAGE.getWarp(warpName);
        if (optionalWarp.isEmpty()) {
            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.warp.notFound", player)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        NamedLocation warp = optionalWarp.get();

        // Get the world, otherwise give a warning and error message
        Optional<ServerLevel> optionalWorld = warp.getWorld();

        if (optionalWorld.isEmpty()) {
            Constants.LOGGER.warn(
                    "({}) Error while going to the warp \"{}\"! \n"
                            + "Couldn't find a world with the id: \"{}\" \n"
                            + "Available worlds: {}",
                    player.getName().getString(),
                    warp.getName(),
                    warp.getWorldString(),
                    WorldLookup.getWorldIds());

            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.common.worldNotFound", player)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    true);

            return;
        }

        ServerLevel warpWorld = optionalWorld.get();

        BlockPos teleportBlockPos = warp.getBlockPos();

        // Check if the player is already at this location (in the same world)
        if (player.blockPosition().equals(teleportBlockPos) && player.level() == warpWorld) {
            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.warp.goSame", player)
                            .withStyle(ChatFormatting.AQUA),
                    true);

        } else {
            // Teleport the player!
            Vec3 teleportPos =
                    new Vec3(
                            teleportBlockPos.getX() + 0.5,
                            teleportBlockPos.getY(),
                            teleportBlockPos.getZ() + 0.5);

            sendPlayerMessage(
                    player, getTranslatedText("commands.teleport_commands.warp.go", player), true);
            teleport(player, warpWorld, teleportPos);
        }
    }

    private static void deleteWarp(ServerPlayer player, String warpName) throws Exception {
        warpName = warpName.toLowerCase();

        // get the existing warp
        Optional<NamedLocation> optionalWarp = STORAGE.getWarp(warpName);

        if (optionalWarp.isPresent()) {
            // Delete the warp
            STORAGE.removeWarp(optionalWarp.get());

            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.warp.delete", player),
                    true);

        } else {
            // the warp is not found
            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.warp.notFound", player)
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }

    private static void renameWarp(ServerPlayer player, String warpName, String newWarpName)
            throws Exception {
        warpName = warpName.toLowerCase();
        newWarpName = newWarpName.toLowerCase();

        // check if there is no existing warp with the new name
        if (STORAGE.getWarp(newWarpName).isPresent()) {
            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.common.nameExists", player)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // get the existing warp
        Optional<NamedLocation> warpToRename = STORAGE.getWarp(warpName);

        if (warpToRename.isPresent()) {

            // set the new name
            warpToRename.get().setName(newWarpName);
            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.warp.rename", player),
                    true);

        } else {
            // the warp is not found
            sendPlayerMessage(
                    player,
                    getTranslatedText("commands.teleport_commands.warp.notFound", player)
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }
}
