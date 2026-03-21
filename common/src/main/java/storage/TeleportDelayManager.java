package tpa;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Timer;
import java.util.TimerTask;

import static tpa.tools.getTranslatedText;

public class TeleportDelayManager {

    private static final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

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
            this.traveller    = traveller;
            this.destination  = destination;
            this.totalSeconds = totalSeconds;
            this.secondsLeft  = totalSeconds;
            // 在主线程初始化时记录起始位置
            this.startPos     = new AtomicReference<>(traveller.blockPosition());
        }
    }

    public static void startDelay(ServerPlayer traveller, ServerPlayer destination, Runnable doTeleport) {
        int delay = ConfigManager.CONFIG.tpa.getDelay();
        if (delay <= 0) { doTeleport.run(); return; }
        TeleportDelayManager.cancel(traveller.getUUID());
        PendingTeleport pt = new PendingTeleport(traveller, destination, delay);
        pending.put(traveller.getUUID(), pt);
        startTimers(traveller, pt, doTeleport);
    }

    public static void startDelaySimple(ServerPlayer traveller, int delay, Runnable doTeleport) {
        if (delay <= 0) { doTeleport.run(); return; }
        TeleportDelayManager.cancel(traveller.getUUID());
        PendingTeleport pt = new PendingTeleport(traveller, traveller, delay);
        pending.put(traveller.getUUID(), pt);
        startTimers(traveller, pt, doTeleport);
    }

    private static void startTimers(ServerPlayer traveller, PendingTeleport pt, Runnable doTeleport) {
        pt.countdownTimer = new Timer();
        pt.countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { scheduleTickOnMainThread(traveller, pt, doTeleport); }
        }, 0, 1000);
        pt.particleTimer = new Timer();
        pt.particleTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { tickParticles(traveller, pt); }
        }, 0, 50);
    }

    /**
     * 将倒计时逻辑提交到主线程执行，确保 blockPosition() 等玩家状态读取线程安全。
     * cancelOnMove 也在主线程读取最新 config，避免配置热更新后不生效。
     */
    private static void scheduleTickOnMainThread(ServerPlayer traveller, PendingTeleport pt, Runnable doTeleport) {
        tpa.SERVER.execute(() -> tickCountdown(traveller, pt, doTeleport));
    }

    private static void tickCountdown(ServerPlayer traveller, PendingTeleport pt, Runnable doTeleport) {
        // 玩家离线或已死亡，取消
        if (!traveller.isAlive() || traveller.hasDisconnected()) {
            TeleportDelayManager.cancel(traveller.getUUID());
            return;
        }

        // 在主线程读取 cancelOnMove 配置与玩家当前位置，保证线程安全与配置实时生效
        boolean cancelOnMove = ConfigManager.CONFIG.tpa.isCancelOnMove();
        if (cancelOnMove && !traveller.blockPosition().equals(pt.startPos.get())) {
            traveller.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.delayCancelled", traveller)
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
            TeleportDelayManager.cancel(traveller.getUUID());
            return;
        }

        if (pt.secondsLeft <= 0) {
            pending.remove(traveller.getUUID());
            pt.countdownTimer.cancel();
            pt.particleTimer.cancel();
            doTeleport.run();
            return;
        }

        Component countdown = getTranslatedText(
                "commands.teleport_commands.tpa.delayCountdown", traveller,
                Component.literal(String.valueOf(pt.secondsLeft))
                        .withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD));
        traveller.displayClientMessage(countdown, true);
        pt.secondsLeft--;
    }

    private static void tickParticles(ServerPlayer traveller, PendingTeleport pt) {
        if (!traveller.isAlive() || traveller.hasDisconnected()) return;
        tpa.SERVER.execute(() -> {
            ServerLevel level = traveller.level();
            double headX = traveller.getX();
            double headY = traveller.getY() + 1.6;
            double headZ = traveller.getZ();
            int count = 30 + RANDOM.nextInt(11);
            for (int i = 0; i < count; i++) {
                double theta = RANDOM.nextDouble() * 2 * Math.PI;
                double phi   = RANDOM.nextDouble() * Math.PI;
                double r     = 0.5 * Math.cbrt(RANDOM.nextDouble());
                double ox = r * Math.sin(phi) * Math.cos(theta);
                double oy = r * Math.cos(phi);
                double oz = r * Math.sin(phi) * Math.sin(theta);
                double vx = (RANDOM.nextDouble() - 0.5) * 0.04;
                double vy = -0.04 - RANDOM.nextDouble() * 0.06;
                double vz = (RANDOM.nextDouble() - 0.5) * 0.04;
                level.sendParticles(ParticleTypes.ENCHANT,
                        headX + ox, headY + oy, headZ + oz,
                        1, vx, vy, vz, 1.0);
            }
        });
    }

    public static void cancel(UUID uuid) {
        PendingTeleport pt = pending.remove(uuid);
        if (pt != null) {
            if (pt.countdownTimer != null) pt.countdownTimer.cancel();
            if (pt.particleTimer  != null) pt.particleTimer.cancel();
        }
    }

    public static boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }
}
