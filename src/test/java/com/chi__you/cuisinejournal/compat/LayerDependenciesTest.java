package com.chi__you.cuisinejournal.compat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Layer ownership plus client-subtype dataflow, not a blanket ban on dependency cycles. */
public final class LayerDependenciesTest {
    public static void main(String[] args) throws Exception {
        ClientTypeFlow.verifyFixtures();
        int verified=0;
        var root=Path.of("build/classes/java/main/com/chi__you/cuisinejournal");
        try(var paths=Files.walk(root)) {
            for(var file:paths.filter(path->path.toString().endsWith(".class")).toList()) {
                var type=new ClassNode();try(var input=Files.newInputStream(file)){new ClassReader(input).accept(type,0);}
                boolean recipe=type.name.contains("/recipe/");
                String top=type.name.contains("$")?type.name.substring(0,type.name.indexOf('$')):type.name;
                boolean common=!type.name.contains("/client/")&&!type.name.contains("/mixin/")
                        &&!top.endsWith("/forge/ForgeClientBridge")
                        &&!top.equals("com/chi__you/cuisinejournal/food/CuisineFoodHud");
                if(common)for(var method:type.methods)if(method.instructions.size()>0){
                    ClientTypeFlow.verify(type,method);verified++;
                }
                for(var method:type.methods)for(var instruction:method.instructions) {
                    String owner=instruction instanceof MethodInsnNode call?call.owner:instruction instanceof FieldInsnNode field?field.owner:"";
                    if(common)check(!owner.startsWith("com/chi__you/cuisinejournal/food/CuisineFoodHud"),
                            "client-only HUD must not be linked from common code: "+type.name);
                    if(recipe)check(!owner.startsWith("com/chi__you/cuisinejournal/network/")
                            &&!owner.startsWith("com/chi__you/cuisinejournal/client/"),"recipe -> transport/client: "+type.name);
                    if(type.name.endsWith("/forge/ForgeEventBridge")||type.name.endsWith("/skills/VanillaSkillNutrition"))
                        check(!owner.equals("com/chi__you/cuisinejournal/client/ClientJournalState"),
                                "skill reads must use the injected view: "+type.name);
                }
            }
        }
        System.out.println("LayerDependenciesTest: recipe isolation, injected skill reads and "+verified+" common methods checked by type flow");
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
