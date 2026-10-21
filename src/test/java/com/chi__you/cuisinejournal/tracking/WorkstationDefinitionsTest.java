package com.chi__you.cuisinejournal.tracking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Set;

public final class WorkstationDefinitionsTest {
    private static int checks;
    private static final ResourceLocation BARREL=ResourceLocation.parse("test:barrel");
    public static void main(String[] args) throws Exception {
        var base=file("base", """
            {"workstations":[{"block":"test:barrel","adapter":"auto","method":"test:ferment",
             "capabilities":["item_input","fluid_input","item_output","fluid_output","container_fill","menu_take","right_click_take"],
             "recipe_types":["test:ferment","test:solid"]}]}
            """);
        var patch=file("patch", """
            {"priority":10,"workstations":[{"block":"test:barrel","add_capabilities":["automation_extract"],
             "remove_capabilities":["menu_take"]}]}
            """);
        var definitions=WorkstationDefinitions.resolve(List.of(patch,base), mod->true);
        var d=definitions.get(BARREL);
        check(d.capabilities().containsAll(Set.of(WorkstationDefinitions.Capability.ITEM_OUTPUT,
                WorkstationDefinitions.Capability.FLUID_OUTPUT,WorkstationDefinitions.Capability.RIGHT_CLICK_TAKE)), "one workstation supports solid fluid and held-container paths");
        check(!d.capabilities().contains(WorkstationDefinitions.Capability.MENU_TAKE)
                &&d.capabilities().contains(WorkstationDefinitions.Capability.AUTOMATION_EXTRACT), "capability add/remove patches existing declaration");
        check(d.method().toString().equals("test:ferment"),"partial patch preserves method");
        check(d.acceptsRecipe(ResourceLocation.parse("test:solid"))&&!d.acceptsRecipe(ResourceLocation.parse("test:unrelated")),"recipe type guard");
        check(d.sources().size()==2,"source pack and resource trace retained");
        check(WorkstationDefinitions.pending(d,WorkstationDefinitions.Adapter.AUTO).equals(d.capabilities()),"unknown implementation never pretends to support fluid capability");
        var disabled=file("disabled", """
            {"priority":20,"workstations":[{"block":"test:barrel","enabled":false}]}
            """);
        check(!WorkstationDefinitions.resolve(List.of(base,disabled),mod->true).get(BARREL).enabled(),"higher priority disables mapping");
        check(WorkstationDefinitions.resolve(List.of(),mod->true).isEmpty(),"reload without files clears old mappings");
        var missing=file("missing", """
            {"required_mods":["absent"],"workstations":[{"block":"test:barrel"}]}
            """);
        check(WorkstationDefinitions.resolve(List.of(missing),mod->false).isEmpty(),"missing required mod skips workstation file");
        var invalid=file("invalid", """
            {"workstations":[{"block":"test:barrel"},{"block":"test:other","capabilities":["not_a_capability"]}]}
            """);
        check(WorkstationDefinitions.resolve(List.of(invalid),mod->true).isEmpty(),"invalid file cannot apply half its workstations");
        var replace=file("replace", """
            {"priority":20,"workstations":[{"block":"test:barrel","capabilities":["item_output"]}]}
            """);
        check(WorkstationDefinitions.resolve(List.of(base,replace),mod->true).get(BARREL).capabilities().size()==1,"explicit capability list replaces earlier list");
        var wrongAdapter=file("wrong", """
            {"workstations":[{"block":"test:barrel","adapter":"cooking_pot","capabilities":["menu_take"]}]}
            """);
        check(!WorkstationDefinitions.pending(WorkstationDefinitions.resolve(List.of(wrongAdapter),mod->true).get(BARREL),
                WorkstationDefinitions.Adapter.AUTO).isEmpty(),"selecting pot adapter for unknown block is not proof of support");
        for(String name:List.of("minecraft","farmersdelight","dungeonsdelight","bosses_delight","youkai_solid","immortalers_delight","deep_aether","miners_delight","torchesbecomesunlight","brewinandchewin","aether","cataclysm","youkai_fluid","enigmaticdelicacy")) {
            var path=java.nio.file.Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations/"+name+".json");
            JsonObject json=JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
            var defaults=WorkstationDefinitions.resolve(List.of(new WorkstationDefinitions.RuleFile(
                    ResourceLocation.parse("test:"+name),"default",json)),mod->true);
            int expected=name.equals("minecraft")?6:name.equals("farmersdelight")?4:name.equals("youkai_solid")?3:
                    name.equals("torchesbecomesunlight")||name.equals("youkai_fluid")?3:1;
            check(defaults.size()==expected,"packaged "+name+" definitions parse");
            check(defaults.values().stream().allMatch(v->v.enabled()&&!v.capabilities().isEmpty()),"packaged "+name+" capabilities complete");
        }
        check(CookingPotCompatibility.methodFor("net.yirmiri.dungeonsdelight.common.block.entity.MonsterPotBlockEntity")
                .equals(com.chi__you.cuisinejournal.recipe.CookingMethod.of("dungeonsdelight","monster_cooking")),
                "monster pot retains native production method");
        check(CookingPotCompatibility.methodFor("io.github.rcneg.bossesdelight.blocks.entity.SoulCookingPotBlockEntity")
                .equals(com.chi__you.cuisinejournal.recipe.CookingMethod.of("bosses_delight","soul_cooking")),
                "soul pot retains native production method");
        check(CookingPotCompatibility.isResultSlot("net.yirmiri.dungeonsdelight.common.block.entity.container.MonsterPotResultSlot"),
                "monster result slot is recognized despite distinct name");
        check(!CookingPotCompatibility.isResultSlot("test.MonsterPotResultSlot")
                &&CookingPotCompatibility.methodFor("test.MonsterPotBlockEntity")==null,
                "unrelated classes never acquire addon production behavior by short name");
        var copper=VerifiedWorkstations.find("com.sammy.minersdelight.content.block.copper_pot.CopperPotBlockEntity");
        check(java.util.Arrays.equals(copper.inputs(),new int[]{0,1,2,3,5})&&java.util.Arrays.equals(copper.outputs(),new int[]{4,6})
                &&copper.container()==5,"copper-pot layout differs from six-slot farmer pot");
        check(copper.method().equals(com.chi__you.cuisinejournal.recipe.CookingMethod.COOKING_POT)
                &&CookingPotCompatibility.isResultSlot("com.sammy.minersdelight.content.block.copper_pot.CopperPotResultSlot"),
                "copper pot reuses actual cooking recipe type and recognizes its result slot");
        System.out.println("WorkstationDefinitionsTest: "+checks+" checks passed");
    }
    private static WorkstationDefinitions.RuleFile file(String name,String json) {
        return new WorkstationDefinitions.RuleFile(ResourceLocation.parse("test:"+name),"pack_"+name,JsonParser.parseString(json).getAsJsonObject());
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
