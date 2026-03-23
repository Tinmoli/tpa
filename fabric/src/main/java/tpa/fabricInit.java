package tpa;

import net.fabricmc.api.ModInitializer;

public class fabricInit implements ModInitializer {

	@Override
	public void onInitialize() {
		tpa.MOD_LOADER = "Fabric";
	}
}
