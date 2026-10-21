package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class UnificationRulesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var changing = new java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation,JsonElement>>(Map.of(
                id("test:tags/items/reload.json"),JsonParser.parseString("{values:[]}")));
        var dynamic = UnificationRulePack.lazy(changing::get);
        try(var first=dynamic.open()){
            check(first.getResource(net.minecraft.server.packs.PackType.SERVER_DATA,id("test:tags/items/reload.json"))!=null,"lazy pack initial contents");
        }
        changing.set(Map.of(id("test:recipes/changed.json"),JsonParser.parseString("{}")));
        try(var second=dynamic.open()){
            check(second.getResource(net.minecraft.server.packs.PackType.SERVER_DATA,id("test:tags/items/reload.json"))==null
                    &&second.getResource(net.minecraft.server.packs.PackType.SERVER_DATA,id("test:recipes/changed.json"))!=null,
                    "reopening same pack rereads changed rules without repository recreation");
        }
        var rules = JsonParser.parseString("""
            {"test:tomato":{
              "items":["test:a","test:b","test:c"],
              "priority":["test:c","test:b","test:a"],
              "resources":{"test:recipes/meal.json":{
                "type":"minecraft:crafting_shapeless",
                "ingredients":[{"tag":"$tag"}],
                "result":{"item":"$representative","count":2}}}}}
            """).getAsJsonObject();
        var available = Set.of(id("test:a"),id("test:b"));
        var compiled = UnificationRules.compile(rules,true,available::contains,mod->true);
        check(compiled.problems().isEmpty(),"valid ordinary-item group compiles");
        check(compiled.replacements().equals(Map.of(id("test:a"),id("test:b"))),"missing highest priority falls back");
        var recipe=compiled.resources().get(id("test:recipes/meal.json")).getAsJsonObject();
        check(recipe.getAsJsonObject("result").get("item").getAsString().equals("test:b")
                &&recipe.getAsJsonObject("result").get("count").getAsInt()==2,"representative expansion preserves quantity");
        check(recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("tag").getAsString()
                .equals("cuisinejournal:unification/test/tomato"),"ingredient template expands exact equivalence tag");
        check(rules.toString().contains("$representative"),"compilation never mutates source document");
        check(compiled.resources().get(id("cuisinejournal:tags/items/unified_hidden.json"))
                .getAsJsonObject().getAsJsonArray("values").size()==1,"only duplicate is hidden");
        try(var pack = new UnificationRulePack(compiled.resources())) {
            var found = new HashMap<ResourceLocation,net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream>>();
            pack.listResources(net.minecraft.server.packs.PackType.SERVER_DATA,"test","recipes",found::put);
            check(found.keySet().equals(Set.of(id("test:recipes/meal.json"))),"pack enumerates real recipe resource path");
            try(var stream=found.get(id("test:recipes/meal.json")).get()) {
                check(JsonParser.parseReader(new java.io.InputStreamReader(stream)).equals(recipe),"generated JSON is readable by resource loader");
            }
            check(pack.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,id("test:recipes/meal.json"))==null,
                    "no client asset overrides");
        }
        var disabled=UnificationRules.compile(rules,false,available::contains,mod->true);
        check(disabled.replacements().isEmpty()&&disabled.resources().size()==1,"default mode has no patches or hidden items");
        check(UnificationRules.compile(rules,true,item->item.equals(id("test:a")),mod->true)
                .replacements().isEmpty(),"single installed member stays untouched");
        var required=rules.deepCopy(); required.getAsJsonObject("test:tomato").add("required_mods",JsonParser.parseString("[\"absent\"]"));
        check(UnificationRules.compile(required,true,available::contains,mod->false).replacements().isEmpty(),"missing required mod skips group");
        var invalid=rules.deepCopy(); invalid.getAsJsonObject("test:tomato").getAsJsonObject("resources")
                .add("test:models/invalid.json",new JsonObject());
        var rejected=UnificationRules.compile(invalid,true,available::contains,mod->true);
        check(rejected.replacements().isEmpty()&&rejected.resources().size()==1&&rejected.problems().size()==1,
                "invalid resource rejects entire group without partial hiding");
        var overlapping=rules.deepCopy(); overlapping.add("test:other",rules.get("test:tomato").deepCopy());
        check(UnificationRules.compile(overlapping,true,available::contains,mod->true).problems().size()==1,"overlap cannot produce chains");
        var reverse=UnificationRules.compile(rules,true,item->true,mod->true);
        check(reverse.replacements().get(id("test:b")).equals(id("test:c")),"preferred installed item selected first");
        var conditional = rules.deepCopy();
        conditional.getAsJsonObject("test:tomato").add("priority_when_loaded",
                JsonParser.parseString("{optional_compat:['test:a','test:c','test:b']}"));
        check(UnificationRules.compile(conditional,true,item->true,mod->false).replacements().get(id("test:a")).equals(id("test:c")),
                "absent optional compat preserves default priority");
        check(UnificationRules.compile(conditional,true,item->true,mod->mod.equals("optional_compat")).replacements().get(id("test:c")).equals(id("test:a")),
                "optional compat priority affects hiding and replacement together");
        conditional.getAsJsonObject("test:tomato").getAsJsonObject("priority_when_loaded").add("missing",JsonParser.parseString("['test:foreign']"));
        check(!UnificationRules.compile(conditional,true,item->true,mod->false).problems().isEmpty(),
                "invalid conditional order rejected even if optional mod absent");
        var badKeep = rules.deepCopy(); badKeep.getAsJsonObject("test:tomato").addProperty("keep_recipes","yes");
        check(!UnificationRules.compile(badKeep,true,item->true,mod->true).problems().isEmpty(),
                "invalid producer policy cannot partially hide a group");
        var empty=UnificationRules.compile(new JsonObject(),true,item->true,mod->true);
        check(empty.replacements().isEmpty(),"no built-in food choices are invented");
        check(com.chi__you.cuisinejournal.recipe.HostItemAvailability.hiddenByUnification(UnificationRules.HIDDEN::equals),
                "catalogue recognizes own hidden tag without Compat");
        try(var input=UnificationRulesTest.class.getResourceAsStream("/cuisinejournal/unification.json")) {
            var bundled = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input))).getAsJsonObject();
            check(bundled.size() > 30 && !bundled.has("cuisinejournal:golden_moth"),
                    "shipping explicit food groups exclude non-equivalent moth jars");
        }
        System.out.println("UnificationRulesTest: "+checks+" checks passed");
    }
    private static ResourceLocation id(String text){return ResourceLocation.parse(text);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
