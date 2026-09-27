package tpa;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Main-thread admission control; a timed-out load keeps its slot until it drains. */
final class RtpSlots<T> {
    private final Map<UUID, T> entries = new LinkedHashMap<>();
    boolean containsKey(UUID id) { return entries.containsKey(id); }
    int size() { return entries.size(); }
    Collection<T> values() { return entries.values(); }
    boolean acquire(UUID id, T value, int limit) {
        if (entries.size() >= limit || entries.containsKey(id)) return false;
        entries.put(id, value); return true;
    }
    boolean release(UUID id, T value, CompletableFuture<?>... work) {
        for (var future : work) if (future != null && !future.isDone()) return false;
        return entries.remove(id, value);
    }
    void clear() { entries.clear(); }
}
