package tpa;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RtpCompatibilityTest {
    private List<String> calls(String resource, String method, String descriptor) throws Exception {
        List<String> calls = new ArrayList<>();
        boolean[] found = {false};
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals(method) || !desc.equals(descriptor)) return null;
                    found[0] = true;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                            calls.add(owner + "." + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(found[0], "Missing mixin target: " + method + descriptor);
        return calls;
    }

    @Test void chunkEntryPointExistsAndDoesNotBlock() throws Exception {
        var calls = calls("/net/minecraft/server/level/ServerChunkCache.class", "getChunkFutureMainThread",
                "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;");
        assertFalse(calls.stream().anyMatch(c -> c.endsWith(".managedBlock") || c.endsWith(".join")
                || c.equals("java/util/concurrent/CompletableFuture.get")));
    }

    @Test void lifecycleMixinTargetsExist() throws Exception {
        calls("/net/minecraft/server/MinecraftServer.class", "tickServer", "(Ljava/util/function/BooleanSupplier;)V");
        calls("/net/minecraft/server/MinecraftServer.class", "stopServer", "()V");
        calls("/net/minecraft/server/level/ServerPlayer.class", "hurtServer",
                "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z");
    }

    @Test void safetySearchCannotSynchronouslyGenerateChunks() throws Exception {
        var calls = calls("/tpa/tools.class", "getRandomSafeBlockPos",
                "(IILnet/minecraft/server/level/ServerLevel;)Ljava/util/Optional;");
        assertFalse(calls.stream().anyMatch(c -> c.endsWith(".getChunk") || c.endsWith(".getChunkFuture")));
    }
}
