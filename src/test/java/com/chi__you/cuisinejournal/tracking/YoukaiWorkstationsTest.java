package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.recipe.WorkstationDisplay;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Edition identities, workstation data and actual Feasts assets/API headers. */
public final class YoukaiWorkstationsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var file=Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations/youkaisfeasts.json");
        var json=json(file);
        var input=new WorkstationDefinitions.RuleFile(id("cuisinejournal:youkaisfeasts"),"test",json);
        check(WorkstationDefinitions.resolve(List.of(input),mod->false).isEmpty(),"absent edition installs no phantom devices");
        var defs=WorkstationDefinitions.resolve(List.of(input),mod->mod.equals("youkaisfeasts"));
        check(defs.size()==10,"ten verified block identities declared");
        var zh=json(Path.of("src/main/resources/assets/cuisinejournal/lang/zh_cn.json"));
        var en=json(Path.of("src/main/resources/assets/cuisinejournal/lang/en_us.json"));
        var paths=List.of("cuisine","drying_rack","pot_cooking","soup_base","steaming","kettle","basin","fermentation");
        for(String path:paths){
            var method=id("youkaisfeasts:"+path);
            check(YoukaiWorkstations.method(id("youkaisfeasts:cuisine_board"),path).equals(method),"feasts method follows registry: "+path);
            check(YoukaiWorkstations.method(id("youkaishomecoming:cuisine_board"),path).equals(id("youkaishomecoming:"+path)),"original edition retains identity: "+path);
            check(WorkstationDisplay.candidates(method).stream().allMatch(item->item.getNamespace().equals("youkaisfeasts")),"feasts icons do not fall back to the absent combat edition");
            String key="cuisinejournal.method.youkaisfeasts."+path;
            check(zh.has(key)&&en.has(key),"both languages contain actual method: "+path);
        }
        check(defs.get(id("youkaisfeasts:cuisine_board")).acceptsRecipe(id("youkaisfeasts:cuisine")),"runtime cuisine type mapped, not serializer suffix");
        check(defs.get(id("youkaisfeasts:wood_basin")).acceptsRecipe(id("youkaisfeasts:simple_basin")),"basin serializer variant included");
        check(defs.get(id("youkaisfeasts:fermentation_tank")).acceptsRecipe(id("youkaisfeasts:simple_fermentation")),"fermentation serializer variant included");
        check(WorkstationDisplay.candidates(id("youkaishomecoming:cuisine")).equals(List.of(id("youkaishomecoming:cuisine_board"))),"original edition display unchanged");
        for(String owner:List.of("YoukaiCookingProduction","InPlaceProductionTracker","FluidProductionTracker","ProductionTracker")){
            var node=compiled("com/chi__you/cuisinejournal/tracking/"+owner);
            check(node.methods.stream().anyMatch(method->{for(var instruction:method.instructions)
                if(instruction instanceof MethodInsnNode call&&call.owner.endsWith("/YoukaiWorkstations")&&call.name.equals("method"))return true;return false;}),
                    "production stores actual edition identity: "+owner);
        }
        String directory=System.getenv("CUISINEJOURNAL_CONFLICT_MODS");
        if(directory==null)System.out.println("SKIPPED: installed Feasts workstation headers/assets");
        else try(var files=Files.list(Path.of(directory))){
            var host=files.filter(p->p.getFileName().toString().contains("youkaisfeasts")&&p.toString().endsWith(".jar")).findFirst().orElseThrow();
            try(var jar=new JarFile(host.toFile())){
                for(var def:defs.values())check(jar.getJarEntry("assets/youkaisfeasts/blockstates/"+def.block().getPath()+".json")!=null,"actual workstation block asset exists: "+def.block());
                for(String path:paths)check(WorkstationDisplay.candidates(id("youkaisfeasts:"+path)).stream().anyMatch(item->jar.getJarEntry("assets/youkaisfeasts/models/item/"+item.getPath()+".json")!=null),"actual icon model exists: "+path);
                String base="dev/xkmc/youkaishomecoming/content/pot/table/";
                for(String recipe:List.of("OrderedCuisineRecipe","UnorderedCuisineRecipe","MixedCuisineRecipe","FixedCuisineRecipe")){
                    var node=read(jar,base+"recipe/"+recipe);
                    boolean family=false;
                    for(int depth=0;depth<8&&node!=null;depth++){if(node.name.equals(base+"recipe/CuisineRecipe")){family=true;break;}
                        var entry=jar.getJarEntry(node.superName+".class");node=entry==null?null:read(jar,node.superName);}
                    check(family,"existing cuisine input reader recognizes actual edition inheritance: "+recipe);
                }
                var recipe=read(jar,base+"recipe/CuisineRecipe");
                check(recipe.methods.stream().anyMatch(m->m.name.equals("base")&&m.desc.equals("()Lnet/minecraft/resources/ResourceLocation;")),"cuisine base getter retained");
                check(recipe.methods.stream().anyMatch(m->m.name.equals("getCustomIngredients")&&m.desc.equals("()Ljava/util/List;")),"custom ingredients getter retained");
                for(String name:List.of("VariantTableItemBase","IngredientTableItem")) {
                    var node=read(jar,base+"item/"+name);
                    check(node.fields.stream().anyMatch(f->f.name.equals(name.startsWith("Variant")?"MAP":"FIXED")),"ingredient model map retained: "+name);
                    check(node.methods.stream().anyMatch(m->m.name.equals("collectIngredients")),"ingredient expansion retained: "+name);
                }
            }
        }
        System.out.println("YoukaiWorkstationsTest: "+checks+" checks passed");
    }
    private static JsonObject json(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static ResourceLocation id(String text){return ResourceLocation.parse(text);}
    private static ClassNode compiled(String name)throws Exception{try(var in=YoukaiWorkstationsTest.class.getClassLoader().getResourceAsStream(name+".class")){return read(Objects.requireNonNull(in));}}
    private static ClassNode read(JarFile jar,String name)throws Exception{try(var in=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name+".class"),name))){return read(in);}}
    private static ClassNode read(java.io.InputStream in)throws Exception{var node=new ClassNode();new ClassReader(in).accept(node,0);return node;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
