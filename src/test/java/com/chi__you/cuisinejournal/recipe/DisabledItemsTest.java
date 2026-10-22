package com.chi__you.cuisinejournal.recipe;

import com.chi__you.foodinteractions.*;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import java.util.*;

/** Real registry tag binding, catalogue exclusions and generator reload behavior. */
public final class DisabledItemsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        var boot=Bootstrap.class.getDeclaredField("isBootstrapped");boot.setAccessible(true);boot.setBoolean(null,true);
        BuiltInRegistries.bootStrap();
        var disabled=TagKey.create(Registries.ITEM,InteractionAvailability.DISABLED_ITEMS);
        var holder=Items.APPLE.builtInRegistryHolder();
        var original=holder.tags().toList();
        var cake=Items.CAKE.builtInRegistryHolder();var cakeTags=cake.tags().toList();
        var apple=id("minecraft:apple");var recipe=id("test:apple_recipe");var crafting=id("minecraft:crafting");
        var allowed=EligibilityRules.builder().allowItem(apple).allowRecipe(recipe).build();
        var rule=JsonParser.parseString("{\"blocks\":[{\"block\":\"minecraft:cake\",\"output\":\"minecraft:apple\",\"input\":{\"item\":\"minecraft:bowl\"}}]}").getAsJsonObject();
        var rules=InteractionRules.resolve(List.of(new InteractionRules.File(id("test:rule"),rule)),mod->true);
        var checkpoint=HostItemAvailability.rollbackPoint();
        try {
            HostItemAvailability.rebuild();
            check(!InteractionAvailability.disabled(apple)&&HostItemAvailability.visible(apple),"absent tag keeps ordinary food visible");
            var baseline=FoodInteractionScanner.scan(rules);
            check(baseline.entries().stream().anyMatch(e->e.output().equals(apple)),"explicit generator relation exists before exclusion");
            holder.bindTags(List.of(disabled));HostItemAvailability.rebuild();
            check(InteractionAvailability.disabled(apple),"actual server tag membership is recognized");
            check(!HostItemAvailability.visible(apple)&&!HostItemAvailability.enabled(Items.APPLE),"disabled item hidden from item visibility");
            check(HostItemAvailability.hiddenFromCatalogue(apple)&&!HostItemAvailability.hiddenByUnification(apple),
                    "pack exclusion remains independent of unified mode");
            check(allowed.evaluateItem(apple,true,t->false)==EligibilityRules.Verdict.EXCLUDED,"catalogue-only and explicit item inclusion cannot bypass disabled tag");
            check(allowed.evaluate(recipe,crafting,apple,true,t->false)==EligibilityRules.Verdict.EXCLUDED,"explicit recipe allow cannot bypass disabled tag");
            check(!IngredientCatalogue.isUsableIngredient(apple),"ingredient catalogue also excludes disabled items");
            var filtered=FoodInteractionScanner.scan(rules);
            check(filtered.entries().stream().noneMatch(e->e.output().equals(apple)),"manual generator rules cannot restore a disabled output");
            check(filtered.report().stream().anyMatch(row->row.contains("skipped_disabled_item")),"generator records why relation was skipped");
            check(HostItemAvailability.visible(id("minecraft:bread")),"unlisted food remains visible");
            var rollback=HostItemAvailability.rollbackPoint();
            holder.bindTags(original);HostItemAvailability.rebuild();
            check(HostItemAvailability.visible(apple),"tag removal and rebuild restore visibility");
            rollback.run();
            check(HostItemAvailability.hiddenFromCatalogue(apple),"failed catalogue rebuild can restore disabled snapshot");
            HostItemAvailability.rebuild();
            cake.bindTags(List.of(disabled));
            check(FoodInteractionScanner.scan(rules).entries().stream().noneMatch(e->e.block().equals(id("minecraft:cake"))),
                    "disabled source block item suppresses its entire display even if output is allowed");
            cake.bindTags(cakeTags);
            check(FoodInteractionScanner.scan(rules).entries().equals(baseline.entries()),"removal returns exact original generated relation set without stale exclusions");
            var noSource=new InteractionDisplay(id("test:block"),null,JsonParser.parseString("{}"),apple,1,Map.of(),"serving","test");
            check(InteractionAvailability.visible(noSource,key->false),"itemless source is supported");
            check(!InteractionAvailability.visible(noSource,apple::equals),"itemless source still respects disabled output");
            var source=new InteractionDisplay(id("test:block"),id("test:whole"),noSource.input(),apple,1,Map.of(),"serving","test");
            check(!InteractionAvailability.visible(source,id("test:whole")::equals),"distinct source item identity also respects exclusions");
            check(Items.APPLE.getFoodProperties()!=null,"tag does not change native edibility");
            var profile=new com.chi__you.cuisinejournal.culinary.CulinaryProfile(new UUID(0,123));
            profile.meal(apple).discover(1);var saved=profile.save().copy();
            holder.bindTags(List.of(disabled));HostItemAvailability.rebuild();
            check(profile.save().equals(saved),"rebuilding visibility never edits player history");
        } finally {holder.bindTags(original);cake.bindTags(cakeTags);checkpoint.run();}
        System.out.println("DisabledItemsTest: "+checks+" checks passed");
    }
    private static ResourceLocation id(String text){return ResourceLocation.parse(text);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
