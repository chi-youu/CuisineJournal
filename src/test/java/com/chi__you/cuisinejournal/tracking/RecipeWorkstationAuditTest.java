package com.chi__you.cuisinejournal.tracking;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class RecipeWorkstationAuditTest {
    private static int checks;
    public static void main(String[] args) {
        var cooking=id("cooking");var liquid=id("fermentation");var pot=id("pot");var second=id("other_pot");
        var d=definition(pot,cooking,true);var d2=definition(second,cooking,true);
        var samples=List.of(
                sample("one",cooking,id("soup"),true,null,"ignored_default_crafting_table",""),
                sample("two",cooking,id("soup"),true,pot,"recipe_toast_override:test.CookingRecipe",""),
                sample("three",cooking,id("stew"),true,null,"none",""),
                sample("wine",liquid,null,false,null,"none",""));
        var rows=RecipeWorkstationAudit.resolve(samples,Map.of(pot,d,second,d2));
        var row=find(rows,cooking);
        check(row.recipes()==3&&row.dishes().size()==2,"recipe counts and dish counts are distinct");
        check(row.candidates().stream().anyMatch(c->c.block().equals(second)),"one type may have multiple mapped stations");
        check(row.candidates().stream().anyMatch(c->c.evidence().startsWith("recipe_toast_override")),"overridden toast candidate carries provenance");
        check(row.status().equals("mapped_needs_runtime_verification"),"mapping never claims tested compatibility");
        check(find(rows,liquid).status().equals("empty_item_output_needs_review"),"liquid or custom empty output still listed");
        check(row.implementations().contains("test.Recipe")&&row.examples().size()==3,"implementation and example ids retained");
        rows=RecipeWorkstationAudit.resolve(samples,Map.of());row=find(rows,cooking);
        check(row.status().equals("needs_workstation_mapping"),"toast hint does not count as installed mapping");
        check(row.candidates().size()==1,"default toast is not a workstation candidate");
        rows=RecipeWorkstationAudit.resolve(samples,Map.of(pot,definition(pot,cooking,false)));
        check(find(rows,cooking).status().equals("needs_workstation_mapping"),"disabled mapping cannot cover recipe type");
        var wildcard=new WorkstationDefinitions.Definition(pot,true,WorkstationDefinitions.Adapter.AUTO,null,Set.of(),Set.of(),List.of());
        check(find(RecipeWorkstationAudit.resolve(samples,Map.of(pot,wildcard)),cooking).status().equals("needs_workstation_mapping"),
                "unrestricted recipe guard is not evidence of matching all types");
        var method=new WorkstationDefinitions.Definition(pot,true,WorkstationDefinitions.Adapter.AUTO,cooking,Set.of(),Set.of(),List.of());
        check(find(RecipeWorkstationAudit.resolve(samples,Map.of(pot,method)),cooking).candidates().stream().anyMatch(c->c.evidence().equals("datapack_method")),
                "method fallback explicitly labelled");
        var failure=sample("bad",id("broken"),null,false,null,"none","output: IllegalStateException");
        check(RecipeWorkstationAudit.resolve(List.of(failure),Map.of()).get(0).status().equals("unreadable_recipe_needs_review"),"unreadable outputs receive review status");
        var unknown=new RecipeWorkstationAudit.Sample(null,null,"bad.Recipe",null,false,false,null,null,"none","identity failure");
        check(RecipeWorkstationAudit.resolve(List.of(unknown),Map.of()).size()==1,"unreadable identity still produces diagnostic row");
        var fake=sample("default",cooking,id("food"),true,ResourceLocation.parse("minecraft:crafting_table"),"ignored_default_crafting_table","");
        check(RecipeWorkstationAudit.resolve(List.of(fake),Map.of()).get(0).candidates().isEmpty(),"default crafting icon cannot falsely map unknown recipe");
        String text=RecipeWorkstationAudit.format(RecipeWorkstationAudit.resolve(samples,Map.of(pot,d)));
        check(text.startsWith("recipe_type\tstatus")&&text.contains("test:fermentation"),"export includes unresolved types");
        check(text.lines().allMatch(line->line.split("\t",-1).length==17),"TSV rows preserve full schema");
        check(RecipeWorkstationAudit.resolve(List.of(),Map.of()).isEmpty(),"empty rebuild leaves no stale types");
        System.out.println("RecipeWorkstationAuditTest: "+checks+" checks passed");
    }
    private static RecipeWorkstationAudit.Sample sample(String name,ResourceLocation type,ResourceLocation output,boolean tracked,
                                                         ResourceLocation block,String evidence,String problem) {
        return new RecipeWorkstationAudit.Sample(id(name),type,"test.Recipe",output,tracked,false,block,block,evidence,problem);
    }
    private static WorkstationDefinitions.Definition definition(ResourceLocation block,ResourceLocation type,boolean enabled) {
        return new WorkstationDefinitions.Definition(block,enabled,WorkstationDefinitions.Adapter.COOKING_POT,type,
                Set.of(WorkstationDefinitions.Capability.ITEM_OUTPUT),Set.of(type),List.of("pack :: test:file"));
    }
    private static RecipeWorkstationAudit.Row find(List<RecipeWorkstationAudit.Row> rows,ResourceLocation type) {
        return rows.stream().filter(row->row.type().equals(type)).findFirst().orElseThrow();
    }
    private static ResourceLocation id(String path){return ResourceLocation.parse("test:"+path);}
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);checks++;}
}
