package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="teamrazor.deepaether.entity.block.CombinerBlockEntity", remap=false)
public abstract class CombinerSpeedMixin {
    @Redirect(method="serverTick", at=@At(value="FIELD", target="Lteamrazor/deepaether/entity/block/CombinerBlockEntity;processingProgress:I", opcode=181), remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "processingProgress", value, "processingTotalTime");
    }
}
