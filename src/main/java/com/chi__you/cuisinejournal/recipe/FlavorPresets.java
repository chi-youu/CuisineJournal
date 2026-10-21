package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Data-driven reusable flavor definitions; local edits override packs by stable id. */
public final class FlavorPresets {
    /** Namespace of presets created in game; exported packs keep the same ids. */
    public static final String CUSTOM_NAMESPACE = "cuisinejournal_custom";
    private static final String FILE_NAME = "cuisinejournal-flavors.json";

    /**
     * @param icon item shown beside the flavor; null uses the generic leaf mark (or the
     *             first potion colour when the flavor carries a potion effect)
     * @param group optional folder in the flavor library (for example one per mod); "" = none
     */
    public record Preset(ResourceLocation id,String name,String description,boolean enabled,List<FlavorStage> stages,
                         ResourceLocation icon,ResourceLocation iconEffect,String group) {
        public Preset {
            Objects.requireNonNull(id);name=name==null?id.toString():name;description=description==null?"":description;
            group=group==null?"":group.strip();
            if(name.length()>256||description.length()>4096||group.length()>64)throw new IllegalArgumentException("Flavor text too long");
            stages=List.copyOf(stages);
            if(stages.size()>6||stages.stream().map(FlavorStage::minStars).distinct().count()!=stages.size())
                throw new IllegalArgumentException("Invalid flavor stage list");
        }
        public Preset(ResourceLocation id,String name,String description,boolean enabled,List<FlavorStage> stages) {
            this(id,name,description,enabled,stages,null,null,"");
        }
        public Preset(ResourceLocation id,String name,String description,boolean enabled,List<FlavorStage> stages,ResourceLocation icon) {
            this(id,name,description,enabled,stages,icon,null,"");
        }
        public Preset(ResourceLocation id,String name,String description,boolean enabled,List<FlavorStage> stages,
                      ResourceLocation icon,ResourceLocation iconEffect) {
            this(id,name,description,enabled,stages,icon,iconEffect,"");
        }
        public JsonObject toJson() {
            var json=new JsonObject();json.addProperty("name",name);json.addProperty("description",description);
            json.addProperty("enabled",enabled);if(icon!=null)json.addProperty("icon",icon.toString());
            if(iconEffect!=null)json.addProperty("icon_effect",iconEffect.toString());
            if(!group.isEmpty())json.addProperty("group",group);
            var array=new JsonArray();stages.forEach(stage->array.add(stage.toJson()));json.add("stages",array);return json;
        }
    }
    private static volatile Map<ResourceLocation,Preset> packed=Map.of(),local=Map.of(),current=Map.of();
    private static volatile Path configDirectory;
    private static volatile boolean writable = true;
    public static Runnable rollbackPoint() {
        var oldPacked=packed; var oldLocal=local; var oldCurrent=current;
        return () -> { packed=oldPacked; local=oldLocal; current=oldCurrent; };
    }
    private static final String DIRECTORY="cuisinejournal/flavors";
    private FlavorPresets() {}
    public static Preset get(ResourceLocation id) { return current.get(id); }
    public static Map<ResourceLocation,Preset> all() { return current; }
    public static Map<ResourceLocation,Preset> defaults() { return packed; }
    public static Map<ResourceLocation,Preset> overrides() { return local; }
    public static void clear() {packed=Map.of();local=Map.of();current=Map.of();}
    public static Preset read(ResourceLocation id,JsonObject json) {
        String name=json.has("name")?json.get("name").getAsString():id.toString();
        String description=json.has("description")?json.get("description").getAsString():"";
        if(name.length()>256||description.length()>4096)throw new IllegalArgumentException("Flavor text too long");
        boolean enabled=!json.has("enabled")||json.get("enabled").getAsBoolean();
        List<FlavorStage> stages=json.has("stages")?FlavorStage.read(json.getAsJsonArray("stages")):List.of();
        ResourceLocation icon=json.has("icon")&&!json.get("icon").isJsonNull()?ResourceLocation.tryParse(json.get("icon").getAsString()):null;
        ResourceLocation iconEffect=json.has("icon_effect")&&!json.get("icon_effect").isJsonNull()?ResourceLocation.tryParse(json.get("icon_effect").getAsString()):null;
        String group=json.has("group")&&!json.get("group").isJsonNull()?json.get("group").getAsString():"";
        return new Preset(id,name,description,enabled,stages,icon,iconEffect,group);
    }
    public static void reload(ResourceManager resources,Path configDirectory) {
        FlavorPresets.configDirectory=configDirectory;
        writable = true;
        Map<ResourceLocation,Preset> next=new TreeMap<>();
        resources.listResources(DIRECTORY,id->id.getPath().endsWith(".json")).forEach((path,resource)-> {
            String raw=path.getPath().substring(DIRECTORY.length()+1,path.getPath().length()-5);
            try(Reader reader=resource.openAsReader()) {
                var id=ResourceLocation.fromNamespaceAndPath(path.getNamespace(),raw);
                next.put(id,read(id,JsonParser.parseReader(reader).getAsJsonObject()));
            }catch(Exception failure) {CuisineJournalLog.LOGGER.warn("Skipping flavor preset {}: {}",path,failure.getMessage());}
        });
        Map<ResourceLocation,Preset> overrides=new TreeMap<>();
        Path file=configDirectory.resolve(FILE_NAME);
        if(Files.isRegularFile(file))try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(!root.has("presets")||!root.get("presets").isJsonObject())
                throw new IllegalArgumentException("Missing local presets object");
            if(root.has("presets"))for(var entry:root.getAsJsonObject("presets").entrySet())try {
                var id=ResourceLocation.parse(entry.getKey());overrides.put(id,read(id,entry.getValue().getAsJsonObject()));
            }catch(RuntimeException failure) {
                CuisineJournalLog.LOGGER.warn("Invalid local flavor {}: {}",entry.getKey(),failure.getMessage());
                writable=false;
            }
            if(!writable)com.chi__you.cuisinejournal.data.UnreadableFiles.preserve(file);
        }catch(Exception failure) {
            CuisineJournalLog.LOGGER.warn("Cannot read {}: {}",file,failure.getMessage());
            com.chi__you.cuisinejournal.data.UnreadableFiles.preserve(file);
            writable = false; overrides.clear(); overrides.putAll(local);
        }
        packed=Map.copyOf(next);local=Map.copyOf(overrides);next.putAll(overrides);current=Map.copyOf(next);
    }

    // ------------------------------------------------------------------ in-game edits

    /** 0 = from a data pack, 1 = data pack entry edited locally, 2 = created in game. */
    public static byte origin(ResourceLocation id) {
        if(!local.containsKey(id))return 0;
        return packed.containsKey(id)?(byte)1:(byte)2;
    }

    /** Saves (or replaces) the local copy of one preset; the file is written atomically. */
    public static synchronized boolean putLocal(Preset preset) {
        Map<ResourceLocation,Preset> next=new TreeMap<>(local);next.put(preset.id(),preset);
        return commit(next);
    }

    /** Drops the local copy: an edited pack preset reverts, an in-game preset is deleted. */
    public static synchronized boolean removeLocal(ResourceLocation id) {
        if(!local.containsKey(id))return false;
        Map<ResourceLocation,Preset> next=new TreeMap<>(local);next.remove(id);
        return commit(next);
    }

    public static synchronized boolean clearLocal() { return commit(new TreeMap<>()); }

    /** Replaces the whole local layer (loading a flavor scheme). */
    public static synchronized boolean replaceLocal(Map<ResourceLocation,Preset> presets) {
        return commit(new TreeMap<>(presets));
    }

    /** A fresh in-game id derived from the name; falls back to a counter for non-latin names. */
    public static ResourceLocation newId(String name) {
        String base=name==null?"":name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+","_").replaceAll("^_+|_+$","");
        if(base.isEmpty())base="flavor";
        String candidate=base;int counter=2;
        while(current.containsKey(ResourceLocation.fromNamespaceAndPath(CUSTOM_NAMESPACE,candidate)))candidate=base+"_"+counter++;
        return ResourceLocation.fromNamespaceAndPath(CUSTOM_NAMESPACE,candidate);
    }

    private static boolean commit(Map<ResourceLocation,Preset> next) {
        Path directory=configDirectory;
        if(directory==null||!writable)return false;
        JsonObject root=new JsonObject();JsonObject presets=new JsonObject();
        next.forEach((id,preset)->presets.add(id.toString(),preset.toJson()));
        root.add("presets",presets);
        if(!writeAtomically(directory.resolve(FILE_NAME),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)))
            return false;
        Map<ResourceLocation,Preset> merged=new TreeMap<>(packed);merged.putAll(next);
        local=Map.copyOf(next);current=Map.copyOf(merged);
        return true;
    }

    public static boolean writeAtomically(Path file,String text) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temporary,text,StandardCharsets.UTF_8);
            try {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported) {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException|RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Could not save {}",file,failure);
            return false;
        }
    }
}
