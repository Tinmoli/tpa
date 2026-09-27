package tpa.teleport;

import static net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import tpa.TpaMod;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

public final class TeleportService {

    public static void teleport(ServerPlayer player, ServerLevel world, Vec3 destination) {
        // 传送前粒子 + 末影人音效
        spawnEnchantBurst(world, player.getX(), player.getY() + 1.6, player.getZ(), 40);
        world.playSound(
                null,
                player.blockPosition(),
                SoundEvent.createVariableRangeEvent(ENDERMAN_TELEPORT.location()),
                SoundSource.PLAYERS,
                0.4f,
                1.0f);

        boolean flying = player.getAbilities().flying;
        player.teleportTo(
                world,
                destination.x,
                destination.y,
                destination.z,
                Set.of(),
                player.getYRot(),
                player.getXRot(),
                false);
        if (flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }

        // 传送完成：末影人音效
        world.playSound(
                null,
                player.blockPosition(),
                SoundEvent.createVariableRangeEvent(ENDERMAN_TELEPORT.location()),
                SoundSource.PLAYERS,
                0.6f,
                1.0f);

        // Use a daemon timer and return to the server thread before touching
        // player/world state.
        Timer timer = new Timer("tpa-post-teleport-particles", true);
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        TpaMod.SERVER.execute(
                                () -> {
                                    if (player.isAlive() && !player.hasDisconnected()) {
                                        spawnEnchantBurst(
                                                world,
                                                player.getX(),
                                                player.getY() + 1.6,
                                                player.getZ(),
                                                40);
                                    }
                                });
                        timer.cancel();
                    }
                },
                100);
    }

    public static void playAcceptSound(ServerPlayer player) {
        player.level()
                .playSound(
                        null,
                        player.blockPosition(),
                        net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS,
                        1.0f,
                        1.0f);
    }

    public static void playDenySound(ServerPlayer player) {
        // 无音效
    }

    private static void spawnEnchantBurst(
            ServerLevel world, double centerX, double centerY, double centerZ, int count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
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
            world.sendParticles(
                    ParticleTypes.ENCHANT,
                    centerX + offsetX,
                    centerY + offsetY,
                    centerZ + offsetZ,
                    1,
                    velocityX,
                    velocityY,
                    velocityZ,
                    1.0);
        }
    }
}
