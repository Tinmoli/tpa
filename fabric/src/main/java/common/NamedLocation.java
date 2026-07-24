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

    public void setIcon(String icon) throws Exception {
        this.icon = icon == null ? "" : icon;
        StorageManager.StorageSaver();
    }

    // function to quickly filter the worlds and get the ServerLevel for the string
    public Optional<ServerLevel> getWorld() {
        return StreamSupport.stream( tpa.SERVER.getAllLevels().spliterator(), false ) // woa, this looks silly
                .filter(level -> Objects.equals( level.dimension().identifier().toString(), this.world ))
                .findFirst();
    }

    // -----

    public void setName(String name) throws Exception {
        this.name = name;
        StorageManager.StorageSaver();
    }
}