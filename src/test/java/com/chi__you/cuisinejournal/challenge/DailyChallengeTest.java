package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.config.*;
import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.recipe.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class DailyChallengeTest {
    private static int checks;
    private static ResourceLocation id(String path){return ResourceLocation.parse("test:"+path);}
    public static void main(String[] args)throws Exception{
        var current=CulinaryRegistries.class.getDeclaredField("current");current.setAccessible(true);Object previous=current.get(null);
        var constructor=CulinaryRegistry.class.getDeclaredConstructor(Map.class,List.class,CulinaryAudit.class,List.class,Map.class,Map.class);
        constructor.setAccessible(true);
        var entries=new ArrayList<MealEntry>();var map=new LinkedHashMap<ResourceLocation,MealEntry>();
        var used=CookingMethod.of(id("used"));var unused=CookingMethod.of(id("unused"));
        for(int i=0;i<12;i++){
            var method=i<6?used:unused;
            var recipe=new RecipeEntry(id("recipe"+i),method.id(),method,id("food"+i),1,List.of(),"test");
            var entry=new MealEntry(recipe.masteryTarget(),null,"test",List.of(recipe),Set.of(method),Set.of(),FoodCategory.OTHER_FOOD,6,.6F);
            entries.add(entry);map.put(entry.target(),entry);
        }
        var registry=(CulinaryRegistry)constructor.newInstance(map,entries,CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of());
        var config=new ServerConfig();config.challenges.dailyChallengeCount=20;
        config.mastery.scope=ConfigEnums.MasteryScope.SUBCATEGORY;
        var today=id("food0");
        try{
            current.set(null,registry);
            var full=DailyRotation.generate(registry,config,Set.of(used.id()),today,12);
            check(full.size()==7,"all eligible task templates enter the pool once");
            check(full.stream().allMatch(d->d.rewards().items().isEmpty()),"daily rewards remain item-free by default");
            var rewardConfig=Toml.read("[challenges]\ndailyRewardItems = [\"minecraft:emerald 3\", \"minecraft:bread\"]\n",ServerConfig.class);
            rewardConfig.validate();
            check(rewardConfig.challenges.dailyRewardItems.equals(List.of("minecraft:emerald 3","minecraft:bread 1")),
                    "daily item rewards load from server TOML and normalize implicit counts");
            config.challenges.dailyRewardItems=new ArrayList<>(rewardConfig.challenges.dailyRewardItems);
            var rewarded=DailyRotation.generate(registry,config,Set.of(used.id()),today,12);
            check(rewarded.stream().allMatch(d->d.rewards().items().equals(rewardConfig.challenges.dailyRewardItems)
                    &&d.rewards().cookingXp()==40),"each generated daily challenge adds configured items alongside cooking XP");
            config.challenges.dailyRewardItems.clear();
            var rewardHistory=new DailyChallengeHistory();rewardHistory.select(12,rewarded);
            check(DailyChallengeHistory.load(rewardHistory.save(new CompoundTag())).tasks().equals(rewarded),
                    "daily item rewards freeze with the day's definitions and survive restart and config changes");
            check(ChallengeReward.parseItem("minecraft:bread 0")==null&&ChallengeReward.parseItem("minecraft:bread -1")==null
                    &&ChallengeReward.parseItem("minecraft:bread nope")==null&&ChallengeReward.parseItem("minecraft:bread 2 extra")==null,
                    "invalid item amounts cannot silently become rewards");
            check(ChallengeReward.normalizeItems(Arrays.asList(null,"","Bad ID","minecraft:bread 2")).equals(List.of("minecraft:bread 2")),
                    "malformed item rewards are skipped without losing valid entries");
            var types=full.stream().map(d->d.objectives().get(0).type()).toList();
            check(types.stream().distinct().count()==types.size(),"templates are never duplicated within the day");
            var station=full.stream().filter(d->d.objectives().get(0).type()==ChallengeObjective.Type.USE_METHOD).findFirst().orElseThrow();
            check(station.objectives().get(0).value().equals(used.id().toString()),"unused installed station cannot be selected");
            check(full.stream().filter(d->d.objectives().get(0).type()==ChallengeObjective.Type.COOK_TODAY)
                    .allMatch(d->d.objectives().get(0).amount()==5&&d.objectives().get(0).value().equals(today.toString())),
                    "today task fixes the daily dish identity and five preparation events");
            check(full.stream().filter(d->d.objectives().get(0).type()==ChallengeObjective.Type.COOK_PERFECT)
                    .allMatch(d->d.objectives().get(0).amount()==1),"perfect objective requires one production event");
            config.mastery.scope=ConfigEnums.MasteryScope.DISH;
            var dishMode=DailyRotation.generate(registry,config,Set.of(),null,12);
            check(dishMode.stream().noneMatch(d->Set.of(ChallengeObjective.Type.COOK_CATEGORY,ChallengeObjective.Type.USE_METHOD,
                    ChallengeObjective.Type.COOK_TODAY).contains(d.objectives().get(0).type())),"dish mode no category task, no history no station, no today no today task");
            config.mastery.enabled=false;
            check(DailyRotation.generate(registry,config,Set.of(),null,12).stream()
                    .noneMatch(d->d.objectives().get(0).type()==ChallengeObjective.Type.COOK_PERFECT),"disabled quality cannot produce a perfect daily objective");
            for(long day=0;day<100;day++){
                var meals=DailyRotation.generate(registry,config,Set.of(),today,day);
                var food=meals.stream().filter(d->d.objectives().get(0).type()==ChallengeObjective.Type.EAT_FOOD).findFirst().orElseThrow();
                int goal=food.objectives().get(0).amount();if(goal<25||goal>40)throw new AssertionError("meal range violated");
            }
            check(true,"food consumption goal remains inclusive 25..40");
            var profile=new CulinaryProfile(new UUID(1,2));profile.method(used).recordPreparation(1,3,today);
            var history=new DailyChallengeHistory();history.include(profile);history.select(12,full);
            var saved=DailyChallengeHistory.load(history.save(new CompoundTag()));
            check(saved.day()==12&&saved.tasks().equals(full)&&saved.methods().equals(Set.of(used.id())),"daily definitions and used method history survive save/restart");
            saved.record(unused);check(saved.tasks().equals(full),"new method cannot reroll frozen tasks");
            var state=new ChallengeState(1);state.start(0,0);
            var perfect=new ChallengeObjective(ChallengeObjective.Type.COOK_PERFECT,"",1,false);
            ChallengeManager.advanceSpecialPreparation(state,0,perfect,today,64,new MealStamp(3,false,5,null,""));
            check(state.progress(0)==0,"ordinary quality cannot complete perfect task");
            ChallengeManager.advanceSpecialPreparation(state,0,perfect,today,64,new MealStamp(3,true,5,null,""));
            check(state.progress(0)==1,"one perfect batch counts one preparation even with sixty four portions");
            state=new ChallengeState(1);state.start(0,0);
            var todayGoal=new ChallengeObjective(ChallengeObjective.Type.COOK_TODAY,today.toString(),5,false);
            ChallengeManager.advanceSpecialPreparation(state,0,todayGoal,id("other"),64,null);
            check(state.progress(0)==0,"wrong dish cannot advance daily dish task");
            for(int i=0;i<5;i++)ChallengeManager.advanceSpecialPreparation(state,0,todayGoal,today,64,null);
            check(state.progress(0)==5&&state.satisfies(List.of(todayGoal)),"five production settlements, not portion count, complete today task");
            check(ChallengeManager.expiredGeneratedPeriod(full.get(0).id(),13),"named daily template ids clean up after their period");
            check(!ChallengeManager.expiredGeneratedPeriod(full.get(0).id(),12),"current named daily ids remain stable");
            config.mastery.enabled=true;config.mastery.scope=ConfigEnums.MasteryScope.SUBCATEGORY;config.challenges.dailyChallengeCount=3;
            check(DailyRotation.generate(registry,config,Set.of(used.id()),today,8).size()==3,"default task count is preserved");
        }finally{current.set(null,previous);}
        contracts();
        System.out.println("DailyChallengeTest: "+checks+" checks passed");
    }
    private static void contracts()throws Exception{
        check(calls("tracking/EatingTracker","onItemEaten","isFood")&&calls("tracking/EatingTracker","onItemEaten","fire"),
                "real food consumption fires before tracked-dish filtering");
        check(calls("progression/ProgressionService","creditMethod","recordMethod"),"production persists used methods");
        check(calls("challenge/ChallengeManager","onMealPrepared","advanceSpecialPreparation")
                &&calls("challenge/ChallengeManager","onFoodEaten","forEachActive"),"new objectives reach authoritative challenge state");
    }
    private static boolean calls(String path,String name,String target)throws Exception{
        var node=new org.objectweb.asm.tree.ClassNode();try(var input=DailyChallengeTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/"+path+".class")){new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node,0);}
        for(var method:node.methods)if(method.name.equals(name)||method.name.startsWith("lambda$"+name+"$"))for(var instruction:method.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals(target))return true;return false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
