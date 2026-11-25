package com.middragonfly.sablelimits.mixin;

import com.middragonfly.sablelimits.limits.LimitManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the per-structure counters in sync when a block inside a Sable plot changes.
 * This is the only place that needs to run for explosions, pistons, drills, deployers, etc.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkSetBlockMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sablelimits$afterSetBlockState(final BlockPos pos,
                                                final BlockState newState,
                                                final boolean isMoving,
                                                final CallbackInfoReturnable<BlockState> cir) {
        final BlockState oldState = cir.getReturnValue();
        if (oldState == null || oldState == newState) {
            return;
        }

        final Level level = ((LevelChunk) (Object) this).getLevel();
        if (!(level instanceof final ServerLevel serverLevel)) {
            return;
        }

        final LimitManager manager = LimitManager.get();
        if (manager != null) {
            manager.onBlockChanged(serverLevel, (LevelChunk) (Object) this, oldState, newState);
        }
    }
}
