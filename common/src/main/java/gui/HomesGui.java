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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import static tpa.tools.getTranslatedText;
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
            Component hint = getTranslatedText("gui.teleport_commands.homes.hint", player)
                    .withStyle(ChatFormatting.YELLOW);

            int slot = i - start;
            setSlot(slot, new GuiElementBuilder(isDefault ? Items.YELLOW_BED : Items.CYAN_BED)
                    .setName(name)
                    .addLoreLine(coords)
                    .addLoreLine(world)
                    .addLoreLine(Component.empty())
                    .addLoreLine(hint)
                    .setCallback((index, type, action) -> {
                        if (type == ClickType.MOUSE_LEFT) {
                            home.getWorld().ifPresent(world1 -> {
                                close();
                                player.displayClientMessage(
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
                            player.displayClientMessage(
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

    private void fillNavBar() {
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build());
        }
        if (page > 0) {
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText("gui.teleport_commands.common.prev_page", player).withStyle(ChatFormatting.WHITE))
                    .setCallback((i, t, a) -> { page--; build(); }).build());
        }
        setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .setName(getTranslatedText("gui.teleport_commands.common.close", player).withStyle(ChatFormatting.RED))
                .setCallback((i, t, a) -> close()).build());
        if ((page + 1) * PAGE_SIZE < homes.size()) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText("gui.teleport_commands.common.next_page", player).withStyle(ChatFormatting.WHITE))
                    .setCallback((i, t, a) -> { page++; build(); }).build());
        }
    }
}
