package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity",remap=false)
public abstract class KaleidoscopeBarrelSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lcom/github/ysbbbbbb/kaleidoscopetavern/blockentity/brew/BarrelBlockEntity;brewTime:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){
        int before=com.chi__you.cuisinejournal.progression.CookingSpeed.number(owner,"brewTime");
        int adjusted=before>0&&value==before-97?KaleidoscopeSpeed.countdown((BlockEntity)owner,"brewTime",value):value;
        KaleidoscopeSpeed.set((BlockEntity)owner,"brewTime",adjusted);
    }
}
