package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.*;
import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.recipe.*;
import com.chi__you.cuisinejournal.network.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class JournalFeaturesTest {
    private static int checks;
    private static ResourceLocation id(String name){return ResourceLocation.parse("test:"+name);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void close(double a,double b){check(Math.abs(a-b)<1e-8,"expected "+a+", actual "+b);}
    public static void main(String[]args)throws Exception{
        var a=id("a");var b=id("b");
        var needs=List.of(new RecipeTracking.Need(List.of(a,b),1),new RecipeTracking.Need(List.of(a),1));
        check(Arrays.equals(RecipeTracking.allocate(needs,Map.of(a,1,b,1)),new int[]{1,1}),"overlapping alternatives can reassign an earlier allocation");
        check(Arrays.stream(RecipeTracking.allocate(needs,Map.of(a,1))).sum()==1,"one item cannot satisfy two slots");
        check(Arrays.equals(RecipeTracking.allocate(List.of(new RecipeTracking.Need(List.of(a,b),4)),Map.of(a,2,b,3)),new int[]{4}),"mixed members satisfy tag requirement");
        check(Arrays.equals(RecipeTracking.allocate(needs,Map.of()),new int[]{0,0}),"empty inventory");
        check(RecipeTracking.batches(5,4)==2&&RecipeTracking.batches(4,4)==1,"output portions round up to whole recipe batches");
        check(SignatureDishService.mayChange(false,Long.MIN_VALUE,2)&&!SignatureDishService.mayChange(true,2,2)
                &&!SignatureDishService.mayChange(true,3,2)&&SignatureDishService.mayChange(true,2,3),"first selection, same day, backward date and next day policy");
        var profile=new CulinaryProfile(new UUID(1,2));profile.selectSignature(a,3);
        var restored=CulinaryProfile.load(profile.playerId(),profile.save());
        check(restored.signatureDish().equals(a)&&restored.signatureChosen()&&restored.signatureChangedDay()==3,"signature selection and daily restriction persist");
        restored.selectSignature(null,4);var cleared=CulinaryProfile.load(restored.playerId(),restored.save());
        check(cleared.signatureDish()==null&&cleared.signatureChosen()&&!SignatureDishService.mayChange(true,cleared.signatureChangedDay(),4),"clearing cannot restore first-choice exemption");
        check(profile.trackRecipe(a,id("recipe1"),5,2)&&profile.trackRecipe(b,id("recipe2"),1,2),"tracks within limit accepted");
        check(!profile.trackRecipe(id("c"),id("recipe3"),4,2),"new dish refused when the limit is reached");
        check(profile.recipeTracks().containsKey(a)&&profile.recipeTracks().containsKey(b)&&!profile.recipeTracks().containsKey(id("c"))
                &&!profile.pinned().contains(id("c"))&&(profile.peekMeal(id("c"))==null||!profile.peekMeal(id("c")).isPinned()),"full list keeps existing tracks and pins nothing new");
        check(profile.trackRecipe(a,id("recipe9"),7,2)&&profile.recipeTracks().get(a).equals(new CulinaryProfile.RecipeTrack(id("recipe9"),7))
                &&new ArrayList<>(profile.recipeTracks().keySet()).equals(List.of(a,b)),"tracked dish may switch recipe and count at the limit, order kept");
        profile.trackRecipe(a,id("recipe1"),5,2);
        check(profile.trackRecipe(a,id("recipe1"),6,1)&&profile.recipeTracks().size()==2,"lowered limit keeps existing tracks and allows editing them");
        restored=CulinaryProfile.load(profile.playerId(),profile.save());
        check(restored.recipeTracks().equals(profile.recipeTracks()),"chosen recipe and target survive restart");
        restored.trackRecipe(b,null,0,2);check(!restored.recipeTracks().containsKey(b)&&!restored.peekMeal(b).isPinned(),"cancel updates both old and new pin data");
        var slot=new JournalFeaturePackets.Slot(List.of(a,b),id("tag"),8);
        var tracked=new JournalFeaturePackets.Tracked(a,id("recipe"),id("method"),5,4,List.of(slot));
        var packet=new JournalFeaturePackets.Sync(a,true,3,4,.1,5,true,List.of(tracked),3);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{packet.write(buffer);check(Network.decode(packet.id(),buffer).equals(packet)&&buffer.readableBytes()==0,"feature state packet preserves full alternatives and counts");
            check(roundTrip(packet).trackLimit()==3,"track limit synced to client");
            var request=new JournalFeaturePackets.Track(a,id("recipe"),5);request.write(buffer);check(Network.decode(request.id(),buffer).equals(request),"tracking request registered");
            var clear=new JournalFeaturePackets.Signature(null);clear.write(buffer);check(Network.decode(clear.id(),buffer).equals(clear),"nullable signature request registered");
        }finally{buffer.release();}
        eligibility();
        undiscoveredTracking();
        tagTranslations();
        System.out.println("JournalFeaturesTest: "+checks+" checks passed");
    }
    private static void tagTranslations() {
        var key=id("raw/meat"); var alias=id("other");
        var tag=new Packets.IngredientView(key,false,0,0,0,0,0,(byte)3,List.of(),false,null,List.of(),List.of(),
                true,List.of(),List.of(alias),null);
        var filter=com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.ONLY;
        check(com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.untranslated(tag,s->false),"raw ID fallback is untranslated");
        check(!com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.untranslated(tag,s->s.equals("tag.item.test.raw.meat")),"primary translation counts including slash paths");
        check(!com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.untranslated(tag,s->s.equals("tag.item.test.other")),"merged alias translation counts");
        var named=new Packets.IngredientView(key,false,0,0,0,0,0,(byte)3,List.of(),false,null,List.of(),List.of(),
                true,List.of(),List.of(),"Raw Meat");
        check(!com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.untranslated(named,s->false),"configured English names are not mistaken for missing names");
        var item=new Packets.IngredientView(key,false,0,0,0,0,0,(byte)1,List.of(),false,null,List.of());
        check(!com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.untranslated(item,s->false),"ordinary items never become untranslated tag candidates");
        check(filter.accepts(true)&&!filter.accepts(false),"only missing tags retained");
        check(com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.NORMAL.accepts(false)
                &&com.chi__you.cuisinejournal.client.journal.TagTranslationFilter.FIRST.accepts(false),"normal and priority modes preserve translated entries");
    }
    private static JournalFeaturePackets.Sync roundTrip(JournalFeaturePackets.Sync packet){
        var b=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{packet.write(b);return (JournalFeaturePackets.Sync)Network.decode(packet.id(),b);}finally{b.release();}
    }
    private static void undiscoveredTracking()throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);bootstrap.setBoolean(null,true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        var config=ConfigManager.server();boolean hidden=config.discovery.hideUndiscoveredDetails;
        int limit=config.advanced.maxPinnedRecipes;
        var current=CulinaryRegistries.class.getDeclaredField("current");current.setAccessible(true);Object previous=current.get(null);
        var constructor=CulinaryRegistry.class.getDeclaredConstructor(Map.class,List.class,CulinaryAudit.class,List.class,Map.class,Map.class);
        constructor.setAccessible(true);
        var dish=id("unseen_dish");var recipeId=id("unseen_recipe");
        var recipe=new RecipeEntry(recipeId,id("crafting"),CookingMethod.CRAFTING,dish,2,
                List.of(new RecipeEntry.IngredientSlot(List.of(id("ingredient")))),"test");
        var meal=new MealEntry(dish,null,"test",List.of(recipe),Set.of(CookingMethod.CRAFTING),Set.of(id("ingredient")),FoodCategory.OTHER_FOOD,4,.5F);
        try{
            current.set(null,constructor.newInstance(Map.of(dish,meal),List.of(meal),CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of()));
            config.discovery.hideUndiscoveredDetails=false;config.advanced.maxPinnedRecipes=1;
            var profile=new CulinaryProfile(new UUID(5,6));
            check(!profile.isDiscovered(dish),"regression starts with undiscovered dish");
            check(JournalFeatureHandler.updateTracking(profile,new JournalFeaturePackets.Track(dish,recipeId,3))
                    ==JournalFeatureHandler.TrackResult.APPLIED,"visible undiscovered recipe can be tracked by actual handler policy");
            check(profile.recipeTracks().get(dish).equals(new CulinaryProfile.RecipeTrack(recipeId,3)),"chosen recipe and portions stored");
            check(!profile.isDiscovered(dish)&&profile.peekMeal(dish).prepared()==0&&profile.cookingXp()==0,
                    "tracking grants no discovery, preparation or XP");
            check(JournalFeatureHandler.updateTracking(profile,new JournalFeaturePackets.Track(dish,id("wrong_recipe"),3))
                    ==JournalFeatureHandler.TrackResult.REJECTED,"client cannot assign unrelated recipe");
            check(JournalFeatureHandler.updateTracking(profile,new JournalFeaturePackets.Track(dish,recipeId,4097))
                    ==JournalFeatureHandler.TrackResult.REJECTED,"count bounds remain enforced");
            config.discovery.hideUndiscoveredDetails=true;
            check(!JournalFeatureHandler.trackingVisible(profile,dish),"hidden undiscovered track cannot leak HUD ingredients");
            check(JournalFeatureHandler.updateTracking(profile,new JournalFeaturePackets.Track(dish,recipeId,4))
                    ==JournalFeatureHandler.TrackResult.REJECTED,"hidden-details rule also protects tracking requests");
            check(profile.recipeTracks().get(dish).target()==3,"rejected edit preserves stored target");
            check(JournalFeatureHandler.updateTracking(profile,new JournalFeaturePackets.Track(dish,null,0))
                    ==JournalFeatureHandler.TrackResult.APPLIED,"hidden existing track can still be cancelled");
            check(profile.recipeTracks().isEmpty()&&!profile.isDiscovered(dish),"cancellation remains discovery-neutral");
            profile.meal(dish).discover(1);
            check(JournalFeatureHandler.updateTracking(profile,new JournalFeaturePackets.Track(dish,recipeId,2))
                    ==JournalFeatureHandler.TrackResult.APPLIED,"discovered recipe works with hidden-details enabled");
        }finally{config.discovery.hideUndiscoveredDetails=hidden;config.advanced.maxPinnedRecipes=limit;current.set(null,previous);}
    }
        private static void eligibility()throws Exception{
        var config=ConfigManager.server();var oldScope=config.mastery.scope;boolean enabled=config.mastery.enabled;double bonus=config.signatureDish.perfectChanceBonus;
        var field=CulinaryRegistries.class.getDeclaredField("current");field.setAccessible(true);Object old=field.get(null);
        var constructor=CulinaryRegistry.class.getDeclaredConstructor(Map.class,List.class,CulinaryAudit.class,List.class,Map.class,Map.class);constructor.setAccessible(true);
        try{
            config.mastery.enabled=true;config.mastery.scope=ConfigEnums.MasteryScope.DISH;config.signatureDish.perfectChanceBonus=.1;
            var dish=id("dish");var recipe=new RecipeEntry(id("recipe"),id("crafting"),CookingMethod.CRAFTING,dish,4,List.of(),"test");
            var meal=new MealEntry(dish,null,"test",List.of(recipe),Set.of(CookingMethod.CRAFTING),Set.of(),FoodCategory.OTHER_FOOD,6,.6F);
            field.set(null,constructor.newInstance(Map.of(dish,meal),List.of(meal),CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of()));
            var p=new CulinaryProfile(new UUID(3,4));p.meal(dish).discover(1);p.meal(dish).addMasteryPoints(1000);
            check(!SignatureDishService.eligible(p,dish),"discovery and mastery alone cannot stand in for personal preparation");
            p.meal(dish).recordPreparation(1,1,CookingMethod.CRAFTING);check(SignatureDishService.eligible(p,dish),"personally prepared mastered recipe qualifies");
            p.selectSignature(dish,1);close(.1,SignatureDishService.bonus(p,dish));close(0,SignatureDishService.bonus(p,id("other")));
            close(.28,ProductionQuality.chance(.08+SignatureDishService.bonus(p,dish),true,11,.01));
            config.mastery.enabled=false;close(0,SignatureDishService.bonus(p,dish));
            config.mastery.enabled=true;p.setSkillsActive(true);p.setSkillBonuses(new net.minecraft.nbt.CompoundTag());
            check(!SignatureDishService.eligible(p,dish),"lowered skill ceiling pauses signature bonus without deleting selection");
            check(p.signatureDish().equals(dish),"inactive signature remains selected");
            var parse=Toml.read("[signatureDish]\nperfectChanceBonus = 0.2\nchangeExperienceLevels = 7\n",ServerConfig.class);parse.validate();
            check(parse.signatureDish.perfectChanceBonus==.2&&parse.signatureDish.changeExperienceLevels==7,"signature bonus and fee configurable");
        }finally{config.mastery.scope=oldScope;config.mastery.enabled=enabled;config.signatureDish.perfectChanceBonus=bonus;field.set(null,old);}
    }
}
