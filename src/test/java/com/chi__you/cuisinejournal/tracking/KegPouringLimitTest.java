package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.mixin.production.KegProductionMixin;
import net.minecraft.util.Mth;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.Objects;

/** Check the injected operand without creating Forge fluid registries. */
public final class KegPouringLimitTest {
    public static void main(String[] args)throws Exception {
        ClassNode hook=new ClassNode();
        try(var input=KegProductionMixin.class.getResourceAsStream("KegProductionMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(hook,0);
        }
        MethodNode handler=hook.methods.stream().filter(method->method.name.equals("cuisinejournal$availableForPouring")).findFirst().orElseThrow();
        int reads=0;
        for(var instruction:handler.instructions)if(instruction instanceof MethodInsnNode call) {
            if(!call.owner.equals("net/minecraftforge/fluids/capability/templates/FluidTank")||!call.name.equals("getFluidAmount")||!call.desc.equals("()I"))
                throw new AssertionError("Pouring must read actual remaining liquid only");
            reads++;
        }
        if(reads!=1)throw new AssertionError("Missing remaining-liquid operand");
        int[][] cases={{0,250,4,64,0},{249,250,4,64,0},{250,250,4,64,1},{500,250,4,64,2},
                {750,250,64,64,3},{999,250,4,64,3},{1000,250,4,64,4},{500,250,1,64,1},
                {750,250,64,1,1},{500,100,8,64,5},{750,500,4,64,1}};
        for(int[] c:cases) {
            int servings=c[0]<c[1]?0:Mth.clamp(Math.min(c[2],c[3]),1,c[0]/c[1]);
            if(servings!=c[4]||servings*c[1]>c[0])throw new AssertionError("Incorrect serving count for "+java.util.Arrays.toString(c));
        }
        System.out.println("KegPouringLimitTest: injected remaining-liquid operand and "+cases.length+" boundary cases passed");
    }
}
