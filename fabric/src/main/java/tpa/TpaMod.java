package tpa;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import tpa.command.BackCommand;
import tpa.command.CommandListCommand;
import tpa.command.HomeCommand;
import tpa.command.ReloadCommand;
import tpa.command.RtpCommand;
import tpa.command.SpawnCommand;
import tpa.command.StorageConvertCommand;
import tpa.command.TpaCommand;
import tpa.command.WarpCommand;
import tpa.config.ConfigManager;
import tpa.language.LanguageManager;
import tpa.rtp.RtpManager;
import tpa.storage.StorageManager;
import tpa.teleport.DeathLocationStorage;
import tpa.teleport.TeleportDelayManager;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TpaMod {
    public static String MOD_LOADER;
    public static Path SAVE_DIR;
    public static Path CONFIG_DIR;
    public static Path LANG_DIR;
    public static MinecraftServer SERVER;

    /** Initializes configuration, storage and runtime services for this server. */
    public static void initializeMod(MinecraftServer server) {
        RtpManager.reset(true);
        Constants.LOGGER.info("Initializing tpa (V{})! Hello {}!", Constants.VERSION, MOD_LOADER);

        // Static state survives integrated-server restarts in the same JVM.
        // Cancel old timers and requests before binding to the new server.
        TeleportDelayManager.cancelAll();
        TpaCommand.clearRequests();

        SAVE_DIR = Path.of(String.valueOf(server.getWorldPath(LevelResource.ROOT)));
        CONFIG_DIR = Paths.get(System.getProperty("user.dir")).resolve("config").resolve("tpa");
        LANG_DIR = CONFIG_DIR.resolve("lang");
        SERVER = server;

        ConfigManager.initialize();
        StorageManager.initialize();
        DeathLocationStorage.clearDeathLocations();

        // Synchronize managed built-in language files on every startup.
        LanguageManager.syncBuiltinLangFiles();
    }

    /**
     * Reloads all mutable runtime state from disk. Pending teleports and TPA requests are cancelled
     * first so callbacks created with old settings or player references cannot run after the
     * reload.
     */
    public static synchronized void reloadRuntimeState() throws Exception {
        RtpManager.reset(false);
        if (CONFIG_DIR == null || LANG_DIR == null || SERVER == null) {
            throw new IllegalStateException("TPA has not finished initializing.");
        }

        TeleportDelayManager.cancelAll();
        TpaCommand.clearRequests();
        ConfigManager.load();
        StorageManager.reloadFromSqlite();
        // Death locations are runtime-only player data and must survive a
        // configuration/storage reload.
        LanguageManager.syncBuiltinLangFiles();

        // Refresh each connected client's command tree because enabled flags are
        // evaluated by command requirements and may have changed.
        for (ServerPlayer player : SERVER.getPlayerList().getPlayers()) {
            SERVER.getCommands().sendCommands(player);
        }

        Constants.LOGGER.info("Reloaded TPA configuration, SQLite storage, and language files.");
    }

    /** Registers the command handlers; their requirements honor the current configuration. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        BackCommand.register(dispatcher);
        HomeCommand.register(dispatcher);
        TpaCommand.register(dispatcher);
        WarpCommand.register(dispatcher);
        SpawnCommand.register(dispatcher);
        CommandListCommand.register(dispatcher);
        RtpCommand.register(dispatcher);
        StorageConvertCommand.register(dispatcher);
        ReloadCommand.register(dispatcher);
    }

    // Runs when the playerDeath mixin calls it, updates the /back command position
    public static void onPlayerDeath(ServerPlayer player) {
        BlockPos pos = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        String world = player.level().dimension().identifier().toString();
        String uuid = player.getStringUUID();

        DeathLocationStorage.setDeathLocation(uuid, pos, world);
    }
}
