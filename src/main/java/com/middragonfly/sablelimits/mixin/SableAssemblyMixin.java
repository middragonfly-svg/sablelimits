package com.middragonfly.sablelimits.mixin;

import com.middragonfly.sablelimits.limits.LimitManager;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Seeds the cache with the exact contents of a freshly assembled Sable structure,
 * so the first placement after assembly does not need a full scan.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SableAssemblyMixin {

    @Inject(method = "assembleBlocks", at = @At("RETURN"))
    private static void sablelimits$seedCache(final ServerLevel level,
                                              final BlockPos anchor,
                                              final Iterable<BlockPos> blocks,
                                              final BoundingBox3ic bounds,
                                              final CallbackInfoReturnable<ServerSubLevel> cir) {
        final ServerSubLevel subLevel = cir.getReturnValue();
        if (subLevel == null) {
            return;
        }
        com.middragonfly.sablelimits.SableLimits.LOGGER.debug("Заполнение кэша после assembleBlocks");
        final LimitManager manager = LimitManager.get();
        if (manager != null) {
            manager.onAssembled(subLevel);
        }
    }
}
