package com.chi__you.cuisinejournal.client.journal;

public final class ScreenReturnSessionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var route = new ScreenReturnSession<Object>();
        Object journal = new Object(), other = new Object(), tree = new Object();
        check(!route.enter(other,true,0) && route.closing(true,true)==null,"independent skill hotkey has no journal parent");
        route.request(journal,0);
        check(route.enter(journal,true,1),"server skill screen claims originating journal");
        check(route.closing(true,true)==journal,"close returns identical journal with page state");
        check(route.closing(true,true)==null,"return is one shot");
        route.request(journal,0);
        check(!route.enter(other,true,1),"abandoned journal cannot claim later tree");
        check(route.closing(true,true)==null,"abandoned return cleared");
        route.request(journal,0);
        check(!route.enter(journal,true,10_001),"failed request times out");
        route.request(journal,0);route.expire(journal,10_001);
        check(!route.enter(journal,true,10_002),"tick expires pending request");
        route.request(journal,0);route.enter(journal,true,1);
        check(route.enter(tree,true,20_000),"mode-switched skill screen keeps parent");
        check(route.closing(true,true)==journal,"replacement tree returns to original journal");
        route.request(journal,0);route.enter(journal,true,1);
        check(route.closing(true,false)==null,"unrelated destination not replaced");
        check(route.closing(true,true)==null,"unrelated destination clears route");
        route.request(journal,0);route.enter(journal,true,1);route.clear();
        check(route.closing(true,true)==null,"disconnect clears journal reference");
        route.request(journal,0);route.expire(other,1);
        check(!route.enter(journal,true,2),"closing journal before server reply clears route");
        var target = new org.objectweb.asm.tree.ClassNode();
        try(var stream=ScreenReturnSessionTest.class.getResourceAsStream("/net/minecraft/client/Minecraft.class")) {
            new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(stream)).accept(target,0);
        }
        check(target.methods.stream().anyMatch(m->m.name.equals("setScreen")
                &&m.desc.equals("(Lnet/minecraft/client/gui/screens/Screen;)V")),"core screen switch target matches including null close");
        String mods=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(mods!=null) try(var paths=java.nio.file.Files.list(java.nio.file.Path.of(mods))) {
            var jarPath=paths.filter(p->p.getFileName().toString().startsWith("puffish_skills-")&&p.toString().endsWith(".jar")).findFirst().orElseThrow();
            try(var jar=new java.util.jar.JarFile(jarPath.toFile())) {
                check(jar.getJarEntry("net/puffish/skillsmod/client/gui/SkillsScreen.class")!=null,"installed host skill-screen class identity matches");
            }
        }
        System.out.println("ScreenReturnSessionTest: "+checks+" checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
