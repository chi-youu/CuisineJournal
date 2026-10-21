package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.steamer.RackItemData", remap=false)
public abstract class SteamSpeedMixin {
    @Shadow public double progress;
    @Redirect(method="tick", at=@At(value="FIELD", target="Ldev/xkmc/youkaishomecoming/content/pot/steamer/RackItemData;progress:D", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, double value, @Coerce Object be, Level level, double heat) {
        var access=(SteamSpeedMixin)owner;
        double before=access.progress;
        access.progress=value>before ? before+CookingSpeed.scale((BlockEntity)be,value-before) : value;
    }
}
