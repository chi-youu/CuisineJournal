package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.bmt.kaleidoscope_world_liquor.block.entity.FreezerBlockEntity",remap=false)
public abstract class KaleidoscopeWorldFreezerMixin {
    @ModifyArg(method="insertItem",at=@At(value="INVOKE",target="Lnet/minecraftforge/items/ItemStackHandler;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V"),index=1,remap=false)
    private ItemStack cuisinejournal$lastInput(ItemStack stack){return KaleidoscopeProduction.insertedVariant(stack);}
    @Inject(method="tryStartCrafting",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(CallbackInfoReturnable<Boolean> cir){KaleidoscopeProduction.begin((BlockEntity)(Object)this,null);}
    @Inject(method="tryStartCrafting",at=@At("RETURN"),remap=false)
    private void cuisinejournal$after(CallbackInfoReturnable<Boolean> cir){KaleidoscopeProduction.endVirtual((BlockEntity)(Object)this,cir.getReturnValue());}
    @Redirect(method="extractOutput",at=@At(value="INVOKE",target="Lcom/bmt/kaleidoscope_world_liquor/crafting/FreezerRecipe;m_8043_(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"),remap=false)
    private ItemStack cuisinejournal$result(@Coerce Object recipe,RegistryAccess access){
        ItemStack result=((Recipe<?>)recipe).getResultItem(access).copyWithCount(1);
        KaleidoscopeProduction.output((BlockEntity)(Object)this,result,true);
        var actor=ProductionTracker.actor();if(actor!=null)KaleidoscopeProduction.claim(actor,result);return result;
    }
}
