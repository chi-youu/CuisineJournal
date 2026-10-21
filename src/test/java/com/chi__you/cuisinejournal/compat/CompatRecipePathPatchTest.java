package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.jar.JarFile;
import java.nio.file.*;

public final class CompatRecipePathPatchTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var id = ResourceLocation.parse("culturaldelights:calamari_roll");
        var original = JsonParser.parseString("""
                {"type":"forge:conditional","recipes":[{"conditions":[{"type":"forge:tag_empty","tag":"test:kept"}],
                "recipe":{"ingredients":[{"tag":"forge:raw_fishes/squid/tentacles"},{"item":"farmersdelight:cooked_rice"}],
                "result":{"item":"culturaldelights:calamari_roll","count":2}}}]}
                """);
        var recipes = new HashMap<ResourceLocation,JsonElement>(); recipes.put(id,original);
        check(CompatRecipePathPatch.repair(recipes,false,key->false)==0 && recipes.get(id)==original,
                "compat mode leaves recipe untouched");
        check(CompatRecipePathPatch.repair(recipes,true,key->true)==0 && recipes.get(id)==original,
                "correct upstream path disables temporary override");
        check(CompatRecipePathPatch.repair(recipes,true,key->false)==1, "known broken recipe repaired");
        String fixed=recipes.get(id).toString();
        check(fixed.contains("kaleidoscope_cookery:cooked_rice")&&!fixed.contains("farmersdelight:cooked_rice"),
                "only old rice input replaced");
        check(fixed.contains("forge:raw_fishes/squid/tentacles")&&fixed.contains("test:kept")
                &&fixed.contains("\"count\":2"), "Delightful ingredient, wrapper conditions and count preserved");
        check(original.toString().contains("farmersdelight:cooked_rice"), "source json not mutated");
        check(CompatRecipePathPatch.repair(recipes,true,key->false)==0, "repeat repair is idempotent");
        var unknown=ResourceLocation.parse("test:calamari_roll"); recipes.put(unknown,original);
        check(CompatRecipePathPatch.repair(recipes,true,key->false)==0&&recipes.get(unknown)==original,
                "unrelated recipes unaffected");
        var shaped=ResourceLocation.parse("culturaldelights:chicken_roll");
        recipes.put(shaped,JsonParser.parseString("""
                {"key":{"A":{"item":"farmersdelight:cooked_rice"}},"pattern":["A"],
                 "result":{"item":"farmersdelight:cooked_rice"}}
                """));
        check(CompatRecipePathPatch.repair(recipes,true,key->false)==1
                &&recipes.get(shaped).getAsJsonObject().getAsJsonObject("result").get("item").getAsString()
                .equals("farmersdelight:cooked_rice"), "shaped input repaired without rewriting output");
        String mods=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(mods!=null) try(var files=Files.list(Path.of(mods))) {
            for(var file:files.filter(path->path.toString().endsWith(".jar")).toList()) try(var jar=new JarFile(file.toFile())) {
                var row=jar.getJarEntry("data/culturaldelights/recipes/calamari_roll.json");
                if(row==null)continue;
                JsonElement recipe;
                try(var input=new java.io.InputStreamReader(jar.getInputStream(row),java.nio.charset.StandardCharsets.UTF_8)) {
                    recipe=JsonParser.parseReader(input);
                }
                var actual=new HashMap<ResourceLocation,JsonElement>();actual.put(id,recipe);
                check(CompatRecipePathPatch.repair(actual,true,key->false)==1, "installed recipe repaired: "+file.getFileName());
                check(!actual.get(id).toString().contains("farmersdelight:cooked_rice"), "installed recipe no longer requires hidden rice");
            }
        }
        var panel=Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/client/journal/FoodDetailPanel.java"));
        int guard=panel.indexOf("if (slot.items().isEmpty()) continue;",panel.indexOf("for (Packets.DetailSlot slot"));
        check(guard>=0 && guard<panel.indexOf("tick % slot.items().size()"), "empty detail slot rejected before modulo");
        var server=Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/network/ServerPacketHandler.java"));
        check(server.contains("if (items.isEmpty()) {")&&server.contains("omitting detail slot"),
                "server omits filtered empty slots with recipe diagnostics");
        System.out.println("CompatRecipePathPatchTest: "+checks+" checks passed");
    }
    private static void check(boolean passed,String message) { if(!passed)throw new AssertionError(message);checks++; }
}
