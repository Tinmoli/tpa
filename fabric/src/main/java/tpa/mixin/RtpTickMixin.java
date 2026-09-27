package tpa.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tpa.RtpManager;

@Mixin(MinecraftServer.class)
public class RtpTickMixin {
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void tpa$rtpTick(CallbackInfo ci) {
        RtpManager.tick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void tpa$rtpStop(CallbackInfo ci) {
        RtpManager.reset(true);
    }
}
