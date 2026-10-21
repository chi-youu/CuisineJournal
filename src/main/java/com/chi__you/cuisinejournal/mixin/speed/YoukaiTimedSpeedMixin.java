package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.base.TimedRecipeBlockEntity", remap=false)
public abstract class YoukaiTimedSpeedMixin {
    @Redirect(method="tick", at=@At(value="FIELD", target="Ldev/xkmc/youkaishomecoming/content/pot/base/TimedRecipeBlockEntity;recipeProgress:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "recipeProgress", value, "totalTime");
    }
}
