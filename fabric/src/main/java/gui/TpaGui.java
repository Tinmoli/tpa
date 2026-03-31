package tpa.gui;

import tpa.tpa;
import tpa.tools;
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

public class TpaGui extends SimpleGui {

    private final ServerPlayer player;
    private final List<ServerPlayer> targets;
    private int page = 0;
    private static final int PAGE_SIZE = 36;

    public TpaGui(ServerPlayer player, List<ServerPlayer> targets) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.targets = targets != null ? new ArrayList<>(targets) : new ArrayList<>();
        setTitle(getTranslatedText("gui.teleport_commands.tpa.title", player,
                Component.literal(String.valueOf(this.targets.size())))
                .withStyle(ChatFormatting.GOLD));
        build();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, targets.size());

        for (int i = start; i < end; i++) {
            ServerPlayer target = targets.get(i);
            int slot = i - start;
            setSlot(slot, new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setName(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW))
                    .addLoreLine(getTranslatedText("gui.teleport_commands.tpa.hint_left", player).withStyle(ChatFormatting.GREEN))
                    .addLoreLine(getTranslatedText("gui.teleport_commands.tpa.hint_right", player).withStyle(ChatFormatting.AQUA))
                    .setCallback(type -> {
                        close();
                        if (type == ClickType.MOUSE_LEFT) {
                            tpa.SERVER.getCommands().performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpa " + target.getName().getString());
                        } else if (type == ClickType.MOUSE_RIGHT) {
                            tpa.SERVER.getCommands().performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpahere " + target.getName().getString());
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
                    .setCallback(() -> { page--; build(); }).build());
        }
        setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .setName(getTranslatedText("gui.teleport_commands.common.close", player).withStyle(ChatFormatting.RED))
                .setCallback((type) -> this.close()).build());
        if ((page + 1) * PAGE_SIZE < targets.size()) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText("gui.teleport_commands.common.next_page", player).withStyle(ChatFormatting.WHITE))
                    .setCallback(() -> { page++; build(); }).build());
        }
    }
}
