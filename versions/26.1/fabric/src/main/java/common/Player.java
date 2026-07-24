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

    // -----

    public String getUUID() {
        return UUID;
    }

    public String getDefaultHome() {
        return DefaultHome;
    }

    // returns all homes
    public List<NamedLocation> getHomes() {
        return unmodifiableList(Homes);
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
        if (!DefaultHome.isEmpty() && getHome(DefaultHome).isEmpty()) {
            DefaultHome = "";
        }
        return true;
    }

    // returns a specific home based on the name (if there is one)
    public Optional<NamedLocation> getHome(String name)  {
        return Homes.stream()
                .filter( home -> Objects.equals( home.getName(), name ))
                .findFirst();
    }

    // -----

    public void setDefaultHome(String defaultHome) throws Exception {
        this.DefaultHome = defaultHome;
        StorageManager.StorageSaver();
    }

    // Adds a NamedLocation to the home list, returns true if it already exists
    public boolean addHome(NamedLocation home) throws Exception {
        if (getHome(home.getName()).isPresent()) {
            // Home with same name found!
            return true;

        } else {
            Homes.add(home);
            StorageManager.StorageSaver();
            return false;
        }
    }

    // -----

    public void deleteHome(NamedLocation home) throws Exception {
        Homes.remove(home);
        StorageManager.StorageSaver();
    }

    // 批量删除满足条件的 home（供 cleanup 使用，避免 unmodifiableList 限制）
    public void removeHomesIf(Predicate<NamedLocation> predicate) {
        Homes.removeIf(predicate);
    }
}
