package tpa.teleport;

import java.util.OptionalInt;

/** Orders chunk loading before height queries and bounds the vertical search. */
final class RtpColumnSearch {
    interface Column {
        void load();

        int surfaceY();

        boolean isSafe(int y);
    }

    private RtpColumnSearch() {}

    static OptionalInt find(Column column, int minY, int maxY, boolean ceiling, int logicalHeight) {
        column.load();
        int highest = Math.min(column.surfaceY(), maxY - 1);
        if (ceiling) highest = Math.min(highest, minY + logicalHeight - 2);
        for (int y = highest; y > minY; y--) {
            if (column.isSafe(y)) return OptionalInt.of(y);
            // Never descend beneath a dangerous surface into a cave in open dimensions.
            if (!ceiling) break;
        }
        return OptionalInt.empty();
    }
}
