package tpa;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class StorageConvertCommand {
    private StorageConvertCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpastorage")
                .requires(source -> source.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_OWNER))
                .then(Commands.literal("json-to-sqlite").executes(context -> {
                    try {
                        java.nio.file.Path jsonFile =
                                tpa.CONFIG_DIR.resolve("storage.json");
                        if (!java.nio.file.Files.isRegularFile(jsonFile)
                                || java.nio.file.Files.size(jsonFile) == 0) {
                            context.getSource().sendFailure(Component.literal(
                                    "storage.json was not found or is empty."));
                            return 0;
                        }
                        // Import through a verified temporary database. The
                        // live DB is replaced only after verification and an
                        // existing DB is retained as a timestamped backup.
                        SqliteStorage.importJsonAtomically(
                                jsonFile,
                                tpa.CONFIG_DIR.resolve("storage.db"));

                        // Reload through the same path as /tpareload so the
                        // active in-memory state always comes from the newly
                        // written SQLite database.
                        tpa.reloadRuntimeState();
                        context.getSource().sendSuccess(() -> Component.literal(
                                "JSON storage imported into SQLite and "
                                + "automatically reloaded. The previous "
                                + "database was backed up when present."),
                                true);
                        return 1;
                    } catch (Exception e) {
                        Constants.LOGGER.error("Failed to convert JSON storage to SQLite", e);
                        context.getSource().sendFailure(Component.literal("Storage conversion failed: " + e.getMessage()));
                        return 0;
                    }
                })));
    }
}
