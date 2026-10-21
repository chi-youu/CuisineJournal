package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.food.FarmersSpellFoods;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Policy and installed-host contracts; never bootstraps Iron's spell runtime. */
public final class B004CompatibilityTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        check(CookingPotCompatibility.methodFor("com.chenjdy.farmers_spell.block.entity.AlchemistPotBlockEntity")
                .equals(CookingMethod.of("farmers_spell","alchemist_cooking")),"arcane pot has a real preparation method");
        check(CookingPotCompatibility.isResultSlot("com.chenjdy.farmers_spell.block.entity.container.AlchemistPotResultSlot"),"actual output slot supports extraction guards");
        for(int stage=1;stage<=9;stage++)check(FarmersSpellFoods.portion("icebreaker_bread",stage)
                .equals(stage<=2?"icebergcream":"icebergcream_sandwich"),"bread follows actual serving stage");
        for(int stage=1;stage<=5;stage++)check(FarmersSpellFoods.portion("saingezi_chicken",stage)
                .equals(stage<5?"bowl_of_saingezi_chicken":""),"empty chicken dish is not food");
        check(FarmersSpellFoods.portion("icebreaker_bread",0).isEmpty()
                &&FarmersSpellFoods.portion("icebreaker_bread",10).isEmpty(),"invalid bread stage never invents a portion");
        check(FarmersSpellFoods.portions("icebreaker_bread").equals(Map.of("icebergcream",2,"icebergcream_sandwich",7)),"bread has two ice cream and seven sandwich servings");
        check(FarmersSpellFoods.portions("saingezi_chicken").get("bowl_of_saingezi_chicken")==4,"chicken produces four actual food servings");
        check(FarmersSpellFoods.portions("red_velvet_cake").get("red_velvet_cake_slice")==8,"placed red velvet has eight bites");
        check(FarmersSpellFoods.portions("fufu").isEmpty(),"decorative powder snow is not another food serving");
        check(FarmersSpellFoods.isPortion(net.minecraft.resources.ResourceLocation.parse("farmers_spell:icebreaker_bread"),
                net.minecraft.resources.ResourceLocation.parse("farmers_spell:icebergcream")),"serving inherits its parent even when also independently cooked");
        check(!FarmersSpellFoods.isPortion(net.minecraft.resources.ResourceLocation.parse("other:icebreaker_bread"),
                net.minecraft.resources.ResourceLocation.parse("farmers_spell:icebergcream")),"matching paths never alias another mod");
        var station=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations/farmers_spell.json"))).getAsJsonObject();
        check(station.getAsJsonArray("required_mods").get(0).getAsString().equals("farmers_spell"),"workstation metadata is optional");
        var potRule=station.getAsJsonArray("workstations").get(0).getAsJsonObject();
        check(potRule.get("adapter").getAsString().equals("cooking_pot"),"uses the verified nine-slot pot layout, not a generic ten-slot layout");
        check(potRule.getAsJsonArray("recipe_types").asList().stream().map(v->v.getAsString()).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("farmers_spell:alchemist_cooking","farmersdelight:cooking")),"both arcane and native farmer recipes are allowed");
        var delivery=own("food/PlacedFoodConsumption");
        check(calls(delivery,"com/chi__you/cuisinejournal/food/FarmersSpellFoods","positions"),"placed stamp reaches every multiblock component");
        check(calls(own("recipe/PlacedServingRelations"),"com/chi__you/cuisinejournal/food/FarmersSpellFoods","portions"),"actual portions are also journal flavor links");
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null){System.out.println("SKIPPED: B004 installed-host checks; "+checks+" local checks passed (CUISINEJOURNAL_COMPAT_MODS unset)");return;}
        List<JarFile> jars=new ArrayList<>();
        try{
            try(var files=Files.list(Path.of(directory))){for(var file:files.filter(p->p.toString().endsWith(".jar")).toList())jars.add(new JarFile(file.toFile()));}
            var pot=read(jars,"com/chenjdy/farmers_spell/block/entity/AlchemistPotBlockEntity");
            if(pot==null){System.out.println("B004CompatibilityTest: "+checks+" local checks; Farmer's Spell absent");return;}
            check(pot.superName.equals("net/minecraft/world/level/block/entity/BlockEntity"),"pot is not an FD subclass and needs its own observer target");
            for(String field:List.of("inventory","scrollHandler","cookTime","cookTimeTotal"))check(pot.fields.stream().anyMatch(f->f.name.equals(field)),"verified host field "+field);
            Map<String,Integer> layout=Map.of("MEAL_DISPLAY_SLOT",6,"CONTAINER_SLOT",7,"OUTPUT_SLOT",8,"SCROLL_SLOT",9);
            layout.forEach((name,value)->check(pot.fields.stream().anyMatch(f->f.name.equals(name)&&Objects.equals(f.value,value)),"scroll is outside meal/input/output inventory: "+name));
            var cooking=pot.methods.stream().filter(m->m.name.equals("processCooking")).findFirst().orElseThrow();
            check(calls(cooking,"com/chenjdy/farmers_spell/block/entity/AlchemistPotBlockEntity","isGluttonyScroll"),"native scroll speed calculation is retained");
            check(calls(cooking,"vectorwing/farmersdelight/common/crafting/CookingPotRecipe","assemble"),"result comes from the real matched recipe");
            var recipe=Objects.requireNonNull(read(jars,"com/chenjdy/farmers_spell/recipe/AlchemistCookingRecipe"));
            check(recipe.superName.equals("vectorwing/farmersdelight/common/crafting/CookingPotRecipe"),"ingredient lookup uses FD recipe interface");
            check(recipe.methods.stream().anyMatch(m->m.name.equals("getRequiredSchool")),"school gate remains owned by the host");
            var stove=Objects.requireNonNull(read(jars,"com/chenjdy/farmers_spell/block/entity/CinderousStoveBlockEntity"));
            check(stove.superName.equals("vectorwing/farmersdelight/common/block/entity/AbstractStoveBlockEntity"),"cinder stove inherits existing stove production hooks");
            check(calls(stove,stove.superName,"serverTick"),"stove actually delegates native processing");
            var cake=Objects.requireNonNull(read(jars,"com/chenjdy/farmers_spell/block/RedVelvetCakeBlock"));
            check(calls(cake,"net/minecraftforge/event/ForgeEventFactory","onItemUseFinish"),"block cake Finish must be swallowed by enclosing consumption transaction");
            check(cake.methods.stream().filter(m->m.name.equals("getMaxBites")).flatMap(m->Arrays.stream(m.instructions.toArray()))
                    .anyMatch(i->i instanceof IntInsnNode constant&&constant.operand==8),"native placed cake count is eight, not recipe cutting count");
            var pie=Objects.requireNonNull(read(jars,"com/chenjdy/farmers_spell/block/EdenAppleTartBlock"));
            check(pie.superName.equals("vectorwing/farmersdelight/common/block/PieBlock"),"Eden tart uses standard pie portion reader");
            for(String name:List.of("IcebreakerBreadBlock","SaingeziChickenBlock")){
                var type=Objects.requireNonNull(read(jars,"com/chenjdy/farmers_spell/block/"+name));
                for(String method:List.of("getOriginPos","getBlockPos"))check(type.methods.stream().anyMatch(m->m.name.equals(method)&&(m.access&Opcodes.ACC_STATIC)!=0&&(m.access&Opcodes.ACC_PUBLIC)!=0),"native multiblock geometry is callable: "+name+"."+method);
            }
            for(var jar:jars){var entry=jar.getJarEntry("data/farmers_spell/recipes/cutting/red_velvet_cake_slice.json");if(entry!=null)try(var reader=new java.io.InputStreamReader(jar.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){
                var cutting=JsonParser.parseReader(reader).getAsJsonObject();
                check(cutting.getAsJsonArray("result").get(0).getAsJsonObject().get("count").getAsInt()==7,"preserve host recipe's seven slices without silently changing yield");
            }}
            System.out.println("B004CompatibilityTest: "+checks+" checks passed");
        }finally{for(var jar:jars)jar.close();}
    }
    private static ClassNode own(String path)throws Exception{try(var input=B004CompatibilityTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/"+path+".class")){var result=new ClassNode();new ClassReader(Objects.requireNonNull(input)).accept(result,0);return result;}}
    private static ClassNode read(List<JarFile> jars,String path)throws Exception{for(var jar:jars){var entry=jar.getJarEntry(path+".class");if(entry!=null)try(var input=jar.getInputStream(entry)){var result=new ClassNode();new ClassReader(input).accept(result,0);return result;}}return null;}
    private static boolean calls(ClassNode type,String owner,String name){return type.methods.stream().anyMatch(m->calls(m,owner,name));}
    private static boolean calls(MethodNode method,String owner,String name){for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;return false;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
