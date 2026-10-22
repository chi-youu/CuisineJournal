package com.chi__you.cuisinejournal.compat;

import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Ownership and registration contracts for the compatibility refactor, without a game bootstrap. */
public final class CompatibilityArchitectureTest {
    private static final String ROOT="com/chi__you/cuisinejournal/";
    private static int checks;
    private static class Base {}
    private static final class Child extends Base {}

    public static void main(String[] args) throws Exception {
        check(HostClasses.matches(new Child(),Base.class.getName()),"host family includes inherited classes");
        check(HostClasses.matches(new Child(),Child.class.getName()),"host family includes exact classes");
        check(!HostClasses.matches(null,Base.class.getName()),"null optional host is absent");
        check(!HostClasses.matches(new Base(),"missing.optional.mod.Class"),"absent host matching never loads that class");
        check(!HostClasses.matches(new Base(),Child.class.getName()),"base class does not masquerade as derived host");
        try(var files=Files.walk(Path.of("src/main/java/"+ROOT))){
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList())
                check(!file.getFileName().toString().matches("B[0-9]{3}.*"),"runtime source named by domain, not installation batch: "+file);
        }
        var config=JsonParser.parseString(Files.readString(Path.of("src/main/resources/mixins.cuisinejournal.json"))).getAsJsonObject();
        check(config.get("plugin").getAsString().equals("com.chi__you.cuisinejournal.compat.CuisineMixinPlugin"),
                "optional adapter contract gate is registered");
        check(!calls("food/CuisineGameplayRules","client/ClientJournalState","rules")
                &&!calls("food/CuisineFoodQuality","client/ClientJournalState","dish")
                &&!calls("food/CuisineVanillaBonuses","client/ClientJournalState","rules"),
                "core food policy reads injected views rather than client caches");
        Set<String> names=new HashSet<>();
        check(config.get("package").getAsString().equals("com.chi__you.cuisinejournal.mixin"),"Mixin root is independent of food domain");
        Map<String,Integer> counts=new TreeMap<>();
        for(String group:List.of("mixins","client"))for(var entry:config.getAsJsonArray(group)){
            String name=entry.getAsString();
            String domain=name.substring(0,name.indexOf('.'));
            check(Set.of("production","speed","unification","food","client").contains(domain),"explicit responsibility: "+name);
            check(group.equals("client")==domain.equals("client"),"client-only registrations stay client-only: "+name);
            counts.merge(domain,1,Integer::sum);
            check(names.add(name),"unique Mixin registration: "+name);
            String path=name.replace('.','/');
            check(Files.isRegularFile(Path.of("src/main/java/"+ROOT+"mixin/"+path+".java")),"registered source exists: "+name);
            check(own("mixin/"+path).name.equals(ROOT+"mixin/"+path),"registered compiled class exists: "+name);
        }
        check(names.size()==231,"original identities, notification bridges and verified food-host adapters registered");
        try(var files=Files.walk(Path.of("src/main/java/"+ROOT+"mixin"))){
            for(var file:files.filter(path->path.toString().endsWith(".java")).toList()){
                String registered=Path.of("src/main/java/"+ROOT+"mixin").relativize(file).toString()
                        .replace('\\','.').replace('/','.').replace(".java","");
                check(names.contains(registered),"every migrated Mixin is registered: "+file);
            }
        }
        check(calls("food/ModFoodCompat","food/KitchenKarrotFoodCompat","properties"),"food dispatcher delegates cocktail values");
        check(calls("food/ModFoodCompat","food/DrinkBeerFoodCompat","properties"),"food dispatcher delegates mixed beer values");
        check(!calls("food/ModFoodCompat","java/lang/Class","forName"),"dispatcher contains no host reflection algorithm");
        check(calls("tracking/CosmopolitanTubProduction","food/IngredientProvenance","sources"),"tub uses shared provenance, not another host adapter");
        check(calls("tracking/KitchenKarrotShaker","food/IngredientProvenance","sources"),"shaker uses shared provenance");
        check(calls("tracking/DrinkBeerMixing","food/IngredientProvenance","sources"),"spice mixing uses shared provenance");
        check(calls("food/CustomPlacedFoods","food/BountifulFaresFoods","table"),"ceramic dish rules owned by Bountiful Fares");
        check(calls("food/CustomPlacedFoods","food/CosmopolitanFoods","servings"),"doubled roll servings owned by Cosmopolitan");
        for(var row:Map.of(
                "food/GrillingFoodCompat","cn.breezeth.kaleidoscope_grilling.",
                "food/KitchenKarrotFoodCompat","io.github.tt432.kitchenkarrot.",
                "food/DrinkBeerFoodCompat","lekavar.lma.drinkbeer.",
                "food/NetherVineryFoodCompat","net.satisfy.nethervinery.").entrySet()){
            for(var method:own(row.getKey()).methods)for(var instruction:method.instructions)
                if(instruction instanceof LdcInsnNode literal&&literal.cst instanceof String value&&value.contains(".")
                        &&List.of("cn.breezeth.","io.github.tt432.","lekavar.lma.","net.satisfy.").stream().anyMatch(value::startsWith))
                    check(value.startsWith(row.getValue()),"host reflection stays in its owner: "+row.getKey()+" -> "+value);
        }
        // Host-native keys stay native; journal-owned data uses one namespaced naming scheme.
        Map<String,Set<String>> persistedKeys=Map.of(
                "food/IngredientProvenance",Set.of("cuisinejournal:ingredient_sources"),
                "food/GrillingFoodCompat",Set.of("HotUntil","cuisinejournal:grilling_heat_multiplier"),
                "tracking/CosmopolitanTubProduction",Set.of("cuisinejournal:cosmopolitan_tub"),
                "tracking/ManualGrillProduction",Set.of("cuisinejournal:manual_grill_owner"),
                "tracking/KitchenKarrotShaker",Set.of("cuisinejournal:kitchen_karrot_shaker"),
                "tracking/DrinkBeerMixing",Set.of("cuisinejournal:drink_beer_mix_owner"));
        for(var entry:persistedKeys.entrySet()){
            var type=own(entry.getKey());Set<String> strings=new HashSet<>();
            for(var field:type.fields)if(field.value instanceof String value)strings.add(value);
            for(var method:type.methods)for(var instruction:method.instructions)
                if(instruction instanceof LdcInsnNode literal&&literal.cst instanceof String value)strings.add(value);
            check(strings.containsAll(entry.getValue()),"canonical persisted keys in "+entry.getKey());
        }
        System.out.println("CompatibilityArchitectureTest: "+checks+" checks passed");
    }
    private static ClassNode own(String name)throws Exception{
        var node=new ClassNode();
        try(var in=CompatibilityArchitectureTest.class.getClassLoader().getResourceAsStream(ROOT+name+".class")){
            new ClassReader(Objects.requireNonNull(in,name)).accept(node,0);
        }
        return node;
    }
    private static boolean calls(String name,String owner,String method)throws Exception{
        String target=owner.startsWith("java/")?owner:ROOT+owner;
        for(var body:own(name).methods)for(var instruction:body.instructions)
            if(instruction instanceof MethodInsnNode call&&call.owner.equals(target)&&call.name.equals(method))return true;
        return false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
