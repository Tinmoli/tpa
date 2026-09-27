package tpa;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import tpa.mixin.RtpChunkAccess;

/** Request state and all world/player access belong exclusively to the server thread. */
public final class RtpManager {
    private static final TicketType TICKET = new TicketType(6000L, TicketType.FLAG_LOADING);
    private static ExecutorService workers;
    private static final RtpSlots<Request> active = new RtpSlots<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private record Protection(ServerPlayer player, long untilTick) {}
    private static final Map<UUID, Protection> protection = new HashMap<>();
    private static long ticks, lastTick, pauseUntil;

    private static final class Request {
        final ServerPlayer player;
        final ServerLevel world, origin;
        final ConfigManager.ConfigClass.Rtp cfg;
        final int cx, cz, min, max;
        final boolean interior;
        final List<String> floors, biomes;
        final long deadline;
        long nextMessage, loadDeadline;
        int attempts;
        boolean finished, ticket;
        RtpSnapshot.Candidate candidate;
        CompletableFuture<RtpSnapshot.Candidate> random;
        CompletableFuture<?> loading;
        CompletableFuture<OptionalInt> checking;
        Request(ServerPlayer p, ServerLevel w, long time) {
            player = p; world = w; origin = (ServerLevel) p.level(); cfg = ConfigManager.CONFIG.rtp;
            var override = cfg.dimensions.get(w.dimension().identifier().toString());
            BlockPos spawn = w.getRespawnData().pos();
            cx = override != null && override.centerX != null ? override.centerX : spawn.getX();
            cz = override != null && override.centerZ != null ? override.centerZ : spawn.getZ();
            min = override != null && override.minRange != null ? override.minRange : cfg.minRange;
            max = Math.max(min, override != null && override.maxRange != null ? override.maxRange : cfg.maxRange);
            String mode = override == null ? "auto" : override.mode;
            interior = mode.equals("interior") || (mode.equals("auto") && w.dimensionType().hasCeiling());
            floors = List.copyOf(override != null && override.floorBlacklist != null ? override.floorBlacklist : cfg.floorBlacklist);
            biomes = List.copyOf(override != null && override.biomeBlacklist != null ? override.biomeBlacklist : cfg.biomeBlacklist);
            deadline = time + cfg.timeoutSeconds * 1000L;
            nextMessage = time + 1000;
        }
    }

    private static long now() { return System.nanoTime() / 1_000_000; }
    private static void message(ServerPlayer player, String key) {
        tools.sendPlayerMessage(player, tools.getTranslatedText("commands.teleport_commands.rtp." + key, player), true);
    }
    private static ExecutorService executor() {
        if (workers == null) workers = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "tpa-rtp-search"); thread.setDaemon(true); return thread;
        });
        return workers;
    }

    public static void request(ServerPlayer player, ServerLevel world) {
        long time = now();
        if (active.containsKey(player.getUUID())) { message(player, "pending"); return; }
        var cfg = ConfigManager.CONFIG.rtp;
        if (cfg.cooldownEnabled && cooldowns.getOrDefault(player.getUUID(), 0L) > time) {
            tools.sendPlayerMessage(player, tools.getTranslatedText("commands.teleport_commands.rtp.cooldown_left", player,
                    Component.literal(String.valueOf((cooldowns.get(player.getUUID()) - time + 999) / 1000))), true);
            return;
        }
        // Draining timed-out loads still count: they cannot bypass admission control.
        if (active.size() >= cfg.maxConcurrentLoads) { message(player, "busy_now"); return; }
        active.acquire(player.getUUID(), new Request(player, world, time), cfg.maxConcurrentLoads);
        message(player, "searching");
    }

    public static void tick(MinecraftServer server) {
        if (tpa.SERVER != server || ConfigManager.CONFIG == null) return;
        long time = now(); ticks++;
        if (lastTick != 0 && time - lastTick > 150) pauseUntil = time + 1000;
        lastTick = time;
        cooldowns.entrySet().removeIf(e -> e.getValue() <= time);
        protection.values().removeIf(p -> ticks >= p.untilTick || server.getPlayerList().getPlayer(p.player.getUUID()) != p.player);
        for (Request r : new ArrayList<>(active.values())) {
            try {
                if (!r.finished) {
                    boolean disconnected = server.getPlayerList().getPlayer(r.player.getUUID()) != r.player;
                    if (disconnected || !r.player.isAlive() || r.player.level() != r.origin || !ConfigManager.CONFIG.rtp.enabled)
                        finish(r, disconnected ? null : "cancelled", false, time);
                    else if (time >= r.deadline) finish(r, "timeout", false, time);
                }
                if (r.finished) { drain(r); continue; }
                if (time >= r.nextMessage) { message(r.player, "searching"); r.nextMessage = time + 1000; }
                advance(r, time);
            } catch (Exception ex) {
                Constants.LOGGER.error("RTP request failed", ex);
                finish(r, "failed_surface", false, time);
                drain(r);
            }
        }
    }

    private static void advance(Request r, long time) {
        if (r.loading != null) {
            if (!r.loading.isDone()) {
                if (time >= r.loadDeadline) release(r);
                return; // Never start a replacement while the previous load is unresolved.
            }
            boolean failed = r.loading.isCompletedExceptionally() || r.loading.isCancelled() || time >= r.loadDeadline;
            r.loading = null;
            if (failed) { release(r); r.candidate = null; return; }
            RtpSnapshot snapshot = RtpCapture.capture(r.world, r.candidate.x(), r.candidate.z(), r.interior, r.floors, r.biomes);
            if (snapshot == null) { release(r); r.candidate = null; return; }
            r.checking = CompletableFuture.supplyAsync(snapshot::find, executor());
            return;
        }
        if (r.checking != null) {
            if (!r.checking.isDone()) return;
            OptionalInt y = r.checking.join(); // Completed only; never waits on the main thread.
            r.checking = null;
            if (y.isPresent()) {
                // Recheck after the asynchronous decision. No stale snapshot can authorize a teleport.
                RtpSnapshot fresh = RtpCapture.capture(r.world, r.candidate.x(), r.candidate.z(), r.interior, r.floors, r.biomes);
                if (fresh != null && fresh.find().equals(y)
                        && r.world.getWorldBorder().isWithinBounds(new BlockPos(r.candidate.x(), y.getAsInt(), r.candidate.z()))) {
                    r.player.setYRot(r.candidate.yaw()); r.player.setXRot(0);
                    tools.Teleporter(r.player, r.world, new Vec3(r.candidate.x() + .5, y.getAsInt(), r.candidate.z() + .5));
                    protection.put(r.player.getUUID(), new Protection(r.player, ticks + r.cfg.invulnerabilityTicks));
                    finish(r, "success", true, time);
                    drain(r);
                    return;
                }
            }
            release(r); r.candidate = null;
            return;
        }
        if (r.random != null) {
            if (!r.random.isDone() || time < pauseUntil) return;
            r.candidate = r.random.join(); r.random = null;
            BlockPos pos = new BlockPos(r.candidate.x(), r.world.getMinY() + 1, r.candidate.z());
            if (!r.world.getWorldBorder().isWithinBounds(pos)) { r.candidate = null; return; }
            // Avoid sharing a ticket with another request for the same chunk.
            if (active.values().stream().anyMatch(other -> other != r && other.ticket && other.world == r.world
                    && chunk(other).equals(chunk(r)))) { r.candidate = null; return; }
            r.world.getChunkSource().addTicketWithRadius(TICKET, chunk(r), 0); r.ticket = true;
            r.loadDeadline = time + r.cfg.loadTimeoutSeconds * 1000L;
            r.loading = ((RtpChunkAccess) r.world.getChunkSource()).tpa$requestChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
            return;
        }
        if (r.attempts >= r.cfg.maxAttempts) { finish(r, "failed_surface", false, time); drain(r); return; }
        if (time < pauseUntil) return;
        r.attempts++;
        int cx = r.cx, cz = r.cz, min = r.min, max = r.max;
        r.random = CompletableFuture.supplyAsync(() -> RtpSnapshot.random(ThreadLocalRandom.current(), cx, cz, min, max), executor());
    }

    private static ChunkPos chunk(Request r) { return new ChunkPos(r.candidate.x() >> 4, r.candidate.z() >> 4); }
    private static void release(Request r) {
        if (r.ticket) { r.world.getChunkSource().removeTicketWithRadius(TICKET, chunk(r), 0); r.ticket = false; }
    }
    private static void finish(Request r, String key, boolean success, long time) {
        if (r.finished) return;
        r.finished = true;
        if (r.cfg.cooldownEnabled) {
            int seconds = success ? r.cfg.cooldownSeconds : r.cfg.failureCooldownSeconds;
            if (seconds > 0) cooldowns.put(r.player.getUUID(), time + seconds * 1000L);
        }
        release(r);
        if (key != null) message(r.player, key);
    }
    private static void drain(Request r) {
        release(r); active.release(r.player.getUUID(), r, r.loading, r.random, r.checking);
    }
    public static boolean isProtected(ServerPlayer player) {
        Protection p = protection.get(player.getUUID());
        return p != null && p.player == player && ticks < p.untilTick;
    }
    public static void reset(boolean stopping) {
        for (Request r : new ArrayList<>(active.values())) { finish(r, "cancelled", false, now()); drain(r); }
        cooldowns.clear(); protection.clear(); lastTick = 0; pauseUntil = 0;
        if (stopping) {
            active.clear();
            if (workers != null) { workers.shutdownNow(); workers = null; }
        }
    }
}
