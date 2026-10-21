package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="com.freefish.torchesbecomesunlight.server.block.blockentity.StewPotBlockEntity", remap=false)
public abstract class StewPotSpeedMixin {
    @Redirect(method="processCooking", at=@At(value="FIELD", target="Lcom/freefish/torchesbecomesunlight/server/block/blockentity/StewPotBlockEntity;cookTime:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "cookTime", value, "cookTimeTotal");
    }
}
