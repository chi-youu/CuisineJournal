package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.hecco.bountifulfares.block.entity.GristmillBlockEntity",remap=false)
public abstract class BountifulGristmillSpeedMixin {
    @Redirect(method="increaseCraftingProgress",at=@At(value="FIELD",target="Lnet/hecco/bountifulfares/block/entity/GristmillBlockEntity;progress:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){CookingSpeed.write((BlockEntity)owner,"progress",value,"maxProgress");}
}
