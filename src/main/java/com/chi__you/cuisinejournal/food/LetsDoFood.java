package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.culinary.MealStamp;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Stack-aware contracts for the installed Let's Do generation, without a hard host dependency. */
public final class LetsDoFood {
    public static final String BEER_QUALITY="brewery.beer_quality";
    public static final String SERVING="cuisinejournal:lets_do_serving";
    private static final Set<String> MODS=Set.of("farm_and_charm","bakery","candlelight","brewery","vinery","meadow","herbalbrews","beachparty","camping","wildernature","bloomingnature");
    private LetsDoFood() {}
    public static boolean family(Object item,String name){return com.chi__you.cuisinejournal.compat.HostClasses.matches(item,name);}
    public static boolean wine(ItemStack stack){return family(stack.getItem(),"net.satisfy.vinery.core.item.DrinkBlockItem")
            ||family(stack.getItem(),"net.satisfy.nethervinery.core.block.NetherDrinkBlockItem");}
    public static boolean beer(ItemStack stack){return family(stack.getItem(),"net.satisfy.brewery.core.item.DrinkBlockItem");}
    public static boolean mapped(ItemStack stack){return wine(stack)||beer(stack);}
    public static boolean host(ItemStack stack){return !stack.isEmpty()&&(MODS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
            ||BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("nethervinery"));}
    public static boolean flask(ItemStack stack){return family(stack.getItem(),"net.satisfy.herbalbrews.core.items.FlaskItem");}
    public static boolean marshmallow(ItemStack stack){return family(stack.getItem(),"net.satisfy.camping.core.world.item.MarshmallowOnAStickItem");}
    public static boolean roasting(ItemStack stack){return marshmallow(stack)&&stack.hasTag()&&stack.getTag().getBoolean("marshmallowRoasting");}
    public static boolean capture(ItemStack stack){return (host(stack)||stack.hasTag()&&stack.getTag().contains(SERVING))&&!flask(stack)&&!roasting(stack)&&!NetherVineryFoodCompat.delayed(stack);}
    public static boolean variant(ItemStack stack){return mapped(stack)||marshmallow(stack)||host(stack)&&stack.hasTag()
            &&(stack.getTag().contains("StoredEffects")||stack.getTag().contains("Effect")||stack.getTag().contains(SERVING));}
    public static int beerStars(int quality){return 2*Math.max(1,Math.min(3,quality))-1;}
    public static int upgradedBeer(int quality,boolean today,boolean perfect){return Math.min(3,Math.max(1,quality)+(today?1:0)+(perfect?1:0));}
    public static int beerQuality(ItemStack stack){return Math.max(1,Math.min(3,stack.hasTag()?stack.getTag().getInt(BEER_QUALITY):1));}
    public static int wineStars(int age,int yearsPerLevel){return Math.min(5,Math.max(0,age)/Math.max(1,yearsPerLevel));}
    public static boolean winePerfect(int age,int yearsPerLevel,int start,int perYear,int maximum){
        return wineStars(age,yearsPerLevel)==5&&(long)start+(long)Math.max(0,perYear)*Math.max(0,age)>=maximum;
    }
    private static Level level(){
        if(CuisineFoodRuntime.isClientThread())return com.chi__you.cuisinejournal.client.ClientWorldView.level();
        var server=com.chi__you.cuisinejournal.CuisineJournalServer.server();return server==null?null:server.overworld();
    }
    private static Object wineCall(String name,ItemStack stack){
        if(!stack.hasTag()||!stack.getTag().contains("Year"))return 0;
        try{return Class.forName("net.satisfy.vinery.core.util.WineYears").getMethod(name,ItemStack.class,Level.class).invoke(null,stack,level());}
        catch(ReflectiveOperationException|LinkageError failure){return null;}
    }
    private static int wineSetting(String name,int fallback){
        try{return Class.forName("net.satisfy.vinery.core.util.WineYears").getField(name).getInt(null);}
        catch(ReflectiveOperationException|LinkageError failure){return fallback;}
    }
    public static int stars(ItemStack stack){
        if(beer(stack))return beerStars(beerQuality(stack));
        if(!wine(stack))return -1;
        Object age=wineCall("getWineAge",stack);
        int amplifier=stack.hasTag()?stack.getTag().getInt("EffectAmplifier"):0;
        return age instanceof Number n?wineStars(n.intValue(),wineSetting("YEARS_PER_EFFECT_LEVEL",6)):Math.min(5,Math.max(0,amplifier));
    }
    public static boolean winePerfect(ItemStack stack){
        if(!wine(stack))return false;
        Object duration=wineCall("getEffectDuration",stack);
        int ticks=duration instanceof Number n?n.intValue():stack.hasTag()?stack.getTag().getInt("EffectDuration"):0;
        return stars(stack)==5&&ticks>=wineSetting("MAX_DURATION",15000);
    }
    public static MealStamp display(ItemStack stack){
        var old=MealStamp.read(stack);int stars=stars(stack);
        return stars<0?old:new MealStamp(stars,wine(stack)?winePerfect(stack):old!=null&&old.perfect(),stars,
                old==null?null:old.cookId(),old==null?"":old.cookName());
    }
    public static void stamp(ItemStack stack,MealStamp stamp,boolean today){
        if(!mapped(stack))return;
        if(beer(stack))stack.getOrCreateTag().putInt(BEER_QUALITY,upgradedBeer(beerQuality(stack),today,stamp.perfect()));
        int stars=stars(stack);
        new MealStamp(stars,wine(stack)?winePerfect(stack):stamp.perfect(),stars,stamp.cookId(),stamp.cookName()).write(stack);
    }
    public static FoodProperties properties(ItemStack stack,FoodProperties original){
        if(flask(stack))return original;
        if(original==null&&stack.getItem() instanceof BlockItem block&&LetsDoPlacedFoods.known(block.getBlock())
                &&!DelightPlacedFoods.is(block.getBlock(),LetsDoPlacedFoods.TABLE)){
            var serving=LetsDoPlacedFoods.serving(block.getBlock(),block.getBlock().defaultBlockState());
            if(serving.hasTag()&&serving.getTag().contains(SERVING)){var row=serving.getTag().getCompound(SERVING);
                original=new FoodProperties.Builder().nutrition(row.getInt("nutrition")).saturationMod(row.getFloat("saturation")).build();}
        }
        if(marshmallow(stack)){
            Object value=HostReflection.invokeDeclared(stack.getItem(),"resolveBaseFood",stack);
            if(value instanceof FoodProperties food)original=food;
        }
        if(stack.hasTag()&&stack.getTag().contains(SERVING,10)){
            var serving=stack.getTag().getCompound(SERVING);
            var b=new FoodProperties.Builder().nutrition(serving.getInt("nutrition")).saturationMod(serving.getFloat("saturation"));
            if(original!=null)for(var effect:original.getEffects())b.effect(()->new MobEffectInstance(effect.getFirst()),effect.getSecond());
            return b.build();
        }
        return original;
    }
    public static List<MobEffectInstance> effects(ItemStack stack,boolean guaranteed){
        List<MobEffectInstance> result=new ArrayList<>();
        if(wine(stack)){
            var raw=stack.getItem().getFoodProperties(stack,null);
            Object duration=wineCall("getEffectDuration",stack),amplifier=wineCall("getEffectLevel",stack);
            int base=number(stack.getItem(),"baseDuration",1800);
            boolean scaled=Boolean.TRUE.equals(HostReflection.field(stack.getItem(),"scaleDurationWithAge"));
            int ticks=scaled&&duration instanceof Number n?n.intValue():base;
            int amp=amplifier instanceof Number n?n.intValue():0;
            if(raw!=null)for(var pair:raw.getEffects())result.add(new MobEffectInstance(pair.getFirst().getEffect(),ticks,amp));
        }else if(beer(stack)){
            Object effect=HostReflection.field(stack.getItem(),"effect");int q=beerQuality(stack);
            if(effect instanceof MobEffect mob)result.add(new MobEffectInstance(mob,number(stack.getItem(),"baseDuration",600)*(q==3?5:q==2?3:1),q-1));
        }else if(host(stack)&&stack.hasTag()){
            var tag=stack.getTag();
            if(tag.contains("Effect")){
                var id=ResourceLocation.tryParse(tag.getString("Effect"));
                if(id!=null)BuiltInRegistries.MOB_EFFECT.getOptional(id).ifPresent(effect->result.add(new MobEffectInstance(effect,tag.getInt("EffectDuration"))));
            }
            for(var raw:tag.getList("StoredEffects",10)){var row=(net.minecraft.nbt.CompoundTag)raw;
                var effect=MobEffect.byId(row.getShort("id"));
                if(effect!=null&&row.getFloat("chance")>0&&(!guaranteed||row.getFloat("chance")>=1))
                    result.add(new MobEffectInstance(effect,row.getInt("duration"),row.getInt("amplifier")));
            }
        }
        if(capture(stack)&&!mapped(stack)&&!(kitchenStage(stack)&&stack.hasTag()&&stack.getTag().contains("StoredEffects",9))){var props=stack.getItem().getFoodProperties(stack,null);
            if(props!=null)for(var pair:props.getEffects())if(pair.getSecond()>0&&(!guaranteed||pair.getSecond()>=1))
                result.add(new MobEffectInstance(pair.getFirst()));
        }
        return List.copyOf(result);
    }
    private static int number(Object value,String field,int fallback){var raw=HostReflection.field(value,field);return raw instanceof Number n?n.intValue():fallback;}
    public static boolean alcoholEffect(MobEffect effect){var id=BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return id!=null&&id.getNamespace().equals("brewery")&&Set.of("drunk","blackout").contains(id.getPath());}
    public static boolean kitchenStage(ItemStack stack){return family(stack.getItem(),"net.satisfy.farm_and_charm.core.item.food.EffectFoodItem")
            ||family(stack.getItem(),"net.satisfy.farm_and_charm.core.item.food.EffectFoodBlockItem");}
    public static ItemStack preserveStage(ItemStack before,ItemStack after){
        if(!kitchenStage(before)||after.isEmpty()||!ItemStack.isSameItem(before,after))return after;
        var result=after.copy();var tags=before.hasTag()?before.getTag().copy():new net.minecraft.nbt.CompoundTag();
        if(after.hasTag())tags.merge(after.getTag());result.setTag(tags);return result;
    }
    public static boolean useAllowed(net.minecraft.world.entity.player.Player player,ItemStack stack){
        if(marshmallow(stack)){
            Object targeted=HostReflection.invokeDeclared(stack.getItem(),"isCampfireTargeted",player.level(),player);
            if(Boolean.TRUE.equals(targeted))return true;
        }
        return KaleidoscopeFood.useAllowed(player,stack);
    }
    public static void tick(net.minecraft.server.level.ServerPlayer player){
        if(CuisineGameplayRules.nativeEffectsEnabled()||player.tickCount%20!=0)return;
        if(!net.minecraftforge.fml.ModList.get().isLoaded("brewery"))return;
        Object state=HostReflection.invoke(player,"brewery$getAlcohol");
        Object amount=HostReflection.invoke(state,"getDrunkenness");
        if(amount instanceof Number n&&n.intValue()>0){
            HostReflection.invoke(state,"soberUp");
            try{var manager=Class.forName("net.satisfy.brewery.core.effect.alcohol.AlcoholManager");
                for(var method:manager.getMethods())if(method.getName().equals("syncAlcohol"))method.invoke(null,player,state);
            }catch(ReflectiveOperationException|LinkageError failure){com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Cannot synchronize Brewery sobriety",failure);}
        }
        for(String name:List.of("drunk","blackout"))BuiltInRegistries.MOB_EFFECT.getOptional(ResourceLocation.fromNamespaceAndPath("brewery",name)).ifPresent(player::removeEffect);
    }
}
