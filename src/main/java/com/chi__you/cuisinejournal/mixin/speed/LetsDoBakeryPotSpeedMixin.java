package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity",remap=false)
public abstract class LetsDoBakeryPotSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/bakery/core/block/entity/SmallCookingPotBlockEntity;cookingTime:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"cookingTime",value,300);}
}
