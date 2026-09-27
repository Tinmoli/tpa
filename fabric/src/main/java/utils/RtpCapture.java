package tpa;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Main-thread only. No live world objects escape into the snapshot. */
final class RtpCapture {
    private static final Set<String> PLANTS = Set.of("minecraft:short_grass", "minecraft:tall_grass",
            "minecraft:fern", "minecraft:large_fern", "minecraft:snow");
    static RtpSnapshot capture(ServerLevel world, int x, int z, boolean interior,
                               List<String> floors, List<String> biomes) {
        if (world.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
        int min = world.getMinY(), max = world.getMaxY();
        byte[] cells = new byte[max - min + 1];
        boolean[] banned = new boolean[cells.length];
        Set<String> floorIds = floors.stream().filter(r -> !r.startsWith("#")).map(RtpCapture::normalize).collect(Collectors.toSet());
        var floorTags = floors.stream().filter(r -> r.startsWith("#")).map(r -> Identifier.tryParse(r.substring(1)))
                .filter(java.util.Objects::nonNull).map(id -> TagKey.create(Registries.BLOCK, id)).toList();
        Set<String> biomeIds = biomes.stream().filter(r -> !r.startsWith("#")).map(RtpCapture::normalize).collect(Collectors.toSet());
        var biomeTags = biomes.stream().filter(r -> r.startsWith("#")).map(r -> Identifier.tryParse(r.substring(1)))
                .filter(java.util.Objects::nonNull).map(id -> TagKey.create(Registries.BIOME, id)).toList();
        for (int y = min; y <= max; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            boolean bad = id.equals("minecraft:bedrock") || floorIds.contains(id) || floorTags.stream().anyMatch(state::is);
            byte flags;
            if (!state.getFluidState().isEmpty()) flags = RtpSnapshot.LIQUID;
            else if (state.is(BlockTags.LEAVES)) flags = RtpSnapshot.LEAVES;
            else if (bad) flags = RtpSnapshot.BAD;
            else if ((state.isAir() || PLANTS.contains(id))
                    && state.getCollisionShape(world, pos).isEmpty()) flags = RtpSnapshot.PASSABLE;
            else if (state.isFaceSturdy(world, pos, Direction.UP)
                    && state.getCollisionShape(world, pos).max(Direction.Axis.Y) == 1.0) flags = RtpSnapshot.FLOOR;
            else flags = RtpSnapshot.BAD;
            cells[y - min] = flags;
            if (y > min && cells[y - min - 1] == RtpSnapshot.FLOOR && flags == RtpSnapshot.PASSABLE) {
                var biome = world.getBiome(pos);
                banned[y - min] = biome.unwrapKey().map(k -> biomeIds.contains(k.identifier().toString())).orElse(false)
                        || biomeTags.stream().anyMatch(biome::is);
            }
        }
        int highest = max - 2;
        if (interior) highest = Math.min(highest, min + world.dimensionType().logicalHeight() - 3);
        return new RtpSnapshot(min, cells, banned, interior, highest);
    }

    private static String normalize(String rule) { return rule.contains(":") ? rule : "minecraft:" + rule; }
}
