package tpa.storage;

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class PlayerData {
    @com.google.gson.annotations.SerializedName("UUID")
    private String uuid;

    @com.google.gson.annotations.SerializedName("DefaultHome")
    private String defaultHome = "";

    @com.google.gson.annotations.SerializedName("Homes")
    private ArrayList<NamedLocation> homes = new ArrayList<>();

    public PlayerData(String uuid) {
        this.uuid = uuid;
    }

    static PlayerData loaded(String uuid, String defaultHome) {
        PlayerData player = new PlayerData(uuid);
        player.defaultHome = defaultHome;
        return player;
    }

    void addLoadedHome(NamedLocation home) {
        home.bindOwner(this);
        homes.add(home);
    }

    void renamedHome(String oldName, String newName) {
        if (defaultHome.equals(oldName)) defaultHome = newName;
    }

    private void requireCurrent() {
        if (StorageManager.STORAGE.getPlayer(uuid).orElse(null) != this)
            throw new IllegalStateException("This menu is out of date; please reopen it");
    }

    public String getUUID() {
        return uuid;
    }

    public String getDefaultHome() {
        return defaultHome;
    }

    // returns all homes
    public List<NamedLocation> getHomes() {
        return homes == null ? List.of() : unmodifiableList(homes);
    }

    boolean normalizeForStorage() {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        if (defaultHome == null) {
            defaultHome = "";
        }
        if (homes == null) {
            homes = new ArrayList<>();
        }
        homes.removeIf(home -> home == null || !home.isStructurallyValid());
        homes.forEach(NamedLocation::normalizeForStorage);
        homes.forEach(home -> home.bindOwner(this));
        if (!defaultHome.isEmpty() && getHome(defaultHome).isEmpty()) {
            defaultHome = "";
        }
        return true;
    }

    // returns a specific home based on the name (if there is one)
    public Optional<NamedLocation> getHome(String name) {
        return getHomes().stream().filter(home -> Objects.equals(home.getName(), name)).findFirst();
    }

    public void setDefaultHome(String defaultHome) throws Exception {
        requireCurrent();
        if (!defaultHome.isEmpty() && getHome(defaultHome).isEmpty())
            throw new IllegalArgumentException("Default home does not exist");
        SqliteStorage.setDefaultHome(StorageManager.SQLITE_FILE, uuid, defaultHome);
        this.defaultHome = defaultHome;
    }

    // Adds a NamedLocation to the home list, returns true if it already exists
    public boolean addHome(NamedLocation home) throws Exception {
        requireCurrent();
        if (getHome(home.getName()).isPresent()) {
            // Home with same name found!
            return true;

        } else {
            SqliteStorage.addLocation(StorageManager.SQLITE_FILE, this, home);
            addLoadedHome(home);
            return false;
        }
    }

    public void deleteHome(NamedLocation home) throws Exception {
        requireCurrent();
        if (!homes.contains(home)) throw new IllegalStateException("Home no longer exists");
        SqliteStorage.deleteLocation(StorageManager.SQLITE_FILE, uuid, home.getName());
        homes.remove(home);
        if (defaultHome.equals(home.getName())) defaultHome = "";
    }

    // 批量删除满足条件的 home（供 cleanup 使用，避免 unmodifiableList 限制）
    public void removeHomesIf(Predicate<NamedLocation> predicate) {
        homes.removeIf(predicate);
    }
}
