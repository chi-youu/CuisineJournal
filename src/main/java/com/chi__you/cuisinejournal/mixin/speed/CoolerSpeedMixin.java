package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="com.renyigesai.immortalers_delight.block.enchantal_cooler.EnchantalCoolerBlockEntity", remap=false)
public abstract class CoolerSpeedMixin {
    @Redirect(method="craftItem", at=@At(value="FIELD", target="Lcom/renyigesai/immortalers_delight/block/enchantal_cooler/EnchantalCoolerBlockEntity;cookingTotalTime:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "cookingTotalTime", value, 100);
    }
}
