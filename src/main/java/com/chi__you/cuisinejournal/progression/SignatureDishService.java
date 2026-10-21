package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import net.minecraft.resources.ResourceLocation;

public final class SignatureDishService {
    private SignatureDishService(){}
    public static boolean eligible(CulinaryProfile profile,ResourceLocation dish){
        var registry=CulinaryRegistries.current();var config=ConfigManager.server();
        if(!config.mastery.enabled||dish==null||!registry.isTracked(dish)||registry.preparationRecipes(dish).isEmpty())return false;
        var record=profile.peekMeal(registry.preparationTarget(dish));
        return record!=null&&record.prepared()>0&&profile.rankOf(dish,config.mastery.toCurve()).isMastered();
    }
    public static double bonus(CulinaryProfile profile,ResourceLocation dish){
        var selected=profile.signatureDish();var registry=CulinaryRegistries.current();
        return selected!=null&&eligible(profile,selected)
                &&registry.preparationTarget(selected).equals(registry.preparationTarget(dish))
                ?ConfigManager.server().signatureDish.perfectChanceBonus:0;
    }
    public static boolean mayChange(boolean chosen,long last,long day){return !chosen||last<day;}
}
