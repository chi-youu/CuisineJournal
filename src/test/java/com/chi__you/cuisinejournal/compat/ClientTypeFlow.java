package com.chi__you.cuisinejournal.compat;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.util.*;

/** SimpleVerifier with classfile-only hierarchy resolution; never loads a Minecraft client class. */
final class ClientTypeFlow {
    private static final Type OBJECT=Type.getObjectType("java/lang/Object");
    private record Header(boolean face,String parent,List<String> interfaces) {}
    private static final Map<String,Header> HEADERS=new HashMap<>();
    private ClientTypeFlow() {}

    static void verify(ClassNode owner,MethodNode method)throws AnalyzerException {
        var verifier=new SimpleVerifier(Opcodes.ASM9,Type.getObjectType(owner.name),
                owner.superName==null?null:Type.getObjectType(owner.superName),
                owner.interfaces.stream().map(Type::getObjectType).toList(),(owner.access&Opcodes.ACC_INTERFACE)!=0){
            @Override protected boolean isSubTypeOf(BasicValue value,BasicValue expected){
                Type actual=value.getType(),target=expected.getType();
                if(actual==null||target==null)return value.equals(expected);
                if(!reference(actual)||!reference(target))return value.equals(expected);
                // Like SimpleVerifier/JVM verification, interface targets accept any reference.
                if(target.getSort()==Type.OBJECT&&isInterface(target))return true;
                return isAssignableFrom(target,actual);
            }
            @Override protected boolean isInterface(Type type){return type.getSort()==Type.OBJECT&&header(type.getInternalName()).face;}
            @Override protected Type getSuperClass(Type type){
                if(type.getSort()==Type.ARRAY)return OBJECT;
                if(client(type))throw unsafe(type,null);
                String parent=header(type.getInternalName()).parent;return parent==null?null:Type.getObjectType(parent);
            }
            @Override protected boolean isAssignableFrom(Type expected,Type actual){
                if(expected.equals(actual))return true;
                if(actual.equals(BasicInterpreter.NULL_TYPE))return reference(expected);
                if(expected.equals(OBJECT)&&reference(actual))return true;
                if(actual.getSort()==Type.ARRAY){
                    if(expected.getSort()==Type.ARRAY){
                        Type left=component(expected),right=component(actual);
                        return reference(left)&&reference(right)?isAssignableFrom(left,right):left.equals(right);
                    }
                    return expected.equals(Type.getObjectType("java/lang/Cloneable"))
                            ||expected.equals(Type.getObjectType("java/io/Serializable"));
                }
                if(client(actual))throw unsafe(actual,expected);
                if(!reference(actual)||expected.getSort()!=Type.OBJECT)return false;
                var queue=new ArrayDeque<String>();var seen=new HashSet<String>();queue.add(actual.getInternalName());
                while(!queue.isEmpty()){
                    String name=queue.remove();if(!seen.add(name))continue;
                    if(name.equals(expected.getInternalName()))return true;
                    var value=header(name);if(value.parent!=null)queue.add(value.parent);queue.addAll(value.interfaces);
                }
                return false;
            }
            @Override protected Class<?> getClass(Type type){throw new AssertionError("Verifier must not load classes: "+type);}
        };
        try{new Analyzer<>(verifier).analyze(owner.name,method);}
        catch(AnalyzerException failure){throw new AnalyzerException(failure.node,owner.name+"."+method.name+method.desc+": "+failure.getMessage(),failure);}
    }
    private static IllegalStateException unsafe(Type actual,Type expected){return new IllegalStateException("Client hierarchy required: "+actual+" -> "+expected);}
    private static Type component(Type type){return Type.getType(type.getDescriptor().substring(1));}
    private static boolean reference(Type type){return type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY;}
    private static boolean client(Type type){
        while(type.getSort()==Type.ARRAY)type=component(type);
        return type.getSort()==Type.OBJECT&&type.getInternalName().startsWith("net/minecraft/client/");
    }
    private static Header header(String name){
        return HEADERS.computeIfAbsent(name,key->{
            try(var input=ClientTypeFlow.class.getClassLoader().getResourceAsStream(key+".class")){
                if(input==null)throw new IllegalStateException("Missing hierarchy metadata: "+key);
                var reader=new ClassReader(input);return new Header((reader.getAccess()&Opcodes.ACC_INTERFACE)!=0,reader.getSuperName(),List.of(reader.getInterfaces()));
            }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        });
    }

    static void verifyFixtures()throws Exception {
        String level="Lnet/minecraft/world/level/Level;",client="Lnet/minecraft/client/multiplayer/ClientLevel;";
        var owner=new ClassNode();owner.name="test/CommonTypeFlow";owner.superName="java/lang/Object";
        var local=method("localReturn","()"+level);
        local.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"test/Source","level",client));
        local.instructions.add(new VarInsnNode(Opcodes.ASTORE,0));local.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        local.instructions.add(new InsnNode(Opcodes.ARETURN));rejects(owner,local);
        var argument=method("argument","()V");
        argument.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"test/Source","client","()"+client,false));
        argument.instructions.add(new VarInsnNode(Opcodes.ASTORE,0));argument.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        argument.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"test/Sink","accept","("+level+")V",false));
        argument.instructions.add(new InsnNode(Opcodes.RETURN));rejects(owner,argument);
        var array=method("array","()["+level);
        array.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"test/Source","levels","["+client));
        array.instructions.add(new InsnNode(Opcodes.ARETURN));rejects(owner,array);
        var merge=method("merge","(Z)"+level);var other=new LabelNode();var end=new LabelNode();
        merge.instructions.add(new VarInsnNode(Opcodes.ILOAD,0));merge.instructions.add(new JumpInsnNode(Opcodes.IFEQ,other));
        merge.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"test/Source","level",client));
        merge.instructions.add(new JumpInsnNode(Opcodes.GOTO,end));merge.instructions.add(other);
        merge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"test/Source","common","()"+level,false));
        merge.instructions.add(end);merge.instructions.add(new InsnNode(Opcodes.ARETURN));rejects(owner,merge);
        var safe=method("bridgeReturn","()"+level);
        safe.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"test/Source","common","()"+level,false));
        safe.instructions.add(new InsnNode(Opcodes.ARETURN));verify(owner,safe);
        var object=method("objectOnly","()Ljava/lang/Object;");
        object.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"test/Source","level",client));
        object.instructions.add(new InsnNode(Opcodes.ARETURN));verify(owner,object);
        var objects=method("objectArray","()[Ljava/lang/Object;");
        objects.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"test/Source","levels","["+client));
        objects.instructions.add(new InsnNode(Opcodes.ARETURN));verify(owner,objects);
    }
    private static MethodNode method(String name,String descriptor){
        var method=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,name,descriptor,null,null);
        method.maxStack=4;method.maxLocals=2;return method;
    }
    private static void rejects(ClassNode owner,MethodNode method)throws Exception {
        try{verify(owner,method);}
        catch(AnalyzerException failure){
            if(!failure.toString().contains("Client hierarchy required"))throw new AssertionError("Fixture failed for unrelated reason",failure);return;
        }
        throw new AssertionError("Unsafe client flow accepted: "+method.name);
    }
}
