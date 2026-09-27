package tpa.gui;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiItemsTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void defaultHomeIconMustNotBeInvisible() {
        assertNotEquals(Items.AIR, BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:cyan_bed")));
    }

    @Test void backgroundMustNotBeInvisible() {
        assertNotEquals(Items.AIR, GuiItems.background());
    }
}
