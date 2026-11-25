package com.middragonfly.sablelimits.mixin;

import com.middragonfly.sablelimits.limits.LimitManager;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Regular Create contraptions (bearings, pistons, pulleys, gantries, cart assemblers, trains)
 * cannot be modified while assembled, so their limits are checked once when the structure has
 * been gathered and before any block is removed from the world.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionAssemblyMixin {

    @Inject(method = "searchMovedStructure", at = @At("RETURN"))
    private void sablelimits$checkCreateAssembly(final Level level,
                                                 final BlockPos pos,
                                                 final Direction forcedDirection,
                                                 final CallbackInfoReturnable<Boolean> cir) throws AssemblyException {
        if (!cir.getReturnValueZ()) {
            return;
        }
        LimitManager.validateCreateContraption(level, (Contraption) (Object) this, pos);
    }
}
