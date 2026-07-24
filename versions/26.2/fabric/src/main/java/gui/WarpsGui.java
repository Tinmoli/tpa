package tpa.gui;

import tpa.tpa;
import tpa.tools;
import tpa.Constants;
import tpa.NamedLocation;
import tpa.StorageManager;
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

public class WarpsGui extends SimpleGui {

    private final ServerPlayer player;
    private List<NamedLocation> warps;
    private int page = 0;
    private static final int PAGE_SIZE = 36;

    public WarpsGui(ServerPlayer player, List<NamedLocation> warps) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.warps = warps != null ? new ArrayList<>(warps) : new ArrayList<>();
        setTitle(getTranslatedText("gui.teleport_commands.warps.title", player,
                Component.literal(String.valueOf(this.warps.size())))
                .withStyle(ChatFormatting.YELLOW));
        build();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);
        boolean canModify = player.createCommandSourceStack().permissions().hasPermission(
                net.minecraft.server.permissions.Permissions.COMMANDS_OWNER);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, warps.size());

        for (int i = start; i < end; i++) {
            NamedLocation warp = warps.get(i);
            Component name = Component.literal(warp.getName()).withStyle(ChatFormatting.AQUA);
            Component coords = Component.literal(String.format("X%d Y%d Z%d", warp.getX(), warp.getY(), warp.getZ()))
                    .withStyle(ChatFormatting.GRAY);
            Component world = Component.literal(warp.getWorldString()).withStyle(ChatFormatting.DARK_GRAY);
            Component actionHint = canModify
                    ? getTranslatedText(
                            "gui.teleport_commands.warps.hint_admin_actions", player)
                            .withStyle(ChatFormatting.YELLOW)
                    : getTranslatedText(
                            "gui.teleport_commands.warps.hint", player)
                            .withStyle(ChatFormatting.YELLOW);
            Component iconHint = canModify
                    ? getTranslatedText(
                            "gui.teleport_commands.warps.hint_admin_icons", player)
                            .withStyle(ChatFormatting.YELLOW)
                    : null;

            int slot = i - start;
            Item displayIcon = resolveIcon(warp);
            setSlot(slot, new GuiElementBuilder(displayIcon)
                    .hideDefaultTooltip()
                    .setName(name)
                    .addLoreLine(coords)
                    .addLoreLine(world)
                    .addLoreLine(Component.empty())
                    .addLoreLine(actionHint)
                    .addLoreLine(iconHint != null ? iconHint : Component.empty())
                    .setCallback(type -> {
                        if (type == ClickType.MOUSE_LEFT_SHIFT && canModify) {
                            close();
                            new IconPickerGui(player, warp, true, () ->
                                    new WarpsGui(player, new ArrayList<>(warps)).open()).open();
                        } else if (type == ClickType.MOUSE_RIGHT_SHIFT && canModify) {
                            try {
                                warp.setIcon("");
                                sendPlayerMessage(player,
                                        getTranslatedText("gui.teleport_commands.warps.icon_reset", player)
                                                .withStyle(ChatFormatting.GREEN), true);
                                build();
                            } catch (Exception ex) {
                                Constants.LOGGER.error("Error resetting warp icon", ex);
                            }
                        } else if (type == ClickType.MOUSE_LEFT) {
                            warp.getWorld().ifPresent(w -> {
                                close();
                                sendPlayerMessage(player,
                                        getTranslatedText("commands.teleport_commands.warp.go", player), true);
                                var pos = new net.minecraft.world.phys.Vec3(
                                        warp.getX() + 0.5, warp.getY(), warp.getZ() + 0.5);
                                Teleporter(player, w, pos);
                            });
                        } else if (type == ClickType.MOUSE_RIGHT && canModify) {
                            try {
                                StorageManager.STORAGE.removeWarp(warp);
                            } catch (Exception ex) {
                                Constants.LOGGER.error("Error removing warp in GUI", ex);
                            }
                            sendPlayerMessage(player,
                                    getTranslatedText("commands.teleport_commands.warp.delete", player), true);
                            warps.remove(warp);
                            if (page > 0 && page * PAGE_SIZE >= warps.size()) page--;
                            build();
                        }
                    }).build());
        }

        fillNavBar();
    }

    private Item resolveIcon(NamedLocation warp) {
        String iconId = warp.getIcon();
        if (!iconId.isBlank()) {
            Identifier id = Identifier.tryParse(iconId);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.getValue(id);
            }
        }
        return Items.ENDER_EYE;
    }

    private void fillNavBar() {
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
        }
        if (page > 0) {
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText("gui.teleport_commands.common.prev_page", player).withStyle(ChatFormatting.WHITE))
                    .setCallback(() -> { page--; build(); }).build());
        }
        setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .setName(getTranslatedText("gui.teleport_commands.common.close", player).withStyle(ChatFormatting.RED))
                .setCallback((type) -> this.close()).build());
        if ((page + 1) * PAGE_SIZE < warps.size()) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText("gui.teleport_commands.common.next_page", player).withStyle(ChatFormatting.WHITE))
                    .setCallback(() -> { page++; build(); }).build());
        }
    }
}
