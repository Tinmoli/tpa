package tpa.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface RtpChunkAccess {
    // The public wrapper calls managedBlock on the server thread. This entry point does not.
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<ChunkResult<ChunkAccess>> tpa$requestChunk(int x, int z, ChunkStatus status, boolean create);
}
