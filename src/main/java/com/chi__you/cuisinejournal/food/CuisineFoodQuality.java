package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Catalogue membership alone does not turn a harvested ingredient into a crafted dish. */
public final class CuisineFoodQuality {
    public static final String QUALITY_SAMPLE="cuisinejournal:quality_sample";
    private CuisineFoodQuality() {}
    public static boolean craftable(ResourceLocation item) {
        if (CuisineFoodRuntime.isClientThread()) {
            return ClientFoodRuleView.current().craftable(item);
        }
        return !CulinaryRegistries.current().preparationRecipes(item).isEmpty();
    }
    public static int stars(boolean craftable, MealStamp stamp) {
        return craftable ? stamp == null ? 0 : stamp.effectiveStars() : 2;
    }
    public static int stars(ItemStack stack) {
        if (!CuisineGameplayRules.starsEnabled()) return 2;
        if(isQualitySample(stack))return MealStamp.read(stack).effectiveStars();
        int letsDoStars=LetsDoFood.stars(stack);if(letsDoStars>=0)return letsDoStars;
        int hostStars=KaleidoscopeFood.stars(stack);
        if(hostStars>=0)return hostStars;
        var source = CuisineAlcohol.sourceId(stack);
        return stars(craftable(source == null ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : source), MealStamp.read(stack));
    }
    public static boolean isQualitySample(ItemStack stack) {
        return stack.hasTag()&&stack.getTag().getBoolean(QUALITY_SAMPLE)&&MealStamp.read(stack)!=null;
    }
    public static void markQualitySample(ItemStack stack,int stars,boolean perfect,java.util.UUID cook,String name) {
        new MealStamp(stars,perfect,5,cook,name).write(stack);
        stack.getOrCreateTag().putBoolean(QUALITY_SAMPLE,true);
    }
    public static MealStamp editedStamp(MealStamp previous,int stars,Boolean perfect) {
        return new MealStamp(stars,perfect==null?previous!=null&&previous.perfect():perfect,5,
                previous==null?null:previous.cookId(),previous==null?"":previous.cookName());
    }
}
