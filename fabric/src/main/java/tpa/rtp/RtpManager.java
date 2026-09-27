package tpa.rtp;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import tpa.Constants;
import tpa.TpaMod;
import tpa.config.ConfigManager;
import tpa.config.ModConfig;
import tpa.language.LanguageManager;
import tpa.mixin.RtpChunkAccess;
import tpa.teleport.TeleportService;
import tpa.util.MessageService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

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
        final ModConfig.Rtp config;
        final int centerX, centerZ, minRange, maxRange;
        final boolean interior;
        final List<String> floors, biomes;
        final long deadline;
        long nextMessage, loadDeadline;
        int attempts;
        boolean finished, ticket;
        RtpTerrainProfile.Candidate candidate;
        CompletableFuture<RtpTerrainProfile.Candidate> random;
        CompletableFuture<?> loading;
        CompletableFuture<OptionalInt> checking;

        Request(ServerPlayer player, ServerLevel world, long time) {
            this.player = player;
            this.world = world;
            origin = (ServerLevel) player.level();
            config = ConfigManager.CONFIG.rtp;
            var override = config.dimensions.get(world.dimension().identifier().toString());
            BlockPos spawn = world.getRespawnData().pos();
            centerX =
                    override != null && override.centerX != null ? override.centerX : spawn.getX();
            centerZ =
                    override != null && override.centerZ != null ? override.centerZ : spawn.getZ();
            minRange =
                    override != null && override.minRange != null
                            ? override.minRange
                            : config.minRange;
            maxRange =
                    Math.max(
                            minRange,
                            override != null && override.maxRange != null
                                    ? override.maxRange
                                    : config.maxRange);
            String mode = override == null ? "auto" : override.mode;
            interior =
                    mode.equals("interior")
                            || (mode.equals("auto") && world.dimensionType().hasCeiling());
            floors =
                    List.copyOf(
                            override != null && override.floorBlacklist != null
                                    ? override.floorBlacklist
                                    : config.floorBlacklist);
            biomes =
                    List.copyOf(
                            override != null && override.biomeBlacklist != null
                                    ? override.biomeBlacklist
                                    : config.biomeBlacklist);
            deadline = time + config.timeoutSeconds * 1000L;
            nextMessage = time + 1000;
        }
    }

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }

    private static void message(ServerPlayer player, String key) {
        MessageService.sendPlayerMessage(
                player,
                LanguageManager.getTranslatedText("commands.teleport_commands.rtp." + key, player),
                true);
    }

    private static ExecutorService executor() {
        if (workers == null)
            workers =
                    Executors.newFixedThreadPool(
                            2,
                            request -> {
                                Thread thread = new Thread(request, "tpa-rtp-search");
                                thread.setDaemon(true);
                                return thread;
                            });
        return workers;
    }

    public static void request(ServerPlayer player, ServerLevel world) {
        long time = now();
        if (active.containsKey(player.getUUID())) {
            message(player, "pending");
            return;
        }
        var config = ConfigManager.CONFIG.rtp;
        if (config.cooldownEnabled && cooldowns.getOrDefault(player.getUUID(), 0L) > time) {
            MessageService.sendPlayerMessage(
                    player,
                    LanguageManager.getTranslatedText(
                            "commands.teleport_commands.rtp.cooldown_left",
                            player,
                            Component.literal(
                                    String.valueOf(
                                            (cooldowns.get(player.getUUID()) - time + 999)
                                                    / 1000))),
                    true);
            return;
        }
        // Draining timed-out loads still count: they cannot bypass admission control.
        if (active.size() >= config.maxConcurrentLoads) {
            message(player, "busy_now");
            return;
        }
        active.acquire(
                player.getUUID(), new Request(player, world, time), config.maxConcurrentLoads);
        message(player, "searching");
    }

    public static void tick(MinecraftServer server) {
        if (TpaMod.SERVER != server || ConfigManager.CONFIG == null) return;
        long time = now();
        ticks++;
        if (lastTick != 0 && time - lastTick > 150) pauseUntil = time + 1000;
        lastTick = time;
        cooldowns.entrySet().removeIf(e -> e.getValue() <= time);
        protection
                .values()
                .removeIf(
                        player ->
                                ticks >= player.untilTick
                                        || server.getPlayerList().getPlayer(player.player.getUUID())
                                                != player.player);
        for (Request request : new ArrayList<>(active.values())) {
            try {
                if (!request.finished) {
                    boolean disconnected =
                            server.getPlayerList().getPlayer(request.player.getUUID())
                                    != request.player;
                    if (disconnected
                            || !request.player.isAlive()
                            || request.player.level() != request.origin
                            || !ConfigManager.CONFIG.rtp.enabled)
                        finish(request, disconnected ? null : "cancelled", false, time);
                    else if (time >= request.deadline) finish(request, "timeout", false, time);
                }
                if (request.finished) {
                    drain(request);
                    continue;
                }
                if (time >= request.nextMessage) {
                    message(request.player, "searching");
                    request.nextMessage = time + 1000;
                }
                advance(request, time);
            } catch (Exception exception) {
                Constants.LOGGER.error("RTP request failed", exception);
                finish(request, "failed_surface", false, time);
                drain(request);
            }
        }
    }

    private static void advance(Request request, long time) {
        if (request.loading != null) {
            if (!request.loading.isDone()) {
                if (time >= request.loadDeadline) release(request);
                return; // Never start a replacement while the previous load is unresolved.
            }
            boolean failed =
                    request.loading.isCompletedExceptionally()
                            || request.loading.isCancelled()
                            || time >= request.loadDeadline;
            request.loading = null;
            if (failed) {
                release(request);
                request.candidate = null;
                return;
            }
            RtpTerrainProfile terrain =
                    RtpTerrainAnalyzer.capture(
                            request.world,
                            request.candidate.x(),
                            request.candidate.z(),
                            request.interior,
                            request.floors,
                            request.biomes);
            if (terrain == null) {
                release(request);
                request.candidate = null;
                return;
            }
            request.checking = CompletableFuture.supplyAsync(terrain::find, executor());
            return;
        }
        if (request.checking != null) {
            if (!request.checking.isDone()) return;
            OptionalInt safeHeight =
                    request.checking.join(); // Completed only; never waits on the main thread.
            request.checking = null;
            if (safeHeight.isPresent()) {
                // Recheck after the asynchronous decision. No stale snapshot can authorize a
                // teleport.
                RtpTerrainProfile currentTerrain =
                        RtpTerrainAnalyzer.capture(
                                request.world,
                                request.candidate.x(),
                                request.candidate.z(),
                                request.interior,
                                request.floors,
                                request.biomes);
                if (currentTerrain != null
                        && currentTerrain.find().equals(safeHeight)
                        && request.world
                                .getWorldBorder()
                                .isWithinBounds(
                                        new BlockPos(
                                                request.candidate.x(),
                                                safeHeight.getAsInt(),
                                                request.candidate.z()))) {
                    request.player.setYRot(request.candidate.yaw());
                    request.player.setXRot(0);
                    TeleportService.teleport(
                            request.player,
                            request.world,
                            new Vec3(
                                    request.candidate.x() + .5,
                                    safeHeight.getAsInt(),
                                    request.candidate.z() + .5));
                    protection.put(
                            request.player.getUUID(),
                            new Protection(
                                    request.player, ticks + request.config.invulnerabilityTicks));
                    finish(request, "success", true, time);
                    drain(request);
                    return;
                }
            }
            release(request);
            request.candidate = null;
            return;
        }
        if (request.random != null) {
            if (!request.random.isDone() || time < pauseUntil) return;
            request.candidate = request.random.join();
            request.random = null;
            BlockPos pos =
                    new BlockPos(
                            request.candidate.x(),
                            request.world.getMinY() + 1,
                            request.candidate.z());
            if (!request.world.getWorldBorder().isWithinBounds(pos)) {
                request.candidate = null;
                return;
            }
            // Avoid sharing a ticket with another request for the same chunk.
            if (active.values().stream()
                    .anyMatch(
                            other ->
                                    other != request
                                            && other.ticket
                                            && other.world == request.world
                                            && chunk(other).equals(chunk(request)))) {
                request.candidate = null;
                return;
            }
            request.world.getChunkSource().addTicketWithRadius(TICKET, chunk(request), 0);
            request.ticket = true;
            request.loadDeadline = time + request.config.loadTimeoutSeconds * 1000L;
            request.loading =
                    ((RtpChunkAccess) request.world.getChunkSource())
                            .requestChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
            return;
        }
        if (request.attempts >= request.config.maxAttempts) {
            finish(request, "failed_surface", false, time);
            drain(request);
            return;
        }
        if (time < pauseUntil) return;
        request.attempts++;
        int centerX = request.centerX,
                centerZ = request.centerZ,
                minRange = request.minRange,
                maxRange = request.maxRange;
        request.random =
                CompletableFuture.supplyAsync(
                        () ->
                                RtpTerrainProfile.random(
                                        ThreadLocalRandom.current(),
                                        centerX,
                                        centerZ,
                                        minRange,
                                        maxRange),
                        executor());
    }

    private static ChunkPos chunk(Request request) {
        return new ChunkPos(request.candidate.x() >> 4, request.candidate.z() >> 4);
    }

    private static void release(Request request) {
        if (request.ticket) {
            request.world.getChunkSource().removeTicketWithRadius(TICKET, chunk(request), 0);
            request.ticket = false;
        }
    }

    private static void finish(Request request, String key, boolean success, long time) {
        if (request.finished) return;
        request.finished = true;
        if (request.config.cooldownEnabled) {
            int seconds =
                    success
                            ? request.config.cooldownSeconds
                            : request.config.failureCooldownSeconds;
            if (seconds > 0) cooldowns.put(request.player.getUUID(), time + seconds * 1000L);
        }
        release(request);
        if (key != null) message(request.player, key);
    }

    private static void drain(Request request) {
        release(request);
        active.release(
                request.player.getUUID(),
                request,
                request.loading,
                request.random,
                request.checking);
    }

    public static boolean isProtected(ServerPlayer player) {
        Protection guard = protection.get(player.getUUID());
        return guard != null && guard.player == player && ticks < guard.untilTick;
    }

    public static void reset(boolean stopping) {
        for (Request request : new ArrayList<>(active.values())) {
            finish(request, "cancelled", false, now());
            drain(request);
        }
        cooldowns.clear();
        protection.clear();
        lastTick = 0;
        pauseUntil = 0;
        if (stopping) {
            active.clear();
            if (workers != null) {
                workers.shutdownNow();
                workers = null;
            }
        }
    }
}
