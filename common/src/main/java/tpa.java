package tpa;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


public class tpa {
	public static String MOD_LOADER;
	public static Path SAVE_DIR;
	public static Path CONFIG_DIR;
	public static Path LANG_DIR;
	public static MinecraftServer SERVER;

	// Built-in language files to export on first run
	private static final String[] BUILTIN_LANGS = {
		"en_us", "zh_cn"
	};

	// Gets ran when the server starts, initializes the mod :3
	public static void initializeMod(MinecraftServer server) {
		Constants.LOGGER.info("Initializing tpa (V{})! Hello {}!", Constants.VERSION, MOD_LOADER);

		SAVE_DIR   = Path.of(String.valueOf(server.getWorldPath(LevelResource.ROOT)));
		CONFIG_DIR = Paths.get(System.getProperty("user.dir")).resolve("config").resolve("tpa");
		LANG_DIR   = CONFIG_DIR.resolve("lang");
		SERVER     = server;

		ConfigManager.ConfigInit();
		StorageManager.StorageInit();
		DeathLocationStorage.clearDeathLocations();

		// Export built-in language files to config/tpa/lang/ (only if they don't exist yet)
		exportBuiltinLangFiles();
	}

	// Copies built-in language files into config/tpa/lang/ if the directory is empty or missing
	private static void exportBuiltinLangFiles() {
		try {
			Files.createDirectories(LANG_DIR);

			// Check if the lang directory has any .json files already
			boolean hasAnyLangFile = LANG_DIR.toFile().listFiles(
				(dir, name) -> name.endsWith(".json")
			) != null && LANG_DIR.toFile().listFiles(
				(dir, name) -> name.endsWith(".json")
			).length > 0;

			if (!hasAnyLangFile) {
				Constants.LOGGER.info("No language files found in lang/ directory, copying built-in language files...");
				for (String lang : BUILTIN_LANGS) {
					Path dest = LANG_DIR.resolve(lang + ".json");
					String resourcePath = String.format("/assets/%s/lang/%s.json", Constants.MOD_ID, lang);
					try (InputStream is = tpa.class.getResourceAsStream(resourcePath)) {
						if (is != null) {
							Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
							Constants.LOGGER.info("Copied language file: {}", lang);
						}
					}
				}
				Constants.LOGGER.info("Language files exported to: {}", LANG_DIR);
			} else {
				Constants.LOGGER.info("Language files found in lang/ directory, skipping export.");
			}
		} catch (Exception e) {
			Constants.LOGGER.error("Failed to export built-in language files! => ", e);
		}
	}

	// initialize commands, also allows me to easily disable any when there is a config
	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		back.register(dispatcher);
		home.register(dispatcher);
		TpaCommand.register(dispatcher);
		warp.register(dispatcher);
		worldspawn.register(dispatcher);
		tpals.register(dispatcher);
		rtp.register(dispatcher);

	}

	// Runs when the playerDeath mixin calls it, updates the /back command position
	public static void onPlayerDeath(ServerPlayer player) {
		BlockPos pos = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
		String world = player.level().dimension().identifier().toString();
		String uuid  = player.getStringUUID();

		DeathLocationStorage.setDeathLocation(uuid, pos, world);
	}
}
