package com.middragonfly.sablelimits.limits;

import com.middragonfly.sablelimits.SableLimits;
import com.middragonfly.sablelimits.SableLimitsConfig;
import com.middragonfly.sablelimits.config.ConfigManager;
import com.middragonfly.sablelimits.notify.Notifier;
import com.middragonfly.sablelimits.perms.Permissions;
import com.middragonfly.sablelimits.storage.LimitsSavedData;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Central cache and enforcement logic.
 */
public final class LimitManager {

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private static LimitManager instance;

    private final MinecraftServer server;
    private final LimitsSavedData savedData;
    private long lastCleanupMillis;
    private long tickCounter;

    private LimitManager(final MinecraftServer server, final LimitsSavedData savedData) {
        this.server = server;
        this.savedData = savedData;
        this.lastCleanupMillis = System.currentTimeMillis();
    }

    public static void init(final MinecraftServer server) {
        if (instance != null && instance.server == server) {
            return;
        }
        instance = new LimitManager(server, LimitsSavedData.getOrLoad(server));
        SableLimits.LOGGER.info("Кэш SableLimits загружен: {} структур", instance.savedData.structures().size());
        instance.cleanupExpired();
    }

    public static void shutdown() {
        instance = null;
    }

    public static LimitManager get() {
        return instance;
    }

    public static void onContainerReady(final SubLevelContainer container) {
        if (!(container instanceof final ServerSubLevelContainer serverContainer)) {
            return;
        }
        serverContainer.addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
                // UNLOADED must keep the cache: this is exactly the restart/chunk-unload case that
                // must not reset the counters. Only a real removal (disassembly/despawn) drops it.
                if (reason != SubLevelRemovalReason.REMOVED) {
                    return;
                }
                final LimitManager manager = instance;
                if (manager != null) {
                    manager.remove(subLevel);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Lookup / scanning
    // ------------------------------------------------------------------

    public StructureCounts getOrScan(final ServerSubLevel subLevel) {
        if (subLevel.getUniqueId() == null) {
            return null;
        }

        final String key = StructureCounts.key(subLevel.getLevel().dimension().location(), subLevel.getUniqueId());
        StructureCounts counts = this.savedData.structures().get(key);
        if (counts == null) {
            counts = Counting.scan(subLevel);
            this.savedData.structures().put(key, counts);
            this.savedData.setDirty();
            SableLimits.LOGGER.debug("Первичный подсчёт {}: {} блоков", key, counts.totalBlocks);
        }
        return counts;
    }

    public void onAssembled(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) {
            return;
        }
        final StructureCounts counts = Counting.scan(subLevel);
        this.savedData.structures().put(counts.key(), counts);
        this.savedData.setDirty();
        SableLimits.LOGGER.debug("Кэш после сборки {}: {} блоков", counts.key(), counts.totalBlocks);
    }

    public void remove(final SubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) {
            return;
        }
        final String key = StructureCounts.key(subLevel.getLevel().dimension().location(), subLevel.getUniqueId());
        if (this.savedData.structures().remove(key) != null) {
            this.savedData.setDirty();
        }
    }

    // ------------------------------------------------------------------
    // Placement checks (hand / deployer)
    // ------------------------------------------------------------------

    /**
     * @return null when placement is allowed, otherwise the message to show to the player.
     */
    public Component checkPlacement(final ServerLevel level,
                                    final ServerPlayer player,
                                    final BlockPos pos,
                                    final Block newBlock) {
        final SableLimitsConfig config = ConfigManager.get();
        if (!config.enabled || level.isClientSide || newBlock == null) {
            return null;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
            return null;
        }

        final StructureCounts counts = this.getOrScan(serverSubLevel);
        if (counts == null) {
            return null;
        }

        final BlockState oldState = level.getBlockState(pos);
        final Block oldBlock = oldState.isAir() ? null : oldState.getBlock();

        final boolean bypassBlocks = player != null && Permissions.bypassBlocks(player);
        if (!bypassBlocks) {
            for (final RuleSet.Rule rule : config.rules.sableRules) {
                if (!rule.matches(newBlock) && !rule.matches(oldBlock)) {
                    continue;
                }
                final int after = rule.countAfter(counts.counts, oldBlock, newBlock);
                if (after > rule.limit) {
                    return this.blockLimitMessage(rule, after, counts);
                }
            }
        }

        final boolean bypassSize = player != null && Permissions.bypassSize(player);
        if (!bypassSize) {
            final int limit = Permissions.sizeLimit(player, config.defaultMaxBlocks);
            if (limit > 0 && limit != Integer.MAX_VALUE) {
                final int afterTotal = counts.totalBlocks - (oldBlock == null ? 0 : 1) + 1;
                if (afterTotal > limit) {
                    return this.sizeLimitMessage(afterTotal, limit);
                }
            }
        }

        counts.lastUpdated = System.currentTimeMillis();
        this.savedData.setDirty();
        return null;
    }

    private Component blockLimitMessage(final RuleSet.Rule rule, final int after, final StructureCounts counts) {
        final SableLimitsConfig config = ConfigManager.get();
        return Notifier.component(config.messages.blockLimit, Map.of(
                "block", rule.display,
                "current", Integer.toString(after),
                "limit", Integer.toString(rule.limit),
                "total", Integer.toString(counts.totalBlocks)
        ));
    }

    private Component sizeLimitMessage(final int after, final int limit) {
        final SableLimitsConfig config = ConfigManager.get();
        return Notifier.component(config.messages.sizeLimit, Map.of(
                "current", Integer.toString(after),
                "limit", Integer.toString(limit),
                "total", Integer.toString(after)
        ));
    }

    // ------------------------------------------------------------------
    // Incremental updates
    // ------------------------------------------------------------------

    public void onBlockChanged(final ServerLevel level, final LevelChunk chunk,
                               final BlockState oldState, final BlockState newState) {
        if (level.isClientSide || oldState == newState) {
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        final LevelPlot plot = container.getPlot(chunk.getPos().x, chunk.getPos().z);
        if (plot == null) {
            return;
        }

        final SubLevel subLevel = plot.getSubLevel();
        if (subLevel == null || subLevel.getUniqueId() == null) {
            return;
        }

        final StructureCounts counts = this.savedData.structures()
                .get(StructureCounts.key(level.dimension().location(), subLevel.getUniqueId()));
        if (counts == null) {
            // Not scanned yet; the first scan will see the final state. Never scan from inside chunk mutation.
            return;
        }

        final Block oldBlock = oldState.isAir() ? null : oldState.getBlock();
        final Block newBlock = newState.isAir() ? null : newState.getBlock();
        if (oldBlock == newBlock) {
            counts.lastUpdated = System.currentTimeMillis();
            return;
        }

        if (oldBlock != null) {
            counts.removeBlock(oldBlock);
        }
        if (newBlock != null) {
            counts.addBlock(newBlock);
        }
        counts.lastUpdated = System.currentTimeMillis();
        this.savedData.setDirty();
    }

    // ------------------------------------------------------------------
    // Assembly checks
    // ------------------------------------------------------------------

    /**
     * Called from a redirect around {@code SubLevelAssemblyHelper.assembleBlocks} for Sable structures.
     * Throwing AssemblyException is the supported way to abort assembly in Create/Simulated.
     */
    public static void validateSableAssembly(final ServerLevel level, final BlockPos anchor,
                                             final Iterable<BlockPos> blocks) throws AssemblyException {
        final SableLimitsConfig config = ConfigManager.get();
        if (!config.enabled) {
            return;
        }

        final Counting.Counts counts = Counting.countPositions(level, blocks);
        SableLimits.LOGGER.debug("Проверка сборки Sable: total={}, правил={}", counts.total(),
                config.rules.sableRules.size());
        if (counts.total() <= 0) {
            return;
        }

        final ServerPlayer actor = Notifier.findNearestPlayer(level, anchor, config.assemblyActorRadius);
        final boolean bypassBlocks = actor != null && Permissions.bypassBlocks(actor);
        final boolean bypassSize = actor != null && Permissions.bypassSize(actor);

        if (!bypassBlocks) {
            for (final RuleSet.Rule rule : config.rules.sableRules) {
                final int after = Counting.countMatching(rule, counts.byBlock());
                if (after > rule.limit) {
                    final Component message = Notifier.component(config.messages.assemblyBlockLimit, Map.of(
                            "block", rule.display,
                            "current", Integer.toString(after),
                            "limit", Integer.toString(rule.limit),
                            "total", Integer.toString(counts.total())
                    ));
                    Notifier.notifyAssembly(level, anchor, message);
                    throw new AssemblyException(message);
                }
            }
        }

        if (!bypassSize) {
            int limit = config.defaultMaxBlocks;
            if (config.assemblyUsesPlayerLimit && actor != null) {
                limit = Permissions.sizeLimit(actor, config.defaultMaxBlocks);
            }
            if (limit > 0 && limit != Integer.MAX_VALUE && counts.total() > limit) {
                final Component message = Notifier.component(config.messages.assemblySizeLimit, Map.of(
                        "current", Integer.toString(counts.total()),
                        "limit", Integer.toString(limit),
                        "total", Integer.toString(counts.total())
                ));
                Notifier.notifyAssembly(level, anchor, message);
                throw new AssemblyException(message);
            }
        }
    }

    /**
     * Called at the end of Create's {@code Contraption.searchMovedStructure} for regular contraptions.
     */
    public static void validateCreateContraption(final Level level, final Contraption contraption,
                                                 final BlockPos anchor) throws AssemblyException {
        final SableLimitsConfig config = ConfigManager.get();
        if (!config.enabled || !(level instanceof final ServerLevel serverLevel)) {
            return;
        }
        if (config.rules.createRules.isEmpty()) {
            return;
        }

        final List<BlockState> states = new ArrayList<>(contraption.getBlocks().size());
        contraption.getBlocks().values().forEach(info -> states.add(info.state()));
        final Counting.Counts counts = Counting.countStates(states);
        if (counts.total() <= 0) {
            return;
        }

        final BlockPos origin = anchor != null ? anchor : contraption.anchor;
        final ServerPlayer actor = origin == null ? null
                : Notifier.findNearestPlayer(serverLevel, origin, config.assemblyActorRadius);
        if (actor != null && Permissions.bypassBlocks(actor)) {
            return;
        }

        final BlockPos messagePos = origin != null ? origin : BlockPos.ZERO;
        for (final RuleSet.Rule rule : config.rules.createRules) {
            final int after = Counting.countMatching(rule, counts.byBlock());
            if (after > rule.limit) {
                final Component message = Notifier.component(config.messages.assemblyBlockLimit, Map.of(
                        "block", rule.display,
                        "current", Integer.toString(after),
                        "limit", Integer.toString(rule.limit),
                        "total", Integer.toString(counts.total())
                ));
                Notifier.notifyAssembly(serverLevel, messagePos, message);
                throw new AssemblyException(message);
            }
        }
    }

    // ------------------------------------------------------------------
    // TTL
    // ------------------------------------------------------------------

    public void cleanupExpired() {
        final SableLimitsConfig config = ConfigManager.get();
        if (config.cacheExpirationDays <= 0) {
            return;
        }

        final long cutoff = System.currentTimeMillis() - config.cacheExpirationDays * DAY_MILLIS;
        final Iterator<Map.Entry<String, StructureCounts>> iterator =
                this.savedData.structures().entrySet().iterator();
        int removed = 0;
        while (iterator.hasNext()) {
            final StructureCounts counts = iterator.next().getValue();
            if (counts.lastUpdated > 0 && counts.lastUpdated < cutoff) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            this.savedData.setDirty();
            SableLimits.LOGGER.info("TTL: удалено {} неактивных записей кэша (старше {} дн.)",
                    removed, config.cacheExpirationDays);
        }
        this.lastCleanupMillis = System.currentTimeMillis();
    }

    public void tick() {
        this.tickCounter++;
        if (this.tickCounter % 20 != 0) {
            return;
        }

        final int hours = ConfigManager.get().cleanupIntervalHours;
        if (hours <= 0) {
            return;
        }

        final long interval = hours * 60L * 60L * 1000L;
        if (System.currentTimeMillis() - this.lastCleanupMillis >= interval) {
            this.cleanupExpired();
        }
    }

    public int cachedStructures() {
        return this.savedData.structures().size();
    }

    public Map<String, StructureCounts> structures() {
        return this.savedData.structures();
    }

    public static void notifyPlacementDenied(final Player player, final Component message) {
        if (player instanceof final ServerPlayer serverPlayer) {
            Notifier.notifyPlacement(serverPlayer, message);
        }
    }
}
