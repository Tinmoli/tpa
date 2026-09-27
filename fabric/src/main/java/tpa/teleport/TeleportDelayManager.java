package tpa.teleport;

import static tpa.language.LanguageManager.getTranslatedText;
import static tpa.util.MessageService.sendPlayerMessage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import tpa.TpaMod;
import tpa.config.ConfigManager;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class TeleportDelayManager {

    private static final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();

    public static class PendingTeleport {
        public final ServerPlayer traveller;
        public final ServerPlayer destination;
        public final int totalSeconds;
        public int secondsLeft;
        public Timer countdownTimer;
        public Timer particleTimer;
        // 用 AtomicReference 保证跨线程可见性
        public final AtomicReference<BlockPos> startPos;

        public PendingTeleport(ServerPlayer traveller, ServerPlayer destination, int totalSeconds) {
            this.traveller = traveller;
            this.destination = destination;
            this.totalSeconds = totalSeconds;
            this.secondsLeft = totalSeconds;
            // 在主线程初始化时记录起始位置
            this.startPos = new AtomicReference<>(traveller.blockPosition());
        }
    }

    public static void startDelay(
            ServerPlayer traveller, ServerPlayer destination, Runnable doTeleport) {
        int delay = ConfigManager.CONFIG.tpa.getDelay();
        if (delay <= 0) {
            doTeleport.run();
            return;
        }
        TeleportDelayManager.cancel(traveller.getUUID());
        PendingTeleport pendingTeleport = new PendingTeleport(traveller, destination, delay);
        pending.put(traveller.getUUID(), pendingTeleport);
        startTimers(traveller, pendingTeleport, doTeleport);
    }

    public static void startDelaySimple(ServerPlayer traveller, int delay, Runnable doTeleport) {
        if (delay <= 0) {
            doTeleport.run();
            return;
        }
        TeleportDelayManager.cancel(traveller.getUUID());
        PendingTeleport pendingTeleport = new PendingTeleport(traveller, traveller, delay);
        pending.put(traveller.getUUID(), pendingTeleport);
        startTimers(traveller, pendingTeleport, doTeleport);
    }

    private static void startTimers(
            ServerPlayer traveller, PendingTeleport pendingTeleport, Runnable doTeleport) {
        // Daemon timers cannot keep the JVM alive after the server shuts down.
        pendingTeleport.countdownTimer = new Timer("tpa-countdown-" + traveller.getUUID(), true);
        pendingTeleport.countdownTimer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        scheduleTickOnMainThread(traveller, pendingTeleport, doTeleport);
                    }
                },
                0,
                1000);
        pendingTeleport.particleTimer = new Timer("tpa-particles-" + traveller.getUUID(), true);
        pendingTeleport.particleTimer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        scheduleParticlesOnMainThread(traveller, pendingTeleport);
                    }
                },
                0,
                50);
    }

    /** 将倒计时逻辑提交到主线程执行，确保 blockPosition() 等玩家状态读取线程安全。 cancelOnMove 也在主线程读取最新 config，避免配置热更新后不生效。 */
    private static void scheduleTickOnMainThread(
            ServerPlayer traveller, PendingTeleport pendingTeleport, Runnable doTeleport) {
        TpaMod.SERVER.execute(() -> tickCountdown(traveller, pendingTeleport, doTeleport));
    }

    private static void tickCountdown(
            ServerPlayer traveller, PendingTeleport pendingTeleport, Runnable doTeleport) {
        // A task can already be queued when a teleport is cancelled or replaced.
        // Never let such a stale task count down or execute its old callback.
        if (pending.get(traveller.getUUID()) != pendingTeleport) {
            return;
        }

        // 玩家离线或已死亡，取消
        if (!traveller.isAlive()
                || traveller.hasDisconnected()
                || !pendingTeleport.destination.isAlive()
                || pendingTeleport.destination.hasDisconnected()) {
            TeleportDelayManager.cancel(traveller.getUUID());
            return;
        }

        // 在主线程读取 cancelOnMove 配置与玩家当前位置，保证线程安全与配置实时生效
        boolean cancelOnMove = ConfigManager.CONFIG.tpa.isCancelOnMove();
        if (cancelOnMove && !traveller.blockPosition().equals(pendingTeleport.startPos.get())) {
            sendPlayerMessage(
                    traveller,
                    getTranslatedText("commands.teleport_commands.tpa.delayCancelled", traveller)
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true);
            TeleportDelayManager.cancel(traveller.getUUID());
            return;
        }

        if (pendingTeleport.secondsLeft <= 0) {
            // Remove only this exact pending teleport. If another teleport has
            // replaced it, its timers and callback must remain untouched.
            if (!pending.remove(traveller.getUUID(), pendingTeleport)) {
                return;
            }
            pendingTeleport.countdownTimer.cancel();
            pendingTeleport.particleTimer.cancel();
            doTeleport.run();
            return;
        }

        Component countdown =
                getTranslatedText(
                        "commands.teleport_commands.tpa.delayCountdown",
                        traveller,
                        Component.literal(String.valueOf(pendingTeleport.secondsLeft))
                                .withStyle(
                                        net.minecraft.ChatFormatting.YELLOW,
                                        net.minecraft.ChatFormatting.BOLD));
        sendPlayerMessage(traveller, countdown, true);
        pendingTeleport.secondsLeft--;
    }

    private static void scheduleParticlesOnMainThread(
            ServerPlayer traveller, PendingTeleport pendingTeleport) {
        TpaMod.SERVER.execute(() -> tickParticles(traveller, pendingTeleport));
    }

    private static void tickParticles(ServerPlayer traveller, PendingTeleport pendingTeleport) {
        // All player and world access is performed on the server thread.
        if (pending.get(traveller.getUUID()) != pendingTeleport
                || !traveller.isAlive()
                || traveller.hasDisconnected()) {
            return;
        }

        ServerLevel level = traveller.level();
        double headX = traveller.getX();
        double headY = traveller.getY() + 1.6;
        double headZ = traveller.getZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = 30 + random.nextInt(11);
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;
            double radius = 0.5 * Math.cbrt(random.nextDouble());
            double offsetX = radius * Math.sin(phi) * Math.cos(theta);
            double offsetY = radius * Math.cos(phi);
            double offsetZ = radius * Math.sin(phi) * Math.sin(theta);
            double velocityX = (random.nextDouble() - 0.5) * 0.04;
            double velocityY = -0.04 - random.nextDouble() * 0.06;
            double velocityZ = (random.nextDouble() - 0.5) * 0.04;
            level.sendParticles(
                    ParticleTypes.ENCHANT,
                    headX + offsetX,
                    headY + offsetY,
                    headZ + offsetZ,
                    1,
                    velocityX,
                    velocityY,
                    velocityZ,
                    1.0);
        }
    }

    public static void cancel(UUID uuid) {
        PendingTeleport pendingTeleport = pending.remove(uuid);
        if (pendingTeleport != null) {
            if (pendingTeleport.countdownTimer != null) pendingTeleport.countdownTimer.cancel();
            if (pendingTeleport.particleTimer != null) pendingTeleport.particleTimer.cancel();
        }
    }

    public static void cancelAll() {
        for (PendingTeleport pendingTeleport : pending.values()) {
            if (pendingTeleport.countdownTimer != null) pendingTeleport.countdownTimer.cancel();
            if (pendingTeleport.particleTimer != null) pendingTeleport.particleTimer.cancel();
        }
        pending.clear();
    }

    public static boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }
}
