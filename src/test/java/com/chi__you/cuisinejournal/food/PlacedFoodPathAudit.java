package com.chi__you.cuisinejournal.food;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Developer audit of actual installed direct-eating bytecode, without loading host classes. */
public final class PlacedFoodPathAudit {
    public static void main(String[] args)throws Exception {
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null)throw new IllegalStateException("CUISINEJOURNAL_COMPAT_MODS required");
        Map<String,String> parents=new HashMap<>();List<String> candidates=new ArrayList<>();
        try(var files=Files.list(Path.of(directory))) {
            for(Path path:files.filter(file->file.toString().endsWith(".jar")).sorted().toList()) {
                if(path.getFileName().toString().startsWith("cuisinejournal"))continue;
                try(JarFile jar=new JarFile(path.toFile())) {
                    for(var entry:jar.stream().filter(e->e.getName().endsWith(".class")).toList()) {
                        try(var input=jar.getInputStream(entry)) {
                            ClassNode type=new ClassNode();new ClassReader(input).accept(type,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                            parents.put(type.name,type.superName);
                            for(MethodNode method:type.methods) {
                                Set<String> calls=new LinkedHashSet<>(),fields=new LinkedHashSet<>();
                                for(var instruction:method.instructions) {
                                    if(instruction instanceof MethodInsnNode call && (
                                            call.owner.equals("net/minecraft/world/food/FoodData") && Set.of("m_38707_","m_38712_","m_38708_","eat").contains(call.name)
                                            ||call.owner.equals("net/minecraft/world/entity/player/Player")&&Set.of("m_6672_","eat").contains(call.name)
                                            ||call.owner.equals("net/minecraft/world/item/ItemStack")&&Set.of("m_41671_","finishUsingItem").contains(call.name)
                                            ||call.owner.equals("net/minecraft/world/item/Item")&&Set.of("m_5922_","finishUsingItem").contains(call.name)
                                            ||call.owner.equals("net/minecraft/world/level/block/CakeBlock")&&Set.of("m_51185_","eat").contains(call.name)))
                                        calls.add(call.owner+"."+call.name+call.desc);
                                    if(instruction instanceof FieldInsnNode field && field.getOpcode()==org.objectweb.asm.Opcodes.GETSTATIC)
                                        fields.add(field.owner+"."+field.name);
                                }
                                if(!calls.isEmpty())candidates.add(type.name+"\t"+path.getFileName()+"\t"+method.name+method.desc+"\t"+calls+"\t"+fields);
                            }
                        }
                    }
                }
            }
        }
        StringBuilder report=new StringBuilder("class\tjar\tmethod\tdirect_calls\tstatic_fields\n");
        for(String row:candidates) {
            String name=row.substring(0,row.indexOf('\t'));Set<String> seen=new HashSet<>();String current=name;
            boolean block=false;
            while(current!=null&&seen.add(current)) {
                if(current.startsWith("net/minecraft/world/level/block/")||current.startsWith("net/minecraft/world/level/block/entity/")){block=true;break;}
                current=parents.get(current);
            }
            if(block)report.append(row).append('\n');
        }
        Path output=Path.of("build/placed-food-path-audit.tsv");Files.writeString(output,report);
        System.out.println("PlacedFoodPathAudit: "+(report.toString().lines().count()-1)+" direct block-eating paths, "+parents.size()+" classes scanned; "+output);
    }
}
