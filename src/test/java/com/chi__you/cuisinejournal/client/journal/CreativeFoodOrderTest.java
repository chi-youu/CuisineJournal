package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.resources.ResourceLocation;
import com.chi__you.cuisinejournal.network.Packets;

import java.util.ArrayList;
import java.util.List;

/** Check ordering independently of Minecraft's creative-tab generation. */
public final class CreativeFoodOrderTest {
    public static void main(String[] args) {
        checkModSearch();
        checkDishSearch();
        ResourceLocation a = id("first:zzz"), b = id("first:aaa"), c = id("second:drink");
        ResourceLocation hiddenA = id("first:hidden_a"), hiddenZ = id("first:hidden_z");
        var positions = CreativeFoodOrder.firstPositions(List.of(List.of(c, a, b), List.of(b, a)));
        require(positions.get(a) == 1 && positions.get(b) == 2, "first creative-tab occurrence wins");
        var items = new ArrayList<>(List.of(hiddenZ, c, b, hiddenA, a));
        items.sort(CreativeFoodOrder.comparator(positions));
        require(items.equals(List.of(c, a, b, hiddenA, hiddenZ)),
                "mods follow first creative occurrence, absent items last within their mod");
        items.sort(CreativeFoodOrder.comparator(positions));
        require(items.equals(List.of(c, a, b, hiddenA, hiddenZ)), "ordering is stable");
        require(CreativeFoodOrder.firstPositions(List.of()).isEmpty(), "empty creative tabs supported");
        ResourceLocation vanilla = id("minecraft:apple"), fdFirst = id("farmersdelight:zzz"),
                fdSecond = id("farmersdelight:aaa"), addon = id("a_addon:food");
        var rankedPositions = CreativeFoodOrder.firstPositions(List.of(List.of(addon, fdFirst, fdSecond, vanilla)));
        var ranked = new ArrayList<>(List.of(fdSecond, addon, vanilla, fdFirst));
        ranked.sort(CreativeFoodOrder.comparator(rankedPositions, List.of("minecraft", "farmersdelight")));
        require(ranked.equals(List.of(vanilla, fdFirst, fdSecond, addon)),
                "Minecraft then Farmers Delight, creative positions retained within each mod");
        var fallback = new ArrayList<>(List.of(c, a));
        fallback.sort(CreativeFoodOrder.comparator(positions, List.of("minecraft", "farmersdelight")));
        require(fallback.equals(List.of(c, a)), "other mods follow creative order rather than namespace");
        var hiddenMod = id("aaa_hidden:food");
        var missingTabs = new ArrayList<>(List.of(hiddenMod, hiddenA, c, a));
        missingTabs.sort(CreativeFoodOrder.comparator(positions));
        require(missingTabs.equals(List.of(c,a,hiddenA,hiddenMod)), "mod missing from every tab falls behind known creative groups");
        var prepared = new ArrayList<>(List.of(b,c,a));
        prepared.sort(CreativeFoodOrder.preparedComparator(item -> 0,CreativeFoodOrder.comparator(positions)));
        require(prepared.equals(List.of(c,a,b)), "unmade dishes tie by mod and creative order");
        prepared.sort(CreativeFoodOrder.preparedComparator(item -> item.equals(b) ? 20 : 4,
                CreativeFoodOrder.comparator(positions)));
        require(prepared.equals(List.of(b,c,a)), "preparation quantities lead, equal positive counts use creative order");
        var itemOrder = CreativeFoodOrder.comparator(rankedPositions, List.of("minecraft", "farmersdelight"));
        var knownZero = ingredient(vanilla, true, 0);
        var knownFdFirst = ingredient(fdFirst, true, 0);
        var knownFdSecond = ingredient(fdSecond, true, 0);
        var unknownUsed = ingredient(addon, false, 50);
        var knownUsed = ingredient(addon, true, 5);
        var ingredients = new ArrayList<>(List.of(unknownUsed, knownFdSecond, knownZero, knownFdFirst, knownUsed));
        ingredients.sort(IngredientOrder.comparator(false, itemOrder));
        require(ingredients.equals(List.of(knownUsed, knownZero, knownFdFirst, knownFdSecond, unknownUsed)),
                "discovered first, usage descending, mod and creative order only break ties");
        ingredients.sort(IngredientOrder.comparator(true, itemOrder));
        require(ingredients.equals(List.of(unknownUsed, knownUsed, knownZero, knownFdFirst, knownFdSecond)),
                "undiscovered-first only reverses discovery groups");
        var unknowns = new ArrayList<>(List.of(ingredient(addon, false, 0), ingredient(fdSecond, false, 0),
                ingredient(vanilla, false, 0), ingredient(fdFirst, false, 0)));
        unknowns.sort(IngredientOrder.comparator(false, itemOrder));
        require(unknowns.stream().map(Packets.IngredientView::item).toList()
                .equals(List.of(vanilla, fdFirst, fdSecond, addon)), "unused undiscovered items use creative tie-breaker");
        var raw=tag("forge:shulker_meat",false);
        var cooked=tag("forge:roasted_shulker_meat",false);
        var salad=tag("forge:salad_ingredients",false);
        var labels=new ArrayList<>(List.of(cooked,raw,salad));
        labels.sort(IngredientOrder.comparator(false,itemOrder));
        require(labels.equals(List.of(salad,raw,cooked)), "same meat tags remain adjacent with raw before cooked");
        var rawMeef=tag("twilightdelight:meef_raw",false);
        var cookedMeef=tag("twilightdelight:meef_cooked",false);
        labels=new ArrayList<>(List.of(cookedMeef,rawMeef));
        labels.sort(IngredientOrder.comparator(false,itemOrder));
        require(labels.equals(List.of(rawMeef,cookedMeef)),"suffix preparation names share a tag family");
        var knownCooked=tag("forge:roasted_shulker_meat",true);
        labels=new ArrayList<>(List.of(raw,knownCooked,salad));
        labels.sort(IngredientOrder.comparator(false,itemOrder));
        require(labels.get(0).equals(knownCooked),"discovery still takes precedence over tag family order");
        var block=id("minecraft:diamond_block");
        var wheat=id("minecraft:wheat");
        var broad=ingredient(wheat,false,0,35);
        var narrow=ingredient(block,false,0,1);
        var utilityOrder=CreativeFoodOrder.comparator(java.util.Map.of(block,0,wheat,1),List.of("minecraft","farmersdelight"));
        var utility=new ArrayList<>(List.of(narrow,broad));
        utility.sort(IngredientOrder.comparator(false,utilityOrder));
        require(utility.equals(List.of(broad,narrow)),"recipe breadth precedes creative position for unused ingredients");
        var usedNarrow=ingredient(block,false,4,1);
        utility=new ArrayList<>(List.of(broad,usedNarrow));
        utility.sort(IngredientOrder.comparator(false,utilityOrder));
        require(utility.equals(List.of(usedNarrow,broad)),"usage takes precedence over recipe breadth");
        var knownNarrow=ingredient(block,true,0,1);
        utility=new ArrayList<>(List.of(broad,knownNarrow));
        utility.sort(IngredientOrder.comparator(false,utilityOrder));
        require(utility.equals(List.of(knownNarrow,broad)),"discovery takes precedence over recipe breadth");
        utility=new ArrayList<>(List.of(broad,narrow));
        utility.sort(IngredientOrder.comparator(true,utilityOrder));
        require(utility.equals(List.of(narrow,broad)),"missing mode retains its existing creative tie breaker");
        var dishes=new ArrayList<>(List.of(fdSecond,fdFirst));
        dishes.sort(IngredientOrder.mealComparator(meal->false,meal->0,itemOrder));
        require(dishes.equals(List.of(fdFirst,fdSecond)),"dish ties follow creative order rather than item ID");
        dishes=new ArrayList<>(List.of(fdFirst,fdSecond,vanilla));
        dishes.sort(IngredientOrder.mealComparator(meal->!meal.equals(vanilla),meal->meal.equals(fdSecond)?3:1,itemOrder));
        require(dishes.equals(List.of(fdSecond,fdFirst,vanilla)),"dish discovery and stars precede creative order");
        var usedTag=tag("test:used_tag",false);
        var unusedTag=tag("test:unused_tag",false);
        var groups=new ArrayList<>(List.of(unusedTag,usedTag));
        groups.sort(IngredientOrder.comparator(false,itemOrder,value->value==usedTag?12:0));
        require(groups.equals(List.of(usedTag,unusedTag)),"tag aggregate uses can drive default priority");
        var manyUnknown=ingredient(wheat,false,0,50);
        var manyKnown=ingredient(block,true,2,50);
        var popularNarrow=ingredient(addon,true,1000,2);
        var mealCount=new ArrayList<>(List.of(popularNarrow,manyUnknown,manyKnown));
        mealCount.sort(IngredientOrder.mealCountComparator(itemOrder,Packets.IngredientView::timesUsed));
        require(mealCount.equals(List.of(manyKnown,manyUnknown,popularNarrow)),
                "dish-count mode orders by all related dishes before discovery and uses");
        mealCount=new ArrayList<>(List.of(knownFdSecond,knownFdFirst));
        mealCount.sort(IngredientOrder.mealCountComparator(itemOrder,Packets.IngredientView::timesUsed));
        require(mealCount.equals(List.of(knownFdFirst,knownFdSecond)),"equal dish counts retain creative tie ordering");
        groups=new ArrayList<>(List.of(unusedTag,usedTag));
        groups.sort(IngredientOrder.mealCountComparator(itemOrder,value->value==usedTag?12:0));
        require(groups.equals(List.of(usedTag,unusedTag)),"dish-count ties use aggregated tag usage");
        System.out.println("CreativeFoodOrderTest: 35 checks passed");
    }

    private static void checkModSearch() {
        var tomato = id("farmersdelight:tomato");
        var rice = id("youkaishomecoming:rice");
        var group = new Packets.IngredientView(id("forge:vegetables"),false,0,Long.MIN_VALUE,Long.MIN_VALUE,
                1,0,(byte)3,List.of(),false,null,List.of(),List.of(),true,List.of(tomato,rice),List.of(id("forge:vegetables")));
        var names = java.util.Map.of(tomato,"番茄",rice,"稻米");
        var query = IngredientSearch.parse("@FaRmErSdElIgHt");
        java.util.function.Predicate<String> mods = ns -> query.matchesMod(ns,List.of("Farmer's Delight","农夫乐事"));
        require(query.matchesItem(tomato,names::get,mods),"mod id is case insensitive");
        require(IngredientSearch.parse("@农夫乐事").matchesMod("farmersdelight",List.of("Farmer's Delight","农夫乐事")),
                "localized mod display name accepted");
        require(query.matches(group,"蔬菜类",item -> query.matchesItem(item,names::get,mods),mods),
                "forge tag matches through items belonging to selected mod");
        var onlyFd = IngredientSearch.parse("@farmersdelight");
        java.util.function.Predicate<String> selected = ns -> onlyFd.matchesMod(ns,List.of());
        require(!onlyFd.matchesItem(rice,names::get,selected),"other mod's item excluded");
        var combined=IngredientSearch.parse("@farmersdelight 番茄");
        require(combined.matches(group,"蔬菜类",item -> combined.matchesItem(item,names::get,selected),selected),
                "mod and ingredient text combine for tags");
        var wrong=IngredientSearch.parse("@farmersdelight 稻米");
        require(!wrong.matches(group,"蔬菜类",item -> wrong.matchesItem(item,names::get,selected),selected),
                "mod and text must match the same tag member");
        var full=IngredientSearch.parse("@\"Farmer's Delight\" 番茄");
        require(full.mod().equals("Farmer's Delight") && full.text().equals("番茄"),"quoted mod name keeps spaces");
        var plain=IngredientSearch.parse("番茄");
        require(plain.matches(group,"蔬菜类",item -> plain.matchesItem(item,names::get,ns -> false),ns -> false),
                "ordinary ingredient search still finds member tags");
        var missing=IngredientSearch.parse("@not_installed");
        java.util.function.Predicate<String> missingMods=ns -> missing.matchesMod(ns,List.of());
        require(!missing.matches(group,"蔬菜类",item -> missing.matchesItem(item,names::get,missingMods),missingMods),
                "missing mod does not match unrelated tags");
        require(IngredientSearch.parse("@").matchesItem(tomato,names::get,ns -> true),"unfinished mod prefix stays editable");
        require(IngredientSearch.parse("治愈").matches(group,"蔬菜类",item -> false,ns -> true,List.of("治愈")),
                "flavor name finds ingredient tag without item name matching");
        var flavorMod=IngredientSearch.parse("@farmersdelight 治愈");
        require(flavorMod.matches(group,"蔬菜类",item -> false,selected,List.of("治愈")),
                "mod filter combines with tag flavor name");
        require(!missing.matches(group,"蔬菜类",item -> false,missingMods,List.of("治愈")),
                "flavor name never bypasses selected mod filter");
    }
    private static void checkDishSearch() {
        List<String> mods=List.of("农夫乐事","Farmer's Delight"),categories=List.of("主食","米饭");
        List<String> extras=List.of("甘甜","力量","minecraft:strength");
        require(DishSearch.matches("@", "farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "unfinished mod prefix does not erase all dishes");
        require(DishSearch.matches("@农夫乐事 力量","farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "localized mod and effect query combine");
        require(DishSearch.matches("@FARMERSDELIGHT 甘甜","farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "case insensitive mod id and flavor search combine");
        require(DishSearch.matches("minecraft:strength","farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "effect registry id is searchable");
        require(!DishSearch.matches("@smc 力量","farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "effect match cannot bypass mod ownership filter");
        require(DishSearch.matches("米饭","farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "dish category search is preserved in the food grid");
        require(DishSearch.matches("炒饭","farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "dish name search is preserved");
        require(DishSearch.matches("@\"Farmer's Delight\" 甘甜","farmersdelight:fried_rice","炒饭","farmersdelight",mods,categories,extras),
                "quoted mod name supports spaces");
    }

    private static Packets.IngredientView tag(String item,boolean known) {
        return new Packets.IngredientView(id(item),known,0,Long.MIN_VALUE,Long.MIN_VALUE,
                1,0,(byte)3,List.of(),false,null,List.of(),List.of(),true,List.of(),List.of(id(item)));
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static Packets.IngredientView ingredient(ResourceLocation item, boolean known, long uses) {
        return ingredient(item,known,uses,1);
    }
    private static Packets.IngredientView ingredient(ResourceLocation item, boolean known, long uses,int meals) {
        return new Packets.IngredientView(item, known, uses, Long.MIN_VALUE, Long.MIN_VALUE,
                meals, 0, (byte) 1, List.of(), false, null, List.of());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
