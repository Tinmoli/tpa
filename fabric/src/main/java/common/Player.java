package tpa;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.Collections.unmodifiableList;

public class Player {
    private String UUID;
    private String DefaultHome = "";
    private ArrayList<NamedLocation> Homes = new ArrayList<>();

    public Player(String uuid) {
        this.UUID = uuid;
    }

    static Player loaded(String uuid, String defaultHome) {
        Player player = new Player(uuid);
        player.DefaultHome = defaultHome;
        return player;
    }

    void addLoadedHome(NamedLocation home) {
        home.bindOwner(this);
        Homes.add(home);
    }

    void renamedHome(String oldName, String newName) {
        if (DefaultHome.equals(oldName)) DefaultHome = newName;
    }

    private void requireCurrent() {
        if (StorageManager.STORAGE.getPlayer(UUID).orElse(null) != this)
            throw new IllegalStateException("This menu is out of date; please reopen it");
    }

    // -----

    public String getUUID() {
        return UUID;
    }

    public String getDefaultHome() {
        return DefaultHome;
    }

    // returns all homes
    public List<NamedLocation> getHomes() {
        return Homes == null ? List.of() : unmodifiableList(Homes);
    }

    boolean normalizeForStorage() {
        if (UUID == null || UUID.isBlank()) {
            return false;
        }
        if (DefaultHome == null) {
            DefaultHome = "";
        }
        if (Homes == null) {
            Homes = new ArrayList<>();
        }
        Homes.removeIf(home -> home == null || !home.isStructurallyValid());
        Homes.forEach(NamedLocation::normalizeForStorage);
        Homes.forEach(home -> home.bindOwner(this));
        if (!DefaultHome.isEmpty() && getHome(DefaultHome).isEmpty()) {
            DefaultHome = "";
        }
        return true;
    }

    // returns a specific home based on the name (if there is one)
    public Optional<NamedLocation> getHome(String name)  {
        return getHomes().stream()
                .filter( home -> Objects.equals( home.getName(), name ))
                .findFirst();
    }

    // -----

    public void setDefaultHome(String defaultHome) throws Exception {
        requireCurrent();
        if (!defaultHome.isEmpty() && getHome(defaultHome).isEmpty())
            throw new IllegalArgumentException("Default home does not exist");
        SqliteStorage.setDefaultHome(StorageManager.SQLITE_FILE, UUID, defaultHome);
        this.DefaultHome = defaultHome;
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

    // -----

    public void deleteHome(NamedLocation home) throws Exception {
        requireCurrent();
        if (!Homes.contains(home)) throw new IllegalStateException("Home no longer exists");
        SqliteStorage.deleteLocation(StorageManager.SQLITE_FILE, UUID, home.getName());
        Homes.remove(home);
        if (DefaultHome.equals(home.getName())) DefaultHome = "";
    }

    // 批量删除满足条件的 home（供 cleanup 使用，避免 unmodifiableList 限制）
    public void removeHomesIf(Predicate<NamedLocation> predicate) {
        Homes.removeIf(predicate);
    }
}
