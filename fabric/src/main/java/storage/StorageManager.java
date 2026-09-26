package tpa;
import com.google.gson.*;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.unmodifiableList;

public class StorageManager {
    public static Path STORAGE_FOLDER;
    public static Path STORAGE_FILE;
    public static Path SQLITE_FILE;
    public static StorageClass STORAGE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /// Initializes the StorageManager class and loads the storage from the filesystem.
    public static void StorageInit() {
        STORAGE_FOLDER = tpa.CONFIG_DIR;
        STORAGE_FILE = STORAGE_FOLDER.resolve("storage.json");
        SQLITE_FILE = STORAGE_FOLDER.resolve("storage.db");

        try {
            // Fresh upgrades from JSON import automatically; existing SQLite always wins.
            if (!Files.exists(SQLITE_FILE) && Files.isRegularFile(STORAGE_FILE)) {
                SqliteStorage.importJsonAtomically(STORAGE_FILE, SQLITE_FILE);
            }
            STORAGE = SqliteStorage.load(SQLITE_FILE);
            STORAGE.cleanup();
        } catch (Exception e) {
            Constants.LOGGER.error("Error while initializing the storage file! Exiting! => ", e);
            throw new RuntimeException("Error while initializing the storage file! Exiting! => ", e);
        }
    }

    /**
     * Reloads the active in-memory state from SQLite and applies configured
     * cleanup rules. cleanup() persists the normalized representation.
     */
    public static void reloadFromSqlite() throws Exception {
        if (SQLITE_FILE == null) {
            throw new IllegalStateException("Storage has not been initialized.");
        }
        StorageClass loaded = SqliteStorage.load(SQLITE_FILE);
        loaded.cleanup();
        STORAGE = loaded;
    }

    /// Saves the storage to the filesystem
    public static void StorageSaver() throws Exception {
        SqliteStorage.save(SQLITE_FILE, STORAGE);
    }

    /**
     * Loads a legacy JSON file exclusively for the JSON -> SQLite import
     * command. Version 0 Player_UUID fields are normalized in memory; the
     * source JSON file itself is never modified.
     */
    public static StorageClass loadJsonFile(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) == 0) {
            throw new IllegalArgumentException("storage.json was not found or is empty.");
        }

        JsonObject root;
        try (java.io.Reader reader = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(
                        "storage.json must contain a JSON object.");
            }
            root = parsed.getAsJsonObject();
        }

        int version = root.has("version") && root.get("version").isJsonPrimitive()
                ? root.get("version").getAsInt() : 0;
        if (version > 1) {
            throw new IllegalStateException(
                    "storage.json version " + version + " is newer than supported version 1.");
        }

        if (root.has("Players") && root.get("Players").isJsonArray()) {
            JsonArray players = root.getAsJsonArray("Players");
            java.util.Iterator<JsonElement> iterator = players.iterator();
            while (iterator.hasNext()) {
                JsonElement element = iterator.next();
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Invalid player entry in storage.json; import cancelled.");
                }

                JsonObject player = element.getAsJsonObject();
                JsonElement uuidElement = player.has("UUID")
                        ? player.get("UUID") : player.get("Player_UUID");
                if (uuidElement == null || !uuidElement.isJsonPrimitive()
                        || uuidElement.getAsString().isBlank()) {
                    throw new IllegalArgumentException("Missing player UUID in storage.json; import cancelled.");
                }

                player.remove("Player_UUID");
                player.addProperty("UUID", uuidElement.getAsString());
            }
        }
        root.addProperty("version", 1);

        StorageClass result = GSON.fromJson(root, StorageClass.class);
        return result == null ? new StorageClass() : result;
    }


    public static class StorageClass {
        private int version = 1;
        private ArrayList<NamedLocation> Warps = new ArrayList<>();
        private ArrayList<Player> Players = new ArrayList<>();

        public List<Player> getPlayers() { return Players == null ? List.of() : unmodifiableList(Players); }
        void addLoadedPlayer(Player player) { Players.add(player); }
        void addLoadedWarp(NamedLocation warp) { Warps.add(warp); }

        /**
         * Normalizes data loaded from JSON/SQLite without touching disk.
         * This makes missing/null fields from old or partially damaged data
         * safe before callers iterate over the collections.
         */
        public void normalize() {
            version = 1;
            if (Warps == null) {
                Warps = new ArrayList<>();
            }
            if (Players == null) {
                Players = new ArrayList<>();
            }
            Warps.removeIf(warp -> warp == null || !warp.isStructurallyValid());
            Warps.forEach(NamedLocation::normalizeForStorage);
            Players.removeIf(player -> player == null
                    || !player.normalizeForStorage());
        }

        /// Cleans up any values in the storage class
        public void cleanup() throws Exception {
            String before = GSON.toJson(this);
            normalize();

            // 删除无效 home（通过 Player 内部方法操作，避免 unmodifiableList 限制）
            for (Player player : Players) {
                if (ConfigManager.CONFIG.home.isDeleteInvalid()) {
                    player.removeHomesIf(home -> home.getWorld().isEmpty());
                }
            }

            // Delete any warps with an invalid world_id (if enabled in config)
            if (ConfigManager.CONFIG.warp.isDeleteInvalid()) {
                Warps.removeIf(warp -> warp.getWorld().isEmpty());
            }

            normalize();
            if (!before.equals(GSON.toJson(this))) SqliteStorage.save(SQLITE_FILE, this);
        }

        public int getVersion() {
            return version;
        }

        // returns all warps
        public List<NamedLocation> getWarps() {
            return Warps == null ? List.of() : unmodifiableList(Warps);
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
                SqliteStorage.addLocation(SQLITE_FILE, null, warp);
                Warps.add(warp);
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
            if (!Warps.contains(warp)) throw new IllegalStateException("Warp no longer exists");
            SqliteStorage.deleteLocation(SQLITE_FILE, null, warp.getName());
            Warps.remove(warp);
        }
    }
}
