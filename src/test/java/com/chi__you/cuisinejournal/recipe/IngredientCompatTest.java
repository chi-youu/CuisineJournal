package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.chi__you.cuisinejournal.culinary.IngredientRecord;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class IngredientCompatTest {
    private static int checks;
    private static final ResourceLocation A = id("a"), B = id("b"), C = id("c"), D = id("dish");
    private static final ResourceLocation T = ResourceLocation.parse("forge:meat"), U = id("other_meat"), G = id("group");
    public static void main(String[] args) {
        checkChineseNameMerge();
        checkMaterialNamesAndGroups();
        checkRequestedScreenshotNames();
        checkAdditionalScreenshotNamesAndMerges();
        checkUnifiedModeTagNames();
        checkContainerExclusions();
        var hiddenInput = new IngredientJournalIndex.Input(D, new RecipeEntry.IngredientSlot(
                List.of(A,B),List.of(B),List.of(T)));
        var hiddenIndex=IngredientJournalIndex.build(List.of(hiddenInput),IngredientCompatRules.empty(),
                tag->List.of(A,B),item->!item.equals(B),tag->false,item->!item.equals(B));
        check(!hiddenIndex.items().contains(B) && hiddenIndex.tags().values().stream()
                .noneMatch(tag->tag.members().contains(B)), "host-hidden items never appear as ingredients or tag members");
        var groupedHidden=IngredientJournalIndex.build(List.of(hiddenInput),rules(List.of(file("hidden_group",
                "{\"merge_tags\":[{\"id\":\"test:group\",\"tags\":[\"forge:meat\"]}]}")),null),
                tag->List.of(A,B),item->!item.equals(B),tag->false,item->!item.equals(B));
        check(groupedHidden.tagSources(G).stream().noneMatch(source->source.members().contains(B))
                &&groupedHidden.tagUses(G).stream().noneMatch(use->use.acceptedItems().contains(B)),
                "tag detail source and recipe provenance do not re-expose hidden members");
        var base = file("base", """
            {"hidden_tags":["forge:meat"],"excluded_items":["test:a"],
             "item_aliases":[{"item":"test:b","into":"test:a"}],
             "processing_recipe_types":{"add":["test:cutting"]}}
            """);
        var override = file("override", """
            {"priority":100,"keep_tags":["c:meat"],"included_items":["test:a"],
             "remove_item_aliases":["test:b"],"tag_names":{"forge:meat":"Meat"},
             "processing_recipe_types":{"remove":["test:cutting"]}}
            """);
        var r = rules(List.of(override, base), null);
        check(!r.hiddenTag(T), "higher priority restores hidden tag across c/forge spellings");
        check(!r.excludedItem(A), "higher priority restores item");
        check(r.alias(B).equals(B), "higher priority removes alias");
        check("Meat".equals(r.displayName(T)), "independent tag display name");
        check(Boolean.FALSE.equals(r.processingOverride(id("cutting"))), "remove wins over lower add");
        r = rules(List.of(base), json("{\"keep_tags\":[\"forge:meat\"]}"));
        check(!r.hiddenTag(T), "local config overrides datapack");
        r = IngredientCompatRules.resolve(List.of(file("missing", "{\"required_mods\":[\"missing_mod\"],\"hidden_tags\":[\"forge:meat\"]}")), mod -> false, null);
        check(!r.hiddenTag(T), "missing required mod skips file");
        var portionFile = file("portions", """
            {"portion_origins":{"test:b":"test:a"}}
            """);
        r = rules(List.of(portionFile), null);
        check(r.portionOrigins().equals(Map.of(B,A)), "portion origin is loaded independently of ingredient aliases");
        check(r.alias(B).equals(B), "portion linkage never merges ingredient entries");
        r = rules(List.of(portionFile), json("{\"portion_origins\":{\"test:b\":\"test:c\"}}"));
        check(r.portionOrigins().get(B).equals(C), "local portion origin overrides data pack");
        r = rules(List.of(portionFile,file("remove_portion", """
            {"priority":100,"portion_origins":{"test:b":null}}
            """)), null);
        check(r.portionOrigins().isEmpty(), "higher priority pack can remove explicit portion origin");
        r = rules(List.of(file("bad", "{\"hidden_tags\":[\"forge:meat\"],\"item_types\":{\"test:a\":42}}")), null);
        check(!r.hiddenTag(T), "malformed file cannot apply partially");
        r = rules(List.of(file("bad_priority", "{\"priority\":{},\"hidden_tags\":[\"forge:meat\"]}")), null);
        check(!r.hiddenTag(T), "malformed priority skipped");
        r = rules(List.of(file("cycle", """
            {"item_aliases":[{"item":"test:a","into":"test:b"},{"item":"test:b","into":"test:a"}]}
            """)), null);
        check(r.alias(A).equals(A) && r.alias(B).equals(B), "cycle leaves original identities intact");
        r = rules(List.of(file("chain", """
            {"item_aliases":[{"item":"test:c","into":"test:b"},{"item":"test:b","into":"test:a"}]}
            """)), null);
        check(r.alias(C).equals(A), "alias chain resolves final item");
        var tags = Map.of(T, List.of(A, B), U, List.of(B, C));
        var mixed = new RecipeEntry.IngredientSlot(List.of(A, B, C), List.of(C), List.of(T));
        r = rules(List.of(file("dissolve", "{\"dissolve_tags\":[\"forge:meat\"]}")), null);
        var index = index(List.of(new IngredientJournalIndex.Input(D, mixed)), r, tags);
        check(index.tags().isEmpty(), "dissolved tag has no entry");
        check(index.itemDishes().get(A).contains(D) && index.itemDishes().get(B).contains(D), "dissolved members get direct uses");
        var taggedOnly = new RecipeEntry.IngredientSlot(List.of(A, B, C), List.of(), List.of(T, U));
        index = index(List.of(new IngredientJournalIndex.Input(D, taggedOnly)), r, tags);
        check(!index.itemDishes().containsKey(C), "dissolving one tag does not credit another tag's members");
        var merge = file("merge", """
            {"merge_tags":[{"id":"test:group","name":"Meat group","tags":["forge:meat","test:other_meat"],
             "types":["cuisinejournal:meat"],"flavor":{"source":true,"effect":"minecraft:strength"}}]}
            """);
        r = rules(List.of(merge), null);
        var inputs = List.of(new IngredientJournalIndex.Input(D, taggedOnly), new IngredientJournalIndex.Input(D, taggedOnly));
        index = index(inputs, r, tags);
        check(index.tags().size() == 1 && index.tags().containsKey(G), "semantic merge uses stable entry id");
        check(index.tags().get(G).members().size() == 3, "merged members deduplicate");
        check(index.tagDishes().get(G).size() == 1, "multiple recipes count same dish once");
        check("Meat group".equals(index.tags().get(G).displayName()), "merged name reaches index");
        check(index.itemTags().get(A).equals(Set.of(G)), "membership points at final tag entry");
        check(r.tagTypes(G).equals(List.of(ResourceLocation.parse("cuisinejournal:meat"))), "merged type override");
        check(r.tagFlavor(G).effect().toString().equals("minecraft:strength"), "merged flavor is presentation metadata");
        check(index.audit().stream().anyMatch(a -> a.original().equals("#forge:meat") && a.result().equals("merged")
                && a.rules().stream().anyMatch(s -> s.contains("pack_merge"))), "audit includes source pack and final action");
        r = rules(List.of(merge, file("undo", "{\"priority\":10,\"remove_merge_tags\":[\"test:group\"]}")), null);
        index = index(inputs, r, tags);
        check(index.tags().size() == 2 && !index.tags().containsKey(G), "whole merge group can be cancelled");
        index = index(inputs, IngredientCompatRules.empty(), tags);
        check(index.tags().size() == 2, "removing datapack resets rules on rebuild");
        r = rules(List.of(file("to_item", "{\"tag_items\":[{\"tag\":\"forge:meat\",\"into\":\"test:a\"}]}")), null);
        index = index(List.of(new IngredientJournalIndex.Input(D, new RecipeEntry.IngredientSlot(List.of(A,B), List.of(), List.of(T)))), r, tags);
        check(index.tags().isEmpty() && index.itemDishes().get(A).contains(D), "tag can become specific item entry");
        check(index.sources(A).contains(B), "tag-to-item keeps member progress sources");
        index = index(List.of(new IngredientJournalIndex.Input(D, new RecipeEntry.IngredientSlot(List.of(A), List.of(), List.of(T)))),
                IngredientCompatRules.empty(), Map.of(T,List.of(A)));
        check(index.tags().isEmpty() && index.itemDishes().containsKey(A), "single-member auto dissolve retained");
        var aliasRule = rules(List.of(file("alias", "{\"item_aliases\":[{\"item\":\"test:b\",\"into\":\"test:a\"}]}")), null);
        index = index(List.of(new IngredientJournalIndex.Input(D, new RecipeEntry.IngredientSlot(List.of(B)))), aliasRule, Map.of());
        check(index.items().equals(Set.of(A)) && index.sources(A).containsAll(List.of(A,B)), "alias sources include old target records");
        IngredientRecord a = new IngredientRecord(A), b = new IngredientRecord(B);
        a.recordUse(5,2,D,CookingMethod.CRAFTING); b.recordUse(2,3,D,CookingMethod.SMELTING);
        var records = Map.of(A,a,B,b);
        var progress = IngredientProgress.collect(List.of(A,B,B), records::get);
        check(progress.known() && progress.timesUsed() == 5, "merged progress sums distinct source records");
        check(progress.firstUsedDay() == 2 && progress.lastUsedDay() == 5, "first/last dates aggregate ignoring sentinel");
        check(progress.methods().size() == 2, "methods union");
        check(a.timesUsed() == 2 && b.timesUsed() == 3, "aggregation does not mutate source saves");
        check(IngredientProgress.collect(List.of(C), records::get).firstUsedDay() == Long.MIN_VALUE, "unknown dates remain sentinel");
        checkProcessingConfig();
        r = rules(List.of(file("real_c", """
            {"merge_tags":[{"id":"test:group","tags":["c:meat","test:other_meat"]}]}
            """)), null);
        check(r.mergeFor(T).tags().contains(ResourceLocation.parse("c:meat")), "merge preserves real c namespace membership lookup");
        r = rules(List.of(merge, file("replace", """
            {"priority":100,"merge_tags":[{"id":"test:group","tags":["test:other_meat"]}]}
            """)), null);
        check(r.mergeFor(T) == null && r.mergeFor(U) != null, "replacing group removes stale old source tags");
        r = rules(List.of(override, file("name_reset", """
            {"priority":200,"tag_names":{"forge:meat":null}}
            """)), null);
        check(r.displayName(T) == null, "name override can reset to translation fallback");
        index = index(inputs, rules(List.of(file("hide", """
            {"hidden_tags":["forge:meat"]}
            """)), null), tags);
        check(index.audit().stream().anyMatch(audit -> audit.original().equals("#forge:meat") && audit.result().equals("hidden")),
                "audit retains hidden original entries");
        checkSameNameTag();
        checkTagGroupOrigins();
        checkSoupTagIndex();
        checkTagAdditionsAndIdentical();
        checkSugarGroup();
        checkEggGroup();
        var available=IngredientJournalIndex.build(List.of(new IngredientJournalIndex.Input(D,
                new RecipeEntry.IngredientSlot(List.of(A,B,C),List.of(B),List.of(T)))),
                IngredientCompatRules.empty(), tag -> List.of(A,B,C), item -> !item.equals(B), tag -> false);
        check(available.tags().get(T).members().equals(List.of(A,C)) && !available.items().contains(B),
                "host-disabled ingredient is removed from visible tag members and direct items");
        check(available.audit().stream().anyMatch(row -> row.original().equals(B.toString())&&row.result().equals("hidden")),
                "host-disabled registered item is retained in audit as hidden");
        System.out.println("IngredientCompatTest: " + checks + " checks passed");
    }

    private static void checkSoupTagIndex() {
        var boar=ResourceLocation.parse("forge:raw_boar");
        var chop=ResourceLocation.parse("youkaishomecoming:raw_boarchop");
        var bits=ResourceLocation.parse("youkaishomecoming:raw_boarchop_bits");
        var soup=ResourceLocation.parse("youkaishomecoming:power_soup");
        var recipe=ResourceLocation.parse("youkaishomecoming:pot_of_power_soup");
        var slot=new RecipeEntry.IngredientSlot(List.of(chop,bits),List.of(),List.of(boar));
        var index=index(List.of(new IngredientJournalIndex.Input(soup,recipe,2,slot)),
                IngredientCompatRules.empty(),Map.of(boar,List.of(chop,bits)));
        check(index.tagDishes().get(boar).contains(soup), "bowl soup belongs to the original raw boar tag");
        check(index.tagUses(boar).get(0).recipe().equals(recipe)
                && index.tagUses(boar).get(0).slotIndex()==2,
                "soup retains original recipe provenance");
        check(index.tagUses(boar).get(0).acceptedItems().containsAll(List.of(chop,bits))
                && !index.itemDishes().containsKey(chop), "tag recipe is not fabricated as explicit raw meat input");
    }

    private static void checkSugarGroup() {
        var tea = ResourceLocation.parse("c:tea_ingredients/sweet/weak");
        var anySugar = ResourceLocation.parse("minecraft:any_sugar");
        var cornSweetener = ResourceLocation.parse("cornexpansion:sweet_ingredient");
        var bountifulSugar = ResourceLocation.parse("bountifulfares:sugar_ingredients");
        try {
            var json = JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/resources/data/cuisinejournal/cuisinejournal/compat/ingredients/general_material_names.json")))
                    .getAsJsonObject();
            var file = new IngredientCompatRules.RuleFile(id("general_material_names"), "builtin", json);
            var rules = IngredientCompatRules.resolve(List.of(file), mod -> true, null);
            var teaMerge = rules.mergeFor(tea);
            var sugarMerge = rules.mergeFor(anySugar);
            check(teaMerge != null && teaMerge == sugarMerge, "tea sweetener and any_sugar share one merge group");
            check(teaMerge.id().equals(ResourceLocation.parse("forge:sugar")), "sugar merge has stable display id");
            check("糖类".equals(teaMerge.displayName()), "sugar merge displays as 糖类");
            check(teaMerge.tags().containsAll(List.of(tea, anySugar)), "sugar merge preserves both original tags");
            check(rules.mergeFor(cornSweetener) == teaMerge && rules.mergeFor(bountifulSugar) == teaMerge,
                    "corn sweetener and bountiful sugar join the existing sugar display group");
            var members = Map.of(tea, List.of(A,B), anySugar, List.of(A,C),
                    cornSweetener, List.of(A,D), bountifulSugar, List.of(A));
            var inputs = List.of(
                    new IngredientJournalIndex.Input(id("corn_dish"), id("corn_recipe"), 0,
                            new RecipeEntry.IngredientSlot(List.of(A,D), List.of(), List.of(cornSweetener))),
                    new IngredientJournalIndex.Input(id("bountiful_dish"), id("bountiful_recipe"), 0,
                            new RecipeEntry.IngredientSlot(List.of(A), List.of(), List.of(bountifulSugar))));
            var index = index(inputs, rules, members);
            var sugarId = teaMerge.id();
            check(index.tags().size() == 1 && index.tags().get(sugarId).members().containsAll(List.of(A,B,C,D)),
                    "sugar display has one deduplicated union, including single-member source tags");
            check(index.tagUses(sugarId).stream().anyMatch(use -> use.recipe().equals(id("corn_recipe"))
                            && use.acceptedItems().equals(List.of(A,D)))
                    && index.tagUses(sugarId).stream().anyMatch(use -> use.recipe().equals(id("bountiful_recipe"))
                            && use.acceptedItems().equals(List.of(A))),
                    "sugar display merge preserves each recipe's original accepted ingredients");
            var missing = index(inputs.subList(0,1), rules, Map.of(cornSweetener,List.of(A,D)));
            check(missing.tags().size() == 1 && missing.tags().get(sugarId).members().equals(List.of(A,D)),
                    "missing optional sugar tags do not require either host or fabricate members");
        } catch (java.io.IOException failure) {
            throw new AssertionError("Unable to read built-in sugar tag rule", failure);
        }
    }

    private static void checkEggGroup() {
        var forgeEggs = ResourceLocation.parse("forge:eggs");
        var cEggs = ResourceLocation.parse("c:eggs");
        var deltaEggs = ResourceLocation.parse("deltaboxlib:eggs");
        try {
            var json = JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/resources/data/cuisinejournal/cuisinejournal/compat/ingredients/egg_group.json")))
                    .getAsJsonObject();
            var file = new IngredientCompatRules.RuleFile(id("egg_group"), "builtin", json);
            var rules = IngredientCompatRules.resolve(List.of(file), mod -> true, null);
            var merge = rules.mergeFor(forgeEggs);
            check(merge != null && rules.mergeFor(deltaEggs) == null, "deltaboxlib eggs need no manual merge declaration");
            check(merge.tags().containsAll(List.of(forgeEggs, cEggs)), "egg group keeps common namespace aliases");
            Map<ResourceLocation, List<ResourceLocation>> members = Map.of(forgeEggs, List.of(A, B),
                    cEggs, List.<ResourceLocation>of(), deltaEggs, List.of(B, A));
            var inputs = List.of(new IngredientJournalIndex.Input(D, id("egg_recipe"), 0,
                    new RecipeEntry.IngredientSlot(List.of(A, B), List.of(), List.of(forgeEggs))),
                    new IngredientJournalIndex.Input(id("egg_dish"), id("delta_recipe"), 0,
                    new RecipeEntry.IngredientSlot(List.of(A, B), List.of(), List.of(deltaEggs))));
            var index = index(inputs, rules, members);
            var entry = index.tags().get(forgeEggs);
            check(index.tags().size() == 1 && entry.members().equals(List.of(A, B)), "identical eggs join the existing group automatically");
            check(entry.ids().containsAll(List.of(forgeEggs, deltaEggs)) && !entry.ids().contains(cEggs),
                    "empty egg aliases are omitted from display ids");
            check(index.tagUses(forgeEggs).size() == 2 && index.tagSources(forgeEggs).size() == 2,
                    "automatic eggs retain both real recipe branches and sources");
            check(index.audit().stream().anyMatch(row -> row.original().equals("#" + deltaEggs)
                    && row.result().equals("merged_identical")), "eggs are automatically merged rather than manually configured");
        } catch (java.io.IOException failure) {
            throw new AssertionError("Unable to read built-in egg tag rule", failure);
        }
    }

    private static void checkTagAdditionsAndIdentical() {
        var base=file("base_group", """
            {"merge_tags":[{"id":"test:group","name":"Meat","tags":["forge:meat"],
            "types":["cuisinejournal:meat"],"flavor":{"source":true,"effect":"minecraft:strength"}}]}
            """);
        var add=file("addon", """
            {"priority":100,"required_mods":["addon"],"merge_tag_additions":[
            {"id":"test:group","tags":["test:other_meat","test:other_meat"]}]}
            """);
        var r=rules(List.of(add,base),null);
        check(r.mergeFor(U).tags().equals(List.of(T,U)), "additions extend existing group without duplicate tags");
        check(r.mergeFor(U).displayName().equals("Meat")&&r.tagFlavor(U).effect().toString().equals("minecraft:strength"),
                "addition inherits base name type and flavor");
        var missing=IngredientCompatRules.resolve(List.of(base,add),mod->false,null);
        check(missing.mergeFor(U)==null&&missing.mergeFor(T)!=null, "missing addon leaves base group intact");
        var removed=rules(List.of(base,add,file("cancel","""
            {"priority":200,"remove_merge_tags":["test:group"]}
            """)),null);
        check(removed.mergeFor(T)==null&&removed.mergeFor(U)==null, "group removal also removes appended tags");
        var undefined=rules(List.of(add),null);
        check(undefined.mergeFor(U)==null, "addition cannot invent undefined group");
        var local=rules(List.of(base),json("""
            {"merge_tag_additions":[{"id":"test:group","tags":["test:other_meat"]}]}
            """));
        check(local.mergeFor(U)!=null,"local addition uses final datapack group");
        var inputs=List.of(new IngredientJournalIndex.Input(D,id("recipe_a"),0,
                new RecipeEntry.IngredientSlot(List.of(A,B),List.of(),List.of(T))),
                new IngredientJournalIndex.Input(id("dish_b"),id("recipe_b"),0,
                new RecipeEntry.IngredientSlot(List.of(A,B),List.of(),List.of(U))));
        var tags=Map.of(T,List.of(A,B),U,List.of(B,A));
        var automaticGroup = index(inputs, rules(List.of(base), null), tags);
        check(automaticGroup.tags().keySet().equals(Set.of(G)), "configured groups participate in identical-member matching and keep their id");
        check("Meat".equals(automaticGroup.tags().get(G).displayName())
                && automaticGroup.tags().get(G).ids().containsAll(List.of(T,U)), "automatic members retain existing group metadata and aliases");
        var keptGroup = rules(List.of(base, file("keep_group", "{\"keep_tags\":[\"test:group\"]}")), null);
        check(index(inputs, keptGroup, tags).tags().size() == 2, "keep on a logical group id opts out");
        var groupNameConflict = rules(List.of(base, file("group_name_conflict",
                "{\"tag_names\":{\"test:group\":\"Group\",\"test:other_meat\":\"Other\"}}")), null);
        check(index(inputs, groupNameConflict, tags).tags().size() == 2, "logical group name conflicts prevent identical merge");
        var groupFlavorConflict = rules(List.of(base, file("group_flavor_conflict", """
            {"tag_flavor":{"test:group":{"source":true,"effect":"minecraft:speed"}}}
            """)), null);
        check(index(inputs, groupFlavorConflict, tags).tags().size() == 2, "logical group flavor is included in conflict checks");
        var groupUnion = rules(List.of(file("group_union", """
            {"merge_tags":[{"id":"test:group","name":"Group","tags":["forge:meat","test:branch"]}]}
            """)), null);
        var unionTags = Map.of(T,List.of(A),id("branch"),List.of(B),U,List.of(A,B));
        check(index(inputs,groupUnion,unionTags).tags().keySet().equals(Set.of(G)), "compare whole configured group union rather than each branch separately");
        var unionSubset = Map.of(T,List.of(A,B),id("branch"),List.of(C),U,List.of(A,B));
        check(index(inputs,groupUnion,unionSubset).tags().size() == 2, "a tag matching only one group branch stays separate");
        var hiddenExtra = rules(List.of(file("group_union_hidden", """
            {"merge_tags":[{"id":"test:group","tags":["forge:meat","test:branch"]}],
             "excluded_items":["test:c"]}
            """)), null);
        check(index(inputs,hiddenExtra,unionSubset).tags().size() == 2, "group equality uses full members even when extras are hidden");
        var groupTypes = rules(List.of(base, file("group_type_conflict", """
            {"tag_types":{"test:group":["test:first"],"test:other_meat":["test:second"]}}
            """)), null);
        check(index(inputs,groupTypes,tags).tags().size() == 2, "logical group type conflicts remain separate");
        var presetConflict = rules(List.of(file("group_preset_conflict", """
            {"merge_tags":[{"id":"test:group","tags":["forge:meat"],
             "flavor":{"source":true,"flavors":["test:salty"]}}],
             "tag_flavor":{"test:other_meat":{"source":true,"flavors":["test:sweet"]}}}
            """)), null);
        check(index(inputs,presetConflict,tags).tags().size() == 2, "different referenced flavor presets cannot silently merge");
        var index=index(inputs,IngredientCompatRules.empty(),tags);
        check(index.tags().size()==1&&index.tags().containsKey(T),"identical member sets merge deterministically preferring forge");
        check(index.tagDishes().get(T).size()==2&&index.tagUses(T).size()==2&&index.tagSources(T).size()==2,
                "identical merge retains both dishes recipes and raw tags");
        check(index.itemTags().get(A).equals(Set.of(T)),"item membership links to automatic winner");
        check(index.audit().stream().anyMatch(row->row.original().equals("#"+U)&&row.result().equals("merged_identical")),
                "automatic merge is visible in audit");
        index=index(inputs,IngredientCompatRules.empty(),Map.of(T,List.of(A,B),U,List.of(A,B,C)));
        check(index.tags().size()==2,"overlap or subset is not enough for automatic merging");
        var noB=rules(List.of(file("exclude", """
            {"excluded_items":["test:c"]}
            """)),null);
        index=index(inputs,noB,Map.of(T,List.of(A,B),U,List.of(A,B,C)));
        check(index.tags().size()==2,"equal visible members after exclusion do not merge unequal real sets");
        var kept=rules(List.of(file("keep", """
            {"keep_tags":["test:other_meat"]}
            """)),null);
        check(index(inputs,kept,tags).tags().size()==2,"keep_tags opts out of automatic grouping");
        var names=rules(List.of(file("names", """
            {"tag_names":{"forge:meat":"First","test:other_meat":"Second"}}
            """)),null);
        check(index(inputs,names,tags).tags().size()==2,"conflicting configured names remain separate");
        var effects=rules(List.of(file("flavors","""
            {"tag_flavor":{"forge:meat":{"source":true,"effect":"minecraft:strength"},
            "test:other_meat":{"source":true,"effect":"minecraft:speed"}}}
            """)),null);
        check(index(inputs,effects,tags).tags().size()==2,"conflicting flavors remain separate");
        var redefined=rules(List.of(base,add,file("replace_group","""
            {"priority":200,"merge_tags":[{"id":"test:group","tags":["forge:meat"]}]}
            """)),null);
        check(redefined.mergeFor(U)==null,"full merge declaration deliberately replaces earlier additions");
    }

    private static void checkTagGroupOrigins() {
        var r = rules(List.of(file("group_origins", """
            {"merge_tags":[{"id":"test:group","tags":["forge:meat","test:other_meat"],
              "flavor":{"source":true,"effect":"minecraft:strength"}}]}
            """)), null);
        var recipe = id("special_recipe");
        var mixed = new RecipeEntry.IngredientSlot(List.of(A, B, C), List.of(C), List.of(U));
        var input = new IngredientJournalIndex.Input(D, recipe, 2, mixed);
        var index = index(List.of(input, input), r, Map.of(T, List.of(A, B, C), U, List.of(B)));
        check(index.tags().size() == 1 && index.tags().get(G).members().equals(List.of(A,B,C)),
                "group includes a configured tag not named by this recipe, with unique members");
        check(index.tagSources(G).stream().anyMatch(source -> source.tag().equals(U)
                && source.members().equals(List.of(B))), "source tag keeps its own subset");
        check(index.tagUses(G).size() == 1, "duplicate recipe input does not duplicate provenance");
        var use = index.tagUses(G).get(0);
        check(use.dish().equals(D) && use.recipe().equals(recipe) && use.slotIndex() == 2 && use.tag().equals(U),
                "original dish recipe slot and real tag survive grouping");
        check(use.acceptedItems().equals(List.of(B)), "explicit OR alternative is not added to tag branch");
        check(index.itemDishes().get(C).contains(D) && !index.itemDishes().containsKey(B),
                "grouping preserves explicit-item versus tagged-item recipe distinction");
        check(r.tagFlavor(T).equals(r.tagFlavor(U)), "both group tags inherit common flavor metadata");
        var changed = index(List.of(input), r, Map.of(T,List.of(A,B,C),U,List.of(A,B)));
        check(changed.tagUses(G).get(0).acceptedItems().equals(List.of(A,B)),
                "tag membership is re-read on rebuild without editing group definition");
        var single = index(List.of(new IngredientJournalIndex.Input(D,recipe,0,
                new RecipeEntry.IngredientSlot(List.of(B),List.of(),List.of(U)))), r,
                Map.of(T,List.of(B),U,List.of(B)));
        check(single.tags().containsKey(G) && single.tagUses(G).size()==1,
                "explicit group retains one entry and provenance even with one unique member");
        var hidden = rules(List.of(file("group_origins", """
            {"merge_tags":[{"id":"test:group","tags":["forge:meat","test:other_meat"]}]}
            """),file("hide_branch", """
            {"priority":200,"hidden_tags":["test:other_meat"]}
            """)),null);
        var kept = new IngredientJournalIndex.Input(id("another_dish"),id("another_recipe"),0,
                new RecipeEntry.IngredientSlot(List.of(A,B),List.of(),List.of(T)));
        var filtered=index(List.of(input,kept),hidden,Map.of(T,List.of(A,B),U,List.of(B)));
        check(filtered.tagUses(G).stream().noneMatch(origin -> origin.tag().equals(U))
                && filtered.tagSources(G).stream().noneMatch(source -> source.tag().equals(U)),
                "hidden branch is excluded from source list and recipe provenance");
        var collisionRules=rules(List.of(file("origin_collision", """
            {"merge_tags":[{"id":"test:a","tags":["forge:meat","test:other_meat"]}]}
            """)),null);
        var collided=index(List.of(input),collisionRules,Map.of(T,List.of(A,B),U,List.of(B)));
        var display=ResourceLocation.parse("cuisinejournal:tag/test/a");
        check(collided.tagUses(display).get(0).tag().equals(U), "collision-safe display ID retains real tag provenance");
    }

    private static void checkChineseNameMerge() {
        var inputs = List.of(
                new IngredientJournalIndex.Input(D, id("recipe_a"), 0,
                        new RecipeEntry.IngredientSlot(List.of(A,B), List.of(), List.of(T))),
                new IngredientJournalIndex.Input(id("dish_b"), id("recipe_b"), 1,
                        new RecipeEntry.IngredientSlot(List.of(B,C), List.of(), List.of(U))));
        var members = Map.of(T,List.of(A,B),U,List.of(B,C));
        var named = rules(List.of(file("chinese_names","""
            {"tag_names":{"forge:meat":"肉类","test:other_meat":"肉类"}}
            """)),null);
        var merged = index(inputs,named,members);
        check(merged.tags().size() == 1 && merged.tags().get(T).members().equals(List.of(A,B,C)),
                "same Chinese names merge different member sets with deduplicated union");
        check(merged.tagUses(T).size() == 2 && merged.tagUses(T).get(1).acceptedItems().equals(List.of(B,C))
                && merged.tagSources(T).size() == 2 && merged.tagDishes().get(T).size() == 2,
                "same-name grouping preserves actual recipe branches, raw members and dishes");
        check(merged.itemTags().get(C).equals(Set.of(T)) && merged.audit().stream().anyMatch(
                row -> row.original().equals("#"+U) && row.result().equals("merged_same_name")),
                "same-name grouping redirects item links and exposes audit reason");
        var kept = rules(List.of(file("chinese_names","""
            {"tag_names":{"forge:meat":"肉类","test:other_meat":"肉类"},"keep_tags":["test:other_meat"]}
            """)),null);
        check(index(inputs,kept,members).tags().size() == 2, "explicit keep overrides same-name merging");
        var english = rules(List.of(file("english_names","""
            {"tag_names":{"forge:meat":"Meat","test:other_meat":"Meat"}}
            """)),null);
        check(index(inputs,english,members).tags().size() == 2, "untranslated English names do not trigger Chinese grouping");
        var mushroom = ResourceLocation.parse("forge:mushrooms");
        var translated = rules(List.of(file("localized","""
            {"tag_names":{"test:other_meat":"蘑菇"}}
            """)),null);
        var localized = index(List.of(new IngredientJournalIndex.Input(D,
                new RecipeEntry.IngredientSlot(List.of(A,B),List.of(),List.of(mushroom))),inputs.get(1)),
                translated,Map.of(mushroom,List.of(A,B),U,List.of(B,C)));
        check(localized.tags().size() == 1 && localized.tags().containsKey(mushroom),
                "built-in Chinese language names match configured compatibility names");
        var explicit = rules(List.of(file("explicit","""
            {"merge_tags":[{"id":"test:group","name":"肉类","tags":["forge:meat"]}],
             "tag_names":{"test:other_meat":"肉类"}}
            """)),null);
        check(index(inputs,explicit,members).tags().keySet().equals(Set.of(G)),
                "existing manual group remains the canonical winner for identical Chinese names");
    }

    private static void checkSameNameTag() {
        ResourceLocation collision=ResourceLocation.parse("aether_redux:golden_swet_ball");
        var slot=new RecipeEntry.IngredientSlot(List.of(collision,B),List.of(collision),List.of(collision));
        var inputs=List.of(new IngredientJournalIndex.Input(D,slot));
        var index=index(inputs,IngredientCompatRules.empty(),Map.of(collision,List.of(collision,B)));
        ResourceLocation display=ResourceLocation.parse("cuisinejournal:tag/aether_redux/golden_swet_ball");
        check(index.tags().containsKey(display)&&index.items().contains(collision),"same-name item and tag both load without aborting registry");
        check(index.tagDishes().get(display).equals(Set.of(D)),"remapped tag preserves reverse recipes");
        check(index.itemTags().get(collision).contains(display),"membership points to remapped tag entry");
        check(index.tags().get(display).ids().contains(collision),"original tag stays available for name and metadata");
        var single=index(List.of(new IngredientJournalIndex.Input(D,new RecipeEntry.IngredientSlot(List.of(collision),List.of(),List.of(collision)))),
                IngredientCompatRules.empty(),Map.of(collision,List.of(collision)));
        check(single.tags().isEmpty()&&single.itemDishes().containsKey(collision),"same-name single-member tag still dissolves");
        var rules=rules(List.of(file("collision_merge", """
            {"merge_tags":[{"id":"aether_redux:golden_swet_ball","name":"Combined","tags":["aether_redux:golden_swet_ball","test:other_meat"]}]}
            """)),null);
        var merged=index(inputs,rules,Map.of(collision,List.of(collision,B),U,List.of(C)));
        check(merged.tags().get(display).members().contains(C),"remapped merge preserves unused source tag members");
        check("Combined".equals(merged.tags().get(display).displayName()),"remapped merge keeps configured name");
    }

    private static void checkContainerExclusions() {
        List<IngredientCompatRules.RuleFile> files = new ArrayList<>();
        for (String name : List.of("minecraft", "trailandtales", "brewinandchewin", "youkai")) {
            var path = java.nio.file.Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/ingredients/"+name+"_containers.json");
            try {
                files.add(new IngredientCompatRules.RuleFile(id(name),"builtin",json(java.nio.file.Files.readString(path))));
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
        var rules = rules(files,null);
        var containers = List.of("minecraft:bowl", "minecraft:flower_pot", "minecraft:glass_bottle",
                "trailandtales_delight:pottery_bowl", "brewinandchewin:tankard", "youkaishomecoming:sake_bottle")
                .stream().map(ResourceLocation::parse).toList();
        for (var container : containers) check(rules.excludedItem(container),"empty container hidden: "+container);
        var ingredient = ResourceLocation.parse("minecraft:sugar");
        var inputs = new ArrayList<IngredientJournalIndex.Input>();
        containers.forEach(container -> inputs.add(new IngredientJournalIndex.Input(D,
                new RecipeEntry.IngredientSlot(List.of(container)))));
        inputs.add(new IngredientJournalIndex.Input(D,new RecipeEntry.IngredientSlot(List.of(ingredient))));
        var index = index(inputs,rules,Map.of());
        check(index.items().equals(Set.of(ingredient)) && index.itemDishes().keySet().equals(Set.of(ingredient)),
                "empty containers leave catalogue, ingredient statistics and reverse links");
        check(index.audit().stream().filter(row -> containers.stream().anyMatch(item -> item.toString().equals(row.original())))
                .allMatch(row -> row.result().equals("hidden") && !row.rules().isEmpty()),"audit preserves explicit container exclusion sources");
        check(!rules.excludedItem(ResourceLocation.parse("youkaishomecoming:bowl_of_cream"))
                && !rules.excludedItem(ResourceLocation.parse("youkaishomecoming:soy_sauce_bottle"))
                && !rules.excludedItem(ResourceLocation.parse("youkaishomecoming:red_wine_bottle")),
                "filled dishes and bottles are not matched by empty-container exclusions");
        var restored = rules(files,json("{\"included_items\":[\"minecraft:bowl\"]}"));
        check(!restored.excludedItem(ResourceLocation.parse("minecraft:bowl")),"pack authors can restore empty container through local overrides");
        var absent = IngredientCompatRules.resolve(files,mod -> false,null);
        check(absent.excludedItem(ResourceLocation.parse("minecraft:glass_bottle"))
                && !absent.excludedItem(ResourceLocation.parse("youkaishomecoming:sake_bottle")),
                "vanilla exclusions stay active while missing mod files are skipped");
    }

    private static void checkMaterialNamesAndGroups() {
        var files = new ArrayList<IngredientCompatRules.RuleFile>();
        var names = Map.ofEntries(
                Map.entry("forge:milk/milk_bottle", "奶瓶类"),
                Map.entry("minecraft:flesh_raw", "血肉类"),
                Map.entry("aquaculturedelight:roasted_tambaqui_ingredients", "烤大盖巨脂鲤用料"),
                Map.entry("forge:crops/tomato", "番茄类"),
                Map.entry("minecraft:can_freezeto_frozen_flesh", "冻腐肉用肉"),
                Map.entry("forge:crops/potato", "马铃薯类"),
                Map.entry("forge:vegetables/potato", "马铃薯类"),
                Map.entry("forge:cheese", "奶酪类"),
                Map.entry("deltaboxlib:cheese", "奶酪类"),
                Map.entry("forge:cheese/mild", "淡奶酪类"),
                Map.entry("forge:crops/rice", "稻米类"),
                Map.entry("aquaculturedelight:fish_chorba_ingredients", "鱼肉乔巴汤用料"),
                Map.entry("forge:seeds/rice", "稻种类"),
                Map.entry("delightful:hot_spices", "爆辣辣椒"),
                Map.entry("mynethersdelight:curry_meats", "咖喱用肉"),
                Map.entry("forge:bread_slices", "面包片类"),
                Map.entry("forge:shrimps", "虾类"),
                Map.entry("forge:cooked_crab_meat", "熟蟹肉类"),
                Map.entry("culturaldelights:avocados", "牛油果类"),
                Map.entry("forge:crops/garlic", "大蒜类"),
                Map.entry("forge:fruits/orange", "橘子类"),
                Map.entry("forge:sea_pickles", "海泡菜类"),
                Map.entry("deltaboxlib:butter", "黄油类"),
                Map.entry("forge:raw_clam", "生蛤蜊肉类"),
                Map.entry("mynethersdelight:hot_spice", "火热调料"),
                Map.entry("forge:crops/ginger", "姜类"),
                Map.entry("forge:tortilla", "玉米饼类"),
                Map.entry("forge:crops/corn", "玉米类"),
                Map.entry("forge:pasta/raw_pasta", "生面条类"),
                Map.entry("forge:fruits/chorus", "紫颂果类"),
                Map.entry("forge:cooked_rice", "米饭类"));
        for (String name : List.of("general_material_names", "raw_meat_group", "fish_names",
                "delightful_names", "mynethersdelight_names", "culturaldelights_names",
                "unusual_delight_names", "aquaculturedelight_names")) {
            try (var input = IngredientCompatTest.class.getResourceAsStream(
                    "/data/cuisinejournal/cuisinejournal/compat/ingredients/" + name + ".json")) {
                files.add(file(name, JsonParser.parseReader(new java.io.InputStreamReader(
                        Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8)).toString()));
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
        var effective = rules(files, null);
        names.forEach((tag, name) -> check(name.equals(effective.displayName(ResourceLocation.parse(tag))),
                "classified material name: " + tag));
        var groups = Map.of(
                "forge:vegetables/potato", List.of("forge:vegetables/potato", "forge:crops/potato"),
                "forge:vegetables/cucumber", List.of("forge:vegetables/cucumber", "culturaldelights:cucumbers"),
                "forge:raw_strider", List.of("forge:raw_strider", "forge:strider_meat", "nethersdelight:raw_strider"),
                "forge:salt", List.of("forge:salt", "deltaboxlib:salt", "forge:any_salt"),
                "forge:ice_cubes", List.of("forge:ice_cubes", "legendary_delicacies:ice_ingredients"));
        var expectedNames = Map.of("forge:vegetables/potato", "马铃薯类",
                "forge:vegetables/cucumber", "黄瓜", "forge:raw_strider", "炽足兽肉",
                "forge:salt", "盐类", "forge:ice_cubes", "冰块类");
        for (var row : groups.entrySet()) {
            var group = ResourceLocation.parse(row.getKey());
            var realTags = row.getValue().stream().map(ResourceLocation::parse).toList();
            for (var candidate : List.of(effective, IngredientCompatRules.resolve(files, mod -> false, null))) {
                for (var tag : realTags) check(candidate.mergeFor(tag) != null
                        && candidate.mergeFor(tag).id().equals(group), "group independent of optional hosts: " + tag);
                var members = new HashMap<ResourceLocation, List<ResourceLocation>>();
                var inputs = new ArrayList<IngredientJournalIndex.Input>();
                for (int i = 0; i < realTags.size(); i++) {
                    var tag = realTags.get(i); var item = id("member_" + i);
                    members.put(tag, List.of(item));
                    inputs.add(new IngredientJournalIndex.Input(D, id("recipe_" + i), 0,
                            new RecipeEntry.IngredientSlot(List.of(item), List.of(), List.of(tag))));
                }
                var merged = index(inputs, candidate, members);
                check(merged.tags().size() == 1 && merged.tags().get(group).members().size() == realTags.size()
                        && expectedNames.get(row.getKey()).equals(merged.tags().get(group).displayName()),
                        "single named entry with member union: " + group);
                check(merged.tagUses(group).size() == realTags.size() && merged.tagUses(group).stream()
                        .allMatch(use -> use.acceptedItems().equals(members.get(use.tag()))),
                        "merge preserves exact recipe branch members: " + group);
                members.remove(realTags.get(realTags.size() - 1));
                var partial = index(inputs.subList(0, inputs.size() - 1), candidate, members);
                check(partial.tags().size() == 1 && partial.tags().get(group).members().size() == realTags.size() - 1,
                        "missing source tag retains remaining members: " + group);
            }
        }
    }

    private static void checkRequestedScreenshotNames() {
        var expected = Map.ofEntries(
                Map.entry("minecraft:anymetal", "金属"),
                Map.entry("minecraft:anyore", "矿物"),
                Map.entry("forge:fruits/banana", "香蕉类"),
                Map.entry("forge:cooking_oil", "食用油"),
                Map.entry("forge:cream", "奶油类"),
                Map.entry("forge:tea_leaves/black", "红茶叶"),
                Map.entry("forge:crops/tuber", "块茎类"),
                Map.entry("forge:crops/zucchini", "西葫芦类"),
                Map.entry("c:foods/oranges", "橙子类"),
                Map.entry("c:foods/passion_fruit", "百香果"),
                Map.entry("forge:coffees/black", "浓缩咖啡"),
                Map.entry("forge:crops/cauliflower", "花椰菜类"),
                Map.entry("forge:crops/sugarcane", "甘蔗类"),
                Map.entry("c:foods/plums", "李子"),
                Map.entry("forge:fruits/cherry", "樱桃"),
                Map.entry("alexscaves:sweet_berries", "甜浆果类"),
                Map.entry("alexscaves:rock_candies", "冰糖块"),
                Map.entry("ltc2:pepper_powders", "胡椒粉"),
                Map.entry("alexsmobsdelight:raw_kangaroo_meat", "生袋鼠肉类"),
                Map.entry("alexsmobsdelight:raw_froststalker_meat", "生寒霜潜伏者肉类"),
                Map.entry("alexsmobsdelight:mantis_shrimp_tails", "螳螂虾尾"),
                Map.entry("cosmopolitan:funguis", "菌瓶"),
                Map.entry("endersdelight:enderman_loot", "末影珍珠类"),
                Map.entry("barbequesdelight:grilled_skewers", "烤串"),
                Map.entry("vanillacookbook:hot_spices", "火热原料"),
                Map.entry("jellyfishing:roasted_seanut", "烤花生类"));
        var files = new ArrayList<IngredientCompatRules.RuleFile>();
        for (String name : List.of("general_material_names",
                "alexscaves_names",
                "ltc2_names",
                "alexsmobsdelight_names",
                "cosmopolitan_names",
                "endersdelight_names",
                "barbequesdelight_names",
                "vanillacookbook_names",
                "jellyfishingdelight_names")) {
            try (var input = IngredientCompatTest.class.getResourceAsStream(
                    "/data/cuisinejournal/cuisinejournal/compat/ingredients/" + name + ".json")) {
                files.add(file(name, JsonParser.parseReader(new java.io.InputStreamReader(
                        Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8)).toString()));
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
        var effective = rules(files, null);
        expected.forEach((tag, name) -> {
            var key = ResourceLocation.parse(tag);
            check(name.equals(effective.displayName(key)), "requested screenshot name: " + tag);
            var slot = new RecipeEntry.IngredientSlot(List.of(A,B), List.of(), List.of(key));
            var index = index(List.of(new IngredientJournalIndex.Input(D,id("named_recipe"),0,slot)),
                    effective, Map.of(key,List.of(A,B)));
            check(index.tags().values().stream().anyMatch(entry -> name.equals(entry.displayName())),
                    "requested name reaches canonical or synthetic display entry: " + tag);
        });
        var absent = IngredientCompatRules.resolve(files, mod -> false, null);
        check(absent.displayName(ResourceLocation.parse("alexsmobsdelight:raw_froststalker_meat")) == null
                && absent.displayName(ResourceLocation.parse("cosmopolitan:funguis")) == null,
                "mod-specific names skip absent hosts");
        check("金属".equals(absent.displayName(ResourceLocation.parse("minecraft:anymetal"))),
                "general material names do not require an optional host");
        for (var rules : List.of(effective, absent)) {
            check("小花".equals(rules.displayName(ResourceLocation.parse("minecraft:small_flowers")))
                    && "冰块类".equals(rules.displayName(ResourceLocation.parse("forge:frozen_dessert_ingredients"))),
                    "later unified-mode request supersedes the earlier skipped flower and dessert names");
        }
    }

    private static void checkAdditionalScreenshotNamesAndMerges() {
        var expected = Map.ofEntries(
                Map.entry("forge:cake_slices", "蛋糕切片"),
                Map.entry("forge:coffee_beans", "咖啡豆"),
                Map.entry("forge:condensed_milk", "炼乳"),
                Map.entry("forge:crops/fiddleheads", "蕨类植物"),
                Map.entry("forge:fruits/cherry", "樱桃"),
                Map.entry("forge:moss", "苔藓"),
                Map.entry("forge:tea_leaves/yellow", "黄茶叶"),
                Map.entry("forge:vegetables/pepper", "辣椒类"),
                Map.entry("minecraft:logs", "原木"),
                Map.entry("forge:cookies", "曲奇类"),
                Map.entry("forge:crops/red_bean", "红豆"),
                Map.entry("forge:crops/sweet_potato", "红薯"),
                Map.entry("forge:dyes", "染料"),
                Map.entry("forge:fungi", "下界菌类"),
                Map.entry("forge:glass_saladingredients", "玻璃制品"),
                Map.entry("forge:water", "水"),
                Map.entry("forge:crops/eggplant", "茄子"),
                Map.entry("alexsmobsdelight:cooked_squid_tentacles", "熟大王乌贼触手"),
                Map.entry("alexsmobsdelight:raw_bear_meat", "生熊肉类"),
                Map.entry("alexsmobsdelight:cooked_bear_meat", "熟熊肉类"),
                Map.entry("alexsmobsdelight:raw_moose_meat", "生驼鹿肉类"),
                Map.entry("alexsmobsdelight:cooked_emu", "熟鸸鹋肉类"),
                Map.entry("alexsmobsdelight:cooked_froststalker_meat", "熟寒霜潜伏者肉"),
                Map.entry("alexsmobsdelight:raw_bison_meat", "生野牛肉类"),
                Map.entry("alexsmobsdelight:raw_tusklin_meat", "生獠牙兽肉类"),
                Map.entry("cavedelight:slam", "猛汉午餐肉类"),
                Map.entry("cavedelight:big_dinosaur_egg", "大龙蛋"),
                Map.entry("cavedelight:radgill", "辐鳃鱼肉类"),
                Map.entry("cavedelight:small_dinosaur_egg", "小龙蛋"),
                Map.entry("cavedelight:soylent", "“绿色食品”"),
                Map.entry("cornexpansion:corn", "玉米"),
                Map.entry("endersdelight:shulker_loot", "潜影贝柱类"),
                Map.entry("endersdelight:enderman_sight", "末影人眼球类"),
                Map.entry("arsdelight:raw_wilden_meat", "生荒野肉类"),
                Map.entry("arsdelight:barks", "至高树皮"),
                Map.entry("arsdelight:raw_chimera", "生奇美拉肉类"),
                Map.entry("arsdelight:leaves", "至高树叶"),
                Map.entry("kitchenkarrot:cooked_meat", "熟肉类"),
                Map.entry("kitchenkarrot:grass_spices", "原版草"),
                Map.entry("kitchenkarrot:meat", "春卷用肉"),
                Map.entry("muffins_thaidelight:lime", "青柠类"),
                Map.entry("muffins_thaidelight:raw_papaya", "青木瓜类"),
                Map.entry("muffins_thaidelight:ripe_papaya", "熟木瓜类"),
                Map.entry("farmersrespite:tea_leaves", "茶叶类"),
                Map.entry("crockpot:parrot_eggs", "鹦鹉蛋类"),
                Map.entry("cosmopolitan:biome_gulimes", "果莱姆"),
                Map.entry("dungeonsdelight:acidics", "怪物松饼用提取物"),
                Map.entry("abnormals_delight:barbecue_stick_ingredients", "烤串用肉"),
                Map.entry("abnormals_delight:slabdish_ingredients", "水中植物"),
                Map.entry("ltc2:nether_peppers", "下界辣椒"),
                Map.entry("alexscaves:gelatins", "明胶类"),
                Map.entry("netherexp:cooked_hogham", "熟疣猪兽肉类"),
                Map.entry("forge:cooked_hogham", "熟疣猪兽肉类"),
                Map.entry("forge:raw_duck", "生鸭肉类"),
                Map.entry("forge:cooked_duck", "熟鸭肉类"),
                Map.entry("forge:fish_slices", "生鱼片类"),
                Map.entry("forge:raw_fishes/bass", "生鲈鱼类"),
                Map.entry("forge:food/raw_fish", "原版鱼类"));
        var files = new ArrayList<IngredientCompatRules.RuleFile>();
        for (String name : List.of("general_material_names",
                "alexsmobsdelight_names",
                "cavedelight_names",
                "cornexpansion_names",
                "endersdelight_names",
                "arsdelight_names",
                "kitchenkarrot_names",
                "muffins_thaidelight_names",
                "farmersrespite_names",
                "crockpot_names",
                "cosmopolitan_names",
                "dungeonsdelight_names",
                "abnormals_delight_names",
                "ltc2_names",
                "alexscaves_names",
                "netherexp_names",
                "raw_meat_names",
                "fish_names",
                "culturaldelights_names",
                "salad_group")) {
            try (var input = IngredientCompatTest.class.getResourceAsStream(
                    "/data/cuisinejournal/cuisinejournal/compat/ingredients/" + name + ".json")) {
                files.add(file(name, JsonParser.parseReader(new java.io.InputStreamReader(
                        Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8)).toString()));
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
        var effective = rules(files, null);
        expected.forEach((tag, name) -> {
            var key = ResourceLocation.parse(tag);
            check(name.equals(effective.displayName(key)), "additional screenshot name: " + tag);
            var index = index(List.of(new IngredientJournalIndex.Input(D,id("named_recipe"),0,
                    new RecipeEntry.IngredientSlot(List.of(A,B),List.of(),List.of(key)))),effective,Map.of(key,List.of(A,B)));
            check(index.tags().values().stream().anyMatch(entry -> name.equals(entry.displayName())),
                    "additional screenshot name reaches synthetic display entries: " + tag);
        });
        var merges = Map.of(
                "forge:salad_ingredients", List.of("forge:salad_ingredients","forge:crops/cabbage",
                        "forge:salad_ingredients/cabbage","farm_and_charm:cabbage","dumplings_delight:cabbage"),
                "forge:ice_cubes", List.of("forge:ice_cubes","legendary_delicacies:ice_ingredients","forge:ice_ingredients"),
                "forge:chocolate", List.of("forge:chocolate","cornexpansion:candied_ingredient"));
        var mergeNames = Map.of("forge:salad_ingredients","卷心菜类","forge:ice_cubes","冰块类","forge:chocolate","巧克力类");
        var absent = IngredientCompatRules.resolve(files,mod -> false,null);
        for (var candidate : List.of(effective,absent)) {
            check((candidate == absent || "茄子类".equals(candidate.displayName(ResourceLocation.parse("culturaldelights:all_eggplants"))))
                    && candidate.mergeFor(ResourceLocation.parse("culturaldelights:all_eggplants")) == null,
                    "later cultural all-eggplants naming request keeps the tag independent");
            check(candidate.displayName(ResourceLocation.parse("forge:raw_squid")) == null
                    && candidate.mergeFor(ResourceLocation.parse("forge:raw_squid")) == null,
                    "skipped raw squid remains unchanged");
            for (var row : merges.entrySet()) {
                var group = ResourceLocation.parse(row.getKey());
                var tags = row.getValue().stream().map(ResourceLocation::parse).toList();
                for (var tag : tags) check(candidate.mergeFor(tag) != null
                        && candidate.mergeFor(tag).id().equals(group), "requested display merge target: " + tag);
                Map<ResourceLocation,List<ResourceLocation>> members = new HashMap<>();
                var inputs = new ArrayList<IngredientJournalIndex.Input>();
                for (int i=0;i<tags.size();i++) {
                    var tag=tags.get(i);var item=id("merge_member_"+i);members.put(tag,List.of(item));
                    inputs.add(new IngredientJournalIndex.Input(D,id("merge_recipe_"+i),i,
                            new RecipeEntry.IngredientSlot(List.of(item),List.of(),List.of(tag))));
                }
                var index=index(inputs,candidate,members);
                check(index.tags().size()==1 && index.tags().get(group).members().size()==tags.size()
                        && mergeNames.get(row.getKey()).equals(index.tags().get(group).displayName()),
                        "requested merge retains deduplicated union and existing group name: "+group);
                check(index.tagUses(group).size()==tags.size() && index.tagUses(group).stream()
                        .allMatch(use -> use.acceptedItems().equals(members.get(use.tag()))),
                        "requested merge does not broaden any real recipe ingredient: "+group);
                members.remove(tags.get(tags.size()-1));
                var partial=index(inputs.subList(0,inputs.size()-1),candidate,members);
                check(partial.tags().size()==1 && partial.tags().get(group).members().size()==tags.size()-1,
                        "missing optional merge source leaves the group usable: "+group);
            }
        }
        check(absent.displayName(ResourceLocation.parse("arsdelight:raw_chimera"))==null
                && absent.displayName(ResourceLocation.parse("cavedelight:soylent"))==null,
                "new host-specific name files do not activate without their mods");
    }

    private static void checkUnifiedModeTagNames() {
        var files = new ArrayList<IngredientCompatRules.RuleFile>();
        try (var paths = java.nio.file.Files.list(java.nio.file.Path.of(
                "src/main/resources/data/cuisinejournal/cuisinejournal/compat/ingredients"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList())
                files.add(file(path.getFileName().toString(), java.nio.file.Files.readString(path)));
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        var effective = rules(files, null);
        var names = Map.ofEntries(
                Map.entry("bakery:dough", "面团类"),
                Map.entry("farm_and_charm:dough", "面团类"),
                Map.entry("deltaboxlib:dough/wheat", "面团类"),
                Map.entry("forge:crops/bell_pepper", "甜椒类"),
                Map.entry("forge:frozen_dessert_ingredients", "冰块类"),
                Map.entry("alexsmobsdelight:large_cooked_meat", "熟熊肉类"),
                Map.entry("culturaldelights:all_eggplants", "茄子类"),
                Map.entry("forge:raw_fishes/perch", "生鲈鱼类"),
                Map.entry("forge:raw_fishes/bass", "生鲈鱼类"),
                Map.entry("forge:pineapples", "菠萝类"));
        names.forEach((tag,name) -> check(name.equals(effective.displayName(ResourceLocation.parse(tag))),
                "unification-visible tag is named by its own ID: " + tag));
        var dough = List.of("bakery:dough", "farm_and_charm:dough", "deltaboxlib:dough/wheat");
        Map<ResourceLocation,List<ResourceLocation>> doughMembers = new HashMap<>();
        var doughInputs = new ArrayList<IngredientJournalIndex.Input>();
        for (int i=0;i<dough.size();i++) {
            var tag=ResourceLocation.parse(dough.get(i));doughMembers.put(tag,List.of(A,B,C));
            doughInputs.add(new IngredientJournalIndex.Input(D,id("dough_"+i),i,
                    new RecipeEntry.IngredientSlot(List.of(A,B,C),List.of(),List.of(tag))));
        }
        var mergedDough=index(doughInputs,effective,doughMembers);
        check(mergedDough.tags().size()==1 && "面团类".equals(mergedDough.tags().values().iterator().next().displayName()),
                "identical dough aliases retain a name whichever ID wins");
        check(mergedDough.tagUses(mergedDough.tags().keySet().iterator().next()).size()==3,
                "dough alias merge preserves all original recipe paths");
        var smallFlowers=ResourceLocation.parse("minecraft:small_flowers");
        var flowers=ResourceLocation.parse("minecraft:flowers");
        check("小花".equals(effective.displayName(smallFlowers)) && effective.mergeFor(smallFlowers)==null,
                "small flowers have their own name and no explicit merge");
        var flowerIndex=index(List.of(
                new IngredientJournalIndex.Input(D,id("small_flower_recipe"),0,
                        new RecipeEntry.IngredientSlot(List.of(A,B),List.of(),List.of(smallFlowers))),
                new IngredientJournalIndex.Input(D,id("flower_recipe"),1,
                        new RecipeEntry.IngredientSlot(List.of(A,B,C),List.of(),List.of(flowers)))),
                effective,Map.of(smallFlowers,List.of(A,B),flowers,List.of(A,B,C)));
        check(flowerIndex.tags().size()==2 && flowerIndex.tags().values().stream()
                        .anyMatch(entry -> "小花".equals(entry.displayName()) && entry.members().equals(List.of(A,B))),
                "small flower subset stays separate from the broader flower tag");
        var groups = Map.of(
                "forge:chocolate", List.of("forge:chocolate","cornexpansion:candied_ingredient","candlelight:chocolate"),
                "forge:ice_cubes", List.of("forge:ice_cubes","forge:frozen_dessert_ingredients"));
        for (var row : groups.entrySet()) {
            var group=ResourceLocation.parse(row.getKey());
            var members=new HashMap<ResourceLocation,List<ResourceLocation>>();
            var inputs=new ArrayList<IngredientJournalIndex.Input>();
            for(int i=0;i<row.getValue().size();i++) {
                var tag=ResourceLocation.parse(row.getValue().get(i));
                var accepted=List.of(A,id("unified_member_"+i));members.put(tag,accepted);
                inputs.add(new IngredientJournalIndex.Input(D,id("unified_recipe_"+i),i,
                        new RecipeEntry.IngredientSlot(accepted,List.of(),List.of(tag))));
            }
            var index=index(inputs,effective,members);
            check(index.tags().size()==1 && index.tags().containsKey(group),
                    "requested group keeps its stable ID: " + group);
            check(index.tags().get(group).members().size()==row.getValue().size()+1,
                    "requested display union deduplicates shared items: " + group);
            check(index.tagUses(group).size()==inputs.size() && index.tagUses(group).stream()
                    .allMatch(use -> use.acceptedItems().equals(members.get(use.tag()))),
                    "unified-mode display grouping never widens a recipe: " + group);
            members.remove(ResourceLocation.parse(row.getValue().get(row.getValue().size()-1)));
            var missing=index(inputs.subList(0,inputs.size()-1),effective,members);
            check(missing.tags().size()==1 && missing.tags().containsKey(group),
                    "optional missing group member leaves remaining sources visible: " + group);
        }
        var absent=IngredientCompatRules.resolve(files,mod -> false,null);
        check(absent.displayName(ResourceLocation.parse("bakery:dough"))==null
                && absent.displayName(ResourceLocation.parse("deltaboxlib:dough/wheat"))==null,
                "optional dough names do not activate without their hosts");
        check(effective.mergeFor(ResourceLocation.parse("forge:raw_squid"))==null,
                "unrelated skipped raw-squid tag remains independent");
    }

    private static void checkProcessingConfig() {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ingredient-compat-test");
            java.nio.file.Path config = dir.resolve("cuisinejournal-ingredients.json");
            java.nio.file.Files.writeString(config, "{}");
            IngredientCatalogueConfig.load(dir);
            check(IngredientCatalogueConfig.current().processing(ResourceLocation.parse("farmersdelight:cutting")),
                    "missing config list preserves automatic processing defaults");
            java.nio.file.Files.writeString(config, """
                {"processingRecipeTypes":[],"compatOverrides":{"processing_recipe_types":{"add":["test:cutting"]}}}
                """);
            IngredientCatalogueConfig.load(dir);
            check(!IngredientCatalogueConfig.current().processing(ResourceLocation.parse("farmersdelight:cutting")),
                    "explicit empty local list replaces defaults");
            check(IngredientCatalogueConfig.current().processing(id("cutting")), "local processing patch overrides local base list");
            java.nio.file.Files.delete(config); java.nio.file.Files.delete(dir);
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static IngredientJournalIndex index(List<IngredientJournalIndex.Input> inputs, IngredientCompatRules r,
                                                Map<ResourceLocation,List<ResourceLocation>> tags) {
        return IngredientJournalIndex.build(inputs,r,key -> tags.getOrDefault(key,List.of()),
                item -> !r.excludedItem(item),r::hiddenTag);
    }
    private static IngredientCompatRules rules(List<IngredientCompatRules.RuleFile> files, JsonObject local) {
        return IngredientCompatRules.resolve(files, mod -> true, local);
    }
    private static IngredientCompatRules.RuleFile file(String name,String json) {
        return new IngredientCompatRules.RuleFile(id("cuisinejournal/compat/"+name+".json"),"pack_"+name,json(json));
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:"+path); }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); checks++; }
}
