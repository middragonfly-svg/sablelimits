package com.middragonfly.sablelimits.storage;

import com.middragonfly.sablelimits.limits.StructureCounts;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent cache of per-sub-level block counters. Stored in the overworld data folder as
 * {@code data/sablelimits.dat}. Deleting a record never removes the limit itself: the structure is
 * simply re-scanned the next time it is touched.
 */
public final class LimitsSavedData extends SavedData {

    public static final String FILE_ID = "sablelimits";

    private final Map<String, StructureCounts> structures = new LinkedHashMap<>();

    public Map<String, StructureCounts> structures() {
        return this.structures;
    }

    public static LimitsSavedData getOrLoad(final MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(LimitsSavedData::new, LimitsSavedData::load), FILE_ID);
    }

    private static LimitsSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final LimitsSavedData data = new LimitsSavedData();
        final ListTag list = tag.getList("structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final StructureCounts counts = StructureCounts.load(list.getCompound(i));
            if (counts != null) {
                data.structures.put(counts.key(), counts);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (final StructureCounts counts : this.structures.values()) {
            list.add(counts.save());
        }
        tag.put("structures", list);
        tag.putInt("version", 1);
        return tag;
    }
}
