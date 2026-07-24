package tpa;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DeathLocationStorage {
    private static final ConcurrentHashMap<String, DeathLocation> deathLocations = new ConcurrentHashMap<>();

    // filters the deathLocationList and finds the one with the matching player uuid (if there is one)
    public static Optional<DeathLocation> getDeathLocation(String uuid) {
        return Optional.ofNullable(deathLocations.get(uuid));
    }

    // updates the deathLocation of a player, if there is no existing entry it will create a new deathLocation.
    public static void setDeathLocation(String uuid, BlockPos pos, String world) {
        deathLocations.compute(uuid, (k, existing) -> {
            if (existing != null) {
                existing.setBlockPos(pos);
                existing.setWorld(world);
                return existing;
            } else {
                return new DeathLocation(pos, world);
            }
        });
    }

    public static void clearDeathLocations() {
        deathLocations.clear();
    }
}
