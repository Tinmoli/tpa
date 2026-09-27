package tpa.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tpa.RtpManager;

@Mixin(ServerPlayer.class)
public class RtpProtectionMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void tpa$protect(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && RtpManager.isProtected((ServerPlayer) (Object) this))
            ci.setReturnValue(false);
    }
}
