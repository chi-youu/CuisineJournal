package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

public final class UnificationContentTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var document = JsonParser.parseString(Files.readString(Path.of("src/main/resources/cuisinejournal/unification.json"))).getAsJsonObject();
        var all = new HashSet<ResourceLocation>();
        document.entrySet().forEach(e -> e.getValue().getAsJsonObject().getAsJsonArray("items").forEach(v -> all.add(id(v.getAsString()))));
        all.add(id("braziliandelight:peanut_seeds")); all.add(id("crabbersdelight:cooked_glow_squid_tentacles"));
        all.add(id("dungeonsdelight:soft_serve_sniffer_egg"));
        var plan = UnificationContent.compile(document, true, all::contains, mod -> true);
        check(UnificationRules.compile(document, true, all::contains, mod -> true).problems().isEmpty(), "no duplicate memberships");
        check(plan.replace(id("braziliandelight:peanut")).equals(id("vintagedelight:peanut")), "peanut priority");
        check(plan.replace(id("rusticdelight:coffee_beans")).equals(id("youkaishomecoming:green_coffee_bean")), "raw coffee never becomes roasted");
        check(plan.replace(id("rusticdelight:roasted_coffee_beans")).equals(id("youkaishomecoming:coffee_beans")), "roasted coffee priority");
        check(plan.replace(id("rusticdelight:calamari_slice")).equals(id("miners_delight:tentacles")), "rustic slice is tentacle tier");
        check(plan.replace(id("oceansdelight:cut_tentacles")).equals(id("oceansdelight:cut_tentacles")), "unique smaller cut retained");
        check(plan.excluded().contains(id("crabbersdelight:cooked_glow_squid_tentacles")), "whole glow squid removes cooked glow tentacles");
        check(!plan.replacements().containsKey(id("aquamirae_delight:golden_moth_in_a_jar")), "different moth contents untouched");
        check(!plan.replacements().containsKey(id("dungeonsdelight:sniffer_shank")), "sniffer leg not a steak");
        check(!plan.replacements().containsKey(id("fruitsdelight:apple_jelly")), "jams excluded");
        check(plan.disabledPlants().contains("rusticdelight:wild_coffee"), "lower wild coffee suppressed");
        check(UnificationContent.compile(document, false, all::contains, mod -> true).equals(UnificationContent.EMPTY), "default mode empty");
        var tags = new LinkedHashMap<ResourceLocation,List<net.minecraft.tags.TagLoader.EntryWithSource>>();
        tags.put(id("test:ingredients"), new ArrayList<>(List.of(
                new net.minecraft.tags.TagLoader.EntryWithSource(net.minecraft.tags.TagEntry.optionalElement(id("braziliandelight:peanut")),"test"),
                new net.minecraft.tags.TagLoader.EntryWithSource(net.minecraft.tags.TagEntry.tag(id("test:nested")),"test"),
                new net.minecraft.tags.TagLoader.EntryWithSource(net.minecraft.tags.TagEntry.element(id("crabbersdelight:raw_glow_squid_tentacles")),"test"))));
        tags.put(UnificationRules.HIDDEN,new ArrayList<>(List.of(
                new net.minecraft.tags.TagLoader.EntryWithSource(net.minecraft.tags.TagEntry.element(id("braziliandelight:peanut")),"test"))));
        UnificationTags.apply(tags,plan);
        check(tags.get(id("test:ingredients")).get(0).entry().getId().equals(id("vintagedelight:peanut"))
                && !tags.get(id("test:ingredients")).get(0).entry().isRequired(),"real tag optional member redirected");
        check(tags.get(id("test:ingredients")).size()==2 && tags.get(id("test:ingredients")).get(1).entry().isTag(),"nested tag intact; excluded glow member removed");
        check(tags.get(UnificationRules.HIDDEN).get(0).entry().getId().equals(id("braziliandelight:peanut")),"hidden identity never redirected to winner");
        coreHooks();
        for (var entry : document.entrySet()) {
            var members = UnificationRules.priority(entry.getValue().getAsJsonObject(), mod -> true);
            for (int start = 0; start < members.size(); start++) {
                var available = new HashSet<ResourceLocation>();
                for (int i = start; i < members.size(); i++) available.add(members.get(i));
                var fallback = UnificationContent.compile(document, true, available::contains, mod -> true);
                check(members.get(start).equals(fallback.winners().get(entry.getKey())), "missing-prefix fallback " + entry.getKey());
                check(fallback.replacements().values().stream().allMatch(available::contains), "no absent target");
            }
        }
        var recipes = new LinkedHashMap<ResourceLocation, JsonElement>();
        var seedRecipe=new LinkedHashMap<ResourceLocation,JsonElement>();
        seedRecipe.put(id("test:seed_food"),json("{type:'minecraft:crafting_shapeless',ingredients:[{item:'braziliandelight:peanut_seeds'}],result:{item:'test:food'}}"));
        UnificationRecipes.apply(seedRecipe,plan);
        check(seedRecipe.containsKey(id("test:seed_food"))&&seedRecipe.get(id("test:seed_food")).toString().contains("vintagedelight:peanut"),
                "excluded seed with a valid representative normalizes its input instead of deleting a unique dish");
        recipes.put(id("moredelight:cut"), json("{type:'farmersdelight:cutting',ingredients:[{item:'minecraft:potato'}],result:[{item:'moredelight:diced_potatoes'}]}"));
        recipes.put(id("moredelight:meal"), json("{type:'farmersdelight:cooking',ingredients:[{item:'moredelight:diced_potatoes'}],result:{item:'moredelight:meal',count:2}}"));
        recipes.put(id("neapolitan:milk/back"), json("{type:'minecraft:crafting_shapeless',ingredients:[{tag:'forge:bottles/milk'},{tag:'forge:bottles/milk'},{tag:'forge:bottles/milk'}],result:{item:'minecraft:milk_bucket'}}"));
        recipes.put(id("rusticdelight:roast"), json("{type:'minecraft:smelting',ingredient:{item:'rusticdelight:coffee_beans'},result:'rusticdelight:roasted_coffee_beans'}"));
        recipes.put(id("dungeonsdelight:egg"), json("{type:'minecraft:smoking',ingredient:{item:'minecraft:sniffer_egg'},result:'dungeonsdelight:soft_serve_sniffer_egg'}"));
        recipes.put(id("trailandtales_delight:egg"), json("{type:'minecraft:smelting',ingredient:{item:'minecraft:sniffer_egg'},result:'trailandtales_delight:cooked_sniffer_egg_block'}"));
        recipes.put(id("corn_delight:cut"), json("{type:'farmersdelight:cutting',ingredients:[{tag:'forge:crops/corn'}],result:[{item:'corn_delight:corn_seeds',count:2},{item:'corn_delight:corncob'}]}"));
        UnificationRecipes.apply(recipes, plan);
        check(!recipes.containsKey(id("moredelight:cut")), "lower duplicate potato cut removed");
        check(recipes.get(id("moredelight:meal")).toString().contains("rusticdelight:potato_slices"), "unique dish input redirected");
        check(!recipes.containsKey(id("neapolitan:milk/back")), "no three-bottle duplication");
        check(recipes.containsKey(id("rusticdelight:roast")) && recipes.get(id("rusticdelight:roast")).toString().contains("green_coffee_bean"), "unique roast method remains with correct stage");
        check(!recipes.containsKey(id("dungeonsdelight:egg")) && recipes.containsKey(id("trailandtales_delight:egg")), "whole egg winner preserves shell chain");
        check(recipes.get(id("corn_delight:cut")).toString().contains("white_kernels"), "optional Brazilian white kernels");
        var after = new LinkedHashMap<>(recipes); UnificationRecipes.apply(recipes, plan);
        check(recipes.equals(after), "recipe edits idempotent");
        check(UnificationRuntime.suppressedDrop(id("crabbersdelight:raw_squid_tentacles"),id("minecraft:squid"),plan), "no parallel tentacle drops");
        check(!UnificationRuntime.suppressedDrop(id("miners_delight:squid"),id("minecraft:squid"),plan), "whole squid winner retained");
        check(!UnificationRuntime.suppressedDrop(id("minecraft:ink_sac"),id("minecraft:squid"),plan), "base ink retained");
        check(UnificationRuntime.suppressedDrop(id("kaleidoscope_twilight:raw_naga_meat"),id("twilightforest:naga"),plan), "no double naga meat");
        check(!UnificationRuntime.suppressedDrop(id("twilightforest:naga_scale"),id("twilightforest:naga"),plan), "naga trophy resources unchanged");
        var only = Set.of(id("unusual_delight:fried_sniffer_egg"),id("dungeonsdelight:soft_serve_sniffer_egg"));
        var eggFallback=UnificationContent.compile(document,true,only::contains,mod->true);
        var fallbackRecipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        fallbackRecipes.put(id("dungeonsdelight:egg"),json("{type:'minecraft:smoking',ingredient:{item:'minecraft:sniffer_egg'},result:'dungeonsdelight:soft_serve_sniffer_egg'}"));
        check(UnificationRecipes.apply(fallbackRecipes,eggFallback)==0,"dungeon smoking retained without higher egg mods");
        fallbackRecipes.put(id("unusual_delight:egg"),json("{type:'minecraft:smoking',ingredient:{item:'minecraft:sniffer_egg'},result:'unusual_delight:fried_sniffer_egg'}"));
        check(UnificationRecipes.apply(fallbackRecipes,eggFallback)==1&&!fallbackRecipes.containsKey(id("unusual_delight:egg")),"dungeon wins smoking over unusual without higher hosts");
        var rawBag = new LinkedHashMap<ResourceLocation,JsonElement>();
        rawBag.put(id("rusticdelight:unpack"),json("{type:'minecraft:crafting_shapeless',ingredients:[{item:'rusticdelight:coffee_beans_bag'}],result:{item:'rusticdelight:coffee_beans',count:9}}"));
        UnificationRecipes.apply(rawBag,plan);
        check(rawBag.size()==1&&rawBag.values().iterator().next().toString().contains("green_coffee_bean"),"unique raw coffee bag still unpacks nine raw beans");
        letsDo(document, all, plan);
        installed(document);
        System.out.println("UnificationContentTest: "+checks+" checks passed");
    }
    private static void letsDo(JsonObject document, Set<ResourceLocation> all, UnificationContent.Plan plan) throws Exception {
        for (String[] pair : List.of(new String[]{"farm_and_charm:butter","youkaishomecoming:butter"},
                new String[]{"farm_and_charm:corn","corn_delight:corn"},
                new String[]{"farm_and_charm:kernels","corn_delight:corn_seeds"},
                new String[]{"wildernature:venison","twilightforest:raw_venison"},
                new String[]{"wildernature:cooked_venison","twilightforest:cooked_venison"},
                new String[]{"herbalbrews:coffee_beans","youkaishomecoming:green_coffee_bean"},
                new String[]{"farm_and_charm:raw_pasta","farmersdelight:raw_pasta"},
                new String[]{"farm_and_charm:bacon","farmersdelight:bacon"},
                new String[]{"farm_and_charm:minced_beef","farmersdelight:minced_beef"},
                new String[]{"farm_and_charm:chicken_parts","farmersdelight:chicken_cuts"},
                new String[]{"farm_and_charm:flour","kaleidoscope_cookery:flour"},
                new String[]{"vintagedelight:oat","farm_and_charm:oat"},
                new String[]{"vintagedelight:oat_seeds","farm_and_charm:oat_seeds"},
                new String[]{"farm_and_charm:strawberry","neapolitan:strawberries"},
                new String[]{"farm_and_charm:strawberry_seeds","neapolitan:strawberry_pips"}))
            check(plan.replace(id(pair[0])).equals(id(pair[1])),"Let's Do priority "+pair[0]);
        for (String untouched : List.of("meadow:alpine_salt","vintagedelight:raw_oats","vintagedelight:oat_bag",
                "neapolitan:white_strawberries","farm_and_charm:roasted_corn","brewery:dried_corn","brewery:dried_oat",
                "bakery:cake_dough","bakery:sweet_dough","kaleidoscope_cookery:raw_noodles","vinery:red_grape",
                "meadow:piece_of_cheese"))
            check(plan.replace(id(untouched)).equals(id(untouched)),"independent form or Compat-owned item untouched "+untouched);
        check(plan.replace(id("farm_and_charm:tomato")).equals(id("kaleidoscope_cookery:tomato"))
                &&plan.replace(id("farm_and_charm:lettuce")).equals(id("kaleidoscope_cookery:lettuce")),
                "explicit Compat omissions now have the approved food fallback");
        check(plan.disabledPlants().containsAll(Set.of("farm_and_charm:wild_corn","vintagedelight:wild_oats",
                "farm_and_charm:wild_strawberries","herbalbrews:wild_coffee_plant")),"only lower wild crop sources suppressed");
        var noCompat = UnificationContent.compile(document,true,all::contains,mod->false);
        check(noCompat.replace(id("farm_and_charm:dough")).equals(id("farmersdelight:wheat_dough")),"standalone dough keeps existing farmer priority");
        check(plan.replace(id("farm_and_charm:dough")).equals(id("kaleidoscope_cookery:raw_dough"))
                && plan.replace(id("farmersdelight:wheat_dough")).equals(id("kaleidoscope_cookery:raw_dough")),
                "with Compat no dough output points back at its hidden farmer item");
        check(noCompat.winner("cuisinejournal:strawberry_storage").equals(id("farm_and_charm:strawberry_bag")),
                "without Berry Good keep usable nine-fruit bag instead of disabled basket recipe");
        check(plan.winner("cuisinejournal:strawberry_storage").equals(id("neapolitan:strawberry_basket")),
                "with Berry Good native basket priority restored");
        var noYoukai = UnificationContent.compile(document,true,item->all.contains(item)&&!item.getNamespace().equals("youkaishomecoming"),mod->false);
        check(noYoukai.winner("cuisinejournal:butter").equals(id("farm_and_charm:butter"))
                && noYoukai.winner("cuisinejournal:coffee_raw").equals(id("herbalbrews:coffee_beans")),
                "Let's Do butter and raw coffee second to Youkai");
        var noCorn = UnificationContent.compile(document,true,item->all.contains(item)&&!item.getNamespace().equals("corn_delight"),mod->false);
        check(noCorn.winner("cuisinejournal:corn").equals(id("farm_and_charm:corn"))
                && noCorn.winner("cuisinejournal:corn_kernel").equals(id("farm_and_charm:kernels")),"Let's Do corn second to Corn Delight");
        var deerFallback = UnificationContent.compile(document,true,item->all.contains(item)
                &&!Set.of("twilightforest","youkaishomecoming").contains(item.getNamespace()),mod->false);
        check(deerFallback.winner("cuisinejournal:venison").equals(id("wildernature:venison"))
                && deerFallback.winner("cuisinejournal:cooked_venison").equals(id("wildernature:cooked_venison")),"wild deer follows Youkai and precedes Environmental");
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.SERVER_DATA,List.of(new UnificationRulePack(Map.of(UnificationContent.POLICY,UnificationContent.encode(plan)))))) {
            check(UnificationContent.read(resources).equals(plan),"committed resource policy preserves alternate producer choices");
        }
        var channels = new LinkedHashMap<ResourceLocation,JsonElement>();
        for (String[] route : List.of(new String[]{"dough","farm_and_charm:dough","kaleidoscope_cookery:raw_dough"},
                new String[]{"flour","farm_and_charm:flour","kaleidoscope_cookery:flour"},
                new String[]{"raw_pasta","farm_and_charm:raw_pasta","farmersdelight:raw_pasta"},
                new String[]{"bacon","farm_and_charm:bacon","farmersdelight:bacon"},
                new String[]{"minced_beef","farm_and_charm:minced_beef","farmersdelight:minced_beef"},
                new String[]{"chicken","farm_and_charm:chicken_parts","farmersdelight:chicken_cuts"})) {
            var key=id("test:"+route[0]);
            var original=json("{type:'farm_and_charm:mincer',ingredient:{item:'minecraft:wheat'},result:{item:'"+route[1]+"',count:3},recipe_type:'STONE',conditions:[{type:'forge:mod_loaded',modid:'farm_and_charm'}]}");
            channels.put(key,original);
            UnificationRecipes.apply(channels,plan);
            var expected=original.deepCopy().getAsJsonObject(); expected.getAsJsonObject("result").addProperty("item",route[2]);
            check(expected.equals(channels.get(key)),"alternate producer changes only output identity "+route[0]);
        }
        var twice=new LinkedHashMap<>(channels); UnificationRecipes.apply(channels,plan);
        check(channels.equals(twice),"multi-channel producer rewrites idempotent");
        var off=new LinkedHashMap<ResourceLocation,JsonElement>();
        off.put(id("test:off"),json("{type:'farm_and_charm:mincer',ingredient:{item:'minecraft:chicken'},result:{item:'farm_and_charm:chicken_parts',count:3}}"));
        check(UnificationRecipes.apply(off,UnificationContent.EMPTY)==0,"default mode preserves original output and channels");
    }
    private static void coreHooks() throws Exception {
        var config=JsonParser.parseString(Files.readString(Path.of("src/main/resources/mixins.cuisinejournal.json"))).getAsJsonObject();
        for(var entry:config.getAsJsonArray("mixins")) {
            String name=entry.getAsString();String simple=name.substring(name.lastIndexOf('.')+1);
            if(!(simple.startsWith("Unification")||simple.startsWith("Unified"))||simple.endsWith("Accessor"))continue;
            var hook=new org.objectweb.asm.tree.ClassNode();
            try(var stream=UnificationContentTest.class.getResourceAsStream("/com/chi__you/cuisinejournal/mixin/"+name.replace('.','/')+".class")){
                new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(hook,0);
            }
            var mixin=hook.invisibleAnnotations.stream().filter(a->a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
            var targetTypes=(List<?>)value(mixin,"value");
            String owner=((org.objectweb.asm.Type)targetTypes.get(0)).getInternalName();
            var target=new org.objectweb.asm.tree.ClassNode();
            try(var stream=UnificationContentTest.class.getResourceAsStream("/"+owner+".class")){
                new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream,owner)).accept(target,0);
            }
            for(var handler:hook.methods) {
                var annotations=new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
                if(handler.visibleAnnotations!=null)annotations.addAll(handler.visibleAnnotations);
                if(handler.invisibleAnnotations!=null)annotations.addAll(handler.invisibleAnnotations);
                for(var injection:annotations) {
                    if(!injection.desc.endsWith("/Inject;")&&!injection.desc.endsWith("/ModifyVariable;"))continue;
                    for(var selector:(List<?>)value(injection,"method")) {
                        var matches=target.methods.stream().filter(m->selector.equals(m.name)||selector.equals(m.name+m.desc)).toList();
                        check(matches.size()==1,name+" unambiguous core method "+selector);
                        var method=matches.get(0);
                        check(((method.access^handler.access)&org.objectweb.asm.Opcodes.ACC_STATIC)==0,name+" static capture");
                        var expected=org.objectweb.asm.Type.getArgumentTypes(method.desc);
                        var supplied=org.objectweb.asm.Type.getArgumentTypes(handler.desc);
                        int offset=injection.desc.endsWith("/ModifyVariable;")?1:0;
                        if(offset==1&&supplied.length==1) {
                            check(Arrays.asList(expected).contains(supplied[0]),name+" modified argument type");
                            continue;
                        }
                        check(supplied.length==expected.length+1,name+" argument count");
                        for(int i=0;i<expected.length;i++)check(expected[i].equals(supplied[i+offset]),name+" core argument "+i);
                    }
                }
            }
        }
    }
    private static Object value(org.objectweb.asm.tree.AnnotationNode node,String key) {
        for(int i=0;i<node.values.size();i+=2)if(key.equals(node.values.get(i)))return node.values.get(i+1);return null;
    }
    private static void installed(JsonObject document) throws Exception {
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS"); if(directory==null)return;
        var items=new HashSet<ResourceLocation>(); var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        var loaded=new HashSet<String>();
        try(var paths=Files.list(Path.of(directory))) {
            for(var path:paths.filter(p->p.toString().endsWith(".jar")&&!p.getFileName().toString().startsWith("cuisinejournal-")).sorted().toList())
                try(var jar=new JarFile(path.toFile())) {
                    var metadata=jar.getJarEntry("META-INF/mods.toml");
                    if(metadata!=null)try(var reader=new java.io.InputStreamReader(jar.getInputStream(metadata),java.nio.charset.StandardCharsets.UTF_8)) {
                        var config=new com.electronwill.nightconfig.toml.TomlParser().parse(reader);
                        List<com.electronwill.nightconfig.core.UnmodifiableConfig> mods=config.get("mods");
                        if(mods!=null)for(var mod:mods)loaded.add(mod.get("modId"));
                    }
                    for(var file:Collections.list(jar.entries())) {
                        String name=file.getName();
                        if(name.matches("assets/[^/]+/models/item/.+[.]json")) {
                            var parts=name.split("/",5); var item=ResourceLocation.tryParse(parts[1]+":"+parts[4].substring(0,parts[4].length()-5));
                            if(item!=null)items.add(item);
                        }
                        if(!name.matches("data/[^/]+/recipes/.+[.]json"))continue;
                        try(var reader=new java.io.InputStreamReader(jar.getInputStream(file),java.nio.charset.StandardCharsets.UTF_8)){
                            var parts=name.split("/",4);var recipeId=ResourceLocation.tryParse(parts[1]+":"+parts[3].substring(0,parts[3].length()-5));
                            if(recipeId!=null)recipes.put(recipeId,JsonParser.parseReader(reader));
                        } catch(JsonParseException ignored) {}
                    }
                }
        }
        var originals=new LinkedHashMap<>(recipes);
        var plan=UnificationContent.compile(document,true,items::contains,loaded::contains);
        check(plan.replacements().size()>50,"installed resource candidates cover batch");
        SquidRecipeCompat.apply(recipes,items::contains);
        int changed=UnificationRecipes.apply(recipes,plan);check(changed>50,"installed recipe paths changed");
        for(var e:recipes.entrySet()) if(e.getValue().isJsonObject()) {
            var outputs=UnificationRecipes.outputs(e.getValue().getAsJsonObject());
            check(outputs.stream().noneMatch(out->plan.replacements().containsKey(out)||plan.excluded().contains(out)),"no hidden direct output "+e.getKey());
        }
        if(loaded.contains("farm_and_charm")) {
            for(String path:List.of("crafting_bowl/dough","mincer/flour","mincer/raw_pasta","mincer/bacon","mincer/minced_beef","mincer/chicken_parts")) {
                var key=id("farm_and_charm:"+path);
                var before=originals.get(key).getAsJsonObject();
                check(recipes.containsKey(key),"real installed alternate production recipe retained "+key);
                var after=recipes.get(key).getAsJsonObject();
                var expected=before.deepCopy();
                var out=expected.getAsJsonObject("result");out.addProperty("item",plan.replace(id(out.get("item").getAsString())).toString());
                check(expected.equals(after),"real installed producer preserves inputs quantities and tool mode "+key);
                var normalized=new LinkedHashMap<ResourceLocation,JsonElement>(); normalized.put(key,after);
                UnificationRecipes.apply(normalized,plan);
                check(normalized.get(key).equals(after),"real producer stable on repeated reload "+key);
            }
            for(String path:List.of("cutting/bacon","cutting/chicken","cutting/minced_beef")) {
                var key=id("farmersdelight:"+path);
                if(originals.containsKey(key))check(recipes.containsKey(key),"farmer counterpart channel retained "+key);
            }
            check(recipes.containsKey(id("farm_and_charm:strawberry_from_bag")),"usable strawberry packaging still unpacks after fruit unification");
            check(recipes.containsKey(id("vintagedelight:cutting/oat_cutting")),"oat grain processing stays available after crop unification");
            check(plan.winner("cuisinejournal:strawberry_storage").equals(id(loaded.contains("berry_good")
                    ?"neapolitan:strawberry_basket":"farm_and_charm:strawberry_bag")),"installed packaging winner follows actual optional recipe dependency");
        }
    }
    private static JsonElement json(String text){return JsonParser.parseString(text);}
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
}
