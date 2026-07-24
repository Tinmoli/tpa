package tpa.gui;

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
import tpa.NamedLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static tpa.tools.getTranslatedText;
import static tpa.tools.sendPlayerMessage;

/**
 * Paginated picker containing every vanilla Minecraft item.
 */
public class IconPickerGui extends SimpleGui {
    private static final int PAGE_SIZE = 45;

    private final ServerPlayer player;
    private final NamedLocation location;
    private final Runnable returnToParent;
    private final boolean warp;
    private final List<Item> icons;
    private int page;

    public IconPickerGui(ServerPlayer player, NamedLocation location,
                         boolean warp, Runnable returnToParent) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.location = location;
        this.warp = warp;
        this.returnToParent = returnToParent;
        this.icons = getVanillaItems();
        setTitle(getTranslatedText("gui.teleport_commands.icon_picker.title", player,
                Component.literal(location.getName())).withStyle(ChatFormatting.YELLOW));
        build();
    }

    private static List<Item> getVanillaItems() {
        List<Item> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (item != Items.AIR && id != null && "minecraft".equals(id.getNamespace())) {
                result.add(item);
            }
        }
        result.sort(Comparator.comparing(item ->
                BuiltInRegistries.ITEM.getKey(item).toString()));
        return result;
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) {
            clearSlot(i);
        }

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, icons.size());
        for (int i = start; i < end; i++) {
            Item item = icons.get(i);
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            setSlot(i - start, new GuiElementBuilder(item)
                    .hideDefaultTooltip()
                    .addLoreLine(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY))
                    .addLoreLine(getTranslatedText(
                            "gui.teleport_commands.icon_picker.select", player)
                            .withStyle(ChatFormatting.YELLOW))
                    .setCallback(type -> select(item))
                    .build());
        }

        fillNavigation();
    }

    private void fillNavigation() {
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray())
                    .hideTooltip().build());
        }

        if (page > 0) {
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText(
                            "gui.teleport_commands.common.prev_page", player)
                            .withStyle(ChatFormatting.WHITE))
                    .setCallback(() -> {
                        page--;
                        build();
                    }).build());
        }

        setSlot(48, new GuiElementBuilder(Items.BARRIER)
                .setName(getTranslatedText(
                        "gui.teleport_commands.icon_picker.back", player)
                        .withStyle(ChatFormatting.RED))
                .setCallback(this::goBack)
                .build());

        setSlot(50, new GuiElementBuilder(Items.ENDER_EYE)
                .setName(getTranslatedText(
                        "gui.teleport_commands.icon_picker.reset", player)
                        .withStyle(ChatFormatting.YELLOW))
                .setCallback(this::reset)
                .build());

        if ((page + 1) * PAGE_SIZE < icons.size()) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(getTranslatedText(
                            "gui.teleport_commands.common.next_page", player)
                            .withStyle(ChatFormatting.WHITE))
                    .setCallback(() -> {
                        page++;
                        build();
                    }).build());
        }
    }

    private void select(Item item) {
        try {
            location.setIcon(BuiltInRegistries.ITEM.getKey(item).toString());
            sendPlayerMessage(player, getTranslatedText(
                    warp
                            ? "gui.teleport_commands.warps.icon_set"
                            : "gui.teleport_commands.homes.icon_set",
                    player).withStyle(ChatFormatting.GREEN), true);
            goBack();
        } catch (Exception e) {
            Constants.LOGGER.error("Error setting location icon", e);
        }
    }

    private void reset() {
        try {
            location.setIcon("");
            sendPlayerMessage(player, getTranslatedText(
                    warp
                            ? "gui.teleport_commands.warps.icon_reset"
                            : "gui.teleport_commands.homes.icon_reset",
                    player).withStyle(ChatFormatting.GREEN), true);
            goBack();
        } catch (Exception e) {
            Constants.LOGGER.error("Error resetting location icon", e);
        }
    }

    private void goBack() {
        close();
        returnToParent.run();
    }
}
