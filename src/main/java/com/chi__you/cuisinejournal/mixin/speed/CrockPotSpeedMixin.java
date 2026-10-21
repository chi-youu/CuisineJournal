package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="com.sihenzhang.crockpot.block.entity.CrockPotBlockEntity",remap=false)
public abstract class CrockPotSpeedMixin {
    @Redirect(method="serverTick",at=@At(value="FIELD",target="Lcom/sihenzhang/crockpot/block/entity/CrockPotBlockEntity;cookingTime:I",opcode=181),remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner,int value){CookingSpeed.write((BlockEntity)owner,"cookingTime",value,"cookingTotalTime");}
}
