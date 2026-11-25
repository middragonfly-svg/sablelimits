package com.middragonfly.sablelimits.perms;

import com.middragonfly.sablelimits.SableLimits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * NeoForge PermissionAPI nodes. LuckPerms automatically acts as the permission handler and maps
 * {@link PermissionTypes#STRING} nodes to LuckPerms meta, which is why limit.size works with
 * {@code /lp group sponsor meta set sablelimits.limit.size 6000}.
 */
public final class Permissions {

    public static final PermissionNode<Boolean> BYPASS_BLOCKS = new PermissionNode<>(
            SableLimits.MODID,
            "bypass.blocks",
            PermissionTypes.BOOLEAN,
            (player, uuid, contexts) -> false
    );

    public static final PermissionNode<Boolean> BYPASS_SIZE = new PermissionNode<>(
            SableLimits.MODID,
            "bypass.size",
            PermissionTypes.BOOLEAN,
            (player, uuid, contexts) -> false
    );

    public static final PermissionNode<String> LIMIT_SIZE = new PermissionNode<>(
            SableLimits.MODID,
            "limit.size",
            PermissionTypes.STRING,
            (player, uuid, contexts) -> ""
    );

    private Permissions() {
    }

    public static void onGatherNodes(final PermissionGatherEvent.Nodes event) {
        event.addNodes(BYPASS_BLOCKS, BYPASS_SIZE, LIMIT_SIZE);
    }

    public static boolean bypassBlocks(final Player player) {
        final Boolean value = queryBoolean(player, BYPASS_BLOCKS);
        return value != null && value;
    }

    public static boolean bypassSize(final Player player) {
        final Boolean value = queryBoolean(player, BYPASS_SIZE);
        return value != null && value;
    }

    /**
     * @return the player's personal block limit, or {@code fallback} when no valid meta value is set.
     *         A non-positive fallback means "unlimited".
     */
    public static int sizeLimit(final Player player, final int fallback) {
        final int defaultValue = fallback > 0 ? fallback : Integer.MAX_VALUE;
        if (!(player instanceof final ServerPlayer serverPlayer)) {
            return defaultValue;
        }

        try {
            final String value = PermissionAPI.getPermission(serverPlayer, LIMIT_SIZE);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            final int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                return defaultValue;
            }
            return parsed;
        } catch (final Throwable ignored) {
            // No permission handler yet (e.g. very early startup), or malformed metadata.
            return defaultValue;
        }
    }

    private static Boolean queryBoolean(final Player player, final PermissionNode<Boolean> node) {
        if (!(player instanceof final ServerPlayer serverPlayer)) {
            return false;
        }
        try {
            return PermissionAPI.getPermission(serverPlayer, node);
        } catch (final Throwable ignored) {
            return false;
        }
    }
}
