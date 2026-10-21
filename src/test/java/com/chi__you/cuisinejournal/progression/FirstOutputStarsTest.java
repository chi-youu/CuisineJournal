package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ConfigEnums;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.recipe.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** A new dish inherits earned subgroup stars without prematurely marking it discovered. */
public final class FirstOutputStarsTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        var config=ConfigManager.server();var scope=config.mastery.scope;
        var registryField=CulinaryRegistries.class.getDeclaredField("current");registryField.setAccessible(true);
        var previous=registryField.get(null);
        try {
            var owner=new UUID(0,17);
            var perfectStamp=new com.chi__you.cuisinejournal.culinary.MealStamp(4,true,5,owner,"Cook");
            var edit=com.chi__you.cuisinejournal.food.CuisineFoodQuality.editedStamp(perfectStamp,2,null);
            check(edit.perfect()&&edit.stars()==2&&owner.equals(edit.cookId())&&edit.cookName().equals("Cook"),
                    "held quality edit preserves omitted perfect flag and original cook");
            check(!com.chi__you.cuisinejournal.food.CuisineFoodQuality.editedStamp(perfectStamp,3,false).perfect(),
                    "held quality edit explicitly clears perfect flag");
            check(com.chi__you.cuisinejournal.food.CuisineFoodQuality.editedStamp(null,1,true).perfect(),
                    "held quality edit can mark an unstamped food perfect without inventing a cook");
            config.mastery.scope=ConfigEnums.MasteryScope.SUBCATEGORY;
            ResourceLocation first=id("test:old"),fresh=id("test:new"),other=id("other:meal");
            var entries=List.of(meal(first),meal(fresh),meal(other));
            Map<ResourceLocation,MealEntry> map=new LinkedHashMap<>();entries.forEach(entry->map.put(entry.target(),entry));
            var constructor=CulinaryRegistry.class.getDeclaredConstructor(Map.class,List.class,CulinaryAudit.class,List.class,Map.class,Map.class);
            constructor.setAccessible(true);
            registryField.set(null,constructor.newInstance(map,entries,CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of()));
            var profile=new CulinaryProfile(new UUID(0,1));
            profile.meal(first).discover(1);profile.meal(first).recordPreparation(1,20,CookingMethod.CRAFTING);
            check(profile.rankOf(fresh,config.mastery.toCurve())==MasteryRank.UNFAMILIAR,"journal still treats unmade dish as undiscovered");
            int earned=profile.categoryRank(id("test:_mod"),config.mastery.toCurve()).ordinal();
            check(earned>0&&CookingRewards.starsFor(profile,fresh,config)==earned,"first new dish receives subgroup stars at stamping");
            check(!profile.isDiscovered(fresh)&&profile.peekMeal(fresh)==null,"stamp calculation never grants discovery or preparation");
            check(CookingRewards.starsFor(profile,other,config)==0,"unrelated subgroup cannot inherit stars");
            profile.meal(fresh).discover(2);
            check(CookingRewards.starsFor(profile,fresh,config)==earned,"discovery alone does not change equal earned output stars");
            config.mastery.scope=ConfigEnums.MasteryScope.DISH;
            check(CookingRewards.starsFor(profile,fresh,config)==0,"per-dish mode never borrows subgroup progression");
            profile.meal(fresh).addMasteryPoints(5);
            check(CookingRewards.starsFor(profile,fresh,config)==profile.rankOf(fresh,config.mastery.toCurve()).ordinal(),"per-dish output uses its own recorded points");
            check(!com.chi__you.cuisinejournal.food.CuisineFoodQuality.craftable(fresh),
                    "catalogue-only fruit does not become a craftable dish from category membership");
            check(com.chi__you.cuisinejournal.food.CuisineFoodQuality.stars(false,null)==2,
                    "harvested ingredient receives fixed two-star baseline without NBT");
            check(com.chi__you.cuisinejournal.food.CuisineFoodQuality.stars(true,null)==0,
                    "unstamped craftable food remains zero-star");
            var recipe=new RecipeEntry(id("test:recipe"),id("minecraft:crafting"),CookingMethod.CRAFTING,fresh,1,List.of(),"test");
            var prepared=new MealEntry(fresh,null,"test",List.of(recipe),Set.of(CookingMethod.CRAFTING),Set.of(),FoodCategory.OTHER_FOOD,0,0);
            registryField.set(null,constructor.newInstance(Map.of(fresh,prepared),List.of(prepared),CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of()));
            check(com.chi__you.cuisinejournal.food.CuisineFoodQuality.craftable(fresh),
                    "actual preparation source retains dish quality semantics");
            var quality=new org.objectweb.asm.tree.ClassNode();
            try(var input=FirstOutputStarsTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/food/CuisineFoodQuality.class")) {
                new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(input)).accept(quality,0);
            }
            boolean override=false;
            for(var method:quality.methods)if(method.name.equals("stars"))for(var instruction:method.instructions)
                if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("isQualitySample"))override=true;
            check(override,"explicit test sample quality takes precedence over ingredient baseline");
            var command=new org.objectweb.asm.tree.ClassNode();
            try(var input=FirstOutputStarsTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/command/AdminCommands.class")) {
                new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(input)).accept(command,0);
            }
            boolean mark=false,progress=false;
            for(var method:command.methods)if(method.name.equals("sample"))for(var instruction:method.instructions)
                if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    if(call.name.equals("markQualitySample"))mark=true;
                    if(List.of("credit","recordPreparation","discover").contains(call.name))progress=true;
                }
            check(mark&&!progress,"test sample command sets quality without fabricating preparation or discovery");
            System.out.println("FirstOutputStarsTest: "+checks+" checks passed");
        } finally {config.mastery.scope=scope;registryField.set(null,previous);}
    }
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static MealEntry meal(ResourceLocation id){return new MealEntry(id,null,id.getNamespace(),List.of(),Set.of(),Set.of(),FoodCategory.OTHER_FOOD,0,0);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
