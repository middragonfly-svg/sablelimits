package com.middragonfly.sablelimits;

import com.middragonfly.sablelimits.command.SableLimitsCommand;
import com.middragonfly.sablelimits.config.ConfigManager;
import com.middragonfly.sablelimits.limits.LimitManager;
import com.middragonfly.sablelimits.perms.Permissions;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SableLimits.MODID)
public final class SableLimits {

    public static final String MODID = "sablelimits";
    public static final Logger LOGGER = LoggerFactory.getLogger("SableLimits");

    public SableLimits(final IEventBus modEventBus, final ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(SableLimits::onPermissionGather);
        NeoForge.EVENT_BUS.addListener(SableLimits::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SableLimits::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SableLimits::onServerStopping);
        NeoForge.EVENT_BUS.addListener(SableLimits::onServerTick);
        NeoForge.EVENT_BUS.addListener(SableLimits::onSubLevelContainerReady);
    }

    private static void onPermissionGather(final PermissionGatherEvent.Nodes event) {
        Permissions.onGatherNodes(event);
    }

    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        SableLimitsCommand.register(event);
    }

    private static void onServerStarted(final ServerStartedEvent event) {
        // Block registries and block tags are only complete once the server data pack reload
        // has finished, which is why the rule set is compiled here rather than in the constructor.
        ConfigManager.loadOnStartup();
        LimitManager.init(event.getServer());
    }

    private static void onServerStopping(final ServerStoppingEvent event) {
        LimitManager.shutdown();
    }

    private static void onServerTick(final ServerTickEvent.Post event) {
        final LimitManager manager = LimitManager.get();
        if (manager != null) {
            manager.tick();
        }
    }

    private static void onSubLevelContainerReady(final ForgeSableSubLevelContainerReadyEvent event) {
        LimitManager.onContainerReady(event.getContainer());
    }
}
