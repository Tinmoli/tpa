package tpa;

import net.fabricmc.api.ModInitializer;

public class TpaInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        TpaMod.MOD_LOADER = "Fabric";
    }
}
