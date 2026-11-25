package com.middragonfly.sablelimits.mixin;

import com.middragonfly.sablelimits.limits.LimitManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks placement of limited blocks onto Sable structures before anything is consumed.
 * Covers the player's hand and Create's Deployer (which calls {@code ItemStack#useOn}).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void sablelimits$checkPlacement(final BlockPlaceContext context,
                                            final CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide() || !(context.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        final LimitManager manager = LimitManager.get();
        if (manager == null) {
            return;
        }

        final Player player = context.getPlayer();
        final ServerPlayer serverPlayer = player instanceof final ServerPlayer sp ? sp : null;
        final BlockItem item = (BlockItem) (Object) this;

        final Component denial = manager.checkPlacement(level, serverPlayer, context.getClickedPos(), item.getBlock());
        if (denial != null) {
            LimitManager.notifyPlacementDenied(player, denial);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
