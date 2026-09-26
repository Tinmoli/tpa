package tpa.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

final class GuiItems {
    private GuiItems() {}
    static Item background() {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:gray_stained_glass_pane"));
    }
}
