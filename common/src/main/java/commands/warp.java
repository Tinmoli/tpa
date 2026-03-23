package tpa;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

import static tpa.StorageManager.*;
import tpa.gui.WarpsGui;
import static tpa.tools.*;
import static net.minecraft.commands.Commands.argument;

public class warp {
    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {

        commandDispatcher.register(Commands.literal("setwarp")
                .requires(source ->
                        source.getPlayer() != null &&
                                source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_OWNER)
                )
                .then(argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            final String name = StringArgumentType.getString(context, "name");
                            final ServerPlayer player = context.getSource().getPlayerOrException();

                            try {
                                SetWarp(player, name);

                            } catch (Exception e) {
                                Constants.LOGGER.error("Error while setting the warp!", e);
                                player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                return 1;
                            }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("warp")
                .requires(source -> source.getPlayer() != null)
                .then(argument("name", StringArgumentType.greedyString())
                        .suggests(new WarpSuggestionProvider())
                        .executes(context -> {
                            final String name = StringArgumentType.getString(context, "name");
                            final ServerPlayer player = context.getSource().getPlayerOrException();

                            try {
                                GoToWarp(player, name);

                            } catch (Exception e) {
                                Constants.LOGGER.error("Error while going to the warp!",e);
                                player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                return 1;
                            }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("delwarp")
                .requires(source ->
                        source.getPlayer() != null &&
                                source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_OWNER)
                )
                .then(argument("name", StringArgumentType.greedyString()).suggests(new WarpSuggestionProvider())
                        .executes(context -> {
                            final String name = StringArgumentType.getString(context, "name");
                            final ServerPlayer player = context.getSource().getPlayerOrException();

                            try {
                                DeleteWarp(player, name);

                            } catch (Exception e) {
                                Constants.LOGGER.error("Error while deleting to the warp!", e);
                                player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                return 1;
                            }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("renamewarp")
                .requires(source ->
                        source.getPlayer() != null &&
                                source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_OWNER)
                )
                .then(argument("name", StringArgumentType.string()).suggests(new WarpSuggestionProvider())
                        .then(argument("newName", StringArgumentType.string())
                                .executes(context -> {
                                    final String name = StringArgumentType.getString(context, "name");
                                    final String newName = StringArgumentType.getString(context, "newName");
                                    final ServerPlayer player = context.getSource().getPlayerOrException();

                                    try {
                                        RenameWarp(player, name, newName);

                                    } catch (Exception e) {
                                        Constants.LOGGER.error("Error while renaming the warp!", e);
                                        player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                        return 1;
                                    }
                                    return 0;
                                }))));

        commandDispatcher.register(Commands.literal("warps")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> {
                    final ServerPlayer player = context.getSource().getPlayerOrException();

                    try {
                        java.util.List<NamedLocation> warps = StorageManager.STORAGE.getWarps();
                        new WarpsGui(player, new java.util.ArrayList<>(warps)).open();

                    } catch (Exception e) {
                        Constants.LOGGER.error("Error while opening warps GUI!", e);
                        player.displayClientMessage(getTranslatedText("commands.teleport_commands.warps.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        return 1;
                    }
                    return 0;
                }));
    }


    private static void SetWarp(ServerPlayer player, String warpName) throws Exception {
        warpName = warpName.toLowerCase();

        BlockPos blockPos = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        String worldString = player.level().dimension().identifier().toString();

        // Create the NamedLocation
        NamedLocation warp = new NamedLocation(warpName, blockPos, worldString);

        // Adds the warp, returns true if the warp already exists
        boolean warpExists = STORAGE.addWarp(warp);

        if (warpExists) {
            // Display error message that the warp already exists
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.exists", player).withStyle(ChatFormatting.RED), true);

        } else {
            // Display message that the home as been set
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.set", player), true);
        }
    }

    private static void GoToWarp(ServerPlayer player, String warpName) throws Exception {
        warpName = warpName.toLowerCase();

        // Gets warp
        Optional<NamedLocation> optionalWarp = STORAGE.getWarp(warpName);
        if (optionalWarp.isEmpty()) {
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.notFound", player).withStyle(ChatFormatting.RED), true);
            return;
        }

        NamedLocation warp = optionalWarp.get();

        // Get the world, otherwise give a warning and error message
        Optional<ServerLevel> optionalWorld = warp.getWorld();

        if (optionalWorld.isEmpty()) {
            Constants.LOGGER.warn("({}) Error while going to the warp \"{}\"! \nCouldn't find a world with the id: \"{}\" \nAvailable worlds: {}",
                    player.getName().getString(),
                    warp.getName(),
                    warp.getWorldString(),
                    tools.getWorldIds());

            player.displayClientMessage(getTranslatedText("commands.teleport_commands.common.worldNotFound", player)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);

            return;
        }

        ServerLevel warpWorld = optionalWorld.get();

        BlockPos teleportBlockPos = warp.getBlockPos();

        // Check if the player is already at this location (in the same world)
        if (player.blockPosition().equals(teleportBlockPos) && player.level() == warpWorld) {
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.goSame", player).withStyle(ChatFormatting.AQUA), true);

        } else {
            // Teleport the player!
            Vec3 teleportPos = new Vec3(teleportBlockPos.getX() + 0.5, teleportBlockPos.getY(), teleportBlockPos.getZ() + 0.5);

            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.go", player), true);
            Teleporter(player, warpWorld, teleportPos);
        }
    }

    private static void DeleteWarp(ServerPlayer player, String warpName) throws Exception {
        warpName = warpName.toLowerCase();

        // get the existing warp
        Optional<NamedLocation> optionalWarp = STORAGE.getWarp(warpName);

        if (optionalWarp.isPresent()) {
            // Delete the warp
            STORAGE.removeWarp(optionalWarp.get());

            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.delete", player), true);

        } else {
            // the warp is not found
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.notFound", player).withStyle(ChatFormatting.RED), true);
        }
    }

    private static void RenameWarp(ServerPlayer player, String warpName, String newWarpName) throws Exception {
        warpName = warpName.toLowerCase();
        newWarpName = newWarpName.toLowerCase();

        // check if there is no existing warp with the new name
        if (STORAGE.getWarp(newWarpName).isPresent()) {
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.common.nameExists", player).withStyle(ChatFormatting.RED), true);
            return;
        }

        // get the existing warp
        Optional<NamedLocation> warpToRename = STORAGE.getWarp(warpName);

        if (warpToRename.isPresent()) {

            // set the new name
            warpToRename.get().setName(newWarpName);
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.rename", player), true);

        } else {
            // the warp is not found
            player.displayClientMessage(getTranslatedText("commands.teleport_commands.warp.notFound", player).withStyle(ChatFormatting.RED), true);
        }
    }

}
