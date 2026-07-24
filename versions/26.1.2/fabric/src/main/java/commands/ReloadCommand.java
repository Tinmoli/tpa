package tpa;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/**
 * Reloads configuration, SQLite storage, and managed language files without
 * restarting the server.
 */
public final class ReloadCommand {
    private ReloadCommand() {}

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpareload")
                .requires(source -> source.permissions()
                        .hasPermission(Permissions.COMMANDS_OWNER))
                .executes(context -> {
                    try {
                        tpa.reloadRuntimeState();
                        context.getSource().sendSuccess(
                                () -> Component.literal(
                                        "TPA configuration, SQLite storage, "
                                                + "and language files reloaded."),
                                true);
                        return 1;
                    } catch (Exception e) {
                        Constants.LOGGER.error(
                                "Failed to reload TPA runtime state", e);
                        context.getSource().sendFailure(Component.literal(
                                "TPA reload failed: " + e.getMessage()));
                        return 0;
                    }
                }));
    }
}