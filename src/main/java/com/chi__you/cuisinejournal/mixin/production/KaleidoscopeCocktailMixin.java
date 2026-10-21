package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.item.ShakerItem",remap=false)
public abstract class KaleidoscopeCocktailMixin {
    @Inject(method="pourResult",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(Level level,ItemStack shaker,BlockPos pos,net.minecraft.world.level.block.state.BlockState state,CallbackInfo ci){
        Object raw=HostReflection.invoke(shaker.getItem(),"getResult",shaker);
        if(raw instanceof ItemStack item){cuisinejournal$result=item.copy();cuisinejournal$shaker=shaker.copy();}
    }
    @Unique private ItemStack cuisinejournal$result=ItemStack.EMPTY;
    @Unique private ItemStack cuisinejournal$shaker=ItemStack.EMPTY;
    @Inject(method="pourResult",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(Level level,ItemStack shaker,BlockPos pos,net.minecraft.world.level.block.state.BlockState state,CallbackInfo ci){
        KaleidoscopeCocktails.pour(level,pos,cuisinejournal$shaker,cuisinejournal$result);
        cuisinejournal$result=ItemStack.EMPTY;cuisinejournal$shaker=ItemStack.EMPTY;
    }
}
