package tpa.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.Set;

public final class TeleportSafety {
    private static final Set<String> UNSAFE_COLLISION_FREE_BLOCKS =
            Set.of(
                    "block.minecraft.lava", "block.minecraft.flowing_lava",
                    "block.minecraft.end_portal", "block.minecraft.end_gateway",
                    "block.minecraft.fire", "block.minecraft.soul_fire",
                    "block.minecraft.powder_snow", "block.minecraft.nether_portal");

    public static Optional<BlockPos> getSafeBlockPos(BlockPos blockPos, ServerLevel world) {
        int row = 1;
        int rows = 3;

        int blockPosX = blockPos.getX();
        int blockPosY = blockPos.getY();
        int blockPosZ = blockPos.getZ();

        if (isBlockPosSafe(blockPos, world)) {
            return Optional.of(blockPos);
        } else {
            while (row <= rows) {
                for (int z = -row; z <= row; z++) {
                    for (int x = -row; x <= row; x++) {
                        for (int y = -row; y <= row; y++) {
                            if ((x == -row || x == row)
                                    || (z == -row || z == row)
                                    || (y == -row || y == row)) {
                                BlockPos newPos =
                                        new BlockPos(blockPosX + x, blockPosY + y, blockPosZ + z);
                                if (isBlockPosSafe(newPos, world)) {
                                    return Optional.of(newPos);
                                }
                            }
                        }
                    }
                }
                row++;
            }
            return Optional.empty();
        }
    }

    public static Optional<BlockPos> getRandomSafeBlockPos(int x, int z, ServerLevel world) {
        if (world.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return Optional.empty();
        BlockPos column = new BlockPos(x, world.getMinY() + 1, z);
        if (!world.getWorldBorder().isWithinBounds(column)) return Optional.empty();
        java.util.OptionalInt height =
                RtpColumnSearch.find(
                        new RtpColumnSearch.Column() {
                            @Override
                            public void load() {
                                // The RTP scheduler has already completed asynchronous loading.
                            }

                            @Override
                            public int surfaceY() {
                                return world.getHeight(
                                        net.minecraft.world.level.levelgen.Heightmap.Types
                                                .MOTION_BLOCKING,
                                        x,
                                        z);
                            }

                            @Override
                            public boolean isSafe(int y) {
                                BlockPos candidate = new BlockPos(x, y, z);
                                return isBlockPosSafe(candidate, world)
                                        && world.getFluidState(candidate).isEmpty()
                                        && world.getFluidState(candidate.above()).isEmpty()
                                        && world.getFluidState(candidate.below()).isEmpty();
                            }
                        },
                        world.getMinY(),
                        world.getMaxY(),
                        world.dimensionType().hasCeiling(),
                        world.dimensionType().logicalHeight());
        return height.isPresent()
                ? Optional.of(new BlockPos(x, height.getAsInt(), z))
                : Optional.empty();
    }

    private static boolean isBlockPosSafe(BlockPos bottomPlayer, ServerLevel world) {
        if (!world.getWorldBorder().isWithinBounds(bottomPlayer)
                || world.isOutsideBuildHeight(bottomPlayer.below())
                || world.isOutsideBuildHeight(bottomPlayer.above())) return false;
        BlockPos belowPlayer =
                new BlockPos(bottomPlayer.getX(), bottomPlayer.getY() - 1, bottomPlayer.getZ());
        String belowPlayerId = world.getBlockState(belowPlayer).getBlock().getDescriptionId();

        String feetBlockId = world.getBlockState(bottomPlayer).getBlock().getDescriptionId();

        BlockPos headPosition =
                new BlockPos(bottomPlayer.getX(), bottomPlayer.getY() + 1, bottomPlayer.getZ());
        String headBlockId = world.getBlockState(headPosition).getBlock().getDescriptionId();

        return !UNSAFE_COLLISION_FREE_BLOCKS.contains(belowPlayerId)
                && !Set.of(
                                "block.minecraft.magma_block",
                                "block.minecraft.cactus",
                                "block.minecraft.campfire",
                                "block.minecraft.soul_campfire")
                        .contains(belowPlayerId)
                && (belowPlayerId.equals("block.minecraft.water")
                        || world.getBlockState(belowPlayer)
                                .isFaceSturdy(world, belowPlayer, net.minecraft.core.Direction.UP))
                && (world.getBlockState(bottomPlayer)
                                .getCollisionShape(world, bottomPlayer)
                                .isEmpty()
                        && !UNSAFE_COLLISION_FREE_BLOCKS.contains(feetBlockId))
                && world.getBlockState(headPosition)
                        .getCollisionShape(world, headPosition)
                        .isEmpty()
                && (!UNSAFE_COLLISION_FREE_BLOCKS.contains(headBlockId));
    }
}
