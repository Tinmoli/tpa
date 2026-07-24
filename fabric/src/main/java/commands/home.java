package tpa;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.List;
import java.util.Optional;

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

import static tpa.StorageManager.STORAGE;
import tpa.gui.HomesGui;
import static tpa.tools.getTranslatedText;
import static net.minecraft.commands.Commands.argument;
import static tpa.tools.Teleporter;
import static tpa.tools.sendPlayerMessage;

public class home {
    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {

        commandDispatcher.register(Commands.literal("sethome")
                .requires(source -> source.getPlayer() != null)
                .then(argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            final String name = StringArgumentType.getString(context, "name");
                            final ServerPlayer player = context.getSource().getPlayerOrException();

                            try {
                                SetHome(player, name);

                            } catch (Exception e) {
                                Constants.LOGGER.error("Error while setting a home! => ", e);
                                sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                return 1;
                            }
                            return 0;
                        })));


        commandDispatcher.register(Commands.literal("home")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> {
                    final ServerPlayer player = context.getSource().getPlayerOrException();

                    try {
                        GoHome(player, "");

                    } catch (Exception e) {
                        Constants.LOGGER.error("Error while going home! => ", e);
                        sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        return 1;
                    }
                    return 0;
                })
                .then(argument("name", StringArgumentType.greedyString())
                        .suggests(new HomeSuggestionProvider())
                        .requires(source -> source.getPlayer() != null)
                        .executes(context -> {
                            final String name = StringArgumentType.getString(context, "name");
                            final ServerPlayer player = context.getSource().getPlayerOrException();

                            try {
                                GoHome(player, name);

                            } catch (Exception e) {
                                Constants.LOGGER.error("Error while going to a specific home! => ", e);
                                sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                return 1;
                            }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("delhome")
                .requires(source -> source.getPlayer() != null)
                .then(argument("name", StringArgumentType.greedyString())
                        .suggests(new HomeSuggestionProvider())
                        .executes(context -> {
                            final String name = StringArgumentType.getString(context, "name");
                            final ServerPlayer player = context.getSource().getPlayerOrException();

                            try {
                                DeleteHome(player, name);

                            } catch (Exception e) {
                                Constants.LOGGER.error("Error while deleting a home! => ", e);
                                sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                return 1;
                            }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("renamehome")
                .requires(source -> source.getPlayer() != null)
                .then(argument("name", StringArgumentType.string())
                        .suggests(new HomeSuggestionProvider())
                        .then(argument("newName", StringArgumentType.greedyString())
                                .executes(context -> {
                                    final String name = StringArgumentType.getString(context, "name");
                                    final String newName = StringArgumentType.getString(context, "newName");
                                    final ServerPlayer player = context.getSource().getPlayerOrException();

                                    try {
                                        RenameHome(player, name, newName);

                                    } catch (Exception e) {
                                        Constants.LOGGER.error("Error while renaming a home! => ", e);
                                        sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.renameError", player)
                                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                        return 1;
                                    }
                                    return 0;
                                }))));


        commandDispatcher.register(Commands.literal("defaulthome")
                .requires(source -> source.getPlayer() != null)
                .then(argument("name", StringArgumentType.greedyString()).suggests(new HomeSuggestionProvider())
                        .executes(context -> {
                            final String name = StringArgumentType.getString(context, "name");
                            final ServerPlayer player = context.getSource().getPlayerOrException();

                            try {
                                SetDefaultHome(player, name);

                            } catch (Exception e) {
                                Constants.LOGGER.error("Error while setting the default home! => ", e);
                                sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                return 1;
                            }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("homes")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> {
                    final ServerPlayer player = context.getSource().getPlayerOrException();

                    try {
                        java.util.Optional<Player> optionalPlayerStorage = StorageManager.STORAGE.getPlayer(player.getStringUUID());
                        Player playerStorage = optionalPlayerStorage.orElse(null);
                        java.util.List<NamedLocation> homes = playerStorage != null ? new java.util.ArrayList<>(playerStorage.getHomes()) : new java.util.ArrayList<>();
                        new HomesGui(player, playerStorage, homes).open();

                    } catch (Exception e) {
                        Constants.LOGGER.error("Error while opening homes GUI! => ", e);
                        sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.error", player).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        return 1;
                    }
                    return 0;
                }));
    }


    // -----

    // Adds a new home to the homeList of a player
    private static void SetHome(ServerPlayer player, String homeName) throws Exception {
        homeName = homeName.toLowerCase();
        BlockPos blockPos = player.blockPosition();
        String worldString = player.level().dimension().identifier().toString();

        // Gets the player's storage and creates it if it doesn't exist
        Player playerStorage = StorageManager.STORAGE.addPlayer(player.getStringUUID());

        // Create the NamedLocation
        NamedLocation warp = new NamedLocation(homeName, blockPos, worldString);

        // Enforce the configured per-player limit before adding a new home.
        int maximum = ConfigManager.CONFIG.home.getPlayerMaximum();
        if (playerStorage.getHome(homeName).isEmpty() && (maximum <= 0 || playerStorage.getHomes().size() >= maximum)) {
            sendPlayerMessage(player, getTranslatedText("commands.teleport_commands.home.maximum", player)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // Adds the home, returns true if the home already exists
        boolean homeExists = playerStorage.addHome(warp);

        if (homeExists) {
            // Display error message that the home already exists
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.exists", player).withStyle(ChatFormatting.RED), true);

        } else {
            // Set it as the default if there are no other homes
            if (playerStorage.getHomes().size() == 1) {
                playerStorage.setDefaultHome(homeName);
            }

            // Display message that the home has been set
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.set", player), true);
        }
    }

    // Teleports the player to the home. It will go to the defaultHome if homeName is empty
    private static void GoHome(ServerPlayer player, String homeName) throws Exception {
        homeName = homeName.toLowerCase();

        // Get player storage
        Optional<Player> optionalPlayerStorage = STORAGE.getPlayer(player.getStringUUID());
        if (optionalPlayerStorage.isEmpty()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.homeless", player).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        Player playerStorage = optionalPlayerStorage.get();

        // If homeName is empty, get the default home
        if (homeName.isEmpty()) {
            String defaultHome = playerStorage.getDefaultHome();

            if (defaultHome.isEmpty()) {
                // No default home set!
                sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.defaultNone", player).withStyle(ChatFormatting.AQUA), true);

                return;
            } else {
                homeName = defaultHome;
            }
        }

        // Get the home (if it exists)
        Optional<NamedLocation> optionalHome = playerStorage.getHome(homeName);
        if (optionalHome.isEmpty()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.notFound", player).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        NamedLocation home = optionalHome.get();

        // Get the world, otherwise give a warning and error message
        Optional<ServerLevel> optionalWorld = home.getWorld();

        if (optionalWorld.isEmpty()) {
            Constants.LOGGER.warn("({}) Error while going to the home \"{}\"! \nCouldn't find a world with the id: \"{}\" \nAvailable worlds: {}",
                    player.getName().getString(),
                    home.getName(),
                    home.getWorldString(),
                    tools.getWorldIds());

            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.common.worldNotFound", player)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);

            return;
        }

        ServerLevel homeWorld = optionalWorld.get();

        BlockPos teleportBlockPos = home.getBlockPos();

        // Check if the player is already at this location (in the same world)
        if (player.blockPosition().equals(teleportBlockPos) && player.level() == homeWorld) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.goSame", player).withStyle(ChatFormatting.AQUA), true);

        } else {
            // Teleport the player!
            Vec3 teleportPos = new Vec3(teleportBlockPos.getX() + 0.5, teleportBlockPos.getY(), teleportBlockPos.getZ() + 0.5);

            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.go", player), true);

            int delay = ConfigManager.CONFIG.home.getDelay();
            if (delay <= 0) {
                Teleporter(player, homeWorld, teleportPos);
            } else {
                final ServerLevel finalHomeWorld = homeWorld;
                final Vec3 finalTeleportPos = teleportPos;
                TeleportDelayManager.startDelaySimple(player, delay, () ->
                    Teleporter(player, finalHomeWorld, finalTeleportPos)
                );
            }
        }
    }

    private static void DeleteHome(ServerPlayer player, String homeName) throws Exception {
        homeName = homeName.toLowerCase();

        // Gets player storage
        Optional<Player> optionalPlayerStorage = STORAGE.getPlayer(player.getStringUUID());
        if (optionalPlayerStorage.isEmpty()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.homeless", player).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        Player playerStorage = optionalPlayerStorage.get();

        // Get the home from the player
        Optional<NamedLocation> optionalHome = playerStorage.getHome(homeName);
        if (optionalHome.isEmpty()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.notFound", player).withStyle(ChatFormatting.RED), true);
            return;
        }

        // delete the home
        playerStorage.deleteHome(optionalHome.get());

        // check if it's the default home, if it is set it to the default value
        if (playerStorage.getDefaultHome().equals(homeName)) {
            playerStorage.setDefaultHome("");

            // todo! maybe ask the player if they want to set a new default home? :3
        }

        sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.delete", player), true);
    }

    private static void RenameHome(ServerPlayer player, String homeName, String newHomeName) throws Exception {
        homeName = homeName.toLowerCase();
        newHomeName = newHomeName.toLowerCase();

        // Gets player storage
        Optional<Player> optionalPlayerStorage = STORAGE.getPlayer(player.getStringUUID());
        if (optionalPlayerStorage.isEmpty()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.homeless", player).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        Player playerStorage = optionalPlayerStorage.get();

        // Check if there already is a home with the new name
        if (playerStorage.getHome(newHomeName).isPresent()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.common.nameExists", player).withStyle(ChatFormatting.RED), true);
            return;
        }

        // Get the home that needs to be renamed
        Optional<NamedLocation> optionalHome = playerStorage.getHome(homeName);
        if (optionalHome.isEmpty()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.notFound", player).withStyle(ChatFormatting.RED), true);
            return;
        }

        // Rename home
        optionalHome.get().setName(newHomeName);

        // check if the current home is the default, then change it to the new name
        if (playerStorage.getDefaultHome().equals(homeName)) {
            playerStorage.setDefaultHome(newHomeName);
        }

        sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.rename", player), true);
    }

    private static void SetDefaultHome(ServerPlayer player, String homeName) throws Exception {
        homeName = homeName.toLowerCase();

        // Gets player storage
        Optional<Player> optionalPlayerStorage = STORAGE.getPlayer(player.getStringUUID());
        if (optionalPlayerStorage.isEmpty()) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.homeless", player).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        Player playerStorage = optionalPlayerStorage.get();

        // Check if the new default home exists
        if ( playerStorage.getHome(homeName).isEmpty() ) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.notFound", player).withStyle(ChatFormatting.RED), true);
            return;
        }

        // Check if the home is already the default
        if (playerStorage.getDefaultHome().equals(homeName)) {
            sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.defaultSame", player).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        // set the new default
        playerStorage.setDefaultHome(homeName);
        sendPlayerMessage(player,getTranslatedText("commands.teleport_commands.home.default", player), true);
    }

}