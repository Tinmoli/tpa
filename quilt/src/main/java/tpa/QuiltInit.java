package tpa;

import net.fabricmc.api.ModInitializer;

public class QuiltInit implements ModInitializer {

    @Override
    public void onInitialize() {
        tpa.MOD_LOADER = "Quilt";
    }
}
