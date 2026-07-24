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
                        SqliteStorage.convertJsonToSqlite(
                                tpa.CONFIG_DIR.resolve("storage.json"),
                                tpa.CONFIG_DIR.resolve("storage.db"));
                        context.getSource().sendSuccess(() -> Component.literal("JSON storage converted to SQLite."), true);
                        return 1;
                    } catch (Exception e) {
                        Constants.LOGGER.error("Failed to convert JSON storage to SQLite", e);
                        context.getSource().sendFailure(Component.literal("Storage conversion failed: " + e.getMessage()));
                        return 0;
                    }
                }))
                .then(Commands.literal("sqlite-to-json").executes(context -> {
                    try {
                        SqliteStorage.convertSqliteToJson(
                                tpa.CONFIG_DIR.resolve("storage.db"),
                                tpa.CONFIG_DIR.resolve("storage.json"));
                        context.getSource().sendSuccess(() -> Component.literal("SQLite storage converted to JSON."), true);
                        return 1;
                    } catch (Exception e) {
                        Constants.LOGGER.error("Failed to convert SQLite storage to JSON", e);
                        context.getSource().sendFailure(Component.literal("Storage conversion failed: " + e.getMessage()));
                        return 0;
                    }
                })));
    }
}
