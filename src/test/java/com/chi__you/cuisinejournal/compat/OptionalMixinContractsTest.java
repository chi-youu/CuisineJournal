package com.chi__you.cuisinejournal.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.*;

public final class OptionalMixinContractsTest {
    public static void main(String[] args) throws Exception {
        var host = type("test/Host");
        var adapter = type("test/Adapter");
        var injection = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        injection.values = new ArrayList<>(List.of("method",List.of("applyRecipe","applyFlexRecipe"),"remap",false,
                "at",List.of(at("HEAD",null))));
        var handler = new MethodNode(0,"begin","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,null);
        handler.visibleAnnotations = List.of(injection);adapter.methods.add(handler);
        check(reason(adapter,host) != null, "old pot without apply methods is disabled before injection");
        host.methods.add(method("applyRecipe"));host.methods.add(method("applyFlexRecipe"));
        check(reason(adapter,host) == null, "matching current pot contract remains enabled");
        var redirect = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Redirect;");
        String invocation="Ltest/Items;splitIntoStacks(Lnet/minecraft/world/item/ItemStack;I)Ljava/util/List;";
        redirect.values=new ArrayList<>(List.of("method",List.of("craftTick"),"remap",false,"at",at("INVOKE",invocation)));
        handler.visibleAnnotations=List.of(redirect);host.methods.clear();var craft=method("craftTick");host.methods.add(craft);
        check(reason(adapter,host) != null, "old hot spring using a different output call disables full adapter");
        craft.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,"test/Items","splitIntoStacks",
                "(Lnet/minecraft/world/item/ItemStack;I)Ljava/util/List;",false));
        check(reason(adapter,host) == null, "matching output invocation accepted");
        craft.instructions.clear();
        check(reason(adapter,host) != null, "missing invocation is not hidden by require zero");
        installed(System.getenv("CUISINEJOURNAL_COMPAT_MODS"),false);
        installed(System.getenv("CUISINEJOURNAL_LEGACY_MODS"),true);
        System.out.println("OptionalMixinContractsTest: missing methods, invocation drift and matched contracts passed");
    }
    private static void installed(String directory,boolean legacy) throws Exception {
        if(directory==null){System.out.println("SKIPPED: "+(legacy?"legacy":"current")+" installed optional contract checks");return;}
        var pairs=Map.of(
                "KaleidoscopeReadyMixin","com/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/PotBlockEntity",
                "HotSpringProductionMixin","com/renyigesai/immortalers_delight/fluid/HotSpringFluidsBlock");
        try(var paths=java.nio.file.Files.list(java.nio.file.Path.of(directory))) {
            var files=paths.filter(path->path.toString().endsWith(".jar")).toList();
            for(var pair:pairs.entrySet()) {
                ClassNode target=null;
                for(var path:files)try(var jar=new java.util.jar.JarFile(path.toFile())) {
                    var entry=jar.getJarEntry(pair.getValue()+".class");if(entry==null)continue;
                    target=new ClassNode();try(var input=jar.getInputStream(entry)){new org.objectweb.asm.ClassReader(input).accept(target,0);}break;
                }
                if(target==null){System.out.println("SKIPPED: optional host absent "+pair.getValue());continue;}
                var mixin=new ClassNode();
                try(var input=OptionalMixinContractsTest.class.getClassLoader().getResourceAsStream(
                        "com/chi__you/cuisinejournal/mixin/production/"+pair.getKey()+".class")) {
                    new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(mixin,0);
                }
                String problem=reason(mixin,target);
                check(legacy?problem!=null:problem==null,"reviewed "+(legacy?"legacy degrades":"current remains enabled")+": "+pair.getKey()+" -> "+problem);
            }
        }
    }
    private static String reason(ClassNode adapter,ClassNode host) {
        return OptionalMixinContracts.incompatibility(adapter,host,name->null);
    }
    private static ClassNode type(String name) { var node=new ClassNode();node.name=name;node.superName="java/lang/Object";return node; }
    private static MethodNode method(String name) {
        var method=new MethodNode(0,name,"()V",null,null);method.instructions.add(new InsnNode(Opcodes.RETURN));return method;
    }
    private static AnnotationNode at(String point,String target) {
        var node=new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        node.values=new ArrayList<>(List.of("value",point));
        if(target!=null){node.values.add("target");node.values.add(target);}return node;
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
