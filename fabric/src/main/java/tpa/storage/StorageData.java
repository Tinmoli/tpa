package tpa.storage;

import static tpa.storage.StorageManager.SQLITE_FILE;

import static java.util.Collections.unmodifiableList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import tpa.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class StorageData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private int version = 1;

    @com.google.gson.annotations.SerializedName("Warps")
    private ArrayList<NamedLocation> warps = new ArrayList<>();

    @com.google.gson.annotations.SerializedName("Players")
    private ArrayList<PlayerData> players = new ArrayList<>();

    public List<PlayerData> getPlayers() {
        return players == null ? List.of() : unmodifiableList(players);
    }

    void addLoadedPlayer(PlayerData player) {
        players.add(player);
    }

    void addLoadedWarp(NamedLocation warp) {
        warps.add(warp);
    }

    /**
     * Normalizes data loaded from JSON/SQLite without touching disk. This makes missing/null fields
     * from old or partially damaged data safe before callers iterate over the collections.
     */
    public void normalize() {
        version = 1;
        if (warps == null) {
            warps = new ArrayList<>();
        }
        if (players == null) {
            players = new ArrayList<>();
        }
        warps.removeIf(warp -> warp == null || !warp.isStructurallyValid());
        warps.forEach(NamedLocation::normalizeForStorage);
        players.removeIf(player -> player == null || !player.normalizeForStorage());
    }

    /// Cleans up any values in the storage class
    public void cleanup() throws Exception {
        String before = GSON.toJson(this);
        normalize();

        // 删除无效 home（通过 Player 内部方法操作，避免 unmodifiableList 限制）
        for (PlayerData player : players) {
            if (ConfigManager.CONFIG.home.isDeleteInvalid()) {
                player.removeHomesIf(home -> home.getWorld().isEmpty());
            }
        }

        // Delete any warps with an invalid world_id (if enabled in config)
        if (ConfigManager.CONFIG.warp.isDeleteInvalid()) {
            warps.removeIf(warp -> warp.getWorld().isEmpty());
        }

        normalize();
        if (!before.equals(GSON.toJson(this))) SqliteStorage.save(SQLITE_FILE, this);
    }

    public int getVersion() {
        return version;
    }

    // returns all warps
    public List<NamedLocation> getWarps() {
        return warps == null ? List.of() : unmodifiableList(warps);
    }

    // filters the warpList and finds the one with the name (if there is one)
    public Optional<NamedLocation> getWarp(String name) {
        return warps.stream().filter(warp -> Objects.equals(warp.getName(), name)).findFirst();
    }

    // filters the playerList and finds the one with the uuid (if there is one)
    public Optional<PlayerData> getPlayer(String uuid) {
        return players.stream()
                .filter(player -> Objects.equals(player.getUUID(), uuid))
                .findFirst();
    }

    // Adds a NamedLocation to the warp list, returns true if a warp with the same name already
    // exists
    public boolean addWarp(NamedLocation warp) throws Exception {
        if (getWarp(warp.getName()).isPresent()) {
            return true;
        } else {
            SqliteStorage.addLocation(SQLITE_FILE, null, warp);
            warps.add(warp);
            return false;
        }
    }

    // Creates a new player, if there already is a player it will return the existing one.
    public PlayerData addPlayer(String uuid) {
        final Optional<PlayerData> existingPlayer = getPlayer(uuid);
        if (existingPlayer.isEmpty()) {
            PlayerData player = new PlayerData(uuid);
            players.add(player);
            return player;
        } else {
            return existingPlayer.get();
        }
    }

    // Remove a warp, if the warp isn't found then nothing will happen
    public void removeWarp(NamedLocation warp) throws Exception {
        if (!warps.contains(warp)) throw new IllegalStateException("Warp no longer exists");
        SqliteStorage.deleteLocation(SQLITE_FILE, null, warp.getName());
        warps.remove(warp);
    }
}
