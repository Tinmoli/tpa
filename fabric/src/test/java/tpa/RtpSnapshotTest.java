package tpa;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RtpSnapshotTest {
    private byte[] air() { byte[] column = new byte[24]; Arrays.fill(column, RtpSnapshot.PASSABLE); return column; }
    private RtpSnapshot snapshot(byte[] cells, boolean interior) {
        return new RtpSnapshot(-8, cells, new boolean[cells.length], interior, 12);
    }
    @Test void drySurfaceReturnsFeetHeight() {
        byte[] c = air(); c[9] = RtpSnapshot.FLOOR;
        assertEquals(2, snapshot(c, false).find().orElseThrow());
    }
    @Test void oceanNeverFallsThroughToUnderwaterCave() {
        byte[] c = air(); c[18] = RtpSnapshot.LIQUID; c[15] = RtpSnapshot.FLOOR; c[5] = RtpSnapshot.FLOOR;
        assertTrue(snapshot(c, false).find().isEmpty());
    }
    @Test void leavesRejectWholeSurfaceColumn() {
        byte[] c = air(); c[18] = RtpSnapshot.LEAVES; c[5] = RtpSnapshot.FLOOR;
        assertTrue(snapshot(c, false).find().isEmpty());
    }
    @Test void dangerousSurfaceNeverFallsBackToCave() {
        byte[] c = air(); c[18] = RtpSnapshot.BAD; c[5] = RtpSnapshot.FLOOR;
        assertTrue(snapshot(c, false).find().isEmpty());
    }
    @Test void headroomAndWorldCeilingAreRequired() {
        byte[] c = air(); c[23] = RtpSnapshot.FLOOR; c[21] = RtpSnapshot.FLOOR;
        assertTrue(snapshot(c, false).find().isEmpty());
    }
    @Test void ceilingDimensionFindsInteriorBelowRoof() {
        byte[] c = air(); Arrays.fill(c, 18, 24, RtpSnapshot.BAD); c[10] = RtpSnapshot.FLOOR;
        assertEquals(3, snapshot(c, true).find().orElseThrow());
    }
    @Test void interiorLavaAndBlockedHeadroomAreRejected() {
        byte[] c = air(); c[10] = RtpSnapshot.FLOOR; c[11] = RtpSnapshot.LIQUID;
        assertTrue(snapshot(c, true).find().isEmpty());
        c[11] = RtpSnapshot.PASSABLE; c[12] = RtpSnapshot.BAD;
        assertTrue(snapshot(c, true).find().isEmpty());
    }
    @Test void emptyEndColumnHasNoDestination() { assertTrue(snapshot(air(), false).find().isEmpty()); }
    @Test void blacklistedBiomeRejectsSurface() {
        byte[] c = air(); c[9] = RtpSnapshot.FLOOR;
        boolean[] banned = new boolean[24]; banned[10] = true;
        assertTrue(new RtpSnapshot(-8, c, banned, false, 12).find().isEmpty());
    }
    @Test void snapshotIsUnaffectedByInputMutation() {
        byte[] c = air(); c[9] = RtpSnapshot.FLOOR; var s = snapshot(c, false);
        c[9] = RtpSnapshot.LIQUID;
        assertEquals(2, s.find().orElseThrow());
    }
    @Test void annulusIsCenteredOnSpawnAndUniformInArea() {
        Random random = new Random(321); double sum = 0;
        for (int i = 0; i < 10000; i++) {
            var p = RtpSnapshot.random(random, 5000, -8000, 1000, 2000);
            double radius = Math.hypot(p.x() - 5000, p.z() + 8000);
            assertTrue(radius >= 999 && radius <= 2001);
            sum += radius * radius;
        }
        assertEquals(2_500_000, sum / 10000, 30000);
    }
}
