package tpa;
import com.google.gson.*;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.unmodifiableList;

public class StorageManager {
    public static Path STORAGE_FOLDER;
    public static Path STORAGE_FILE;
    public static StorageClass STORAGE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int defaultVersion = new StorageClass().getVersion();

    /// Initializes the StorageManager class and loads the storage from the filesystem.
    public static void StorageInit() {
        STORAGE_FOLDER = tpa.CONFIG_DIR;
        STORAGE_FILE = STORAGE_FOLDER.resolve("storage.json");

        try {
            StorageLoader();
        } catch (Exception e) {
            Constants.LOGGER.error("Error while initializing the storage file! Exiting! => ", e);
            throw new RuntimeException("Error while initializing the storage file! Exiting! => ", e);
        }
    }

    /// Loads the storage from the filesystem
    public static void StorageLoader() throws Exception {

        if (!STORAGE_FILE.toFile().exists() || STORAGE_FILE.toFile().length() == 0) {
            Constants.LOGGER.warn("Storage file was not found or was empty! Initializing storage");
            Files.createDirectories(STORAGE_FOLDER);
            STORAGE = new StorageClass();
            StorageSaver();
            Constants.LOGGER.info("Storage created successfully!");
            return; // 文件刚创建，无需迁移和重新加载
        }

        StorageMigrator();

        try (FileReader reader = new FileReader(STORAGE_FILE.toFile())) {
            STORAGE = GSON.fromJson(reader, StorageClass.class);
        }
        if (STORAGE == null) {
            Constants.LOGGER.warn("Storage file was empty! Initializing storage");
            STORAGE = new StorageClass();
            StorageSaver();
            return;
        }

        STORAGE.cleanup();

        StorageSaver(); // Save it so any missing values get added to the file.
        Constants.LOGGER.info("Storage loaded successfully!");
    }

    /// This function checks what version the storage file is and migrates it to the current version of the mod.
    public static void StorageMigrator() throws Exception {
        JsonObject jsonObject;
        try (FileReader reader = new FileReader(STORAGE_FILE.toFile())) {
            jsonObject = GSON.fromJson(reader, JsonObject.class);
        }

        int version = jsonObject.has("version") ? jsonObject.get("version").getAsInt() : 0;

        if (version < defaultVersion) {
            Constants.LOGGER.warn("Storage file is v{}, migrating to v{}!", version, defaultVersion);

            if (version == 0) {
                if (jsonObject.has("Players") && jsonObject.get("Players").isJsonArray()) {
                    JsonArray players = jsonObject.get("Players").getAsJsonArray();
                    for (JsonElement playerElement : players) {
                        JsonObject player = playerElement.getAsJsonObject();
                        String UUID = player.has("Player_UUID")
                                ? player.get("Player_UUID").getAsString() : (player.has("UUID")
                                ? player.get("UUID").getAsString() : null);
                        if (UUID == null || UUID.isBlank()) {
                            players.remove(player);
                        } else {
                            player.remove("Player_UUID");
                            player.addProperty("UUID", UUID);
                        }
                    }
                }
                jsonObject.addProperty("version", 1);
            }

            byte[] json = GSON.toJson(jsonObject, JsonObject.class).getBytes();
            Files.write(STORAGE_FILE, json, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Constants.LOGGER.info("Storage file migrated to v{} successfully!", defaultVersion);

        } else if (version > defaultVersion) {
            String message = String.format(
                    "Teleport Commands: The storage file's version is newer than the supported version, found v%s, expected <= v%s.\n" +
                    "If you intentionally backported then you can attempt to downgrade the storage file located at this location: \"%s\".\n",
                    version, defaultVersion, STORAGE_FILE.toAbsolutePath());
            throw new IllegalStateException(message);
        }
    }

    /// Saves the storage to the filesystem
    public static void StorageSaver() throws Exception {
        byte[] json = GSON.toJson(StorageManager.STORAGE).getBytes();
        Files.write(STORAGE_FILE, json, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }


    public static class StorageClass {
        private final int version = 1;
        private final ArrayList<NamedLocation> Warps = new ArrayList<>();
        private final ArrayList<Player> Players = new ArrayList<>();

        /// Cleans up any values in the storage class
        public void cleanup() throws Exception {
            // 先删除 UUID 为空的玩家（使用 removeIf 避免 ConcurrentModificationException）
            Players.removeIf(player -> player.getUUID().isBlank());

            // 删除无效 home（通过 Player 内部方法操作，避免 unmodifiableList 限制）
            for (Player player : Players) {
                if (ConfigManager.CONFIG.home.isDeleteInvalid()) {
                    player.removeHomesIf(home -> home.getWorld().isEmpty());
                }
            }

            // 删除没有任何 home 的玩家
            Players.removeIf(player -> player.getHomes().isEmpty());

            // Delete any warps with an invalid world_id (if enabled in config)
            if (ConfigManager.CONFIG.warp.isDeleteInvalid()) {
                Warps.removeIf(warp -> warp.getWorld().isEmpty());
            }

            StorageSaver();
        }

        public int getVersion() {
            return version;
        }

        // returns all warps
        public List<NamedLocation> getWarps() {
            return unmodifiableList(Warps);
        }

        // filters the warpList and finds the one with the name (if there is one)
        public Optional<NamedLocation> getWarp(String name) {
            return Warps.stream()
                    .filter(warp -> Objects.equals(warp.getName(), name))
                    .findFirst();
        }

        // filters the playerList and finds the one with the uuid (if there is one)
        public Optional<Player> getPlayer(String uuid) {
            return Players.stream()
                    .filter(player -> Objects.equals(player.getUUID(), uuid))
                    .findFirst();
        }

        // -----

        // Adds a NamedLocation to the warp list, returns true if a warp with the same name already exists
        public boolean addWarp(NamedLocation warp) throws Exception {
            if (getWarp(warp.getName()).isPresent()) {
                return true;
            } else {
                Warps.add(warp);
                StorageSaver();
                return false;
            }
        }

        // Creates a new player, if there already is a player it will return the existing one.
        public Player addPlayer(String uuid) {
            final Optional<Player> OptionalPlayer = getPlayer(uuid);
            if (OptionalPlayer.isEmpty()) {
                Player player = new Player(uuid);
                Players.add(player);
                return player;
            } else {
                return OptionalPlayer.get();
            }
        }

        // -----

        // Remove a warp, if the warp isn't found then nothing will happen
        public void removeWarp(NamedLocation warp) throws Exception {
            Warps.remove(warp);
            StorageSaver();
        }
    }
}
