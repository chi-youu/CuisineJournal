package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.culinary.MealStamp;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import java.util.*;

/** Optional host data is read through verified contracts, without loading absent host classes. */
public final class KaleidoscopeFood {
    public static final String QUALITY="kaleidoscope_cookery:quality";
    public static final String INHERITED_STARS="cuisinejournal:cocktail_stars";
    public static final String SERVING="cuisinejournal:kaleidoscope_serving";
    private static final String TAVERN="com.github.ysbbbbbb.kaleidoscopetavern.item.";
    private static final String COOKERY="com.github.ysbbbbbb.kaleidoscopecookery.item.";
    private static final Map<net.minecraft.world.entity.player.Player,ItemStack> REJECTED=new WeakHashMap<>();
    private KaleidoscopeFood() {}
    public static boolean nativeDrink(ItemStack stack) {
        return com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),TAVERN+"DrinkBlockItem")||com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),TAVERN+"CocktailBlockItem")
                ||com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),COOKERY+"TeacupItem");
    }
    public static boolean water(ItemStack stack) {
        return stack.is(Items.POTION)&&PotionUtils.getPotion(stack)==net.minecraft.world.item.alchemy.Potions.WATER
                &&PotionUtils.getMobEffects(stack).isEmpty();
    }
    public static boolean drink(ItemStack stack){return nativeDrink(stack)||water(stack);}
    public static boolean noPerfect(ItemStack stack){return nativeDrink(stack)&&!com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),COOKERY+"TeacupItem");}
    public static boolean kitchen(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().startsWith("kaleidoscope_")&&!nativeDrink(stack);}
    public static boolean quality(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(QUALITY,3);}
    public static boolean brewed(ItemStack stack){return com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),TAVERN+"DrinkBlockItem");}
    public static boolean brewed(ResourceLocation id){return BuiltInRegistries.ITEM.containsKey(id)&&brewed(new ItemStack(BuiltInRegistries.ITEM.get(id)));}
    public static int minimumStars(List<Integer> values){return values.stream().mapToInt(Integer::intValue).min().orElse(2);}
    public static int qualityStars(int quality){return switch(quality){case 3->0;case 2->2;case 1->4;default->5;};}
    public static double qualityRatio(int quality){return switch(quality){case 3->.3;case 2->.6;case 1->.9;default->1.2;};}
    public static int stars(ItemStack stack) {
        if(quality(stack))return qualityStars(Math.max(0,Math.min(3,stack.getTag().getInt(QUALITY))));
        if(stack.hasTag()&&stack.getTag().contains(INHERITED_STARS,3))return Math.max(0,Math.min(5,stack.getTag().getInt(INHERITED_STARS)));
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),TAVERN+"DrinkBlockItem"))return Math.max(0,Math.min(5,brewLevel(stack)-1));
        return -1;
    }
    public static int brewLevel(ItemStack stack){return stack.hasTag()?Math.max(0,Math.min(6,stack.getTag().getInt("BrewLevel"))):0;}
    public static FoodProperties properties(ItemStack stack,FoodProperties original) {
        if(stack.hasTag()&&stack.getTag().contains(SERVING,10)){
            var value=stack.getTag().getCompound(SERVING);double ratio=quality(stack)?qualityRatio(stack.getTag().getInt(QUALITY)):1;
            var builder=new FoodProperties.Builder().nutrition((int)Math.round(value.getInt("nutrition")*ratio))
                    .saturationMod((float)(value.getFloat("saturation")*ratio));
            if(original!=null){
                if(original.canAlwaysEat())builder.alwaysEat();
                for(var effect:original.getEffects())builder.effect(()->new MobEffectInstance(effect.getFirst()),effect.getSecond());
            }
            if(original==null&&stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem){
                Object raw=HostReflection.field(blockItem.getBlock(),"foodProperties");
                if(raw instanceof FoodProperties food)for(var effect:food.getEffects()){
                    var nativeEffect=effect.getFirst();
                    builder.effect(()->new MobEffectInstance(nativeEffect.getEffect(),Math.max(1,(int)Math.round(nativeEffect.getDuration()*ratio)),nativeEffect.getAmplifier()),effect.getSecond());
                }
            }
            return builder.build();
        }
        return drink(stack)&&!CuisineEdibility.isFood(original)?new FoodProperties.Builder().nutrition(0).saturationMod(0).alwaysEat().build():original;
    }
    public static FoodProperties baseProperties(ItemStack stack) {
        if(stack.hasTag()&&stack.getTag().contains(SERVING,10)){
            var value=stack.getTag().getCompound(SERVING);return new FoodProperties.Builder().nutrition(value.getInt("nutrition")).saturationMod(value.getFloat("saturation")).build();
        }
        ItemStack base=stack.copy();base.removeTagKey(QUALITY);base.removeTagKey(MealStamp.ROOT);
        return base.getItem().getFoodProperties(base,null);
    }
    public static void stamp(ItemStack stack,MealStamp original,boolean today) {
        if(quality(stack)) {
            int q=Math.max(0,Math.min(3,stack.getTag().getInt(QUALITY)));
            q=Math.max(0,q-(today?1:0)-(original.perfect()?1:0));stack.getOrCreateTag().putInt(QUALITY,q);
            new MealStamp(qualityStars(q),original.perfect(),qualityStars(q),original.cookId(),original.cookName()).write(stack);
        }else if(noPerfect(stack)) {
            if(com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),TAVERN+"DrinkBlockItem")&&today)
                stack.getOrCreateTag().putInt("BrewLevel",Math.min(6,brewLevel(stack)+1));
            int stars=stars(stack);
            new MealStamp(stars<0?original.stars():stars,false,5,original.cookId(),original.cookName()).write(stack);
        }
    }
    private static List<?> effectRows(ItemStack stack) {
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),TAVERN+"SignatureCocktailBlockItem")) {
            Object effects=HostReflection.invoke(stack.getItem(),"getEffects",stack);return effects instanceof List<?> list?list:List.of();
        }
        try {
            Class<?> listener=Class.forName("com.github.ysbbbbbb.kaleidoscopetavern.datamap.resources.DrinkEffectDataReloadListener");
            Object map=listener.getField("INSTANCE").get(null);
            Object data=map instanceof Map<?,?> values?values.get(stack.getItem()):null;
            Object raw=HostReflection.invoke(data,"effects");
            if(raw instanceof List<?> groups&&!groups.isEmpty()) {
                int index=com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),TAVERN+"DrinkBlockItem")?brewLevel(stack)-1:0;
                if(index<0)return List.of();Object rows=groups.get(Math.min(index,groups.size()-1));
                return rows instanceof List<?> list?list:List.of();
            }
        }catch(ReflectiveOperationException|LinkageError ignored){}
        return List.of();
    }
    public static List<MobEffectInstance> effects(ItemStack stack,boolean guaranteed){
        List<MobEffectInstance> result=new ArrayList<>();
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),COOKERY+"TeacupItem")){
            Object raw=HostReflection.field(stack.getItem(),"effects");
            if(raw instanceof List<?> list)for(Object value:list)if(value instanceof com.mojang.datafixers.util.Pair<?,?> pair
                    &&pair.getFirst() instanceof java.util.function.Supplier<?> supplier&&pair.getSecond() instanceof Number chance
                    &&chance.floatValue()>0&&(!guaranteed||chance.floatValue()>=1)&&supplier.get() instanceof MobEffectInstance effect)result.add(new MobEffectInstance(effect));
        }else for(Object row:effectRows(stack)){
            Object chance=HostReflection.invoke(row,"probability"),effect=HostReflection.invoke(row,"effect");
            Object seconds=HostReflection.invoke(row,"duration"),level=HostReflection.invoke(row,"amplifier");
            if(chance instanceof Number p&&p.floatValue()>0&&(!guaranteed||p.floatValue()>=1)&&effect instanceof MobEffect mob&&seconds instanceof Number duration&&level instanceof Number amplifier)
                result.add(new MobEffectInstance(mob,Math.max(1,duration.intValue()*20),amplifier.intValue()));
        }
        return result;
    }
    public static boolean useAllowed(net.minecraft.world.entity.player.Player player,ItemStack stack) {
        return !CuisineFoodRuntime.activeForCurrentThread()||(player.level().isClientSide?CuisineFoodClientState.canConsume(stack)
                :player instanceof ServerPlayer server&&CuisineFoodService.canConsume(server,stack));
    }
    public static void reject(net.minecraft.world.entity.player.Player player,ItemStack stack){REJECTED.put(player,stack.copy());}
    public static boolean rejected(net.minecraft.world.entity.player.Player player,ItemStack stack){var value=REJECTED.remove(player);return value!=null&&ItemStack.isSameItemSameTags(value,stack);}

}
