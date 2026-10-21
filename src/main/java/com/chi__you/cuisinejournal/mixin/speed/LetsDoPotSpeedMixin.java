package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity",remap=false)
public abstract class LetsDoPotSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/farm_and_charm/core/block/entity/CookingPotBlockEntity;cookingTime:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"cookingTime",value,900);}
}
