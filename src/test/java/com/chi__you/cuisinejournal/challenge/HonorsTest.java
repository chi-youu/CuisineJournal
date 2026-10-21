package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.network.*;
import com.chi__you.cuisinejournal.milestone.Milestone;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import com.google.gson.JsonParser;
import java.util.*;

public final class HonorsTest {
    private static int checks;
    private static ResourceLocation id(String name){return ResourceLocation.parse("test:"+name);}
    public static void main(String[] args) throws Exception {
        var task=new ChallengeObjective(ChallengeObjective.Type.COOK_RECIPE,"",2,true);
        check(!ChallengeManager.expiredGeneratedPeriod(ResourceLocation.parse("cuisinejournal:weekly/1_0"),14),
                "retired weekly records are preserved rather than deleted");
        check(!ChallengeManager.expiredGeneratedPeriod(ResourceLocation.parse("cuisinejournal:daily/14_0"),14)
                &&ChallengeManager.expiredGeneratedPeriod(ResourceLocation.parse("cuisinejournal:daily/13_0"),14),
                "only past generated daily periods are cleaned up");
        boolean rejectedWeekly=false;
        try{ChallengeDefinition.Scope.byName("WEEKLY");}catch(ChallengeParseException expected){rejectedWeekly=true;}
        check(rejectedWeekly,"legacy weekly definitions cannot silently become permanent challenges");
        var oldWeeklyConfig=com.chi__you.cuisinejournal.config.Toml.read("[challenges]\nweeklyEnabled = true\nweeklyChallengeCount = 9\ndailyChallengeCount = 4\n",
                com.chi__you.cuisinejournal.config.ServerConfig.class);
        var cleaned=com.chi__you.cuisinejournal.config.Toml.write(oldWeeklyConfig,List.of());
        check(!cleaned.contains("weeklyEnabled")&&!cleaned.contains("weeklyChallengeCount")&&oldWeeklyConfig.challenges.dailyChallengeCount==4,
                "retired weekly config is ignored and removed without losing daily tuning");
        check(!ChallengeManager.expiredGeneratedPeriod(ResourceLocation.parse("test:daily/custom"),99),
                "cleanup does not delete external datapack task records");
        var state=new ChallengeState(1);state.start(100,120);
        check(state.started()&&!state.expire(219)&&state.expire(220)&&state.failed(),"attempt expires at exact tick boundary");
        state.start(220,120);check(!state.failed()&&state.progress(0)==0&&state.deadline()==340,"retry creates clean attempt");
        state.advanceUnique(0,id("a"));state.advanceUnique(0,id("a"));check(state.progress(0)==1,"unique targets deduplicate");
        var restored=ChallengeState.load(state.save(),1);
        check(restored.started()&&restored.deadline()==340&&restored.progress(0)==1,"attempt and progress survive restart");
        restored.advanceUnique(0,id("b"));check(restored.satisfies(List.of(task)),"all objective conditions required");
        restored.complete(1);check(!restored.advance(0,5)&&!restored.expire(1000),"completion is stable and cannot expire");
        var daily=new ChallengeState(1);daily.preparePeriod(3,72001,96000,true);daily.advance(0,1);
        check(!daily.preparePeriod(3,73000,96000,true)&&daily.progress(0)==1,"same-period reload preserves progress");
        daily.preparePeriod(5,120000,144000,true);check(daily.progress(0)==0&&daily.deadline()==144000,"offline skipped periods reset to current period");
        var legacy=new ChallengeState(1);legacy.advance(0,3);legacy.complete(2);
        legacy.preparePeriod(2,48000,72000,true);check(legacy.isCompleted(),"legacy completion not silently wiped on migration");
        legacy.preparePeriod(3,72000,96000,true);check(!legacy.isCompleted()&&legacy.progress(0)==0,"next period genuinely resets legacy state");
        var variety=new ChallengeState(1);variety.start(0,0);
        check(variety.variety(id("z"))==1&&variety.variety(id("a"))==2,"variety counts this attempt only");
        var ordered=ChallengeState.load(variety.save(),1);
        check(ordered.variety(id("z"))==1,"variety duplicate resets after serialization");
        check(ordered.dailyVariety(id("a"),1)==1&&ordered.dailyVariety(id("a"),1)==1
                &&ordered.dailyVariety(id("b"),1)==2&&ordered.dailyVariety(id("c"),2)==1,"daily variety is day-local");
        var profile=new CulinaryProfile(new UUID(1,2));
        check(!profile.selectBadge(ResourceLocation.parse("cuisinejournal:feast_master")),"an unearned badge cannot be equipped");
        profile.awardBadge(ResourceLocation.parse("cuisinejournal:feast_master"));
        check(profile.selectBadge(ResourceLocation.parse("cuisinejournal:feast_master")),"an earned badge can be equipped");
        check(!profile.selectBadge(id("unearned"))&&profile.selectBadge(null),"only owned badges may be equipped and selection can clear");
        profile.selectBadge(ResourceLocation.parse("cuisinejournal:feast_master"));
        var reloaded=CulinaryProfile.load(profile.playerId(),profile.save());
        check(reloaded.selectedBadge().equals(profile.selectedBadge()),"equipped honor permanently persists");
        var tampered=profile.save();tampered.putString("selectedBadge","test:unearned");
        check(CulinaryProfile.load(profile.playerId(),tampered).selectedBadge()==null,"saved selection cannot equip an unearned badge");
        check(!profile.save().contains("title")&&!profile.save().contains("honorsMigrated"),"no duplicate title identity or retired migration marker");
        reloaded.reset(CulinaryProfile.ResetScope.CHALLENGES);
        check(reloaded.badges().contains(profile.selectedBadge())&&reloaded.selectedBadge().equals(profile.selectedBadge()),
                "resetting challenge attempts cannot erase permanent honors");
        var limited=new ChallengeState(1);limited.start(10,0);limited.limitDeadline(100);
        check(limited.deadline()==100&&limited.expire(100),"manual rotating challenge cannot extend beyond period deadline");
        var a=new ChallengeDefinition(id("a"),ChallengeDefinition.Scope.CHAIN,List.of(task),ChallengeReward.NONE,id("b"),false);
        var b=new ChallengeDefinition(id("b"),ChallengeDefinition.Scope.CHAIN,List.of(task),ChallengeReward.NONE,id("a"),false);
        var c=new ChallengeDefinition(id("c"),ChallengeDefinition.Scope.CHAIN,List.of(task),ChallengeReward.NONE,id("a"),false);
        ChallengeRegistry.replace(List.of(a,b,c));check(ChallengeRegistry.current().size()==0,"dependency cycles and unreachable descendants removed");
        var single=new ChallengeDefinition(id("manual"),ChallengeDefinition.Scope.PERMANENT,List.of(task),ChallengeReward.NONE,null,false,true,200);
        ChallengeRegistry.replace(List.of(single));ChallengeRegistry.setRotating(List.of(new ChallengeDefinition(id("day"),ChallengeDefinition.Scope.DAILY,
                List.of(task),ChallengeReward.NONE,null,false)));
        check(ChallengeRegistry.current().all().size()==2&&ChallengeRegistry.current().get(id("day"))!=null,"rotating challenges participate in unified registry");
        ChallengeRegistry.clear();
        rotationTests();
        badgeDefinitionTests();
        var json=JsonParser.parseString("{\"scope\":\"PERMANENT\",\"duration_seconds\":6,\"objectives\":[{\"type\":\"COOK_RECIPE\",\"amount\":2}]}").getAsJsonObject();
        var parsed=ChallengeDefinition.fromJson(id("parsed"),json);check(!parsed.manual()&&parsed.durationTicks()==0,"old manual and duration settings cannot re-enable acceptance or countdown");
        var reward=ChallengeReward.fromJson(JsonParser.parseString("{\"mastery_xp\":3,\"mastery_dish\":\"test:soup\"}").getAsJsonObject());
        check(reward.masteryXp()==3&&reward.masteryDish().equals(id("soup")),"mastery reward names an explicit target rather than guessing");
        var milestone=Milestone.fromJson(id("level"),JsonParser.parseString("{\"type\":\"COOKING_LEVEL\",\"threshold\":1,\"badge\":\"test:badge\"}").getAsJsonObject());
        check(milestone.badge().equals(id("badge")),"milestone carries only a badge reward");
        var objective=new Packets.ChallengeView.ObjectiveView("COOK_RECIPE","test:soup",1,3);
        var view=new Packets.ChallengeView(id("challenge"),"title","description",false,1,3,"DAILY",false,true,false,false,300,
                List.of(objective),40,id("badge"),List.of("minecraft:emerald 3","minecraft:bread 1"));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{new Packets.SyncJournalExtras(List.of(view),List.of(),List.of(),List.of(),List.of(),0).write(buffer);
            check(Packets.SyncJournalExtras.read(buffer).challenges().get(0).equals(view),"all objectives timing status and item rewards survive network");
        }finally{buffer.release();}
        var statistic=new Packets.SyncStatistics(0,0,0,0,0,0,0,0,null,0,null,0,0,0,0,0,0,id("badge"),
                List.of(new Packets.SyncStatistics.Achievement(id("badge"),true,true,"source",0,id("icon"))));
        buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{statistic.write(buffer);check(Packets.SyncStatistics.read(buffer).equals(statistic),"equipped badge and icon survive network");}finally{buffer.release();}
        System.out.println("HonorsTest: "+checks+" checks passed");
    }
    private static void badgeDefinitionTests() throws Exception {
        CulinaryBadge.replace(List.of(new CulinaryBadge(ResourceLocation.parse("cuisinejournal:feast_master"),id("icon"))));
        check(CulinaryBadge.get(ResourceLocation.parse("cuisinejournal:feast_master"))!=null,"badge metadata uses its exact wearable identity");
        check(CulinaryBadge.get(ResourceLocation.parse("other:feast_master"))==null,"same path in another namespace is a different badge");
        CulinaryBadge.clear();
        var root=java.nio.file.Path.of("src/main/resources/data");
        try(var paths=java.nio.file.Files.walk(root)){
            var files=paths.filter(p->p.toString().endsWith(".json")&&p.toString().contains("cuisinejournal")
                    &&(p.toString().contains("badges")||p.toString().contains("milestones")||p.toString().contains("challenges"))).toList();
            var badgeIds=new HashSet<ResourceLocation>();
            for(var file:files)if(file.toString().contains("badges")){
                var relative=root.relativize(file);String namespace=relative.getName(0).toString();
                var badge=CulinaryBadge.fromJson(ResourceLocation.fromNamespaceAndPath(namespace,file.getFileName().toString().replace(".json","")),
                        JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject());badgeIds.add(badge.id());
            }
            for(var file:files){
                var json=JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
                if(file.toString().contains("milestones")){
                    check(!json.has("rewards")&&!json.has("cooking_xp"),"shipped milestone never grants economic rewards");
                    if(json.has("badge"))check(badgeIds.contains(ResourceLocation.parse(json.get("badge").getAsString())),
                            "milestone reward names an existing badge definition");
                }
            }
        }
    }
    private static void rotationTests() throws Exception {
        var current=com.chi__you.cuisinejournal.recipe.CulinaryRegistries.class.getDeclaredField("current");
        current.setAccessible(true);var previous=current.get(null);
        var constructor=com.chi__you.cuisinejournal.recipe.CulinaryRegistry.class.getDeclaredConstructor(Map.class,List.class,
                com.chi__you.cuisinejournal.recipe.CulinaryAudit.class,List.class,Map.class,Map.class);constructor.setAccessible(true);
        var entries=new ArrayList<com.chi__you.cuisinejournal.recipe.MealEntry>();
        var map=new LinkedHashMap<ResourceLocation,com.chi__you.cuisinejournal.recipe.MealEntry>();
        for(int i=0;i<12;i++){
            var recipe=new com.chi__you.cuisinejournal.recipe.RecipeEntry(id("recipe"+i),id("crafting"),
                    com.chi__you.cuisinejournal.recipe.CookingMethod.CRAFTING,id("food"+i),1,List.of(),"test");
            var entry=new com.chi__you.cuisinejournal.recipe.MealEntry(id("food"+i),null,"test",List.of(recipe),
                    Set.of(recipe.method()),Set.of(),com.chi__you.cuisinejournal.recipe.FoodCategory.OTHER_FOOD,6,.6F);
            entries.add(entry);map.put(entry.target(),entry);
        }
        var registry=constructor.newInstance(map,entries,com.chi__you.cuisinejournal.recipe.CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of());
        var config=new com.chi__you.cuisinejournal.config.ServerConfig();
        config.challenges.dailyChallengeCount=3; // the default is now one; rotation is checked on three
        var roll=DailyRotation.class.getDeclaredMethod("roll",long.class,com.chi__you.cuisinejournal.config.ServerConfig.class);roll.setAccessible(true);
        try {
            current.set(null,registry);roll.invoke(null,14L,config);
            var first=List.copyOf(ChallengeRegistry.current().all());
            check(first.size()==3&&DailyRotation.dailyChallenges().size()==3,"only daily generated challenges are registered");
            roll.invoke(null,14L,config);check(first.equals(List.copyOf(ChallengeRegistry.current().all())),"same-day restart preserves generated definitions");
            roll.invoke(null,15L,config);check(!first.equals(List.copyOf(ChallengeRegistry.current().all())),
                    "new day rotates daily challenge identity");
            check(ChallengeRegistry.current().all().stream().allMatch(d->d.objectives().get(0).amount()>0
                    &&(!d.objectives().get(0).uniqueOnly()||d.objectives().get(0).amount()<=12)),"unique goals do not exceed available foods");
            roll.invoke(null,21L,config);check(ChallengeRegistry.current().all().stream().allMatch(d->d.scope()==ChallengeDefinition.Scope.DAILY),
                    "crossing former weekly boundary never generates weekly challenges");
        } finally {current.set(null,previous);DailyRotation.reset();ChallengeRegistry.clear();}
    }
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
}
