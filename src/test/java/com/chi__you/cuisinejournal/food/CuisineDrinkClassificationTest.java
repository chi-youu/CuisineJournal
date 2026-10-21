package com.chi__you.cuisinejournal.food;

import net.minecraft.world.item.UseAnim;

public final class CuisineDrinkClassificationTest {
    public static void main(String[] args) throws Exception {
        check(CuisineFoodDefinition.isDrink(null, false, UseAnim.DRINK), "drink animation");
        check(!CuisineFoodDefinition.isDrink(null, false, UseAnim.EAT), "ordinary food");
        check(CuisineFoodDefinition.isDrink(null, true, UseAnim.EAT), "recognized alcohol");
        check(!CuisineFoodDefinition.isDrink("food", true, UseAnim.DRINK), "explicit food overrides alcohol and animation");
        check(CuisineFoodDefinition.isDrink("drink", false, UseAnim.EAT), "explicit drink overrides animation");
        check(!calls("CuisineFoodStats","drinkBonus","CuisineAlcohol","strength"),"drink bonus is not multiplied by alcohol strength");
        check(!calls("CuisineFoodEffects","collect","CuisineAlcohol"),"alcohol effects use ordinary configured levels without strength or meter exclusions");
        check(!calls("CuisineNativeFoodEffects","filter","CuisineAlcohol"),"native effect suppression has no alcohol exceptions");
        check(CuisineDrinkClassificationTest.class.getClassLoader().getResource("com/chi__you/cuisinejournal/food/CuisineAlcoholMeter.class")==null,
                "obsolete cross-mod alcohol meter is not shipped");
        System.out.println("CuisineDrinkClassificationTest: 9 checks passed");
    }

    private static boolean calls(String type,String method,String target) throws Exception {
        return calls(type,method,target,null);
    }
    private static boolean calls(String type,String method,String target,String targetMethod) throws Exception {
        var node=new org.objectweb.asm.tree.ClassNode();
        try(var input=CuisineDrinkClassificationTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/food/"+type+".class")) {
            new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(input)).accept(node,0);
        }
        for(var m:node.methods)if(m.name.equals(method))for(var instruction:m.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.endsWith("/"+target)
                    &&(targetMethod==null||call.name.equals(targetMethod)))return true;
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
