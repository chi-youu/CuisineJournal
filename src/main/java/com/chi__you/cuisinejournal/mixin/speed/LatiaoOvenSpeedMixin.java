package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="com.doggystudio.chirencqr.ltc.server.block.entity.LatiaoOvenBlockEntity",remap=false)
public abstract class LatiaoOvenSpeedMixin {
    @Redirect(method="serverTick",at=@At(value="FIELD",target="Lcom/doggystudio/chirencqr/ltc/server/block/entity/LatiaoOvenBlockEntity;cookingProgress:I",opcode=181),remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner,int value){CookingSpeed.write((BlockEntity)owner,"cookingProgress",value,"cookingTotalTime");}
}
