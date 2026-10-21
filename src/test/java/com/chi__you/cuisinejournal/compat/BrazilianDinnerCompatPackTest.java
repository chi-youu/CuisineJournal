package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Load the original mod's data followed by the patch through Minecraft's real resource/tag loaders. */
public final class BrazilianDinnerCompatPackTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var egg=id("deltaboxlib:tags/items/fried_egg.json");
        var recipe=id("braziliandelight:recipes/brazilian_dinner.json");
        var original = new HashMap<ResourceLocation,JsonElement>();
        String mods=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(mods!=null)try(var files=Files.list(Path.of(mods))) {
            for(var file:files.filter(path->path.toString().endsWith(".jar")).toList())try(var jar=new JarFile(file.toFile())) {
                var row=jar.getJarEntry("data/braziliandelight/recipes/brazilian_dinner.json");
                if(row==null)continue;
                for(var resource:List.of(egg,recipe)) {
                    var entry=jar.getJarEntry("data/"+resource.getNamespace()+"/"+resource.getPath());
                    try(var reader=new java.io.InputStreamReader(jar.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){
                        original.put(resource,JsonParser.parseReader(reader));
                    }
                }
            }
        }
        if(original.isEmpty()) {
            original.put(egg,JsonParser.parseString("{\"values\":[\"farmersdelight:fried_egg\",\"farmersdelight:cooked_rice\"]}"));
            original.put(recipe,JsonParser.parseString("{\"ingredients\":[{\"item\":\"braziliandelight:cooked_carioca_beans\"},{\"item\":\"braziliandelight:cooked_black_beans\"},{\"tag\":\"deltaboxlib:fried_egg\"},{\"tag\":\"deltaboxlib:fried_egg\"}],\"result\":{\"item\":\"braziliandelight:brazilian_dinner\"}}"));
        }
        try(var resources=new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(new UnificationRulePack(original),new BrazilianDinnerCompatPack()))) {
            check(resources.getResource(recipe).orElseThrow().sourcePackId().equals(BrazilianDinnerCompatPack.ID),
                    "recipe override is read from the high-priority patch");
            JsonObject fixed;
            try(var reader=resources.getResource(recipe).orElseThrow().openAsReader()){fixed=JsonParser.parseReader(reader).getAsJsonObject();}
            var inputs=fixed.getAsJsonArray("ingredients");
            check(inputs.size()==4&&inputs.get(2).getAsJsonObject().get("item").getAsString().equals("farmersdelight:cooked_rice")
                    &&inputs.get(3).getAsJsonObject().get("tag").getAsString().equals("forge:cooked_eggs"),
                    "one exact Farmer's Delight rice and one cooked egg tag slot");
            check(inputs.get(0).equals(original.get(recipe).getAsJsonObject().getAsJsonArray("ingredients").get(0))
                    &&inputs.get(1).equals(original.get(recipe).getAsJsonObject().getAsJsonArray("ingredients").get(1))
                    &&fixed.get("result").equals(original.get(recipe).getAsJsonObject().get("result")),
                    "beans and output unchanged from installed mod");
            var tags=new TagLoader<ResourceLocation>(value->Optional.of(value),"tags/items").loadAndBuild(resources);
            var members=tags.get(id("deltaboxlib:fried_egg"));
            check(members.contains(id("farmersdelight:fried_egg")),"original egg preserved");
            check(!members.contains(id("farmersdelight:cooked_rice"))&&!members.contains(id("kaleidoscope_cookery:cooked_rice")),
                    "Forge removal executes after original rice additions");
            check(!members.contains(id("kaleidoscope_cookery:fried_egg")),"no extra egg introduced by patch");
            check(!resources.getResource(id("forge:tags/items/foods/cooked_rice.json")).isPresent(),
                    "patch no longer expands the unrelated rice tag");
            check(resources.getResourceStack(egg).size()==2,"tag loader sees original then patch, not a replacement of all members");
        }
        System.out.println("BrazilianDinnerCompatPackTest: "+checks+" checks passed");
    }
    private static ResourceLocation id(String text){return ResourceLocation.parse(text);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
