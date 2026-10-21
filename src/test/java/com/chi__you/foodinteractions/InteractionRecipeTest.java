package com.chi__you.foodinteractions;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rules and recipe documents are tested independently of mod block registration. */
public final class InteractionRecipeTest {
    private static int checks;
    public static void main(String[] args) {
        var block=id("test:feast");var output=id("test:soup");
        var input=JsonParser.parseString("{\"item\":\"test:cup\"}").getAsJsonObject();
        var display=new InteractionDisplay(block,block,input,output,1,Map.of("servings","3"),"serving","fixture");
        var json=display.json();
        check(json.get("ghost").getAsBoolean(),"generated recipe can never execute interaction");
        check(!json.has("comment"),"no unsolicited viewer information badge");
        check(json.get("type").getAsString().equals("lychee:block_interacting"),"uses Lychee built-in viewer category");
        check(json.getAsJsonObject("item_in").get("item").getAsString().equals("test:cup"),"container is not forced to vanilla bowl");
        check(json.getAsJsonObject("block_in").getAsJsonObject("state").get("servings").getAsString().equals("3"),"display matches an actual nonempty state");
        check(json.getAsJsonArray("post").get(0).getAsJsonObject().get("item").getAsString().equals("test:soup"),"post action exposes actual output");
        json.getAsJsonObject("item_in").addProperty("item","test:changed");
        check(input.get("item").getAsString().equals("test:cup"),"serialization cannot mutate shared input");
        check(!InteractionRules.shouldGenerate(output,Set.of(output),true),"existing crafting/cutting recipe suppresses extra display");
        check(InteractionRules.shouldGenerate(output,Set.of(),true),"removing existing recipe allows missing path to appear");
        check(!InteractionRules.shouldGenerate(output,Set.of(),false),"explicit exclusion wins over missing recipe");
        var same=new InteractionDisplay(block,block,input,output,1,Map.of("servings","3"),"serving","another explanation");
        check(display.recipeId().equals(same.recipeId()),"identity does not change with report wording or reload");
        var alternate=new InteractionDisplay(block,block,input,id("test:another_food"),1,Map.of("servings","2"),"serving","fixture");
        check(!display.recipeId().equals(alternate.recipeId()),"different output/state cannot collide");
        var base=file("test:a","{\"blocks\":[{\"block\":\"test:feast\",\"enabled\":false}]}");
        var override=file("test:z","{\"blocks\":[{\"block\":\"test:feast\",\"output\":\"test:soup\",\"input\":{\"item\":\"test:cup\"}}]}");
        var resolved=InteractionRules.resolve(List.of(override,base),mod->true);
        check(resolved.blocks().get(block).has("output")&&!resolved.blocks().get(block).has("enabled"),"later rule replaces earlier exclusion deterministically");
        var absent=file("test:optional","{\"required_mods\":[\"missing\"],\"blocks\":[{\"block\":\"test:other\",\"enabled\":false}]}");
        check(InteractionRules.resolve(List.of(absent),mod->false).blocks().isEmpty(),"missing optional mod skips file");
        var malformed=file("test:bad","{\"blocks\":[{\"block\":\"test:feast\"},{\"block\":\"invalid id\"}]}");
        var bad=InteractionRules.resolve(List.of(malformed),mod->true);
        check(bad.blocks().isEmpty()&&bad.problems().size()==1,"bad file cannot apply half its overrides");
        check(InteractionRules.resolve(List.of(),mod->true).blocks().isEmpty(),"removed rules leave no persistent state");
        var tools=JsonParser.parseString("[{\"item\":\"minecraft:iron_axe\"},{\"item\":\"minecraft:diamond_axe\"}]");
        var cut=new InteractionDisplay(block,block,tools,output,1,Map.of("bites","0"),"cutting","fixture");
        var hands=cut.json().getAsJsonArray("item_in");
        check(hands.size()==1&&hands.get(0).isJsonArray()&&hands.get(0).getAsJsonArray().size()==2,
                "multiple tools are alternatives within one hand, not two required hands");
        check(!cut.json().toString().contains("forge:compound"),"Forge compound serializer is never directly referenced");
        hands.get(0).getAsJsonArray().remove(0);
        check(tools.getAsJsonArray().size()==2,"nested tool serialization preserves original alternatives");
        var bamboo=new InteractionDisplay(id("trailandtales_delight:bamboo_tube_rice_block"),block,input,output,1,
                Map.of("servings","1","open","true"),"serving","verified open bamboo");
        check(bamboo.json().getAsJsonObject("block_in").getAsJsonObject("state").get("open").getAsString().equals("true"),
                "bamboo serving display requires an opened tube");
        var emptyHand=new InteractionDisplay(id("netherexp:nether_pizza"),null,
                JsonParser.parseString("{\"item\":\"minecraft:air\"}"),id("netherexp:nether_pizza_slice"),1,
                Map.of("slices","4"),"serving","fixture");
        check(emptyHand.json().getAsJsonObject("item_in").get("item").getAsString().equals("minecraft:air"),
                "tool-free pizza display uses explicit empty hand");
        var cosmic=new InteractionDisplay(id("enigmaticlegacy:cosmic_cake"),null,
                JsonParser.parseString("{\"item\":\"enigmaticdelicacy:etherium_machete\"}"),
                id("enigmaticdelicacy:cosmic_cake_slice"),1,Map.of("bites","0"),"cutting","fixture");
        check(cosmic.json().getAsJsonObject("item_in").get("item").getAsString().equals("enigmaticdelicacy:etherium_machete"),
                "cosmic cake never substitutes arbitrary knives for required machete");
        var tags=JsonParser.parseString("[{\"tag\":\"farmersdelight:tools/knives\"},{\"tag\":\"forge:tools/knives\"}]");
        var cake=new InteractionDisplay(block,null,tags,output,1,Map.of("bites","0"),"cutting","fixture");
        check(cake.json().getAsJsonArray("item_in").get(0).getAsJsonArray().size()==2,
                "common cake accepts either host knife tag in a single hand");
        var freezing = new InteractionDisplay(id("trailandtales_delight:ice_bucket"),id("trailandtales_delight:rice_ice_cream_mix"),
                JsonParser.parseString("{\"item\":\"trailandtales_delight:rice_ice_cream_mix\"}"),
                id("trailandtales_delight:bucket_rice_ice_cream"),1,Map.of("covered","false","ice_cream_type","0"),"freezing","verified random ticks");
        check(freezing.json().get("ghost").getAsBoolean(),"ice freezing display cannot replace host production");
        check(freezing.recipeId().getPath().startsWith("freezing/trailandtales_delight/ice_bucket/"),"freezing identity separate from serving");
        check(freezing.json().getAsJsonObject("item_in").get("item").getAsString().endsWith("rice_ice_cream_mix"),"freezing hand input is the mix");
        check(freezing.json().getAsJsonArray("post").get(0).getAsJsonObject().get("item").getAsString().endsWith("bucket_rice_ice_cream"),"freezing display yields frozen bucket");
        var iceServing = new InteractionDisplay(id("trailandtales_delight:bucket_rice_ice_cream"),null,input,output,1,
                Map.of("covered","false","servings","4"),"serving","verified open lid");
        check(iceServing.json().getAsJsonObject("block_in").getAsJsonObject("state").get("covered").getAsString().equals("false"),"serving display requires open lid");
        System.out.println("InteractionRecipeTest: "+checks+" checks passed");
    }
    private static ResourceLocation id(String raw){return ResourceLocation.parse(raw);}
    private static InteractionRules.File file(String id,String json){return new InteractionRules.File(id(id),JsonParser.parseString(json).getAsJsonObject());}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
