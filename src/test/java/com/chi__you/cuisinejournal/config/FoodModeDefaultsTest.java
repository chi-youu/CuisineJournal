package com.chi__you.cuisinejournal.config;

import com.chi__you.cuisinejournal.network.Packets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.nio.file.*;
import java.util.jar.JarFile;

public final class FoodModeDefaultsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var config=new ServerConfig();
        check(com.chi__you.cuisinejournal.food.CuisineFoodConfig.FOOD_SYSTEM.getDefault().equals("vanilla"),
                "fresh common config defaults to Vanilla");
        var fresh=new ServerConfig();
        check(FoodModeDefaults.apply(fresh,"vanilla")&&!fresh.flavors.enabled&&fresh.nutritionDisplay.enabled
                &&fresh.foodEffects.enabled,"fresh Vanilla applies nutrition on, flavors off, native effects on");
        check(!config.compatibility.unifiedMode, "unification defaults off");
        check(com.chi__you.cuisinejournal.compat.KaleidoscopeUnificationCompat.datapackMode(false).equals("COMPAT")
                && com.chi__you.cuisinejournal.compat.KaleidoscopeUnificationCompat.datapackMode(true).equals("UNITE"),
                "only default compatibility and unified modes are exposed");
        config.compatibility.unifiedMode=true;
        check(Toml.read(Toml.write(config,List.of()),ServerConfig.class).compatibility.unifiedMode,
                "reserved unification choice survives config round-trip");
        for(boolean enabled:List.of(false,true))for(boolean valheim:List.of(false,true))for(boolean preserve:List.of(false,true))
            check(com.chi__you.cuisinejournal.food.CuisineGameplayRules.nativeEffectsEnabled(enabled,valheim,preserve)
                    ==(enabled&&(!valheim||preserve)),"native effect policy agrees in both modes");
        check(config.mastery.perDish() && config.foodEffects.enabled, "new installs default to dish stars and native effects");
        config.mastery.scope=null;config.validate();
        check(config.mastery.perDish(), "invalid mastery scope falls back to dish");
        check(FoodModeDefaults.apply(config,"valheim")&&config.flavors.enabled&&config.foodEffects.enabled
                &&!config.nutritionDisplay.enabled,"Valheim applies dependent defaults");
        config.foodEffects.enabled=true;config.nutritionDisplay.enabled=true;config.flavors.enabled=false;
        check(!FoodModeDefaults.apply(config,"valheim")&&config.foodEffects.enabled&&config.nutritionDisplay.enabled
                &&!config.flavors.enabled,"same-mode reload preserves manual overrides");
        var persisted=Toml.read(Toml.write(config,List.of()),ServerConfig.class);persisted.validate();
        check(!FoodModeDefaults.apply(persisted,"valheim")&&persisted.foodEffects.enabled&&persisted.nutritionDisplay.enabled,
                "manual overrides and mode marker survive restart");
        check(FoodModeDefaults.apply(persisted,"vanilla")&&!persisted.flavors.enabled&&persisted.foodEffects.enabled
                &&persisted.nutritionDisplay.enabled,"Vanilla applies inverse defaults");
        persisted.flavors.enabled=true;persisted.foodEffects.enabled=false;persisted.nutritionDisplay.enabled=false;
        check(!FoodModeDefaults.apply(persisted,"vanilla")&&persisted.flavors.enabled&&!persisted.foodEffects.enabled,
                "Vanilla also permits independent manual overrides");
        check(FoodModeDefaults.apply(persisted,"valheim")&&persisted.flavors.enabled&&!persisted.foodEffects.enabled,
                "switching back preserves a manually disabled native effect setting");
        persisted.foodEffects.enabled=true;
        check(FoodModeDefaults.apply(persisted,"vanilla")&&persisted.foodEffects.enabled,
                "switching to vanilla preserves enabled native effects");
        persisted.foodEffects.enabled=false;
        check(FoodModeDefaults.apply(persisted,"valheim")&&!persisted.foodEffects.enabled,
                "switching to valheim never rewrites the native effect setting");
        boolean invalid=false;try{FoodModeDefaults.apply(persisted,"other");}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"unknown mode rejected");
        var rules=new Packets.SyncRules(1,2,3,100,List.of(1,2,3,4,5),"CUSTOM",false,true,0,0,0,1,0,0,
                true,false,0,.05,1,1.5,true,false,false,false);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{rules.write(buffer);check(Packets.SyncRules.read(buffer).equals(rules)&&buffer.readableBytes()==0,
                "flavor and nutrition toggles round-trip independently");}finally{buffer.release();}
        contracts();
        for (boolean unified : List.of(false,true)) for (boolean installed : List.of(false,true))
            for (boolean busy : List.of(false,true)) {
            buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                var settings=new Packets.SyncServerSettings(true,"valheim",false,true,false,false,0,1,
                        "COOK_RECIPE","PERSONAL",false,unified,installed,busy);
                settings.write(buffer);
                check(Packets.SyncServerSettings.read(buffer).equals(settings)&&buffer.readableBytes()==0,
                        "server setting preserves unified mode and optional mod presence");
            } finally { buffer.release(); }
        }
        if(Files.isRegularFile(Path.of("build/kaleidoscope-compat-2.6.5-patch2-audit.jar")))
        try(var jar=new JarFile("build/kaleidoscope-compat-2.6.5-patch2-audit.jar")) {
            var node=new ClassNode();
            try(var input=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(
                    "com/bmt/kaleidoscope_compat/config/ForgeConfig.class")))) { new ClassReader(input).accept(node,0); }
            check(node.fields.stream().anyMatch(field->field.name.equals("DATAPACK_MODE")
                    &&field.desc.equals("Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;")),
                    "audited Compat exposes the optional mode bridge");
        }
        buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var request=new Packets.SetServerSetting(Packets.SetServerSetting.Key.NUTRITION,"false");request.write(buffer);
            check(com.chi__you.cuisinejournal.network.Network.decode(request.id(),buffer).equals(request)
                    &&buffer.readableBytes()==0,"nutrition display setting is registered and round-trips");
        } finally { buffer.release(); }
        String apple=System.getenv("CUISINEJOURNAL_APPLESKIN_JAR");
        if(apple!=null)try(var jar=new JarFile(apple)) {
            method(jar,"squeek/appleskin/client/TooltipOverlayHandler","shouldShowTooltip",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Z");
            method(jar,"squeek/appleskin/client/HUDOverlayHandler","shouldRenderAnyOverlays","()Z");
            method(jar,"squeek/appleskin/client/HUDOverlayHandler","renderExhaustion",
                    "(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FII)V");
        }
        String mods=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(mods!=null)try(var paths=Files.list(Path.of(mods))) {
            var file=paths.filter(path->path.getFileName().toString().startsWith("obscure_api")&&path.toString().endsWith(".jar"))
                    .findFirst().orElse(null);
            if(file!=null)try(var jar=new JarFile(file.toFile())) {
                method(jar,"com/obscuria/obscureapi/client/TooltipBuilder$AttributeIcons","putFoodIcons",
                        "(Ljava/util/List;Lnet/minecraft/world/item/ItemStack;)V");
            }
        }
        if(mods!=null)try(var paths=Files.list(Path.of(mods))) {
            for(var path:paths.filter(p->p.toString().endsWith(".jar")).toList())try(var jar=new JarFile(path.toFile())) {
                String quark="org/violetmoon/quark/content/client/tooltip/FoodTooltips";
                if(jar.getJarEntry(quark+".class")!=null) {
                    method(jar,quark,"makeTooltip","(Lorg/violetmoon/zeta/client/event/play/ZGatherTooltipComponents;ZZ)V");
                    var node=new ClassNode();
                    try(var input=jar.getInputStream(jar.getJarEntry(quark+".class"))){new ClassReader(input).accept(node,0);}
                    var target=node.methods.stream().filter(m->m.name.equals("makeTooltip")).findFirst().orElseThrow();
                    check((target.access&org.objectweb.asm.Opcodes.ACC_STATIC)!=0,"Quark provider target static");
                    check(target.instructions.iterator().hasNext(),"Quark tooltip method retains code for icon-only argument patch");
                }
            }
        }
        System.out.println("FoodModeDefaultsTest: "+checks+" checks passed");
    }
    private static void contracts() throws Exception {
        for(String mixin:List.of("AppleSkinNutritionHudMixin","ObscureNutritionIconsMixin")) {
            var node=new ClassNode();
            try(var input=FoodModeDefaultsTest.class.getClassLoader().getResourceAsStream(
                    "com/chi__you/cuisinejournal/mixin/client/"+mixin+".class")) {
                new ClassReader(Objects.requireNonNull(input)).accept(node,0);
            }
            check(node.methods.stream().flatMap(m->{var list=new ArrayList<MethodInsnNode>();
                for(var instruction:m.instructions)if(instruction instanceof MethodInsnNode call)list.add(call);return list.stream();})
                .anyMatch(call->call.name.equals("nutritionDisplayEnabled")),"optional nutrition integration reads shared switch: "+mixin);
        }
    }
    private static void method(JarFile jar,String path,String name,String descriptor) throws Exception {
        var node=new ClassNode();
        try(var input=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(path+".class")))){new ClassReader(input).accept(node,0);}
        check(node.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(descriptor)),"installed optional method matches: "+name);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
