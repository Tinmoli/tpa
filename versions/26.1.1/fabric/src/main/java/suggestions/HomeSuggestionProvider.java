package tpa;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static tpa.StorageManager.STORAGE;

public class HomeSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Optional<Player> optionalPlayerStorage = STORAGE.getPlayer(player.getStringUUID());

            if (optionalPlayerStorage.isPresent()) {
                Player playerStorage = optionalPlayerStorage.get();

                for (NamedLocation currentHome : playerStorage.getHomes()) {
                    builder.suggest(currentHome.getName());
                }
            }

            // Build and return the suggestions
            return builder.buildFuture();
        } catch (Exception e) {
            Constants.LOGGER.error("Error getting home suggestions! ", e);
            return null;
        }
    }
}
