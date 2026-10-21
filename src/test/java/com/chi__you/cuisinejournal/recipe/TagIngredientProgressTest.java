package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.nio.file.*;

public final class TagIngredientProgressTest {
    private static int checks;
    private static final ResourceLocation BEEF=id("beef"), PEPPER=id("stuffed_pepper"),
            PIE=id("pie"), OTHER=id("unrelated_dish"), SLICE=id("pie_slice");
    public static void main(String[] args) throws Exception {
        var profile=new CulinaryProfile(UUID.randomUUID());
        profile.ingredient(BEEF).markKnown();
        profile.ingredient(BEEF).recordUse(14,30,OTHER,CookingMethod.CRAFTING);
        profile.meal(OTHER).recordPreparation(14,30,CookingMethod.CRAFTING);
        var unmade=progress(profile,List.of(PEPPER));
        check(!unmade.known()&&unmade.timesUsed()==0,"using member thirty times in unrelated dish never discovers tag");
        check(unmade.firstUsedDay()==Long.MIN_VALUE&&unmade.lastUsedDay()==Long.MIN_VALUE&&unmade.methods().isEmpty(),
                "unmade tag cannot inherit dates or workstation from members");
        profile.meal(PEPPER).discover(15);
        profile.meal(PEPPER).recordEaten(15,2);
        profile.meal(PEPPER).recordServed(3);
        check(!progress(profile,List.of(PEPPER)).known(),"obtaining, eating, serving or discovering dish is not preparing it");
        profile.meal(PEPPER).recordPreparation(17,4,CookingMethod.COOKING_POT);
        var first=progress(profile,List.of(PEPPER));
        check(first.known()&&first.timesUsed()==4,"first actual related preparation discovers tag and credits output amount");
        check(first.firstUsedDay()==17&&first.lastUsedDay()==17,"dates follow tagged dish preparation not old ingredient history");
        check(first.methods().equals(List.of(CookingMethod.COOKING_POT.id())),"methods come only from tagged dishes");
        profile.meal(PEPPER).recordPreparation(20,2,CookingMethod.COOKING_POT);
        profile.meal(PIE).recordPreparation(18,3,CookingMethod.CRAFTING);
        var merged=progress(profile,List.of(PEPPER,PEPPER,PIE,PIE));
        check(merged.timesUsed()==9,"repeated slots, aliases and same dish across merged tags never multiply count");
        check(merged.firstUsedDay()==17&&merged.lastUsedDay()==20&&merged.methods().size()==2,"linked dishes combine date bounds and unique methods");
        var aliased=TagIngredientProgress.collect(List.of(PIE,SLICE,SLICE),
                dish->dish.equals(SLICE)?PIE:dish,profile::peekMeal);
        check(aliased.timesUsed()==3,"portion and whole refer to same preparation only once");
        check(profile.peekMeal(SLICE)==null,"reading tag progress never creates phantom meal records");
        var restored=CulinaryProfile.load(profile.playerId(),profile.save());
        check(progress(restored,List.of(PEPPER,PIE)).equals(merged),"old persisted dish records suffice without resetting history");
        check(!progress(restored,List.of(id("new_tag_only_dish"))).known(),"adding tag member or unrelated tag creates no usage");
        check(progress(restored,List.of(PEPPER)).timesUsed()==6,"removing merged relation removes only that dish's contribution");
        var large=profile.meal(PIE).save();large.putLong("prepared",Long.MAX_VALUE);
        var huge=MealRecord.load(PIE,large);
        check(TagIngredientProgress.collect(List.of(PEPPER,PIE),dish->dish,
                dish->dish.equals(PIE)?huge:profile.peekMeal(dish)).timesUsed()==Long.MAX_VALUE,"aggregation saturates at long max");
        var tagId=id("raw_meat");var anotherTag=id("other_meat_use");
        var tagSlot=new RecipeEntry.IngredientSlot(List.of(BEEF),List.of(),List.of(tagId));
        var explicitSlot=new RecipeEntry.IngredientSlot(List.of(BEEF));
        var rules=IngredientCompatRules.resolve(List.of(new IngredientCompatRules.RuleFile(id("keep"),"test",
                com.google.gson.JsonParser.parseString("{\"merge_tags\":[{\"id\":\"test:raw_meat\",\"tags\":[\"test:raw_meat\"]}]}").getAsJsonObject())),mod->true,null);
        var index=IngredientJournalIndex.build(List.of(new IngredientJournalIndex.Input(PEPPER,tagSlot),
                new IngredientJournalIndex.Input(OTHER,explicitSlot)),rules,tagKey->List.of(BEEF),item->true,key->false);
        check(index.tagDishes().get(tagId).equals(Set.of(PEPPER)),"shared member with explicit-item recipe does not add unrelated dish to tag");
        check(TagIngredientProgress.collect(index.tagDishes().get(tagId),dish->dish,profile::peekMeal).timesUsed()==6,
                "real reverse index counts only tagged recipe dishes, not member uses");
        var tag=id("pepper_ingredients");
        var view=new Packets.IngredientView(tag,first.known(),first.timesUsed(),first.firstUsedDay(),first.lastUsedDay(),
                1,0,(byte)3,List.of(),false,null,first.methods(),List.of(PEPPER),true,List.of(BEEF),List.of(tag));
        var delta=new JournalSyncPackets.IngredientProgress(tag,true,merged.known(),merged.timesUsed(),
                merged.firstUsedDay(),merged.lastUsedDay(),0,merged.methods());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet=new JournalSyncPackets.Progress(1,1,true,List.of(),List.of(delta),List.of(),List.of(),List.of());
            packet.write(buffer);
            var copy=JournalSyncPackets.Progress.read(buffer);
            check(copy.equals(packet)&&buffer.readableBytes()==0,"tag count and dates survive existing progress wire format");
            var updated=copy.ingredients().get(0).apply(view);
            check(updated.known()&&updated.timesUsed()==9&&updated.firstUsedDay()==17&&updated.lastUsedDay()==20,"delta updates tag's own authoritative fields");
        } finally { buffer.release(); }
        contracts();
        System.out.println("TagIngredientProgressTest: "+checks+" checks passed");
    }
    private static IngredientProgress progress(CulinaryProfile profile,List<ResourceLocation> dishes) {
        return TagIngredientProgress.collect(dishes,dish->dish,profile::peekMeal);
    }
    private static void contracts() throws Exception {
        var server=Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/network/ServerPacketHandler.java"));
        var sync=Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/network/JournalSyncServer.java"));
        check(server.contains("TagIngredientProgress.collect(registry, tag.key(), profile::peekMeal)")
                &&sync.contains("TagIngredientProgress.collect(registry, template.item(), profile::peekMeal)"),
                "initial catalogue and delta sync share the same tag policy");
        var client=Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/client/journal/page/IngredientsPage.java"));
        int start=client.indexOf("private static long usedTimes");
        var helper=client.substring(start,client.indexOf("private void stars",start));
        check(helper.contains("return value.timesUsed();")&&!helper.contains("members()"),"client no longer sums member use counts");
        int dates=client.indexOf("private Component dates");
        check(!client.substring(dates,start).contains("members()"),"client no longer inherits member use dates");
    }
    private static ResourceLocation id(String path){return ResourceLocation.parse("test:"+path);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
