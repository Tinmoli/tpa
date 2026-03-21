package tpa;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class tpaSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String playerUUID = player.getStringUUID();
            TpaCommand.getAllRequests().stream()
                    .filter(r -> r.targetUUID.equals(playerUUID))
                    .forEach(r -> {
                        ServerPlayer initiator = context.getSource().getServer()
                                .getPlayerList().getPlayer(UUID.fromString(r.initiatorUUID));
                        if (initiator != null) builder.suggest(initiator.getName().getString());
                    });
            return builder.buildFuture();
        } catch (Exception e) {
            Constants.LOGGER.error("Error getting tpa suggestions! ", e);
            return builder.buildFuture();
        }
    }
}
