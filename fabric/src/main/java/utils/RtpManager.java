package tpa;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import tpa.mixin.RtpChunkAccess;

/** All state and world access are confined to the server thread. No blocking waits. */
public final class RtpManager {
    private static final TicketType SEARCH_TICKET = new TicketType(600L, TicketType.FLAG_LOADING);
    private record Spot(ServerLevel world, BlockPos pos, long expires) {}
    private static final class Request {
        final ServerPlayer player;
        final ServerLevel world, origin;
        final long deadline;
        int attempts;
        Request(ServerPlayer player, ServerLevel world, long now) {
            this.player = player;
            this.world = world;
            this.origin = (ServerLevel) player.level();
            deadline = now + 30_000;
        }
    }
    private record Flight(ServerLevel world, int x, int z, Request request,
                          long generation, CompletableFuture<?> future) {}
    private static final List<Spot> spots = new ArrayList<>();
    private static final LinkedHashMap<UUID, Request> requests = new LinkedHashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final List<Flight> flights = new ArrayList<>();
    private static long generation, nextLoad, lastTick, pauseUntil;

    private static long now() { return System.nanoTime() / 1_000_000; }
    private static void message(ServerPlayer player, String key) {
        tools.sendPlayerMessage(player, tools.getTranslatedText("commands.teleport_commands.rtp." + key, player), true);
    }

    public static void reset(boolean stopping) {
        generation++;
        spots.clear();
        for (Request request : requests.values()) message(request.player, "cancelled");
        requests.clear();
        cooldowns.clear();
        // A reload cannot cancel Minecraft generation. Keep its slot occupied until it drains.
        if (stopping) { flights.forEach(RtpManager::release); flights.clear(); }
        nextLoad = now() + 10_000;
        lastTick = 0;
        pauseUntil = 0;
    }

    public static void request(ServerPlayer player, ServerLevel world) {
        long time = now();
        if (requests.containsKey(player.getUUID())) { message(player, "pending"); return; }
        if (ConfigManager.CONFIG.rtp.cooldownEnabled && cooldowns.getOrDefault(player.getUUID(), 0L) > time) {
            tools.sendPlayerMessage(player, tools.getTranslatedText("commands.teleport_commands.rtp.cooldown_remaining", player,
                    net.minecraft.network.chat.Component.literal(String.valueOf((cooldowns.get(player.getUUID()) - time + 999) / 1000))), true);
            return;
        }
        if (requests.size() >= 8) { message(player, "busy"); return; }
        requests.put(player.getUUID(), new Request(player, world, time));
        if (ConfigManager.CONFIG.rtp.cooldownEnabled && ConfigManager.CONFIG.rtp.cooldownSeconds > 0)
            cooldowns.put(player.getUUID(), time + ConfigManager.CONFIG.rtp.cooldownSeconds * 1000L);
        message(player, "searching");
    }

    public static void tick(MinecraftServer server) {
        if (tpa.SERVER != server || ConfigManager.CONFIG == null) return;
        long time = now();
        if (lastTick != 0 && time - lastTick > 150) pauseUntil = time + 10_000;
        lastTick = time;
        cooldowns.entrySet().removeIf(e -> e.getValue() <= time);
        spots.removeIf(s -> s.expires <= time);
        requests.values().removeIf(r -> {
            boolean disconnected = server.getPlayerList().getPlayer(r.player.getUUID()) != r.player;
            boolean invalid = !ConfigManager.CONFIG.rtp.isEnabled() || r.player.level() != r.origin || !r.player.isAlive();
            if (!disconnected && (invalid || time >= r.deadline)) message(r.player, invalid ? "cancelled" : "timeout");
            return disconnected || invalid || time >= r.deadline;
        });
        for (Flight done : new ArrayList<>(flights)) {
            if (!done.future.isDone()) continue;
            flights.remove(done);
            release(done);
            if (done.generation == generation && ConfigManager.CONFIG.rtp.isEnabled()
                    && !done.future.isCompletedExceptionally() && !done.future.isCancelled()
                    && (done.request == null || requests.get(done.request.player.getUUID()) == done.request)) {
                Optional<BlockPos> safe = search(done.world, done.x, done.z);
                if (safe.isPresent()) {
                    if (done.request != null) complete(done.request, safe.get());
                    else if (spots.size() < 6 && spots.stream().filter(s -> s.world == done.world).count() < 2)
                        spots.add(new Spot(done.world, safe.get(), time + 300_000));
                } else if (done.request != null && done.request.attempts >= 3) {
                    requests.remove(done.request.player.getUUID());
                    message(done.request.player, "noSafeLocation");
                }
            }
        }
        if (!ConfigManager.CONFIG.rtp.isEnabled()) { spots.clear(); return; }
        // A ready, still-loaded cached location need not wait for a new generation slot.
        for (Request request : new ArrayList<>(requests.values())) {
            if (flights.stream().anyMatch(f -> f.request == request)) continue;
            if (request.attempts >= 3) continue;
            Spot cached = spots.stream().filter(s -> s.world == request.world).findFirst().orElse(null);
            if (cached == null) continue;
            if (cached.world.getChunkSource().getChunkNow(cached.pos.getX() >> 4, cached.pos.getZ() >> 4) != null) {
                spots.remove(cached);
                Optional<BlockPos> safe = tools.getRandomSafeBlockPos(cached.pos.getX(), cached.pos.getZ(), cached.world);
                safe.ifPresent(pos -> complete(request, pos));
            } else if (flights.size() < ConfigManager.CONFIG.rtp.maxConcurrentLoads && time >= nextLoad && time >= pauseUntil) {
                spots.remove(cached);
                start(request.world, cached.pos.getX(), cached.pos.getZ(), request, time);
            }
        }
        if (flights.size() >= ConfigManager.CONFIG.rtp.maxConcurrentLoads || time < nextLoad || time < pauseUntil) return;
        Request request = requests.values().stream().filter(r -> flights.stream().noneMatch(f -> f.request == r)).findFirst().orElse(null);
        ServerLevel world;
        if (request != null) {
            if (request.attempts >= 3) {
                requests.remove(request.player.getUUID());
                message(request.player, "noSafeLocation");
                return;
            }
            world = request.world;
        } else {
            if (spots.size() >= 6 || server.getPlayerList().getPlayers().isEmpty()) return;
            // Prepare only dimensions currently used by online players; at most two spots each.
            world = server.getPlayerList().getPlayers().stream().map(p -> (ServerLevel) p.level()).distinct()
                    .filter(w -> spots.stream().filter(s -> s.world == w).count()
                            + flights.stream().filter(f -> f.world == w && f.request == null).count() < 2).findFirst().orElse(null);
            if (world == null) return;
        }
        var rng = ThreadLocalRandom.current();
        double min = ConfigManager.CONFIG.rtp.minRange, max = ConfigManager.CONFIG.rtp.maxRange;
        double radius = Math.sqrt(min * min + rng.nextDouble() * (max * max - min * min));
        double angle = rng.nextDouble() * Math.PI * 2;
        int x = (int) Math.round(Math.cos(angle) * radius), z = (int) Math.round(Math.sin(angle) * radius);
        start(world, x, z, request, time);
    }

    private static void start(ServerLevel world, int x, int z, Request request, long time) {
        nextLoad = time + (request == null ? 10_000 : 2_000);
        if (flights.stream().anyMatch(f -> f.world == world && (f.x >> 4) == (x >> 4) && (f.z >> 4) == (z >> 4))) return;
        if (request != null) request.attempts++;
        if (!world.getWorldBorder().isWithinBounds(new BlockPos(x, world.getMinY() + 1, z))) return;
        try {
            world.getChunkSource().addTicketWithRadius(SEARCH_TICKET, new ChunkPos(x >> 4, z >> 4), 0);
            var future = ((RtpChunkAccess) world.getChunkSource()).tpa$requestChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
            flights.add(new Flight(world, x, z, request, generation, future));
        } catch (Exception e) {
            world.getChunkSource().removeTicketWithRadius(SEARCH_TICKET, new ChunkPos(x >> 4, z >> 4), 0);
            Constants.LOGGER.error("Unable to schedule RTP chunk", e);
        }
    }

    private static void release(Flight done) {
        done.world.getChunkSource().removeTicketWithRadius(SEARCH_TICKET, new ChunkPos(done.x >> 4, done.z >> 4), 0);
    }

    private static Optional<BlockPos> search(ServerLevel world, int x, int z) {
        // Reuse a single chunk rather than generating sixteen distant chunks.
        for (int i = 0; i < 16; i++) {
            int sx = i == 0 ? x : (x & ~15) + ThreadLocalRandom.current().nextInt(1, 15);
            int sz = i == 0 ? z : (z & ~15) + ThreadLocalRandom.current().nextInt(1, 15);
            double distance = Math.hypot(sx, sz);
            if (distance < ConfigManager.CONFIG.rtp.minRange || distance > ConfigManager.CONFIG.rtp.maxRange) continue;
            Optional<BlockPos> safe = tools.getRandomSafeBlockPos(sx, sz, world);
            if (safe.isPresent()) return safe;
        }
        return Optional.empty();
    }

    private static void complete(Request request, BlockPos pos) {
        requests.remove(request.player.getUUID());
        try {
            tools.Teleporter(request.player, request.world, new Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5));
        } catch (Exception e) {
            Constants.LOGGER.error("RTP teleport failed", e);
            message(request.player, "cancelled");
        }
    }

}
