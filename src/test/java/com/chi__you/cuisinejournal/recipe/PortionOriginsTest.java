package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ConfigEnums;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Shared preparations and separate consumption survive client sync and saved profile round-trips. */
public final class PortionOriginsTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        ResourceLocation whole=id("whole"),slice=id("slice"),crumb=id("crumb"),other=id("other"),group=id("_mod");
        check(PortionOrigins.resolve(Map.of(slice,Set.of(whole)),Set.of()).get(slice).equals(whole),"unique source binds automatically");
        check(PortionOrigins.resolve(Map.of(slice,Set.of(whole)),Set.of(slice)).isEmpty(),"independent cooking recipe blocks alias");
        check(PortionOrigins.resolve(Map.of(slice,Set.of(whole,other)),Set.of()).isEmpty(),"ambiguous parent never guessed");
        check(PortionOrigins.resolve(Map.of(slice,Set.of(whole),whole,Set.of(slice)),Set.of()).isEmpty(),"cycle rejected");
        check(PortionOrigins.resolve(Map.of(slice,Set.of(whole),whole,Set.of(slice),crumb,Set.of(slice)),Set.of()).isEmpty(),"chain into cycle rejected");
        check(PortionOrigins.resolve(Map.of(crumb,Set.of(slice),slice,Set.of(whole)),Set.of()).get(crumb).equals(whole),"chain resolves to stable original");
        check(PortionOrigins.resolve(Map.of(slice,Set.of(slice)),Set.of()).isEmpty(),"self binding rejected");
        check(PortionOrigins.resolve(Map.of(),Set.of(),Map.of(slice,whole)).get(slice).equals(whole),
                "configured non-block portion is linked without bites property");
        check(PortionOrigins.resolve(Map.of(slice,Set.of(other)),Set.of(),Map.of(slice,whole)).get(slice).equals(whole),
                "confirmed data rule replaces automatic source candidate");
        check(PortionOrigins.resolve(Map.of(),Set.of(slice),Map.of(slice,whole)).isEmpty(),
                "configured portion never replaces independent cooking");
        RecipeEntry preparation = recipe("bake", whole, other);
        RecipeEntry repack = recipe("repack", whole, slice);
        check(PortionOrigins.preparations(whole,List.of(preparation,repack),Map.of(slice,whole)).equals(List.of(preparation)),
                "only genuine whole preparation is exposed, not assembling its own slices");
        var newPages = new LinkedHashMap<ResourceLocation,List<RecipeEntry>>();
        var admitted = PortionOrigins.admit(newPages,Map.of(whole,List.of(preparation)),Map.of(slice,whole));
        check(admitted.equals(Map.of(slice,whole)) && newPages.containsKey(slice) && newPages.get(slice).isEmpty(),
                "unconfigured slice admitted before pages exist, with no fake cooking recipe");
        check(!newPages.containsKey(whole),"whole remains hidden when it is not edible");
        check(PortionOrigins.admit(newPages,Map.of(),Map.of(crumb,other)).isEmpty() && !newPages.containsKey(crumb),
                "missing or excluded parent recipe cannot admit a portion");
        var existingRecipes = List.of(preparation);
        newPages.put(slice,existingRecipes);
        PortionOrigins.admit(newPages,Map.of(whole,List.of(preparation)),Map.of(slice,whole));
        check(newPages.get(slice).equals(existingRecipes),"admission preserves existing page recipes");

        ClientJournalState.clear();
        var ordinary=new Packets.DishView(whole,"test",group,List.of(),1,0,group,id("dimension"));
        var portion=new Packets.DishView(slice,"test",group,List.of(),2,0,group,id("dimension"),whole);
        var second=new Packets.DishView(crumb,"test",group,List.of(),2,0,group,id("dimension"),whole);
        ClientJournalState.acceptCatalogue(new Packets.SyncCatalogue(true,true,List.of(ordinary,portion,second)));
        var parentRecord=new Packets.RecordView(whole,true,10,2,0,false,false,3,8,15);
        var sliceRecord=new Packets.RecordView(slice,true,99,7,1,true,false,1,2,99);
        ClientJournalState.acceptProfile(new Packets.SyncProfile(true,0,10,9,1,0,0,0,List.of(parentRecord,sliceRecord),List.of(),List.of()));
        check(ClientJournalState.record(slice).prepared()==10,"portion preparations read parent, never multiply by slices");
        check(ClientJournalState.record(slice).eaten()==7&&ClientJournalState.record(whole).eaten()==2,"consumption stays separate");
        check(ClientJournalState.record(slice).firstPreparedDay()==3&&ClientJournalState.record(slice).lastPreparedDay()==8,"preparation dates read parent");
        check(ClientJournalState.record(slice).masteryPoints()==15&&ClientJournalState.record(slice).favorite(),"parent mastery and own preference remain separate");
        check(ClientJournalState.categoryMasteryPoints(slice)==10,"whole and two portions count parent only once per subgroup");
        check(ClientJournalState.isDiscovered(crumb)&&ClientJournalState.discoveredCount()==3,"parent discovery reaches visible portions");
        ClientJournalState.acceptProfile(new Packets.SyncProfile(false,0,11,9,1,0,0,0,
                List.of(new Packets.RecordView(whole,true,11,2,0,false,false,3,9,16)),List.of(),List.of()));
        check(ClientJournalState.record(slice).prepared()==11&&ClientJournalState.record(slice).eaten()==7,"parent delta updates every portion without losing own consumption");
        ClientJournalState.acceptCatalogue(new Packets.SyncCatalogue(true,true,List.of(portion)));
        check(ClientJournalState.record(slice).prepared()==11&&ClientJournalState.dish(whole)==null,"hidden nonfood original still supplies preparation history");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var packet=new Packets.SyncCatalogue(true,true,List.of(portion));packet.write(buffer);
            check(Packets.SyncCatalogue.read(buffer).dishes().get(0).preparationTarget().equals(whole)&&buffer.readableBytes()==0,"source target survives packet round-trip");
        }finally{buffer.release();}

        var current=CulinaryRegistries.class.getDeclaredField("current");current.setAccessible(true);Object previous=current.get(null);
        var config=ConfigManager.server();var scope=config.mastery.scope;
        try {
            List<MealEntry> entries=List.of(meal(whole),meal(slice),meal(crumb));Map<ResourceLocation,MealEntry> map=new LinkedHashMap<>();entries.forEach(e->map.put(e.target(),e));
            var constructor=CulinaryRegistry.class.getDeclaredConstructor(Map.class,List.class,CulinaryAudit.class,List.class,Map.class,Map.class);constructor.setAccessible(true);
            CulinaryRegistry registry=(CulinaryRegistry)constructor.newInstance(map,entries,CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of());
            var origins=CulinaryRegistry.class.getDeclaredField("portionOrigins");origins.setAccessible(true);origins.set(registry,Map.of(slice,whole,crumb,whole));current.set(null,registry);
            CulinaryProfile profile=new CulinaryProfile(new UUID(1,2));
            profile.meal(whole).recordPreparation(3,10,CookingMethod.CRAFTING);profile.meal(whole).discover(3);profile.meal(whole).addMasteryPoints(15);
            profile.meal(slice).recordEaten(4,7);
            check(profile.categoryMasteryPoints(group,registry)==10,"server deduplicates same parent within subgroup");
            check(registry.isPortionOf(whole,slice),"tracked whole and tracked slice preserve separation inheritance");
            check(registry.isRepacking(whole,Set.of(slice,crumb)) && !registry.isRepacking(whole,Set.of(slice,other))
                    && !registry.isRepacking(whole,Set.of()),"only reconstructing own portions blocks preparation credit");
            check(profile.preparationRecord(slice).prepared()==10&&profile.peekMeal(slice).eaten()==7,"server preparation lookup never overwrites own consumption");
            config.mastery.scope=ConfigEnums.MasteryScope.DISH;
            check(profile.rankOf(slice,config.mastery.toCurve())==profile.rankOf(whole,config.mastery.toCurve()),"single dish stars shared with parent");
            var restored=CulinaryProfile.load(profile.playerId(),profile.save());
            check(restored.preparationRecord(slice).prepared()==10&&restored.peekMeal(slice).eaten()==7,"saved own and parent records remain independent");
            check(profile.peekMeal(crumb)==null&&profile.isDiscovered(crumb),"read-only alias never creates a fake portion preparation record");
            var diner=new CulinaryProfile(new UUID(8,9));
            check(diner.claimFirstMeal(slice) && !diner.claimFirstMeal(crumb),"linked forms share lifetime first meal reward");
            diner.meal(whole).addBonusMasteryPoints(4);
            diner.meal(whole).recordPreparation(2,10,CookingMethod.CRAFTING);
            check(diner.categoryMasteryPoints(group,registry)==14,"subcategory counts parent production and extra mastery once");
            var oldDiner=new CulinaryProfile(new UUID(9,10));oldDiner.meal(crumb).recordEaten(1,1);
            check(!oldDiner.claimFirstMeal(slice),"old portion consumption blocks new first-meal claim across aliases");
            var bonusRecord=new Packets.RecordView(whole,true,10,0,0,false,false,1,2,14,4);
            var bonusBuffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                new Packets.SyncProfile(true,0,10,0,0,0,0,0,List.of(bonusRecord),List.of(),List.of()).write(bonusBuffer);
                check(Packets.SyncProfile.read(bonusBuffer).records().get(0).equals(bonusRecord),
                        "extra mastery round trips independently of preparation count");
            } finally { bonusBuffer.release(); }
            ClientJournalState.acceptProfile(new Packets.SyncProfile(true,0,10,0,0,0,0,0,List.of(bonusRecord),List.of(),List.of()));
            check(ClientJournalState.categoryMasteryPoints(slice)==14 && ClientJournalState.record(slice).prepared()==10,
                    "client star progress includes bonus while production statistics stay unchanged");
            RecipeEntry wholeRecipe=new RecipeEntry(id("whole_recipe"),id("cooking"),CookingMethod.COOKING_POT,whole,1,
                    List.of(new RecipeEntry.IngredientSlot(List.of(other))),"test");
            MealEntry original=new MealEntry(whole,null,"test",List.of(wholeRecipe),Set.of(CookingMethod.COOKING_POT),
                    Set.of(other),FoodCategory.OTHER_FOOD,0,0);
            CulinaryRegistry directOnly=(CulinaryRegistry)constructor.newInstance(Map.of(whole,meal(whole)),List.of(meal(whole)),
                    CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of());
            var explicit=CulinaryRegistry.class.getDeclaredField("byExplicitItem");explicit.setAccessible(true);
            explicit.set(directOnly,Map.of(other,List.of(original)));
            var ingredientSources=CulinaryRegistry.class.getDeclaredField("ingredientSources");ingredientSources.setAccessible(true);
            ingredientSources.set(directOnly,Map.of(other,List.of(other)));
            current.set(null,directOnly);
            profile.ingredient(other).markKnown();
            profile.ingredient(other).recordUse(1,3,whole,CookingMethod.COOKING_POT);
            profile.ingredient(id("tag_only_member")).markKnown();
            profile.ingredient(id("tag_only_member")).recordUse(1,8,whole,CookingMethod.COOKING_POT);
            check(directOnly.listedIngredients().equals(Set.of(other)), "only explicit recipe input enters primary ingredient catalogue");
            check(profile.journalIngredientsDiscoveredCount()==1&&profile.journalIngredientUses()==3,
                    "journal statistics exclude detail-only and historical orphan records");
            check(profile.peekIngredient(id("tag_only_member")).timesUsed()==8,
                    "excluding ingredient from statistics does not erase stored usage");
            current.set(null,registry);
            CulinaryRegistry hidden=(CulinaryRegistry)constructor.newInstance(Map.of(slice,meal(slice)),List.of(meal(slice)),
                    CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of());
            origins.set(hidden,Map.of(slice,whole));
            var preparationOnly=CulinaryRegistry.class.getDeclaredField("preparationOnly");preparationOnly.setAccessible(true);
            preparationOnly.set(hidden,Map.of(whole,original));
            check(hidden.isTracked(whole)&&!hidden.targets().contains(whole)&&hidden.entries().size()==1,
                    "nonfood parent can be tracked for production without an extra journal page");
            check(hidden.preparationRecipes(slice).equals(List.of(wholeRecipe)),"slice recipe lookup points to original preparation");
            check(hidden.progressEntry(whole).recipes().equals(List.of(wholeRecipe))
                    &&hidden.progressEntry(whole).target().equals(slice),"hidden parent uses visible portion subgroup but real original recipe");
        }finally{current.set(null,previous);config.mastery.scope=scope;ClientJournalState.clear();}
        System.out.println("PortionOriginsTest: "+checks+" checks passed");
    }
    private static ResourceLocation id(String path){return ResourceLocation.parse("test:"+path);}
    private static RecipeEntry recipe(String path,ResourceLocation target,ResourceLocation input) {
        return new RecipeEntry(id(path),id("crafting"),CookingMethod.CRAFTING,target,1,
                List.of(new RecipeEntry.IngredientSlot(List.of(input))),"test");
    }
    private static MealEntry meal(ResourceLocation id){return new MealEntry(id,null,"test",List.of(),Set.of(),Set.of(),FoodCategory.OTHER_FOOD,0,0);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
