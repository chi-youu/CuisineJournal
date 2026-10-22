package com.chi__you.cuisinejournal.skills;

import com.google.gson.*;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.progression.ProductionQuality;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

public final class CookingSkillsTest {
    private static int checks;
    private static final Path ROOT=Path.of("src/main/resources/data/cuisinejournal/puffish_skills/categories/valheim");
    private static JsonObject read(String file)throws Exception{return JsonParser.parseString(Files.readString(ROOT.resolve(file))).getAsJsonObject();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private static void close(double a,double b){check(Math.abs(a-b)<1e-6,"expected "+a+" got "+b);}
    public static void main(String[] args)throws Exception{
        var definitions=read("definitions.json");var nodes=read("skills.json");var connections=read("connections.json");
        check(nodes.size()==68&&definitions.size()==68,"complete authored node set");
        check(!nodes.has("s4_b_side_2")&&!definitions.has("s4_b_side_2"),"duplicate health node removed");
        var merged=definitions.getAsJsonObject("s4_b_side_1");
        check(merged.get("cost").getAsInt()==2&&merged.getAsJsonArray("rewards").get(0).getAsJsonObject()
                .getAsJsonObject("data").getAsJsonObject("effects").get("health").getAsInt()==2,"merged health retains total reward and price");
        vanillaTree(definitions,nodes,connections);
        nutritionHooks();
        Map<String,String> descriptions=Map.of(
                "s1_main_1","生命上限＋1", "s1_main_3","进食时长－5%",
                "s1_main_4","完美率＋1%", "s1_main_5","烹饪速度＋10%",
                "s1_merge_1","食物星级上限＋1", "s1_merge_main_1","食物槽＋1",
                "s2_main_5","保底完美率＋1%", "s2_merge_main_1","临时食物槽＋1");
        descriptions.forEach((id,text)->check(definitions.getAsJsonObject(id).get("description").getAsString().equals(text),"concise description for "+id));
        for(var entry:definitions.entrySet()) {
            var definition=entry.getValue().getAsJsonObject();
            String description=definition.get("description").getAsString();
            check(!description.contains("前置代价")&&!description.contains("相对原始")&&!description.contains("（加算）")
                    &&!description.contains("仍需熟练度"),"no explanatory filler for "+entry.getKey());
            check(definition.getAsJsonArray("rewards").get(0).getAsJsonObject().getAsJsonObject("data").get("id").getAsString()
                    .equals(entry.getKey()),"internal reward identity retained for "+entry.getKey());
        }
        var edges=connections.getAsJsonObject("normal").getAsJsonArray("unidirectional");
        Map<String,List<String>> incoming=new HashMap<>();
        for(var pair:edges){var row=pair.getAsJsonArray();String a=row.get(0).getAsString(),b=row.get(1).getAsString();
            check(nodes.has(a)&&nodes.has(b),"all edges reference existing nodes");incoming.computeIfAbsent(b,k->new ArrayList<>()).add(a);}
        for(int stage=1;stage<=4;stage++){
            check(incoming.get("s"+stage+"_merge_1").equals(List.of("s"+stage+"_a","s"+stage+"_b")),"either entry merges into main path");
            for(String branch:List.of("a","b"))check(incoming.get("s"+stage+"_"+branch+"_side_1").equals(List.of("s"+stage+"_"+branch)),"side route requires its own entry");
        }
        var exclusions=connections.getAsJsonObject("exclusive").getAsJsonArray("bidirectional");
        check(exclusions.size()==5,"four forks plus endings are exclusive");
        for(int stage=1;stage<=4;stage++){
            int[] costs=new int[2];
            for(int side=0;side<2;side++){
                String prefix="s"+stage+"_"+(side==0?"a":"b");
                for(var e:definitions.entrySet())if(e.getKey().equals(prefix)||e.getKey().startsWith(prefix+"_side_"))costs[side]+=e.getValue().getAsJsonObject().get("cost").getAsInt();
            }
            check(costs[0]==costs[1],"paired routes have identical total cost at stage "+stage);
        }
        for(int mask=0;mask<32;mask++){
            var effects=new ArrayList<CompoundTag>();int cost=0;
            Set<String> purchased=new HashSet<>();
            for(var e:definitions.entrySet()){
                String id=e.getKey();boolean take=true;
                for(int stage=1;stage<=4;stage++)if(id.startsWith("s"+stage+"_a")||id.startsWith("s"+stage+"_b"))
                    take=id.startsWith("s"+stage+"_"+(((mask>>(stage-1))&1)==0?"a":"b"));
                if(id.startsWith("ending_"))take=id.equals((mask&16)==0?"ending_capacity":"ending_speed");
                if(!take)continue;
                purchased.add(id);var def=e.getValue().getAsJsonObject();cost+=def.get("cost").getAsInt();
                var effect=new CompoundTag();def.getAsJsonArray("rewards").get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("effects")
                        .entrySet().forEach(v->effect.putDouble(v.getKey(),v.getValue().getAsDouble()));effects.add(effect);
            }
            check(cost==92,"all complete legal builds cost 92 points");
            for(var id:purchased){var parents=incoming.getOrDefault(id,List.of());check(parents.isEmpty()||parents.stream().anyMatch(purchased::contains),"purchased node has reachable parent");}
            var profile=new CulinaryProfile(new UUID(0,mask+1));profile.setSkillsActive(true);profile.setSkillBonuses(CookingSkills.sum(effects));
            close(5,CookingSkills.starCap(profile));
            close((mask&2)==0?((mask&16)==0?2:2.5):((mask&16)==0?1.5:2),1+CookingSkills.value(profile,"speed"));
            check(CookingSkills.value(profile,"health")+10>=6,"capstone retains at least three hearts in full builds");
            close(((mask&1)==0?13:10)+((mask&8)==0?0:1)-((mask&16)==0?7:0),5+CookingSkills.value(profile,"health")/2);
            close(((mask&1)==0?.95:.8)+((mask&4)==0?-.25:.10),CookingSkills.eatMultiplier(profile));
            close(((mask&8)==0?4:5)+((mask&16)==0?1:0),2+CookingSkills.value(profile,"ordinary"));
            close((mask&8)==0?2:1,CookingSkills.value(profile,"temporary"));
            close((mask&8)==0?20:5,5+CookingSkills.value(profile,"temporary_seconds"));
            var round=CulinaryProfile.load(profile.playerId(),profile.save());check(round.skillBonuses().equals(profile.skillBonuses())&&round.skillsActive(),"offline production bonuses persist");
            double chance=ProductionQuality.chance(.08+CookingSkills.value(profile,"perfect"),false,64,
                    CookingSkills.minimum(profile,.01),CookingSkills.penalty(profile));
            close(CookingSkills.minimum(profile,.01),chance);
        }
        check(com.chi__you.cuisinejournal.mastery.CategoryMastery.rankCap(0,10)==1,"no discovered foods still permits one-star cap");
        close(0,CookingSkills.shieldDamage(4,3));close(1,CookingSkills.shieldRemaining(4,3));
        close(3,CookingSkills.shieldDamage(4,7));close(0,CookingSkills.shieldRemaining(4,7));
        var profile=new CulinaryProfile(new UUID(9,9));var bonus=new CompoundTag();bonus.putDouble("stars",1);bonus.putDouble("health",3);
        profile.setSkillBonuses(bonus);profile.setSkillsActive(true);close(2,CookingSkills.starCap(profile));
        close(3,ProductionQuality.stars(CookingSkills.starCap(profile),true));
        check(new com.chi__you.cuisinejournal.culinary.MealStamp(3,true,5,null,"").effectiveStars()==4,"today and perfect bypass base skill ceiling");
        profile.setSkillsActive(false);close(5,CookingSkills.starCap(profile));close(0,CookingSkills.value(profile,"health"));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var sync=new SkillPackets.Sync(true,3,.7,.04,.02,.002,4);
        try{sync.write(buffer);check(SkillPackets.Sync.read(buffer).equals(sync)&&buffer.readableBytes()==0,"skill projection round trip");}finally{buffer.release();}
        check(incoming.get("s4_a_side_3").equals(List.of("s4_a_side_2")),"second temporary slot needs half-duration penalty");
        check(incoming.get("s4_b_side_4").equals(List.of("s4_b_side_3")),"fifth ordinary slot needs temporary penalty");
        hostParser();
        System.out.println("CookingSkillsTest: "+checks+" checks passed (Skills "
                +System.getProperty("cuisinejournal.skillsVersion","classpath default")+")");
    }
    private static void hostParser() throws Exception {
        String expectedVersion=System.getProperty("cuisinejournal.skillsVersion");
        if(expectedVersion!=null){
            var location=Path.of(net.puffish.skillsmod.SkillsMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            try(var jar=new java.util.jar.JarFile(location.toFile())){
                String metadata;try(var input=jar.getInputStream(jar.getJarEntry("META-INF/mods.toml"))){metadata=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
                var version=java.util.regex.Pattern.compile("(?m)^\s*version\s*=\s*\"([^\"]+)\"").matcher(metadata);
                check(version.find()&&version.group(1).equals(expectedVersion),"test runs actual Skills "+expectedVersion+", not the other classpath version");
            }
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        // Standalone JavaExec has no Forge launcher event transformation.
        var bootstrap=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);bootstrap.setBoolean(null,true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        var type=net.puffish.skillsmod.SkillsMod.class;
        var field=type.getDeclaredField("instance");field.setAccessible(true);Object old=field.get(null);
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var platform=new net.puffish.skillsmod.server.setup.ServerPlatform(){
            public boolean isFakePlayer(net.minecraft.server.level.ServerPlayer player){return false;}
            public boolean isModLoaded(String id){return true;}
        };
        field.set(null,constructor.newInstance(Path.of("build/skills-parser-test"),null,platform,null));
        try {
            SkillsIntegration.installRewards();
            var context=new net.puffish.skillsmod.api.config.ConfigContext(){
                public net.minecraft.server.MinecraftServer getServer(){return null;}
                public void emitWarning(String warning){throw new AssertionError(warning);}
            };
            var base=ROOT.getParent().getParent();
            var reader=new net.puffish.skillsmod.config.reader.ConfigReader(){
                public net.puffish.skillsmod.api.util.Result<net.puffish.skillsmod.api.json.JsonElement,net.puffish.skillsmod.api.util.Problem> read(Path file){
                    return net.puffish.skillsmod.api.json.JsonElement.parseFile(base.resolve(file),net.puffish.skillsmod.api.json.JsonPath.create(file.toString()));
                }
                public boolean exists(Path file){return Files.exists(base.resolve(file));}
            };
            var path=net.puffish.skillsmod.api.json.JsonPath.create("cuisinejournal:puffish_skills/config.json");
            net.puffish.skillsmod.api.json.JsonElement entry;
            try(var input=Objects.requireNonNull(CookingSkillsTest.class.getClassLoader().getResourceAsStream(
                    "data/cuisinejournal/puffish_skills/config.json"));
                var text=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)) {
                entry=net.puffish.skillsmod.api.json.JsonElement.parseReader(text,path).getSuccess().orElseThrow();
            }
            var pack=net.puffish.skillsmod.config.PackConfig.parse("cuisinejournal",entry,context);
            check(pack.getSuccess().isPresent(),"actual Skills data-pack entry parser accepts shipped config: "+pack.getFailure());
            check(pack.getSuccess().orElseThrow().categories().equals(List.of("valheim","vanilla")),"data-pack entry enables both mode categories");
            var invalid=net.puffish.skillsmod.api.json.JsonElement.parseString(
                    "{\"version\":3,\"categories\":[\"valheim\"],\"show_warnings\":true}",path).getSuccess().orElseThrow();
            check(net.puffish.skillsmod.config.PackConfig.parse("cuisinejournal",invalid,context).getSuccess().isEmpty(),
                    "mod-config-only show_warnings is rejected at data-pack entry, before category parsing");
            var categories=reader.readCategories("cuisinejournal",pack.getSuccess().orElseThrow().categories(),0,context);
            check(categories.getSuccess().isPresent(),"data-pack category list resolves all shipped definitions: "+categories.getFailure());
            var result=reader.readCategory("cuisinejournal","valheim",0,context);
            check(result.getSuccess().isPresent(),"actual host parser accepts tree: "+result.getFailure());
            var config=result.getSuccess().orElseThrow();
            var vanillaResult=reader.readCategory("cuisinejournal","vanilla",1,context);
            check(vanillaResult.getSuccess().isPresent(),"actual host parser accepts Vanilla tree: "+vanillaResult.getFailure());
            var vanillaConfig=vanillaResult.getSuccess().orElseThrow();
            var vanillaData=net.puffish.skillsmod.server.data.CategoryData.create(vanillaConfig.general());
            vanillaData.setPoints(net.minecraft.resources.ResourceLocation.parse("cuisinejournal:cooking_level"),100);
            vanillaData.unlockSkill("s1_main_1");vanillaData.lock();vanillaData.unlock();
            check(vanillaData.getUnlockedSkillIds().contains("s1_main_1")&&vanillaData.getSpentPoints(vanillaConfig)==1,
                    "mode hiding preserves purchased nodes and spent points");
            var savedVanilla=vanillaData.writeNbt(new CompoundTag());
            var restoredVanilla=net.puffish.skillsmod.server.data.CategoryData.read(savedVanilla);
            var separateValheim=net.puffish.skillsmod.server.data.CategoryData.create(config.general());
            check(restoredVanilla.getUnlockedSkillIds().contains("s1_main_1")&&separateValheim.getUnlockedSkillIds().isEmpty(),
                    "mode purchase records persist independently");
            var data=net.puffish.skillsmod.server.data.CategoryData.create(config.general());
            data.setPoints(net.minecraft.resources.ResourceLocation.parse("cuisinejournal:cooking_level"),100);
            var first=config.skills().getById("s1_main_1").orElseThrow();
            check(data.canUnlockSkill(config,first,false),"real host accepts only root initially");
            var locked=config.skills().getById("s1_merge_1").orElseThrow();
            check(!data.canUnlockSkill(config,locked,false),"cannot jump directly to merge");
            for(int i=1;i<=5;i++)data.unlockSkill("s1_main_"+i);
            data.unlockSkill("s1_a");
            check(data.canUnlockSkill(config,locked,false),"one entry unlocks merge");
            check(!data.canUnlockSkill(config,config.skills().getById("s1_b").orElseThrow(),false),"other fork is excluded");
            data.unlockSkill("s1_merge_1");
            check(!data.canUnlockSkill(config,config.skills().getById("s1_b_side_1").orElseThrow(),false),"merge cannot backtrack into unchosen side");
            data.resetSkills();check(data.getSpentPoints(config)==0&&data.getPointsTotal()==100,"host reset refunds spent points without adding sources");
        } finally {field.set(null,old);}
    }
    private static void vanillaTree(JsonObject original,JsonObject nodes,JsonObject connections)throws Exception {
        Path vanilla=ROOT.resolveSibling("vanilla");
        JsonObject defs=JsonParser.parseString(Files.readString(vanilla.resolve("definitions.json"))).getAsJsonObject();
        check(JsonParser.parseString(Files.readString(vanilla.resolve("skills.json"))).equals(nodes),"mode trees share coordinates and stable skill ids");
        check(JsonParser.parseString(Files.readString(vanilla.resolve("connections.json"))).equals(connections),"mode trees share connectivity");
        check(defs.size()==68,"complete Vanilla node set");
        for(var entry:defs.entrySet()){
            var effect=entry.getValue().getAsJsonObject().getAsJsonArray("rewards").get(0).getAsJsonObject().getAsJsonObject("data");
            check(effect.get("tree").getAsString().equals("vanilla"),"Vanilla reward namespace "+entry.getKey());
            for(String removed:List.of("ordinary","temporary","temporary_seconds","duration"))
                check(!effect.getAsJsonObject("effects").has(removed),"no inactive Valheim effect in Vanilla "+entry.getKey());
        }
        for(int mask=0;mask<32;mask++){
            int cost=0;double hunger=0,saturation=0,regen=0,half=0,doubled=0;
            for(var entry:defs.entrySet()){
                String id=entry.getKey();boolean take=true;
                for(int stage=1;stage<=4;stage++)if(id.startsWith("s"+stage+"_a")||id.startsWith("s"+stage+"_b"))
                    take=id.startsWith("s"+stage+"_"+(((mask>>(stage-1))&1)==0?"a":"b"));
                if(id.startsWith("ending_"))take=id.equals((mask&16)==0?"ending_capacity":"ending_speed");
                if(!take)continue;
                var def=entry.getValue().getAsJsonObject();cost+=def.get("cost").getAsInt();
                var effects=def.getAsJsonArray("rewards").get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("effects");
                hunger+=number(effects,"hunger_cap");saturation+=number(effects,"saturation_cap");regen+=number(effects,"saturation_regen");
                half+=number(effects,"health_multiplier");doubled+=number(effects,"regen_double");
            }
            check(cost==94,"Vanilla complete routes have matching 94 point cost");
            close(16,hunger);close(16,saturation);
            close(36,VanillaSkillNutrition.cap(hunger));close(36,VanillaSkillNutrition.cap(saturation));
            close((mask&8)==0?.4:.1,regen);
            close((mask&16)==0?-.5:0,half);close((mask&16)==0?1:0,doubled);
            close((1+regen)*((mask&16)==0?2:1),VanillaSkillNutrition.recovery(regen,doubled>0));
        }
        close(20,VanillaSkillNutrition.cap(0));close(1,VanillaSkillNutrition.recovery(0,false));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var state=new SkillPackets.Sync(true,4,.8,.02,.02,.001,4,1,36,36);
        try{state.write(buffer);check(SkillPackets.Sync.read(buffer).equals(state),"expanded caps round trip");
            var nutrition=new SkillPackets.Nutrition(34,32.5F);nutrition.write(buffer);
            check(SkillPackets.Nutrition.read(buffer).equals(nutrition),"actual expanded nutrition round trip");}finally{buffer.release();}
    }
    private static double number(JsonObject value,String key){return value.has(key)?value.get(key).getAsDouble():0;}
    private static void nutritionHooks() throws Exception {
        var node=new org.objectweb.asm.tree.ClassNode();
        try(var stream=CookingSkillsTest.class.getClassLoader().getResourceAsStream("net/minecraft/world/food/FoodData.class")){
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(node,0);
        }
        var tick=node.methods.stream().filter(m->m.name.equals("tick")).findFirst().orElseThrow();
        var heals=new ArrayList<org.objectweb.asm.tree.MethodInsnNode>();int fullConstants=0;
        for(var instruction:tick.instructions){
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("heal"))heals.add(call);
            if(instruction instanceof org.objectweb.asm.tree.IntInsnNode value&&value.operand==20)fullConstants++;
        }
        check(heals.size()==2&&heals.get(0).desc.equals("(F)V"),"saturation heal hook targets first of exactly two natural healing calls");
        check(fullConstants==1,"full-hunger regeneration cap has exactly one injection target");
        var eat=node.methods.stream().filter(m->m.name.equals("eat")&&m.desc.equals("(IF)V")).findFirst().orElseThrow();
        int caps=0,floatMin=0;
        for(var instruction:eat.instructions){
            if(instruction instanceof org.objectweb.asm.tree.IntInsnNode value&&value.operand==20)caps++;
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals("java/lang/Math")&&call.desc.equals("(FF)F"))floatMin++;
        }
        check(caps==1&&floatMin==1,"nutrition clamp hooks have unique host targets");
        // Reproduce the host's constant rewrite: our argument hook must survive it.
        for(var instruction:eat.instructions.toArray())
            if(instruction instanceof org.objectweb.asm.tree.IntInsnNode value&&value.operand==20){
                eat.instructions.insert(instruction,new org.objectweb.asm.tree.MethodInsnNode(
                        org.objectweb.asm.Opcodes.INVOKESTATIC,"test/TwilightCap","food","(I)I",false));
            }
        long intClamps=0;
        for(var instruction:eat.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                &&call.owner.equals("java/lang/Math")&&call.name.equals("min")&&call.desc.equals("(II)I"))intClamps++;
        check(intClamps==1,"food clamp argument remains injectable after Twilight rewrites constant");
        var hook=new org.objectweb.asm.tree.ClassNode();
        try(var input=CookingSkillsTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/mixin/food/FoodDataMixin.class")){
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(hook,0);
        }
        var capHook=hook.methods.stream().filter(m->m.name.equals("cuisinejournal$hungerCap")).findFirst().orElseThrow();
        check(capHook.visibleAnnotations.stream().anyMatch(a->a.desc.endsWith("/ModifyArg;")),"food cap no longer competes for ModifyConstant");
        String hostJar=System.getenv("CUISINEJOURNAL_TWILIGHT_JAR");
        if(hostJar!=null)try(var jar=new java.util.jar.JarFile(hostJar)) {
            var host=new org.objectweb.asm.tree.ClassNode();
            try(var input=jar.getInputStream(jar.getJarEntry("xy177/twilightsparksdelight/common/food/ExtendedFoodProgression.class"))){
                new org.objectweb.asm.ClassReader(input).accept(host,0);
            }
            for(String method:List.of("getMaxFood","getMaxSaturation"))
                check(host.methods.stream().anyMatch(m->m.name.equals(method)&&m.desc.equals(
                        "(Lnet/minecraft/world/entity/player/Player;)"+(method.equals("getMaxFood")?"I":"F"))),"installed Twilight cap API "+method);
            var hostMixin=new org.objectweb.asm.tree.ClassNode();
            try(var input=jar.getInputStream(jar.getJarEntry("xy177/twilightsparksdelight/mixin/ExtendedFoodDataMixin.class"))){
                new org.objectweb.asm.ClassReader(input).accept(hostMixin,0);
            }
            check(hostMixin.methods.stream().anyMatch(m->m.name.equals("tsd$foodCap")&&m.desc.equals("(I)I")),"installed host constant modifier confirms reproduced conflict");
            var bind=hostMixin.methods.stream().filter(m->m.name.equals("tsd$setOwner")).findFirst().orElseThrow();
            check(java.util.Arrays.stream(bind.instructions.toArray()).anyMatch(i->i instanceof org.objectweb.asm.tree.MethodInsnNode call
                    &&call.owner.equals("xy177/twilightsparksdelight/common/food/ExtendedFoodProgression")&&call.name.equals("getMaxFood")),
                    "installed host runtime clamp reads bridged cap API");
        }
        close(36,VanillaSkillNutrition.higherCap(36,24));
        close(40,VanillaSkillNutrition.higherCap(36,40));
        close(40,VanillaSkillNutrition.higherCap(20,40));
        close(20,VanillaSkillNutrition.higherCap(20,20));
        close(36,VanillaSkillNutrition.higherCap(36,20));
        var profile=new CulinaryProfile(new UUID(34,56));profile.setSkillMode("vanilla");
        check(CulinaryProfile.load(profile.playerId(),profile.save()).skillMode().equals("vanilla"),"offline skill aggregate remembers its mode");
        for(double efficiency:new double[]{0,.1,.4}){
            float saturation=6,base=saturation/6;
            close(base*(1+efficiency),base*VanillaSkillNutrition.recovery(efficiency,false));
            close(base*(1+efficiency)*2,base*VanillaSkillNutrition.recovery(efficiency,true));
        }
    }
}
