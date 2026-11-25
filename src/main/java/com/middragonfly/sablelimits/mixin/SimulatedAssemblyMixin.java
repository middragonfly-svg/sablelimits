package com.middragonfly.sablelimits.mixin;

import com.middragonfly.sablelimits.limits.LimitManager;
import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Simulated/Aeronautics gathers blocks with its own contraption implementation and then calls
 * {@link SubLevelAssemblyHelper#assembleBlocks}. Validating right before that call aborts assembly
 * (via the AssemblyException that Physics Assembler and Swivel Bearing already handle) without
 * touching the world.
 * <p>
 * {@link Pseudo} keeps this mixin optional for Sable installations without Simulated.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.SimAssemblyHelper", remap = false)
public class SimulatedAssemblyMixin {

    @Redirect(
            method = "assembleFromSingleBlock",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper;assembleBlocks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
            )
    )
    private static ServerSubLevel sablelimits$validateAndAssemble(final ServerLevel level,
                                                                  final BlockPos anchor,
                                                                  final Iterable<BlockPos> blocks,
                                                                  final BoundingBox3ic bounds) throws AssemblyException {
        com.middragonfly.sablelimits.SableLimits.LOGGER.debug("Перехват сборки Sable (Simulated)");
        LimitManager.validateSableAssembly(level, anchor, blocks);
        return SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
    }
}
