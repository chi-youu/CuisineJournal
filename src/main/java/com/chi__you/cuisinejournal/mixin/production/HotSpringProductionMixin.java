package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.HotSpringProduction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.renyigesai.immortalers_delight.fluid.HotSpringFluidsBlock",remap=false)
public abstract class HotSpringProductionMixin {
    @Inject(method="craftTick",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(Level level,BlockPos pos,CallbackInfo ci){HotSpringProduction.begin(level,pos);}
    @Inject(method="craftTick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(Level level,BlockPos pos,CallbackInfo ci){HotSpringProduction.end(level,pos);}
    @Redirect(method="craftTick",at=@At(value="INVOKE",target="Lcom/renyigesai/immortalers_delight/util/ItemUtils;splitIntoStacks(Lnet/minecraft/world/item/ItemStack;I)Ljava/util/List;",remap=false),remap=false)
    private java.util.List<ItemStack> cuisinejournal$result(ItemStack item,int count) {
        java.util.List<ItemStack> outputs=new java.util.ArrayList<>();
        ItemStack batch=HotSpringProduction.output(item.copyWithCount(count),count);
        for(int left=count;left>0;){int amount=Math.min(left,item.getMaxStackSize());outputs.add(batch.copyWithCount(amount));left-=amount;}
        return outputs;
    }
    @Inject(method="m_7892_",at=@At("HEAD"),remap=false)
    private void cuisinejournal$enter(net.minecraft.world.level.block.state.BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.Entity entity,CallbackInfo ci){if(!level.isClientSide&&entity instanceof net.minecraft.world.entity.item.ItemEntity item)com.chi__you.cuisinejournal.tracking.HotSpringSource.mark(item);}
}
