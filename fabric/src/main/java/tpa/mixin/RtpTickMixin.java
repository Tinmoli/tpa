package tpa.mixin;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tpa.rtp.RtpManager;

@Mixin(MinecraftServer.class)
public class RtpTickMixin {
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void tickRtp(CallbackInfo ci) {
        RtpManager.tick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void stopRtp(CallbackInfo ci) {
        RtpManager.reset(true);
    }
}
