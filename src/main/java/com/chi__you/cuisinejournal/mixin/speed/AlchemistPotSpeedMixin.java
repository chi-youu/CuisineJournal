package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.chenjdy.farmers_spell.block.entity.AlchemistPotBlockEntity",remap=false)
public abstract class AlchemistPotSpeedMixin {
    @Redirect(method="processCooking",at=@At(value="FIELD",
            target="Lcom/chenjdy/farmers_spell/block/entity/AlchemistPotBlockEntity;cookTime:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){
        CookingSpeed.write((BlockEntity)owner,"cookTime",value,"cookTimeTotal");
    }
}
