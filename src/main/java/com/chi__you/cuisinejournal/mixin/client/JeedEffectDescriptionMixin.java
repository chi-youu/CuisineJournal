package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.food.CuisineEffectAdapters;
import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Pseudo
@Mixin(targets="net.mehvahdjukaar.jeed.common.EffectWindowEntry",remap=false)
public abstract class JeedEffectDescriptionMixin {
    @Shadow @Final protected MobEffectInstance effect;
    @Inject(method="getDescription()Ljava/util/List;",at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$description(CallbackInfoReturnable<List<FormattedText>> cir) {
        var id=BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
        String key=CuisineEffectAdapters.descriptionKey(id,true);
        if(key!=null)cir.setReturnValue(Minecraft.getInstance().font.getSplitter().splitLines(
                Component.translatable(CuisineFoodClientState.active() ? key : effect.getEffect().getDescriptionId()+".description"),
                140,net.minecraft.network.chat.Style.EMPTY));
    }
}
