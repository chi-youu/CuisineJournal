package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodConfig;
import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import com.chi__you.cuisinejournal.food.CuisineFoodService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "setDayTime", at = @At("HEAD"))
    private void cuisinejournal$skipNight(long newDayTime, CallbackInfo ci) {
        if (!CuisineFoodRuntime.active() || !CuisineFoodConfig.PASS_TICKS_DURING_NIGHT.get()) {
            return;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        long currentDayTime = level.getLevelData().getDayTime();
        long passedTicks = newDayTime - currentDayTime;
        // Normal daylight advances by one tick. A sleep skip jumps forward by
        // many ticks and is the only setDayTime path that should age food.
        if (passedTicks <= 1L || passedTicks > 24000L) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            CuisineFoodService.skipNight(player, passedTicks);
        }
    }
}
