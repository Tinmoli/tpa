package tpa.rtp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Main-thread only. No live world objects escape into the snapshot. */
final class RtpTerrainAnalyzer {
    private static final Set<String> PLANTS =
            Set.of(
                    "minecraft:short_grass",
                    "minecraft:tall_grass",
                    "minecraft:fern",
                    "minecraft:large_fern",
                    "minecraft:snow");

    static RtpTerrainProfile capture(
            ServerLevel world,
            int x,
            int z,
            boolean interior,
            List<String> floors,
            List<String> biomes) {
        if (world.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
        int minY = world.getMinY(), maxY = world.getMaxY();
        byte[] cells = new byte[maxY - minY + 1];
        boolean[] bannedBiomes = new boolean[cells.length];
        Set<String> floorIds =
                floors.stream()
                        .filter(rule -> !rule.startsWith("#"))
                        .map(RtpTerrainAnalyzer::normalize)
                        .collect(Collectors.toSet());
        var floorTags =
                floors.stream()
                        .filter(rule -> rule.startsWith("#"))
                        .map(rule -> Identifier.tryParse(rule.substring(1)))
                        .filter(java.util.Objects::nonNull)
                        .map(tagId -> TagKey.create(Registries.BLOCK, tagId))
                        .toList();
        Set<String> biomeIds =
                biomes.stream()
                        .filter(rule -> !rule.startsWith("#"))
                        .map(RtpTerrainAnalyzer::normalize)
                        .collect(Collectors.toSet());
        var biomeTags =
                biomes.stream()
                        .filter(rule -> rule.startsWith("#"))
                        .map(rule -> Identifier.tryParse(rule.substring(1)))
                        .filter(java.util.Objects::nonNull)
                        .map(tagId -> TagKey.create(Registries.BIOME, tagId))
                        .toList();
        for (int y = minY; y <= maxY; y++) {
            BlockPos position = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(position);
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            boolean blockedFloor =
                    blockId.equals("minecraft:bedrock")
                            || floorIds.contains(blockId)
                            || floorTags.stream().anyMatch(state::is);
            byte flags;
            if (!state.getFluidState().isEmpty()) flags = RtpTerrainProfile.LIQUID;
            else if (state.is(BlockTags.LEAVES)) flags = RtpTerrainProfile.LEAVES;
            else if (blockedFloor) flags = RtpTerrainProfile.BAD;
            else if ((state.isAir() || PLANTS.contains(blockId))
                    && state.getCollisionShape(world, position).isEmpty())
                flags = RtpTerrainProfile.PASSABLE;
            else if (state.isFaceSturdy(world, position, Direction.UP)
                    && state.getCollisionShape(world, position).max(Direction.Axis.Y) == 1.0)
                flags = RtpTerrainProfile.FLOOR;
            else flags = RtpTerrainProfile.BAD;
            cells[y - minY] = flags;
            if (y > minY
                    && cells[y - minY - 1] == RtpTerrainProfile.FLOOR
                    && flags == RtpTerrainProfile.PASSABLE) {
                var biome = world.getBiome(position);
                bannedBiomes[y - minY] =
                        biome.unwrapKey()
                                        .map(
                                                biomeKey ->
                                                        biomeIds.contains(
                                                                biomeKey.identifier().toString()))
                                        .orElse(false)
                                || biomeTags.stream().anyMatch(biome::is);
            }
        }
        int highestFloor = maxY - 2;
        if (interior)
            highestFloor = Math.min(highestFloor, minY + world.dimensionType().logicalHeight() - 3);
        return new RtpTerrainProfile(minY, cells, bannedBiomes, interior, highestFloor);
    }

    private static String normalize(String rule) {
        return rule.contains(":") ? rule : "minecraft:" + rule;
    }
}
