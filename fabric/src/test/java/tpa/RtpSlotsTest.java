package tpa;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class RtpSlotsTest {
    @Test void defaultCapacityAdmitsTenAndRejectsEleventh() {
        var slots = new RtpSlots<Object>();
        int limit = new ConfigManager.ConfigClass.Rtp().maxConcurrentLoads;
        for (int i = 0; i < 10; i++) assertTrue(slots.acquire(UUID.randomUUID(), new Object(), limit));
        assertFalse(slots.acquire(UUID.randomUUID(), new Object(), limit));
        assertEquals(10, slots.size());
    }
    @Test void tenRequestsOnlyAdmitConfiguredNumber() {
        var slots = new RtpSlots<Object>(); int admitted = 0;
        for (int i = 0; i < 10; i++) if (slots.acquire(UUID.randomUUID(), new Object(), 2)) admitted++;
        assertEquals(2, admitted);
    }
    @Test void timedOutLoadRetainsSlotUntilCompletion() {
        var slots = new RtpSlots<Object>(); UUID id = UUID.randomUUID(); Object job = new Object();
        slots.acquire(id, job, 1); var load = new CompletableFuture<Void>();
        assertFalse(slots.release(id, job, load));
        assertFalse(slots.acquire(UUID.randomUUID(), new Object(), 1));
        load.complete(null); assertTrue(slots.release(id, job, load));
        assertTrue(slots.acquire(UUID.randomUUID(), new Object(), 1));
    }
    @Test void failureAndRepeatedReleaseDoNotLeakOrOverRelease() {
        var slots = new RtpSlots<Object>(); UUID id = UUID.randomUUID(); Object job = new Object();
        slots.acquire(id, job, 2);
        var future = CompletableFuture.failedFuture(new IllegalStateException("failure"));
        assertTrue(slots.release(id, job, future)); assertFalse(slots.release(id, job, future));
        assertEquals(0, slots.size());
    }
    @Test void reducedLimitAndDuplicatePlayerCannotBypassAdmission() {
        var slots = new RtpSlots<Object>(); UUID id = UUID.randomUUID(); Object job = new Object();
        assertTrue(slots.acquire(id, job, 2)); assertFalse(slots.acquire(id, new Object(), 2));
        assertTrue(slots.acquire(UUID.randomUUID(), new Object(), 2));
        assertFalse(slots.acquire(UUID.randomUUID(), new Object(), 1));
    }
}
