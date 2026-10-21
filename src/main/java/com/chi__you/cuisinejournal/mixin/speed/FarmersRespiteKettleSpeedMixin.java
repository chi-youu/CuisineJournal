package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="umpaz.farmersrespite.common.block.entity.KettleBlockEntity",remap=false)
public abstract class FarmersRespiteKettleSpeedMixin {
    @Redirect(method="processBrewing",at=@At(value="FIELD",target="Lumpaz/farmersrespite/common/block/entity/KettleBlockEntity;brewTime:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){CookingSpeed.write((BlockEntity)owner,"brewTime",value,"brewTimeTotal");}
}
