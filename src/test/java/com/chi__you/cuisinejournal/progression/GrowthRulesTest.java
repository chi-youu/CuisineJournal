package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.recipe.*;
import com.chi__you.cuisinejournal.food.CuisineStarBonuses;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class GrowthRulesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var config = new ServerConfig();
        verifySocialAndSpeed();
        verifyBatchXp();
        check(config.mastery.bonuses.masteryPerMealServedToOthers==1 && XpEngine.servedToOthersXp(config)==8,
                "another player's meal rewards one mastery point and eight cooking XP by default");
        config.mastery.enabled=false; config.mastery.bonuses.enabled=false;
        check(XpEngine.servedToOthersXp(config)==8,"cook XP does not depend on mastery or food bonuses being enabled");
        config.progression.xpMultiplier=2;
        check(XpEngine.servedToOthersXp(config)==16,"cook XP follows global experience multiplier");
        config.progression.enabled=false;
        check(XpEngine.servedToOthersXp(config)==0,"disabling cooking XP disables cook's meal reward");
        config.progression.enabled=true; config.progression.xpMultiplier=1;
        config.progression.baseXpPerMealServedToOthers=0;
        check(XpEngine.servedToOthersXp(config)==0,"cook meal XP can be disabled independently");
        config.progression.baseXpPerMealServedToOthers=8;
        config.mastery.enabled=true; config.mastery.bonuses.enabled=true;
        config.progression.experimentationDailyCap=10;
        var profile = new CulinaryProfile(new UUID(7,8));
        profile.addCookingXp(XpEngine.servedToOthersXp(config),config.progression.toCurve());
        check(CulinaryProfile.load(profile.playerId(),profile.save()).cookingXp()==8,
                "cook meal experience survives profile save without a live player");
        var input = new XpEngine.Inputs(1,0,false,false,0,1,0,0);
        var award = XpEngine.compute(input,config);
        check(award.experimentationXpAwarded()==8,"first experiment grants configured amount");
        profile.recordExperimentationXp(4,8);
        award=XpEngine.compute(new XpEngine.Inputs(1,0,false,false,0,1,profile.experimentationXpOn(4),0),config);
        check(award.experimentationXpAwarded()==2,"last experiment receives only remaining daily allowance");
        profile.recordExperimentationXp(4,2);
        check(XpEngine.compute(new XpEngine.Inputs(1,0,false,false,0,1,10,0),config).experimentationXpAwarded()==0,
                "daily allowance is shared across experiments");
        var loaded=CulinaryProfile.load(profile.playerId(),profile.save());
        check(loaded.experimentationXpOn(4)==10 && loaded.experimentationXpOn(5)==0,"allowance persists through save and resets by day");
        config.progression.experimentationDailyLimit=false;
        check(XpEngine.compute(new XpEngine.Inputs(1,0,false,false,0,2,10,0),config).experimentationXpAwarded()==16,
                "disabling cap preserves variant rewards");
        config.progression.experimentationDailyLimit=true; config.progression.xpMultiplier=2;
        check(XpEngine.compute(input,config).experimentationXpAwarded()==8,"daily budget precedes global XP multiplier");
        config.progression.enabled=false;
        check(XpEngine.compute(input,config).experimentationXpAwarded()==0,"disabled XP does not consume allowance");
        var dish=ResourceLocation.parse("test:dish");
        var record=new MealRecord(dish);
        record.recordPreparation(1,10,CookingMethod.CRAFTING);
        record.addMasteryPoints(10); record.addBonusMasteryPoints(3);
        check(record.prepared()==10 && record.bonusMasteryPoints()==3 && record.masteryPoints()==13,
                "extra mastery never fabricates preparation count");
        check(record.claimFirstMeal() && !record.claimFirstMeal(),"first meal is lifetime once");
        var restored=MealRecord.load(dish,record.save());
        check(!restored.claimFirstMeal() && restored.bonusMasteryPoints()==3,"first meal and bonus survive save");
        var legacy=record.save();legacy.remove("firstMealClaimed");legacy.putLong("eaten",1);
        check(!MealRecord.load(dish,legacy).claimFirstMeal(),"old eaten records cannot claim new first meal reward");
        check(CuisineStarBonuses.scaled(20,0.05,2)==22 && CuisineStarBonuses.duration(100,0.1,3)==130,
                "star base-value modifiers scale linearly");
        var stages=FlavorStage.read(JsonParser.parseString("""
            [{"min_stars":0,"effects":[{"id":"minecraft:poison","level":1,"duration_seconds":10}]},
             {"min_stars":2,"effects":[{"id":"minecraft:strength","level":1}]},
             {"min_stars":4,"effects":[{"id":"minecraft:strength","level":2}]}]
            """).getAsJsonArray());
        check(FlavorStage.select(stages,1).get(0).id().getPath().equals("poison"),"low star stage keeps negative effect");
        check(FlavorStage.select(stages,2).size()==1 && FlavorStage.select(stages,2).get(0).id().getPath().equals("strength"),
                "higher stage replaces negative effect rather than stacking old stage");
        check(FlavorStage.select(stages,5).get(0).level()==2,"high star stage upgrades positive effect");
        check(FlavorStage.select(stages.subList(1,3),0).isEmpty(),"locked flavor has no effect below first threshold");
        checkInvalid("""
            [{"min_stars":2.5,"effects":[]}]
            """, "fractional star stages rejected");
        checkInvalid("""
            [{"min_stars":6,"effects":[]}]
            """, "unreachable stages rejected");
        checkInvalid("""
            [{"min_stars":0,"effects":[]},{"min_stars":0,"effects":[]}]
            """, "duplicate stages rejected");
        check(FlavorStage.select(List.of(stages.get(0),new FlavorStage(3,List.of())),4).isEmpty(),
                "empty high stage can remove all flavor effects");
        var rules=IngredientCompatRules.resolve(List.of(new IngredientCompatRules.RuleFile(
                ResourceLocation.parse("test:cuisinejournal/compat/stages.json"),"test",
                JsonParser.parseString("""
                {"tag_flavor":{"forge:eggs":{"source":true,"stages":[{"min_stars":3,"effects":[]}]}}}
                """).getAsJsonObject())),mod->true,null);
        check(rules.tagFlavor(ResourceLocation.parse("forge:eggs")).stages().get(0).minStars()==3,
                "stages are supported by existing datapack flavor rules");
        var directory=java.nio.file.Files.createTempDirectory("growth-flavor-config");
        var file=directory.resolve("cuisinejournal-ingredients.json");
        try {
            java.nio.file.Files.writeString(file,"""
                {"flavor":{"test:fruit":{"source":true,"stages":[{"min_stars":2,
                "effects":[{"id":"minecraft:strength","level":2,"duration_seconds":30}]}]}}}
                """);
            IngredientCatalogueConfig.load(directory);
            check(IngredientCatalogueConfig.current().flavor(ResourceLocation.parse("test:fruit")).stages().get(0)
                    .effects().get(0).durationSeconds()==30,"local flavor parses staged effects");
            IngredientCatalogueConfig.load(directory);
            check(IngredientCatalogueConfig.current().flavor(ResourceLocation.parse("test:fruit")).stages().get(0)
                    .effects().get(0).id().toString().equals("minecraft:strength"),"config save/reload preserves effect IDs and stage schema");
            var references=List.of("test:fruit","test:fruit");
            check(com.chi__you.cuisinejournal.food.CuisineFlavorEffects.resolveSources(references,1).isEmpty(),
                    "runtime flavor binding respects star gate");
            var resolved=com.chi__you.cuisinejournal.food.CuisineFlavorEffects.resolveSources(references,3);
            check(resolved.size()==1 && resolved.get(0).level()==2 && resolved.get(0).durationTicks()==600,
                    "runtime flavor binding deduplicates and retains real level and duration");
            check(com.chi__you.cuisinejournal.food.CuisineFlavorEffects.resolveSources(List.of("test:missing"),5).isEmpty(),
                    "unconfigured source cannot invent runtime flavor");
        } finally {java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(directory);}
        System.out.println("GrowthRulesTest: "+checks+" checks passed");
    }
    private static void check(boolean value,String description){if(!value)throw new AssertionError(description);checks++;}
    private static void verifyBatchXp() {
        var config = new ServerConfig();
        var batch = XpEngine.compute(new XpEngine.Inputs(20,0,false,false,0,0,0,0),config);
        check(batch.base()==120&&batch.total()==60,"batch XP spans full, 75 percent, 50 percent and floor tiers");
        check(Math.abs(batch.repetitionMultiplier()-0.5)<1e-9,"batch reports weighted average multiplier");
        var offset=XpEngine.compute(new XpEngine.Inputs(11,4,false,false,0,0,0,0),config);
        check(offset.total()==35,"batch beginning inside a tier crosses next boundary correctly");
        var discovery=XpEngine.compute(new XpEngine.Inputs(20,0,true,true,2,1,0,0),config);
        check(discovery.total()==60+75+25+20+8,"one-time discovery and experiment rewards are outside repeat decay");
        config.progression.baseXpPerPreparation=20;
        long individual=0;
        for(int prior=0;prior<40;prior++)individual+=XpEngine.compute(new XpEngine.Inputs(1,prior,false,false,0,0,0,0),config).total();
        var whole=XpEngine.compute(new XpEngine.Inputs(40,0,false,false,0,0,0,0),config);
        check(whole.total()==individual,"integer-valued per-tier rewards match individual extraction totals");
        config.progression.xpMultiplier=2;config.mastery.xpBonusPerMasteredDish=0.1;config.mastery.xpBonusCap=0.5;
        var multiplied=XpEngine.compute(new XpEngine.Inputs(40,0,false,false,0,0,0,3),config);
        check(multiplied.total()==Math.round(whole.total()*1.3*2),"global and mastery multipliers apply after segmented base reward");
        var late=XpEngine.compute(new XpEngine.Inputs(64,100,false,false,0,0,0,0),new ServerConfig());
        check(late.total()==77,"batch entirely after last tier uses the floor");
        var overflow=XpEngine.compute(new XpEngine.Inputs(64,Integer.MAX_VALUE,false,false,0,0,0,0),new ServerConfig());
        check(overflow.total()==77,"large prior counts cannot overflow into early reward tiers");
        config=new ServerConfig();config.antiFarming.enabled=false;
        check(XpEngine.compute(new XpEngine.Inputs(64,0,false,false,0,0,0,0),config).total()==384,"disabled repeat decay pays full batch XP");
        config.antiFarming.enabled=true;config.antiFarming.tiers=List.of();config.antiFarming.fullXpPreparations=3;
        check(XpEngine.compute(new XpEngine.Inputs(5,0,false,false,0,0,0,0),config).total()==20,"empty tiers cross directly from full rewards to floor");
        config.antiFarming.fullXpPreparations=0;
        check(XpEngine.compute(new XpEngine.Inputs(5,0,false,false,0,0,0,0),config).total()==6,"zero full-reward allowance starts at floor");
        config.progression.enabled=false;
        check(XpEngine.compute(new XpEngine.Inputs(Integer.MAX_VALUE,0,true,true,2,1,0,0),config).total()==0,"disabled XP bypasses batch calculations");
    }
    private static void verifySocialAndSpeed() {
        var config = new ServerConfig();
        var dish = ResourceLocation.parse("test:stew");
        var other = ResourceLocation.parse("test:pie");
        var social = new SocialRewards();
        int mastery=0;long xp=0;
        for(int i=1;i<=70;i++) {
            var award=social.claim(1,dish,config); mastery+=award.mastery();xp+=award.xp();
            check(award.multiplier()==(i<=3?1:i<=10?0.5:i<=20?0.25:0.1),"social tier boundary "+i);
            if(i==10)check(mastery==6&&xp==52,"half rewards accumulate one mastery per two meals");
            if(i==20)check(mastery==9&&xp==72,"quarter rewards retain incomplete mastery");
        }
        check(mastery==14&&xp==112,"minimum rewards accumulate without rounding loss");
        social=SocialRewards.load(social.save());
        check(social.mealsOn(1)==70&&social.claim(1,other,config).mastery()==0,"daily count crosses dishes and survives save");
        check(social.claim(2,dish,config).multiplier()==1,"new Minecraft day resets decay");
        config.socialRewards.fullMeals=0;config.socialRewards.halfMeals=100;
        social=new SocialRewards();
        check(social.claim(1,dish,config).mastery()==0,"fractional dish credit waits for whole point");
        social=SocialRewards.load(social.save());
        check(social.claim(2,other,config).mastery()==0,"another dish cannot steal fractional mastery");
        check(social.claim(2,dish,config).mastery()==1,"earned fractions persist through save and day change");
        config.mastery.enabled=false;config.progression.enabled=false;
        check(social.claim(2,dish,config).xp()==0,"disabled growth does not pay or bank fresh XP");
        config.mastery.enabled=true;config.progression.enabled=true;
        config.socialRewards.dailyDecay=false;
        check(social.claim(2,dish,config).mastery()==1,"decay can be disabled independently");
        var profile=new CulinaryProfile(new UUID(11,12));
        profile.claimSocialReward(1,dish,config);
        var saved=CulinaryProfile.load(profile.playerId(),profile.save());
        check(saved.save().getCompound("socialRewards").getLong("meals")==1,"profile owns persisted social counts");
        var budget=new ProgressBudget();
        check(budget.advance(0,1,100,0.5)==1&&budget.advance(1,2,100,0.5)==3,"fractional speed is deterministic");
        check(budget.advance(98,99,100,20)==100,"speed lands exactly at finish equality");
        check(budget.advance(80,78,100,20)==78&&budget.advance(80,0,100,20)==0,"cooling and reset remain native");
        check(budget.advance(0,1,100,0)==1&&budget.advance(0,1,100,Double.NaN)==1,"disabled and invalid rates do nothing");
        var twoStep=new ProgressBudget();
        check(twoStep.advance(0,2,100,0.5)==3,"native two-tick stove step scales proportionally");
        verifyVanillaNutrition();
    }
    private static void verifyVanillaNutrition() {
        var normal=new MealStamp(5,false,5,null,"");
        var perfect=new MealStamp(4,true,5,null,"");
        var plain=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(8,0.6F,null,0.05,1,1.5);
        check(plain.nutrition()==8&&plain.saturationModifier()==0.6F,"unstamped food retains exact native nutrition");
        var max=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(8,0.6F,normal,0.05,1,1.5);
        check(max.nutrition()==8&&Math.abs(2*8*max.saturationModifier()-13)<0.00001,"five stars gives coefficient plus 25 percent and one actual saturation");
        var quality=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(8,0.6F,perfect,0.05,1,1.5);
        check(perfect.effectiveStars()==5&&quality.nutrition()==12&&Math.abs(2*12*quality.saturationModifier()-19)<0.00001,
                "perfect upgrades effective star and nutrition; flat one saturation is not multiplied");
        var low=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(3,0.5F,new MealStamp(2,true,5,null,""),0.05,1,1.5);
        check(low.nutrition()==5&&Math.abs(low.saturationModifier()-0.575)<0.00001,"perfect odd nutrition rounds nearest and uses effective three stars");
        var zero=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(0,0,normal,0.05,1,1.5);
        check(zero.nutrition()==0&&Float.isFinite(zero.saturationModifier()),"zero nutrition cannot divide by zero");
        var reset=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(8,0.6F,perfect,0,0,1);
        check(reset.nutrition()==8&&reset.saturationModifier()==0.6F,"all vanilla quality bonuses can be disabled by configuration");
        check(new MealStamp(5,true,5,null,"").effectiveStars()==5,"perfect at max stars still caps at five");
        boolean[] restored={false};
        try {
            com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.itemApplication(()-> {
                check(com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.applyingItem(),"item overload suppresses double placed-food bonus");
                com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.itemApplication(()-> {});
                restored[0]=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.applyingItem();
                throw new IllegalStateException("host failed");
            });
        } catch(IllegalStateException expected) {}
        check(restored[0]&&!com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.applyingItem(),"nested and failed host nutrition clear the exact scope");
    }
    private static void checkInvalid(String json,String message) {
        try { FlavorStage.read(JsonParser.parseString(json).getAsJsonArray()); }
        catch (IllegalArgumentException failure) { checks++; return; }
        throw new AssertionError(message);
    }
}
