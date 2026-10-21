package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="auviotre.enigmatic.delicacy.contents.entities.EtheriumStoveBlockEntity", remap=false)
public abstract class EtheriumFurnaceSpeedMixin {
    @Redirect(method="cookingTick", at=@At(value="FIELD", target="Lauviotre/enigmatic/delicacy/contents/entities/EtheriumStoveBlockEntity;furnaceTime:I", opcode=181), remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "furnaceTime", value, "furnaceTimeTotal");
    }
}
