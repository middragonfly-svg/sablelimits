package com.middragonfly.sablelimits.notify;

import com.middragonfly.sablelimits.SableLimitsConfig;
import com.middragonfly.sablelimits.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formats and delivers user-facing messages. Placement denials use the action bar (rate limited),
 * assembly denials go to chat of every player near the structure.
 */
public final class Notifier {

    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

    private Notifier() {
    }

    public static Component component(final String template, final Map<String, String> placeholders) {
        String result = template == null ? "" : template;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return Component.literal(result.replace('&', '\u00a7'));
    }

    public static void notifyPlacement(final ServerPlayer player, final Component message) {
        if (player == null || player.connection == null) {
            return;
        }

        final SableLimitsConfig config = ConfigManager.get();
        final long now = player.serverLevel().getGameTime();
        final Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && config.messageCooldownTicks > 0 && now - last < config.messageCooldownTicks) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        player.displayClientMessage(message, true);
    }

    public static void notifyAssembly(final ServerLevel level, final BlockPos anchor, final Component message) {
        final int radius = Math.max(0, ConfigManager.get().assemblyActorRadius);
        for (final ServerPlayer player : nearbyPlayers(level, anchor, radius)) {
            player.sendSystemMessage(message);
        }
    }

    public static ServerPlayer findNearestPlayer(final ServerLevel level, final BlockPos pos, final int radius) {
        final List<ServerPlayer> players = nearbyPlayers(level, pos, radius);
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        final Vec3 center = Vec3.atCenterOf(pos);
        for (final ServerPlayer player : players) {
            final double distance = player.distanceToSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static List<ServerPlayer> nearbyPlayers(final ServerLevel level, final BlockPos pos, final int radius) {
        final Vec3 center = Vec3.atCenterOf(pos);
        final double radiusSq = (double) radius * radius;
        return level.players().stream()
                .filter(player -> player.distanceToSqr(center) <= radiusSq)
                .toList();
    }
}
