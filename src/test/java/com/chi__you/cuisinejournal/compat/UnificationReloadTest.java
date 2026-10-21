package com.chi__you.cuisinejournal.compat;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlagSet;
import java.util.*;
import java.util.concurrent.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public final class UnificationReloadTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var initial = List.of("vanilla", "file/before", "kaleidoscope_compat:always",
                "kaleidoscope_compat:compat", "file/after");
        var unified = List.of("kaleidoscope_compat:always", "kaleidoscope_compat:unite",
                "kaleidoscope_compat:unite_farmersdelight");
        check(UnificationPacks.replace(initial, unified).equals(List.of("vanilla","file/before",
                unified.get(0),unified.get(1),unified.get(2),"file/after")), "other pack priority survives replacement");
        check(UnificationPacks.managed("kaleidoscope_compat:custom")
                && !UnificationPacks.managed("file/kaleidoscope_compat"), "unrelated pack ids not captured");
        var required=UnificationPacks.requiredOrder(List.of("vanilla","file/user"),
                List.of(pack("vanilla"),pack("file/user"),pack(UnificationRulePack.ID)));
        check(required.stream().map(Pack::getId).toList().equals(List.of("vanilla",UnificationRulePack.ID,"file/user")),
                "first required insertion cannot outrank explicit user datapacks");
        check(UnificationPacks.requiredOrder(List.of("file/user",UnificationRulePack.ID),required)==required,
                "explicit existing pack order not rewritten");
        RepositorySource mixed = consumer -> {
            consumer.accept(pack("vanilla")); consumer.accept(pack("kaleidoscope_compat:compat"));
            consumer.accept(pack("file/after"));
        };
        var repository = new PackRepository(mixed);
        var field = PackRepository.class.getDeclaredField("sources"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var sources = (Set<RepositorySource>)field.get(repository);
        repository.reload(); repository.setSelected(List.of("vanilla","kaleidoscope_compat:compat","file/after"));
        var before = List.copyOf(repository.getSelectedIds());
        List<Collection<String>> loaded = new ArrayList<>();
        var pending = new CompletableFuture<Void>();
        var first = new UnificationReload.Transaction(repository,sources, ids -> {loaded.add(List.copyOf(ids)); return pending;});
        var applying = first.applySources(List.of(consumer -> consumer.accept(pack("kaleidoscope_compat:unite"))));
        check(!applying.isDone(), "switch waits for actual resource reload");
        check(!repository.isAvailable("kaleidoscope_compat:compat")
                && repository.isAvailable("kaleidoscope_compat:unite")
                && repository.isAvailable("file/after"), "old required pack removed while unrelated source retained");
        pending.complete(null); applying.join();
        int sourceCount = sources.size();
        for (int i=0;i<4;i++) {
            String mode = i%2==0 ? "compat" : "unite";
            var change = new UnificationReload.Transaction(repository,sources,ids -> CompletableFuture.completedFuture(null));
            change.applySources(List.of(consumer -> consumer.accept(pack("kaleidoscope_compat:"+mode)))).join();
            check(sources.size()==sourceCount, "repeat switch does not accumulate wrappers or finders");
        }
        var originalSources = List.copyOf(sources);
        var originalSelection = List.copyOf(repository.getSelectedIds());
        int[] calls={0};
        var failed = new UnificationReload.Transaction(repository,sources,ids -> ++calls[0]==1
                ? CompletableFuture.failedFuture(new IllegalStateException("broken pack"))
                : CompletableFuture.completedFuture(null));
        check(failed.applySources(List.of(consumer -> consumer.accept(pack("kaleidoscope_compat:compat"))))
                .isCompletedExceptionally(), "resource reload failure propagates");
        failed.rollback().join();
        check(List.copyOf(sources).equals(originalSources)
                && List.copyOf(repository.getSelectedIds()).equals(originalSelection) && calls[0]==2,
                "rollback restores exact sources and selection then reloads runtime");
        var restored = new UnificationReload.Transaction(repository,sources,ids -> CompletableFuture.completedFuture(null));
        restored.applySources(List.of(consumer -> consumer.accept(pack("kaleidoscope_compat:compat")))).join();
        check(List.copyOf(repository.getSelectedIds()).equals(before), "round trip restores original priority");
        var node = new ClassNode();
        try(var input=UnificationReloadTest.class.getClassLoader().getResourceAsStream("net/minecraft/world/item/CreativeModeTabs.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(node,ClassReader.SKIP_CODE);
        }
        check(node.fields.stream().anyMatch(f->f.name.equals("CACHED_PARAMETERS")
                &&f.desc.equals("Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;")),
                "creative cache accessor matches mapped game field");
        try(var input=UnificationReloadTest.class.getClassLoader().getResourceAsStream("net/minecraft/server/packs/repository/PackRepository.class")) {
            node = new ClassNode(); new ClassReader(Objects.requireNonNull(input)).accept(node,ClassReader.SKIP_CODE);
        }
        check(node.fields.stream().anyMatch(f->f.name.equals("sources")&&f.desc.equals("Ljava/util/Set;")),
                "repository source accessor matches game field");
        var host=java.nio.file.Path.of("build/kaleidoscope-compat-2.6.5-patch2-audit.jar");
        if(java.nio.file.Files.isRegularFile(host))try(var jar=new java.util.jar.JarFile(host.toFile())) {
            try(var input=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(
                    "com/bmt/kaleidoscope_compat/datapack/DatapackLoader.class")))) {
                node = new ClassNode(); new ClassReader(input).accept(node,ClassReader.SKIP_CODE);
            }
            check(node.methods.stream().anyMatch(m->m.name.equals("onDatapackLoad")
                    &&m.desc.equals("(Lnet/minecraftforge/event/AddPackFindersEvent;)V")
                    &&(m.access&org.objectweb.asm.Opcodes.ACC_STATIC)!=0), "host pack registration contract matches");
        }
        System.out.println("UnificationReloadTest: "+checks+" checks passed");
    }
    private static Pack pack(String id) {
        return Pack.create(id,Component.literal(id),UnificationPacks.managed(id), ignored -> null,
                new Pack.Info(Component.empty(),15,FeatureFlagSet.of()),PackType.SERVER_DATA,
                Pack.Position.TOP,false,PackSource.DEFAULT);
    }
    private static void check(boolean passed,String label) { if(!passed)throw new AssertionError(label); checks++; }
}
