package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.recipe.GrillingRecipes;
import com.google.gson.JsonObject;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="cn.breezeth.kaleidoscope_grilling.data.GrillingDataManager",remap=false)
public abstract class GrillingSquidIngredientsMixin {
    @Inject(method="readRoot",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$ingredients(JsonObject root,Map<?,?> skewers,Map<?,?> kinds,CallbackInfo ci){
        GrillingRecipes.repairSquid(root);
    }
}
