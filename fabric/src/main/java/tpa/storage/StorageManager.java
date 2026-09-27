package tpa.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import tpa.Constants;
import tpa.TpaMod;

import java.nio.file.Files;
import java.nio.file.Path;

public class StorageManager {
    public static Path STORAGE_FOLDER;
    public static Path STORAGE_FILE;
    public static Path SQLITE_FILE;
    public static StorageData STORAGE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /// Initializes the StorageManager class and loads the storage from the filesystem.
    public static void initialize() {
        STORAGE_FOLDER = TpaMod.CONFIG_DIR;
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
            throw new RuntimeException(
                    "Error while initializing the storage file! Exiting! => ", e);
        }
    }

    /**
     * Reloads the active in-memory state from SQLite and applies configured cleanup rules.
     * cleanup() persists the normalized representation.
     */
    public static void reloadFromSqlite() throws Exception {
        if (SQLITE_FILE == null) {
            throw new IllegalStateException("Storage has not been initialized.");
        }
        StorageData loaded = SqliteStorage.load(SQLITE_FILE);
        loaded.cleanup();
        STORAGE = loaded;
    }

    /// Saves the storage to the filesystem
    public static void save() throws Exception {
        SqliteStorage.save(SQLITE_FILE, STORAGE);
    }

    /**
     * Loads a legacy JSON file exclusively for the JSON -> SQLite import command. Version 0
     * Player_UUID fields are normalized in memory; the source JSON file itself is never modified.
     */
    public static StorageData loadJsonFile(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) == 0) {
            throw new IllegalArgumentException("storage.json was not found or is empty.");
        }

        JsonObject root;
        try (java.io.Reader reader =
                Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("storage.json must contain a JSON object.");
            }
            root = parsed.getAsJsonObject();
        }

        int version =
                root.has("version") && root.get("version").isJsonPrimitive()
                        ? root.get("version").getAsInt()
                        : 0;
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
                    throw new IllegalArgumentException(
                            "Invalid player entry in storage.json; import cancelled.");
                }

                JsonObject player = element.getAsJsonObject();
                JsonElement uuidElement =
                        player.has("UUID") ? player.get("UUID") : player.get("Player_UUID");
                if (uuidElement == null
                        || !uuidElement.isJsonPrimitive()
                        || uuidElement.getAsString().isBlank()) {
                    throw new IllegalArgumentException(
                            "Missing player UUID in storage.json; import cancelled.");
                }

                player.remove("Player_UUID");
                player.addProperty("UUID", uuidElement.getAsString());
            }
        }
        root.addProperty("version", 1);

        StorageData result = GSON.fromJson(root, StorageData.class);
        return result == null ? new StorageData() : result;
    }
}
