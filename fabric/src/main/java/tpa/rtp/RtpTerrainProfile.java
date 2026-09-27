package tpa.rtp;

import java.util.OptionalInt;
import java.util.random.RandomGenerator;

/** Immutable, world-free input to the search executor. */
final class RtpTerrainProfile {
    static final byte PASSABLE = 1, FLOOR = 2, BAD = 4, LIQUID = 8, LEAVES = 16;
    private final int minY;
    private final byte[] cells;
    private final boolean[] bannedBiomes;
    private final boolean interior;
    private final int highestFloor;

    RtpTerrainProfile(
            int minY, byte[] cells, boolean[] bannedBiomes, boolean interior, int highestFloor) {
        this.minY = minY;
        this.cells = cells.clone();
        this.bannedBiomes = bannedBiomes.clone();
        this.interior = interior;
        this.highestFloor = highestFloor;
        if (cells.length != bannedBiomes.length)
            throw new IllegalArgumentException("Column lengths differ");
    }

    OptionalInt find() {
        int top = Math.min(cells.length - 3, highestFloor - minY);
        // Open dimensions must reject any obstruction above the first candidate too.
        if (!interior)
            for (int i = cells.length - 1; i > top; i--)
                if (cells[i] != PASSABLE) return OptionalInt.empty();
        for (int i = top; i >= 0; i--) {
            byte cell = cells[i];
            if (cell == PASSABLE) continue;
            if (cell == FLOOR
                    && cells[i + 1] == PASSABLE
                    && cells[i + 2] == PASSABLE
                    && !bannedBiomes[i + 1]) return OptionalInt.of(minY + i + 1);
            if (!interior) return OptionalInt.empty();
        }
        return OptionalInt.empty();
    }

    record Candidate(int x, int z, float yaw) {}

    static Candidate random(
            RandomGenerator random, int centerX, int centerZ, int minRange, int maxRange) {
        double radius =
                Math.sqrt(
                        (double) minRange * minRange
                                + random.nextDouble()
                                        * ((double) maxRange * maxRange
                                                - (double) minRange * minRange));
        double angle = random.nextDouble() * Math.PI * 2;
        return new Candidate(
                (int) Math.round(centerX + radius * Math.cos(angle)),
                (int) Math.round(centerZ + radius * Math.sin(angle)),
                random.nextFloat() * 360f);
    }
}
