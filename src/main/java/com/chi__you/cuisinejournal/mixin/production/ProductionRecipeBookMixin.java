package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ProductionRecipeBookMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handlePlaceRecipe",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V",shift=At.Shift.AFTER))
    private void cuisinejournal$begin(ServerboundPlaceRecipePacket p,CallbackInfo ci){ProductionTracker.beginMenu(player,player.containerMenu);}
    @Inject(method="handlePlaceRecipe",at=@At("RETURN"))
    private void cuisinejournal$end(ServerboundPlaceRecipePacket p,CallbackInfo ci){ProductionTracker.endOperation(player);}
}
