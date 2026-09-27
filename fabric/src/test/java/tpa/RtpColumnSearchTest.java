package tpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RtpColumnSearchTest {
    private static final class Column implements RtpColumnSearch.Column {
        boolean loaded;
        final int surface;
        final Set<Integer> safe;
        final List<Integer> visited = new ArrayList<>();
        Column(int surface, Integer... safe) { this.surface = surface; this.safe = Set.of(safe); }
        public void load() { loaded = true; }
        public int surfaceY() { return loaded ? surface : -64; }
        public boolean isSafe(int y) { visited.add(y); return safe.contains(y); }
    }

    @Test void findsLandInPreviouslyUnloadedChunk() {
        Column column = new Column(90, 90);
        assertEquals(90, RtpColumnSearch.find(column, -64, 319, false, 384).orElseThrow());
        assertTrue(column.loaded);
    }

    @Test void doesNotFallBackIntoCavesUnderOceanOrLava() {
        Column column = new Column(63, 40);
        assertTrue(RtpColumnSearch.find(column, -64, 319, false, 384).isEmpty());
        assertEquals(List.of(63), column.visited);
    }

    @Test void netherSearchStaysBelowRoofAndFindsInteriorFloor() {
        Column column = new Column(128, 128, 80);
        assertEquals(80, RtpColumnSearch.find(column, 0, 255, true, 128).orElseThrow());
        assertTrue(column.visited.stream().allMatch(y -> y <= 126));
    }

    @Test void emptyEndColumnDoesNotReturnVoid() {
        Column column = new Column(0);
        assertTrue(RtpColumnSearch.find(column, 0, 255, false, 256).isEmpty());
        assertTrue(column.visited.isEmpty());
    }

    @Test void leavesRoomForHeadAtWorldHeightLimit() {
        Column column = new Column(320, 319, 318);
        assertEquals(318, RtpColumnSearch.find(column, -64, 319, false, 384).orElseThrow());
    }

    @Test void rejectsColumnsWithoutSafeFloor() {
        Column column = new Column(128);
        assertTrue(RtpColumnSearch.find(column, 0, 255, true, 128).isEmpty());
    }
}
