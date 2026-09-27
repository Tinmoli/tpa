package tpa.gui;

import static tpa.language.LanguageManager.getTranslatedText;
import static tpa.teleport.TeleportService.teleport;
import static tpa.util.MessageService.sendPlayerMessage;

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

import tpa.Constants;
import tpa.config.ConfigManager;
import tpa.storage.NamedLocation;
import tpa.storage.PlayerData;
import tpa.teleport.TeleportDelayManager;

import java.util.ArrayList;
import java.util.List;

public class HomesGui extends SimpleGui {

    private final ServerPlayer player;
    private List<NamedLocation> homes;
    private final PlayerData playerStorage;
    private int page = 0;
    private boolean choosingDefault;
    private static final int PAGE_SIZE = 36;

    public HomesGui(ServerPlayer player, PlayerData playerStorage, List<NamedLocation> homes) {
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
        setTitle(
                getTranslatedText(
                                "gui.teleport_commands.homes.title",
                                player,
                                Component.literal(String.valueOf(current)),
                                Component.literal(String.valueOf(max)))
                        .withStyle(ChatFormatting.YELLOW));
        if (choosingDefault) {
            setTitle(getTranslatedText("gui.teleport_commands.homes.choose_default_title", player));
        }
    }

    private void build() {
        updateTitle();
        for (int i = 0; i < getSize(); i++) clearSlot(i);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, homes.size());

        for (int i = start; i < end; i++) {
            NamedLocation home = homes.get(i);

            Component name = Component.literal(home.getName()).withStyle(ChatFormatting.AQUA);
            Component coords =
                    Component.literal(
                                    String.format(
                                            "X%d Y%d Z%d", home.getX(), home.getY(), home.getZ()))
                            .withStyle(ChatFormatting.GRAY);
            Component world =
                    Component.literal(home.getWorldString()).withStyle(ChatFormatting.DARK_GRAY);
            Component actionHint =
                    getTranslatedText(
                                    choosingDefault
                                            ? "gui.teleport_commands.homes.choose_default_hint"
                                            : "gui.teleport_commands.homes.hint_actions_v2",
                                    player)
                            .withStyle(ChatFormatting.YELLOW);
            Component iconHint =
                    getTranslatedText("gui.teleport_commands.homes.hint_icons", player)
                            .withStyle(ChatFormatting.YELLOW);

            int slot = i - start;
            Item displayIcon = resolveIcon(home);
            setSlot(
                    slot,
                    new GuiElementBuilder(displayIcon)
                            .hideDefaultTooltip()
                            .setName(name)
                            .addLoreLine(coords)
                            .addLoreLine(world)
                            .addLoreLine(
                                    playerStorage != null
                                                    && playerStorage
                                                            .getDefaultHome()
                                                            .equals(home.getName())
                                            ? getTranslatedText(
                                                            "commands.teleport_commands.common.default",
                                                            player)
                                                    .withStyle(ChatFormatting.GRAY)
                                            : Component.empty())
                            .addLoreLine(Component.empty())
                            .addLoreLine(actionHint)
                            .addLoreLine(iconHint)
                            .setCallback(
                                    type -> {
                                        if (!ConfigManager.CONFIG.home.isEnabled()) {
                                            close();
                                            return;
                                        }
                                        if (type == ClickType.MOUSE_MIDDLE
                                                || choosingDefault
                                                        && type == ClickType.MOUSE_LEFT) {
                                            if (playerStorage == null) {
                                                return;
                                            }
                                            if (playerStorage
                                                    .getDefaultHome()
                                                    .equals(home.getName())) {
                                                choosingDefault = false;
                                                sendPlayerMessage(
                                                        player,
                                                        getTranslatedText(
                                                                        "commands.teleport_commands.home.defaultSame",
                                                                        player)
                                                                .withStyle(ChatFormatting.AQUA),
                                                        true);
                                                build();
                                                return;
                                            }
                                            try {
                                                playerStorage.setDefaultHome(home.getName());
                                                choosingDefault = false;
                                                sendPlayerMessage(
                                                        player,
                                                        getTranslatedText(
                                                                        "commands.teleport_commands.home.default",
                                                                        player)
                                                                .withStyle(ChatFormatting.GREEN),
                                                        true);
                                                build();
                                            } catch (Exception ex) {
                                                Constants.LOGGER.error(
                                                        "Error setting default home in GUI", ex);
                                                sendPlayerMessage(
                                                        player,
                                                        getTranslatedText(
                                                                        "commands.teleport_commands.home.error",
                                                                        player)
                                                                .withStyle(ChatFormatting.RED),
                                                        true);
                                            }
                                        } else if (choosingDefault) {
                                            // Selection mode must never delete homes or open
                                            // another editor.
                                            return;
                                        } else if (type == ClickType.MOUSE_LEFT_SHIFT) {
                                            close();
                                            new IconPickerGui(
                                                            player,
                                                            home,
                                                            false,
                                                            () ->
                                                                    new HomesGui(
                                                                                    player,
                                                                                    playerStorage,
                                                                                    new ArrayList<>(
                                                                                            homes))
                                                                            .open())
                                                    .open();
                                        } else if (type == ClickType.MOUSE_RIGHT_SHIFT) {
                                            try {
                                                home.setIcon("");
                                                sendPlayerMessage(
                                                        player,
                                                        getTranslatedText(
                                                                        "gui.teleport_commands.homes.icon_reset",
                                                                        player)
                                                                .withStyle(ChatFormatting.GREEN),
                                                        true);
                                                build();
                                            } catch (Exception ex) {
                                                Constants.LOGGER.error(
                                                        "Error resetting home icon", ex);
                                            }
                                        } else if (type == ClickType.MOUSE_LEFT) {
                                            home.getWorld()
                                                    .ifPresent(
                                                            world1 -> {
                                                                close();
                                                                sendPlayerMessage(
                                                                        player,
                                                                        getTranslatedText(
                                                                                "commands.teleport_commands.home.go",
                                                                                player),
                                                                        true);
                                                                int delay =
                                                                        ConfigManager.CONFIG.home
                                                                                .getDelay();
                                                                var pos =
                                                                        new net.minecraft.world.phys
                                                                                .Vec3(
                                                                                home.getX() + 0.5,
                                                                                home.getY(),
                                                                                home.getZ() + 0.5);
                                                                if (delay <= 0) {
                                                                    teleport(player, world1, pos);
                                                                } else {
                                                                    TeleportDelayManager
                                                                            .startDelaySimple(
                                                                                    player,
                                                                                    delay,
                                                                                    () ->
                                                                                            teleport(
                                                                                                    player,
                                                                                                    world1,
                                                                                                    pos));
                                                                }
                                                            });
                                        } else if (type == ClickType.MOUSE_RIGHT) {
                                            confirmDelete(home);
                                        }
                                    })
                            .build());
        }

        fillNavBar();
    }

    private void confirmDelete(NamedLocation home) {
        for (int i = 0; i < getSize(); i++) clearSlot(i);
        setTitle(getTranslatedText("gui.teleport_commands.homes.delete_title", player));
        setSlot(
                13,
                new GuiElementBuilder(resolveIcon(home))
                        .setName(Component.literal(home.getName()))
                        .addLoreLine(Component.literal(home.getWorldString()))
                        .addLoreLine(
                                Component.literal(
                                        String.format(
                                                "X%d Y%d Z%d",
                                                home.getX(), home.getY(), home.getZ())))
                        .build());
        setSlot(
                30,
                new GuiElementBuilder(Items.EMERALD)
                        .setName(
                                getTranslatedText(
                                        "gui.teleport_commands.homes.delete_confirm", player))
                        .setCallback(
                                type -> {
                                    if (type != ClickType.MOUSE_LEFT || playerStorage == null)
                                        return;
                                    if (!ConfigManager.CONFIG.home.isEnabled()) {
                                        close();
                                        return;
                                    }
                                    try {
                                        playerStorage.deleteHome(home);
                                    } catch (Exception ex) {
                                        Constants.LOGGER.error("Error deleting home in GUI", ex);
                                        sendPlayerMessage(
                                                player,
                                                getTranslatedText(
                                                                "commands.teleport_commands.home.error",
                                                                player)
                                                        .withStyle(ChatFormatting.RED),
                                                true);
                                        build();
                                        return;
                                    }
                                    sendPlayerMessage(
                                            player,
                                            getTranslatedText(
                                                    "commands.teleport_commands.home.delete",
                                                    player),
                                            true);
                                    homes.remove(home);
                                    if (page > 0 && page * PAGE_SIZE >= homes.size()) page--;
                                    build();
                                })
                        .build());
        setSlot(
                32,
                new GuiElementBuilder(Items.BARRIER)
                        .setName(
                                getTranslatedText(
                                        "gui.teleport_commands.homes.delete_cancel", player))
                        .setCallback(
                                type -> {
                                    if (type == ClickType.MOUSE_LEFT) build();
                                })
                        .build());
    }

    private Item resolveIcon(NamedLocation home) {
        String iconId = home.getIcon();
        if (!iconId.isBlank()) {
            Identifier id = Identifier.tryParse(iconId);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.getValue(id);
            }
        }
        return BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:cyan_bed"));
    }

    private void fillNavBar() {
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(GuiItems.background()).hideTooltip().build());
        }
        if (page > 0) {
            setSlot(
                    45,
                    new GuiElementBuilder(Items.ARROW)
                            .setName(
                                    getTranslatedText(
                                                    "gui.teleport_commands.common.prev_page",
                                                    player)
                                            .withStyle(ChatFormatting.WHITE))
                            .setCallback(
                                    () -> {
                                        page--;
                                        build();
                                    })
                            .build());
        }
        setSlot(
                48,
                new GuiElementBuilder(Items.COMPASS)
                        .setName(
                                getTranslatedText(
                                        choosingDefault
                                                ? "gui.teleport_commands.homes.cancel_default"
                                                : "gui.teleport_commands.homes.set_default",
                                        player))
                        .addLoreLine(
                                getTranslatedText(
                                        "gui.teleport_commands.homes.choose_default_hint", player))
                        .setCallback(
                                type -> {
                                    if (type != ClickType.MOUSE_LEFT) return;
                                    if (!ConfigManager.CONFIG.home.isEnabled()) {
                                        close();
                                        return;
                                    }
                                    choosingDefault = !choosingDefault;
                                    build();
                                })
                        .build());
        setSlot(
                49,
                new GuiElementBuilder(Items.BARRIER)
                        .setName(
                                getTranslatedText("gui.teleport_commands.common.close", player)
                                        .withStyle(ChatFormatting.RED))
                        .setCallback((type) -> this.close())
                        .build());
        if ((page + 1) * PAGE_SIZE < homes.size()) {
            setSlot(
                    53,
                    new GuiElementBuilder(Items.ARROW)
                            .setName(
                                    getTranslatedText(
                                                    "gui.teleport_commands.common.next_page",
                                                    player)
                                            .withStyle(ChatFormatting.WHITE))
                            .setCallback(
                                    () -> {
                                        page++;
                                        build();
                                    })
                            .build());
        }
    }
}
