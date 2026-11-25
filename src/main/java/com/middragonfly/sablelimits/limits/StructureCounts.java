package com.middragonfly.sablelimits.limits;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.UUID;

/**
 * Mutable per-structure counters. Instances live in {@link com.middragonfly.sablelimits.storage.LimitsSavedData}
 * and are updated incrementally on every block change inside the Sable plot.
 */
public final class StructureCounts {

    public final ResourceLocation dimension;
    public final UUID subLevelId;
    public final Object2IntOpenHashMap<Block> counts = new Object2IntOpenHashMap<>();
    public int totalBlocks;
    public long lastUpdated;

    public StructureCounts(final ResourceLocation dimension, final UUID subLevelId, final long lastUpdated) {
        this.dimension = dimension;
        this.subLevelId = subLevelId;
        this.lastUpdated = lastUpdated;
    }

    public String key() {
        return key(this.dimension, this.subLevelId);
    }

    public static String key(final ResourceLocation dimension, final UUID subLevelId) {
        return dimension + "|" + subLevelId;
    }

    public int get(final Block block) {
        return this.counts.getInt(block);
    }

    public void add(final Block block, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.counts.addTo(block, amount);
        this.totalBlocks += amount;
    }

    public void remove(final Block block, final int amount) {
        if (amount <= 0) {
            return;
        }
        final int current = this.counts.getInt(block);
        final int removed = Math.min(current, amount);
        if (removed <= 0) {
            return;
        }
        final int left = current - removed;
        if (left <= 0) {
            this.counts.removeInt(block);
        } else {
            this.counts.put(block, left);
        }
        this.totalBlocks -= removed;
        if (this.totalBlocks < 0) {
            this.totalBlocks = 0;
        }
    }

    public void addBlock(final Block block) {
        if (block == null) {
            return;
        }
        this.add(block, 1);
    }

    public void removeBlock(final Block block) {
        if (block == null) {
            return;
        }
        this.remove(block, 1);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("dimension", this.dimension.toString());
        tag.putUUID("subLevel", this.subLevelId);
        tag.putInt("total", this.totalBlocks);
        tag.putLong("lastUpdated", this.lastUpdated);

        final ListTag blocks = new ListTag();
        for (final Object2IntMap.Entry<Block> entry : this.counts.object2IntEntrySet()) {
            if (entry.getIntValue() <= 0 || entry.getKey() == null) {
                continue;
            }
            final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(entry.getKey());
            if (id == null) {
                continue;
            }
            final CompoundTag blockTag = new CompoundTag();
            blockTag.putString("id", id.toString());
            blockTag.putInt("count", entry.getIntValue());
            blocks.add(blockTag);
        }
        tag.put("blocks", blocks);
        return tag;
    }

    public static StructureCounts load(final CompoundTag tag) {
        final ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null || !tag.hasUUID("subLevel")) {
            return null;
        }
        final StructureCounts counts = new StructureCounts(dimension, tag.getUUID("subLevel"),
                tag.getLong("lastUpdated"));

        final ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        int total = 0;
        for (int i = 0; i < blocks.size(); i++) {
            final CompoundTag blockTag = blocks.getCompound(i);
            final ResourceLocation id = ResourceLocation.tryParse(blockTag.getString("id"));
            if (id == null) {
                continue;
            }
            final Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == null) {
                continue;
            }
            final int count = blockTag.getInt("count");
            if (count <= 0) {
                continue;
            }
            counts.counts.addTo(block, count);
            total += count;
        }
        counts.totalBlocks = total;
        return counts;
    }

    @Override
    public String toString() {
        return "StructureCounts[" + this.key() + ", total=" + this.totalBlocks + ", distinct=" + this.counts.size() + "]";
    }
}
