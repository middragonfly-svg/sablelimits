package com.middragonfly.sablelimits.limits;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Map;

/**
 * Full scan of a Sable structure. This is only used when a cache record is missing (first sight,
 * expired TTL, config reload after being disabled) or right after assembly.
 * <p>
 * The plot bounding box is tight around all non-air blocks, so counting complete chunk sections is
 * equivalent to counting inside the bounding box. Palette counts are used instead of iterating all
 * 4096 positions of a section, which keeps even 128k-block ships cheap.
 */
public final class Counting {

    private Counting() {
    }

    public record Counts(Object2IntOpenHashMap<Block> byBlock, int total) {
    }

    public static StructureCounts scan(final ServerSubLevel subLevel) {
        final Level level = subLevel.getLevel();
        final StructureCounts counts = new StructureCounts(level.dimension().location(),
                subLevel.getUniqueId(), System.currentTimeMillis());
        if (subLevel.getUniqueId() == null) {
            return counts;
        }

        final LevelPlot plot = subLevel.getPlot();
        for (final PlotChunkHolder holder : plot.getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }

            final LevelChunkSection[] sections = chunk.getSections();
            for (final LevelChunkSection section : sections) {
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }

                section.getStates().count((state, count) -> {
                    if (state == null || state.isAir() || count <= 0) {
                        return;
                    }
                    final Block block = state.getBlock();
                    if (block != null) {
                        counts.counts.addTo(block, count);
                        counts.totalBlocks += count;
                    }
                });
            }
        }

        return counts;
    }

    /**
     * Counts blocks from a collection of world positions gathered by the assembly code.
     */
    public static Counts countPositions(final Level level, final Iterable<BlockPos> positions) {
        final Object2IntOpenHashMap<Block> byBlock = new Object2IntOpenHashMap<>();
        final LongOpenHashSet seen = new LongOpenHashSet();
        int total = 0;

        for (final BlockPos pos : positions) {
            final long key = pos.asLong();
            if (!seen.add(key)) {
                continue;
            }

            final BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            final Block block = state.getBlock();
            if (block == null) {
                continue;
            }

            byBlock.addTo(block, 1);
            total++;
        }

        return new Counts(byBlock, total);
    }

    public static Counts countStates(final Iterable<BlockState> states) {
        final Object2IntOpenHashMap<Block> byBlock = new Object2IntOpenHashMap<>();
        int total = 0;
        for (final BlockState state : states) {
            if (state == null || state.isAir()) {
                continue;
            }
            final Block block = state.getBlock();
            if (block == null) {
                continue;
            }
            byBlock.addTo(block, 1);
            total++;
        }
        return new Counts(byBlock, total);
    }

    public static int countMatching(final RuleSet.Rule rule, final Map<Block, Integer> counts) {
        int total = 0;
        for (final Map.Entry<Block, Integer> entry : counts.entrySet()) {
            if (rule.matches(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }
}
