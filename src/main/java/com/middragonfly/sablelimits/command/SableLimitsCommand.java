package com.middragonfly.sablelimits.command;

import com.middragonfly.sablelimits.SableLimits;
import com.middragonfly.sablelimits.SableLimitsConfig;
import com.middragonfly.sablelimits.config.ConfigManager;
import com.middragonfly.sablelimits.limits.LimitManager;
import com.middragonfly.sablelimits.notify.Notifier;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

public final class SableLimitsCommand {

    private SableLimitsCommand() {
    }

    public static void register(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(SableLimits.MODID)
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(SableLimitsCommand::reload))
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(SableLimitsCommand::status)));
    }

    private static int reload(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        try {
            final SableLimitsConfig config = ConfigManager.reload();
            final LimitManager manager = LimitManager.get();
            final int cached = manager == null ? 0 : manager.cachedStructures();

            context.getSource().sendSuccess(() -> Notifier.component(config.messages.reloaded, Map.of(
                    "rules", Integer.toString(config.rules.sableRules.size() + config.rules.createRules.size()),
                    "sable", Integer.toString(config.rules.sableRules.size()),
                    "create", Integer.toString(config.rules.createRules.size()),
                    "cached", Integer.toString(cached)
            )), true);
            return 1;
        } catch (final Exception e) {
            SableLimits.LOGGER.error("Не удалось перезагрузить конфиг", e);
            context.getSource().sendFailure(Component.literal("SableLimits: " + e.getMessage()));
            return 0;
        }
    }

    private static int status(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        final SableLimitsConfig config = ConfigManager.get();
        final LimitManager manager = LimitManager.get();
        final int cached = manager == null ? 0 : manager.cachedStructures();

        context.getSource().sendSuccess(() -> Component.literal(
                "SableLimits: enabled=" + config.enabled
                        + ", defaultMaxBlocks=" + config.defaultMaxBlocks
                        + ", TTL=" + config.cacheExpirationDays + "d"
                        + ", правил Sable=" + config.rules.sableRules.size()
                        + ", Create=" + config.rules.createRules.size()
                        + ", структур в кэше=" + cached), false);
        return 1;
    }
}
