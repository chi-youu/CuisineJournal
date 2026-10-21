package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.IngredientProvenance;
import com.chi__you.cuisinejournal.food.KaleidoscopeFood;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.RiceBowlRecipe",remap=false)
public abstract class KaleidoscopeRiceBowlMixin {
    @Inject(method="assemble",at=@At("RETURN"),remap=false)
    private void cuisinejournal$inherit(CraftingContainer input,RegistryAccess access,CallbackInfoReturnable<ItemStack> cir){
        ItemStack result=cir.getReturnValue();java.util.List<ItemStack> inputs=new java.util.ArrayList<>();
        for(int i=0;i<input.getContainerSize();i++)if(!input.getItem(i).isEmpty())inputs.add(input.getItem(i));
        IngredientProvenance.sources(result,inputs);
        ItemStack parent=inputs.stream().filter(KaleidoscopeFood::quality).min(java.util.Comparator.comparingInt(item->item.getTag().getInt(KaleidoscopeFood.QUALITY))).orElse(ItemStack.EMPTY);
        var stamp=com.chi__you.cuisinejournal.culinary.MealStamp.read(parent);if(stamp!=null)stamp.write(result);
        else if(!parent.isEmpty())new com.chi__you.cuisinejournal.culinary.MealStamp(
                KaleidoscopeFood.stars(parent),false,KaleidoscopeFood.stars(parent),null,"").write(result);
    }
}
