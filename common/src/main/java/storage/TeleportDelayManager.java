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
        public final BlockPos startPos;

        public PendingTeleport(ServerPlayer traveller, ServerPlayer destination, int totalSeconds) {
            this.traveller    = traveller;
            this.destination  = destination;
            this.totalSeconds = totalSeconds;
            this.secondsLeft  = totalSeconds;
            this.startPos     = traveller.blockPosition();
        }
    }

    public static void startDelay(ServerPlayer traveller, ServerPlayer destination, Runnable doTeleport) {
        int delay = ConfigManager.CONFIG.tpa.getDelay();
        boolean cancelOnMove = ConfigManager.CONFIG.tpa.isCancelOnMove();
        if (delay <= 0) { doTeleport.run(); return; }
        TeleportDelayManager.cancel(traveller.getUUID());
        PendingTeleport pt = new PendingTeleport(traveller, destination, delay);
        pending.put(traveller.getUUID(), pt);
        startTimers(traveller, pt, cancelOnMove, doTeleport);
    }

    public static void startDelaySimple(ServerPlayer traveller, int delay, Runnable doTeleport) {
        boolean cancelOnMove = ConfigManager.CONFIG.tpa.isCancelOnMove();
        if (delay <= 0) { doTeleport.run(); return; }
        TeleportDelayManager.cancel(traveller.getUUID());
        PendingTeleport pt = new PendingTeleport(traveller, traveller, delay);
        pending.put(traveller.getUUID(), pt);
        startTimers(traveller, pt, cancelOnMove, doTeleport);
    }

    private static void startTimers(ServerPlayer traveller, PendingTeleport pt, boolean cancelOnMove, Runnable doTeleport) {
        pt.countdownTimer = new Timer();
        pt.countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { tickCountdown(traveller, pt, cancelOnMove, doTeleport); }
        }, 0, 1000);
        pt.particleTimer = new Timer();
        pt.particleTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { tickParticles(traveller, pt); }
        }, 0, 50);
    }

    private static void tickCountdown(ServerPlayer traveller, PendingTeleport pt, boolean cancelOnMove, Runnable doTeleport) {
        if (!traveller.isAlive() || traveller.hasDisconnected()) {
            TeleportDelayManager.cancel(traveller.getUUID());
            return;
        }
        if (cancelOnMove && !traveller.blockPosition().equals(pt.startPos)) {
            tpa.SERVER.execute(() -> traveller.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.delayCancelled", traveller)
                            .withStyle(net.minecraft.ChatFormatting.RED), true));
            TeleportDelayManager.cancel(traveller.getUUID());
            return;
        }
        if (pt.secondsLeft <= 0) {
            pending.remove(traveller.getUUID());
            pt.countdownTimer.cancel();
            pt.particleTimer.cancel();
            tpa.SERVER.execute(doTeleport);
            return;
        }
        final int secs = pt.secondsLeft;
        tpa.SERVER.execute(() -> {
            Component countdown = getTranslatedText(
                    "commands.teleport_commands.tpa.delayCountdown", traveller,
                    Component.literal(String.valueOf(secs))
                            .withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD));
            traveller.displayClientMessage(countdown, true);
        });
        pt.secondsLeft--;
    }

    private static void tickParticles(ServerPlayer traveller, PendingTeleport pt) {
        if (!traveller.isAlive() || traveller.hasDisconnected()) return;
        tpa.SERVER.execute(() -> {
            ServerLevel level = traveller.level();
            double headX = traveller.getX();
            double headY = traveller.getY() + 1.6;
            double headZ = traveller.getZ();
            int count = 30 + RANDOM.nextInt(11); // 30~40个
            for (int i = 0; i < count; i++) {
                double theta = RANDOM.nextDouble() * 2 * Math.PI;
                double phi   = RANDOM.nextDouble() * Math.PI;
                double r     = 0.5 * Math.cbrt(RANDOM.nextDouble()); // 半兗0.5格
                double ox = r * Math.sin(phi) * Math.cos(theta);
                double oy = r * Math.cos(phi);
                double oz = r * Math.sin(phi) * Math.sin(theta);
                double vx = (RANDOM.nextDouble() - 0.5) * 0.04;
                double vy = -0.04 - RANDOM.nextDouble() * 0.06; // 每秒1格向下
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
