package tpa;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class NamedLocation {
    private String name;
    private final int x;
    private final int y;
    private final int z;
    private final String world;
    // Optional item registry ID used by HomesGui. Missing/null keeps the default bed icon.
    private String icon = "";
    private transient Player owner;

    static NamedLocation loaded(String name, int x, int y, int z, String world, String icon) {
        NamedLocation location = new NamedLocation(name, new BlockPos(x, y, z), world);
        location.icon = icon;
        return location;
    }

    void bindOwner(Player owner) { this.owner = owner; }

    private void requireCurrent() {
        boolean current = owner == null
                ? StorageManager.STORAGE.getWarp(name).orElse(null) == this
                : StorageManager.STORAGE.getPlayer(owner.getUUID()).orElse(null) == owner
                    && owner.getHome(name).orElse(null) == this;
        if (!current) throw new IllegalStateException("This menu is out of date; please reopen it");
    }

    public NamedLocation(String name, BlockPos pos, String world) {
        this.name = name;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.world = world;
    }

    // -----

    public String getName() {
        return this.name;
    }

    public BlockPos getBlockPos() {
         return new BlockPos(this.x, this.y, this.z);
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    // Return the world id as a string
    public String getWorldString() {
        return this.world;
    }

    public String getIcon() {
        return icon == null ? "" : icon;
    }

    boolean isStructurallyValid() {
        return name != null && !name.isBlank()
                && world != null && !world.isBlank();
    }

    void normalizeForStorage() {
        if (icon == null) {
            icon = "";
        }
    }

    public void setIcon(String icon) throws Exception {
        requireCurrent();
        SqliteStorage.setIcon(StorageManager.SQLITE_FILE, owner == null ? null : owner.getUUID(), name, icon == null ? "" : icon);
        this.icon = icon == null ? "" : icon;
    }

    // function to quickly filter the worlds and get the ServerLevel for the string
    public Optional<ServerLevel> getWorld() {
        return StreamSupport.stream( tpa.SERVER.getAllLevels().spliterator(), false ) // woa, this looks silly
                .filter(level -> Objects.equals( level.dimension().identifier().toString(), this.world ))
                .findFirst();
    }

    // -----

    public void setName(String name) throws Exception {
        requireCurrent();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Location name cannot be empty");
        SqliteStorage.renameLocation(StorageManager.SQLITE_FILE, owner == null ? null : owner.getUUID(), this.name, name);
        if (owner != null) owner.renamedHome(this.name, name);
        this.name = name;
    }
}
