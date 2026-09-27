package tpa.mixin;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tpa.TpaMod;

@Mixin(MinecraftServer.class)
public class ServerStartMixin {

    @Inject(
            method = "runServer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;",
                            ordinal = 0))
    private void runServer(CallbackInfo info) {
        TpaMod.initializeMod((MinecraftServer) (Object) this);
    }
}
