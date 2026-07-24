package tpa.gui;

import tpa.tpa;
import tpa.tools;
import tpa.ConfigManager;
import tpa.Constants;
import tpa.NamedLocation;
import tpa.Player;
import tpa.TeleportDelayManager;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import static tpa.tools.getTranslatedText;
import static tpa.tools.sendPlayerMessage;
import static tpa.tools.Teleporter;

public class HomesGui extends SimpleGui {

    private final ServerPlayer player;
    private List<NamedLocation> homes;
    private final Player playerStorage;
    private int page = 0;
    private static final int PAGE_SIZE = 36;

    public HomesGui(ServerPlayer player, Player playerStorage, List<NamedLocation> homes) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.playerStorage = playerStorage;
        this.homes = homes != null ? new ArrayList<>(homes) : new ArrayList<>();
        updateTitle();
        build();
    }

    private void updateTitle() {
        int max = ConfigManager.CONFIG.home.getPlayerMaximum();
        int current = homes.size();
        setTitle(getTranslatedText("gui.teleport_commands.homes.title", player,
                Component.literal(String.valueOf(current)),
                Component.literal(String.valueOf(max)))
                .withStyle(ChatFormatting.YELLOW));
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, homes.size());

        for (int i = start; i < end; i++) {
            NamedLocation home = homes.get(i);
            boolean isDefault = playerStorage != null && playerStorage.getDefaultHome().equals(home.getName());

            Component name = Component.literal(home.getName())
                    .withStyle(isDefault ? ChatFormatting.GOLD : ChatFormatting.AQUA);
            Component coords = Component.literal(String.format("X%d Y%d Z%d", home.getX(), home.getY(), home.getZ()))
                    .withStyle(ChatFormatting.GRAY);
            Component world = Component.literal(home.getWorldString())
                    .withStyle(ChatFormatting.DARK_GRAY);
            Component actionHint = getTranslatedText(
                    "gui.teleport_commands.homes.hint_actions", player)
                    .withStyle(ChatFormatting.YELLOW);
            Component iconHint = getTranslatedText(
                    "gui.teleport_commands.homes.hint_icons", player)
                    .withStyle(ChatFormatting.YELLOW);

            int slot = i - start;
            Item displayIcon = resolveIcon(home, isDefault);
            setSlot(slot, new GuiElementBuilder(displayIcon)
                    .setName(name)
                    .addLoreLine(coords)
                    .addLoreLine(world)
                    .addLoreLine(Component.empty())
                    .addLoreLine(actionHint)
                    .addLoreLine(iconHint)
                    .setCallback(type -> {
                        if (type == ClickType.MOUSE_MIDDLE) {
                            if (playerStorage == null) {
                                return;
                            }
                            if (playerStorage.getDefaultHome().equals(home.getName())) {
                                sendPlayerMessage(player,
                                        getTranslatedText(
                                                "commands.teleport_commands.home.defaultSame",
                                                player).withStyle(ChatFormatting.AQUA), true);
                                return;
                            }
                            try {
                                playerStorage.setDefaultHome(home.getName());
                                sendPlayerMessage(player,
                                        getTranslatedText(
                                                "commands.teleport_commands.home.default",
                                                player).withStyle(ChatFormatting.GREEN), true);
                                build();
                            } catch (Exception ex) {
                                Constants.LOGGER.error(
                                        "Error setting default home in GUI", ex);
                                sendPlayerMessage(player,
                                        getTranslatedText(
                                                "commands.teleport_commands.home.error",
                                                player).withStyle(ChatFormatting.RED), true);
                            }
                        } else if (type == ClickType.MOUSE_LEFT_SHIFT) {
                            close();
                            new IconPickerGui(player, home, false, () ->
                                    new HomesGui(player, playerStorage,
                                            new ArrayList<>(homes)).open()).open();
                        } else if (type == ClickType.MOUSE_RIGHT_SHIFT) {
                            try {
                                home.setIcon("");
                                sendPlayerMessage(player,
                                        getTranslatedText("gui.teleport_commands.homes.icon_reset", player)
                                                .withStyle(ChatFormatting.GREEN), true);
                                build();
                            } catch (Exception ex) {
                                Constants.LOGGER.error("Error resetting home icon", ex);
                            }
                        } else if (type == ClickType.MOUSE_LEFT) {
                            home.getWorld().ifPresent(world1 -> {
                                close();
                                sendPlayerMessage(player,
                                        getTranslatedText("commands.teleport_commands.home.go", player), true);
                                int delay = ConfigManager.CONFIG.home.getDelay();
                                var pos = new net.minecraft.world.phys.Vec3(
                                        home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                                if (delay <= 0) {
                                    Teleporter(player, world1, pos);
                                } else {
                                    TeleportDelayManager.startDelaySimple(player, delay, () ->
                                            Teleporter(player, world1, pos));
                                }
                            });
                        } else if (type == ClickType.MOUSE_RIGHT) {
                            try {
                                if (playerStorage != null) {
                                    playerStorage.deleteHome(home);
                                    if (playerStorage.getDefaultHome().equals(home.getName())) {
                                        playerStorage.setDefaultHome("");
                                    }
                                }
                            } catch (Exception ex) {
                                Constants.LOGGER.error("Error deleting home in GUI", ex);
                            }
                            sendPlayerMessage(player,
                                    getTranslatedText("commands.teleport_commands.home.delete", player), true);
                            homes.remove(home);
                            if (page > 0 && page * PAGE_SIZE >= homes.size()) page--;
                            updateTitle();
                            build();
                        }
                    }).build());
        }

        fillNavBar();
    }

    private Item resolveIcon(NamedLocation home, boolean isDefault) {
        String iconId = home.getIcon();
        if (!iconId.isBlank()) {
            Identifier id = Identifier.tryParse(iconId);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.getValue(id);
            }
        }
        return isDefault ? Items.YELLOW_BED : Items.CYAN_BED;
    }

    private void fillNavBar() {
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build());
        }
        if (page > 0) {
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText("gui.teleport_commands.common.prev_page", player).withStyle(ChatFormatting.WHITE))
                    .setCallback(() -> { page--; build(); }).build());
        }
        setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .setName(getTranslatedText("gui.teleport_commands.common.close", player).withStyle(ChatFormatting.RED))
                .setCallback((type) -> this.close()).build());
        if ((page + 1) * PAGE_SIZE < homes.size()) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText("gui.teleport_commands.common.next_page", player).withStyle(ChatFormatting.WHITE))
                    .setCallback(() -> { page++; build(); }).build());
        }
    }
}
