package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Real installed JSON and resource precedence, not a claim of gameplay verification. */
public final class SquidLootCompatTest {
    private static final ResourceLocation SQUID=id("minecraft:entities/squid"), GLOW=id("minecraft:entities/glow_squid");
    private static int checks;
    public static void main(String[] args)throws Exception {
        var cultural=SquidLootCompat.culturalTable(false);
        var glowing=SquidLootCompat.culturalTable(true);
        var dumpling=SquidLootCompat.dumplingTable();
        installed(cultural,glowing,dumpling);
        var original=cultural.deepCopy();
        Map<ResourceLocation,JsonElement> tables=new HashMap<>(Map.of(SQUID,cultural,GLOW,glowing));
        check(SquidLootCompat.apply(tables,mod->false,key->true,true)==0,"absent host does not change tables");
        check(SquidLootCompat.apply(tables,mod->true,key->false,true)==0,"external packs are not patched");
        check(SquidLootCompat.apply(tables,mod->true,key->true,true)==2,"both known Cultural tables repaired");
        check(cultural.equals(original),"original JSON never mutated");
        check(pools(tables,SQUID).size()==3,"Cultural winner gains ink and one Dumplings chance pool");
        check(pools(tables,GLOW).size()==2,"glow winner gains only glow ink");
        check(pools(tables,SQUID).get(0).equals(original.getAsJsonArray("pools").get(0)),"native Cultural amount and looting unchanged");
        var chance=pools(tables,SQUID).get(2).getAsJsonObject().getAsJsonArray("conditions").get(0).getAsJsonObject();
        check(chance.get("chance").getAsDouble()==.5,"Dumplings keeps one 50 percent roll");
        check(itemCount(tables.get(GLOW),"dumplings_delight:calamari")==0,"no invented Dumplings glow squid drop");
        check(SquidLootCompat.apply(tables,mod->true,key->true,true)==0,"repeat application is idempotent");
        tables=new HashMap<>(Map.of(SQUID,dumpling));
        check(SquidLootCompat.apply(tables,mod->true,key->true,true)==1,"Dumplings table can win and retain Cultural drop");
        check(pools(tables,SQUID).get(0).equals(dumpling.getAsJsonArray("pools").get(0))
                &&pools(tables,SQUID).get(1).equals(dumpling.getAsJsonArray("pools").get(1)),"Dumplings existing pools unchanged");
        check(itemCount(tables.get(SQUID),"dumplings_delight:calamari")==1,"no second chance roll on existing Dumplings table");
        tables=new HashMap<>(Map.of(SQUID,cultural));
        SquidLootCompat.apply(tables,mod->true,key->true,false);
        check(pools(tables,SQUID).size()==2,"upstream fixed modifier prevents duplicate fallback chance roll");
        tables=new HashMap<>(Map.of(SQUID,cultural));
        SquidLootCompat.apply(tables,mod->mod.equals("culturaldelights"),key->true,true);
        check(pools(tables,SQUID).size()==2,"Cultural alone only restores ink");
        var custom=cultural.deepCopy();custom.addProperty("random_sequence","pack:custom");
        tables=new HashMap<>(Map.of(SQUID,custom,id("test:other"),cultural));
        check(SquidLootCompat.apply(tables,mod->true,key->true,true)==0,"changed tables and unrelated IDs stay intact");
        resources(cultural,dumpling);
        var disabled=UnificationContent.EMPTY;
        check(!UnificationRuntime.suppressedDrop(id("dumplings_delight:calamari"),id("minecraft:squid"),disabled),"compatibility mode preserves Dumplings meat");
        var plan=new UnificationContent.Plan(Map.of(),Map.of("cuisinejournal:squid",id("miners_delight:squid")),Set.of(),Set.of(),Set.of());
        check(UnificationRuntime.suppressedDrop(id("dumplings_delight:calamari"),id("minecraft:squid"),plan),"unified mode suppresses newly installed duplicate squid meat");
        check(UnificationRuntime.suppressedDrop(id("culturaldelights:squid"),id("minecraft:squid"),plan),"existing unified Cultural suppression unchanged");
        check(!UnificationRuntime.suppressedDrop(id("minecraft:ink_sac"),id("minecraft:squid"),plan),"unified mode does not remove ink");
        check(!UnificationRuntime.suppressedDrop(id("minecraft:glow_ink_sac"),id("minecraft:glow_squid"),plan),"unified mode does not remove glow ink");
        System.out.println("SquidLootCompatTest: "+checks+" checks passed");
    }
    private static void installed(JsonObject cultural,JsonObject glow,JsonObject dumpling)throws Exception {
        String folder=System.getenv("CUISINEJOURNAL_COMPAT_MODS");if(folder==null)return;
        boolean foundCultural=false,foundDumpling=false;
        try(var files=Files.list(Path.of(folder))){for(var file:files.filter(p->p.toString().endsWith(".jar")).toList())try(var jar=new JarFile(file.toFile())){
            if(jar.getJarEntry("com/baisylia/culturaldelights/CulturalDelights.class")!=null){
                check(read(jar,"data/minecraft/loot_tables/entities/squid.json").equals(cultural),"Cultural signature matches actual installed JSON");
                check(read(jar,"data/minecraft/loot_tables/entities/glow_squid.json").equals(glow),"glow signature matches actual installed JSON");foundCultural=true;
            }
            if(jar.getJarEntry("data/dumplings_delight/loot_modifiers/add_calamari.json")!=null){
                check(read(jar,"data/minecraft/loot_tables/entities/squid.json").equals(dumpling),"Dumplings signature matches actual installed JSON");
                check(!read(jar,"data/dumplings_delight/loot_modifiers/add_calamari.json").getAsJsonObject().has("type"),"broken duplicate modifier confirmed in installed host");foundDumpling=true;
            }
        }}
        check(foundCultural&&foundDumpling,"installed regression actually inspected both audited hosts");
    }
    private static JsonElement read(JarFile jar,String path)throws Exception {
        try(var in=new java.io.InputStreamReader(jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(path),path)),java.nio.charset.StandardCharsets.UTF_8)){return JsonParser.parseReader(in);}
    }
    private static void resources(JsonObject cultural,JsonObject dumpling)throws Exception {
        var path=id("minecraft:loot_tables/entities/squid.json");
        var modifier=id("dumplings_delight:loot_modifiers/add_calamari.json");
        var invalidModifier=new JsonObject();invalidModifier.add("conditions",new JsonArray());
        invalidModifier.addProperty("item","dumplings_delight:calamari");
        var files=Map.of(path,(JsonElement)cultural,modifier,invalidModifier);
        try(var resources=new MultiPackResourceManager(PackType.SERVER_DATA,List.of(new TestPack("mod_resources",files)))){
            check(SquidLootCompat.brokenDumplingModifier(resources),"resource reader detects missing modifier type");
            Map<ResourceLocation,JsonElement> selected=new HashMap<>();
            SimpleJsonResourceReloadListener.scanDirectory(resources,"loot_tables",new Gson(),selected);
            check(SquidLootCompat.apply(selected,resources,mod->true)==1,"real scanner maps entity table IDs and patch accepts mod source");
            check(itemCount(selected.get(SQUID),"minecraft:ink_sac")==1&&itemCount(selected.get(SQUID),"dumplings_delight:calamari")==1,"real loaded Cultural table regains both missing pools");
        }
        try(var resources=new MultiPackResourceManager(PackType.SERVER_DATA,List.of(new TestPack("mod_resources",files),
                new TestPack("file/custom",Map.of(path,dumpling))))){
            Map<ResourceLocation,JsonElement> selected=new HashMap<>();
            SimpleJsonResourceReloadListener.scanDirectory(resources,"loot_tables",new Gson(),selected);
            check(SquidLootCompat.apply(selected,resources,mod->true)==0,"even matching custom high-priority override is respected");
            check(selected.get(SQUID).equals(dumpling),"custom pack data unchanged after real selection");
        }
    }
    private static final class TestPack extends AbstractPackResources {
        private final UnificationRulePack contents;
        TestPack(String name,Map<ResourceLocation,JsonElement> files){super(name,true);contents=new UnificationRulePack(files);}
        @Override public IoSupplier<InputStream> getRootResource(String... path){return contents.getRootResource(path);}
        @Override public IoSupplier<InputStream> getResource(PackType type,ResourceLocation id){return contents.getResource(type,id);}
        @Override public void listResources(PackType type,String namespace,String path,ResourceOutput out){contents.listResources(type,namespace,path,out);}
        @Override public Set<String> getNamespaces(PackType type){return contents.getNamespaces(type);}
        @Override public void close(){contents.close();}
    }
    private static JsonArray pools(Map<ResourceLocation,JsonElement> tables,ResourceLocation key){return tables.get(key).getAsJsonObject().getAsJsonArray("pools");}
    private static int itemCount(JsonElement value,String item){
        if(value.isJsonObject()){var object=value.getAsJsonObject();int count=object.has("name")&&object.get("name").isJsonPrimitive()&&object.get("name").getAsString().equals(item)?1:0;
            for(var entry:object.entrySet())count+=itemCount(entry.getValue(),item);return count;}
        if(value.isJsonArray()){int count=0;for(var child:value.getAsJsonArray())count+=itemCount(child,item);return count;}return 0;
    }
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
