package com.chi__you.cuisinejournal.compat;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.config.*;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Small generated data pack containing only explicitly declared JSON overrides. */
public final class UnificationRulePack extends AbstractPackResources {
    public static final String ID = "cuisinejournal:unification";
    private final Map<ResourceLocation, byte[]> files;
    UnificationRulePack(Map<ResourceLocation, JsonElement> resources) {
        super(ID, true);
        var result = new LinkedHashMap<ResourceLocation, byte[]>();
        resources.forEach((id, json) -> result.put(id, json.toString().getBytes(StandardCharsets.UTF_8)));
        files = Map.copyOf(result);
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA)
            event.addRepositorySource(consumer -> consumer.accept(create(configuredMode())));
    }

    public static Pack create(boolean enabled) {
        return lazy(() -> generate(enabled));
    }
    static Pack lazy(java.util.function.Supplier<Map<ResourceLocation,JsonElement>> source) {
        return Pack.readMetaAndCreate(ID, Component.literal("Cuisine Journal Unification"), true,
                ignored -> new UnificationRulePack(source.get()), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
    }

    private static Map<ResourceLocation, JsonElement> generate(boolean enabled) {
        var document = new JsonObject();
        try (var input = UnificationRulePack.class.getResourceAsStream("/cuisinejournal/unification.json")) {
            if (input != null) document = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException failure) { CuisineJournalLog.LOGGER.warn("Cannot read bundled unification rules", failure); }
        Path local = FMLPaths.CONFIGDIR.get().resolve("cuisinejournal/unification.json");
        if (Files.isRegularFile(local)) try {
            for (var entry : JsonParser.parseString(Files.readString(local)).getAsJsonObject().entrySet())
                document.add(entry.getKey(), entry.getValue());
        } catch (IOException | RuntimeException failure) { CuisineJournalLog.LOGGER.warn("Cannot read {}", local, failure); }
        var result = UnificationRules.compile(document, enabled, BuiltInRegistries.ITEM::containsKey,
                mod -> ModList.get().isLoaded(mod));
        var content = UnificationContent.compile(document, enabled, BuiltInRegistries.ITEM::containsKey,
                mod -> ModList.get().isLoaded(mod));
        var generated = new LinkedHashMap<>(result.resources());
        generated.put(UnificationContent.POLICY, UnificationContent.encode(content));
        var hidden = generated.get(ResourceLocation.parse("cuisinejournal:tags/items/unified_hidden.json")).deepCopy().getAsJsonObject();
        var hiddenValues = hidden.getAsJsonArray("values");
        content.replacements().keySet().forEach(item->{var value=new JsonPrimitive(item.toString());if(!hiddenValues.contains(value))hiddenValues.add(value);});
        content.excluded().forEach(item -> hiddenValues.add(item.toString()));
        content.disabledPlants().stream().map(ResourceLocation::parse).filter(BuiltInRegistries.ITEM::containsKey)
                .forEach(item -> hiddenValues.add(item.toString()));
        generated.put(ResourceLocation.parse("cuisinejournal:tags/items/unified_hidden.json"), hidden);
        result.problems().forEach(problem -> CuisineJournalLog.LOGGER.warn("Unification rule skipped: {}", problem));
        CuisineJournalLog.LOGGER.info("Unification {}: {} replacements, {} resources", enabled, result.replacements().size(), result.resources().size());
        return generated;
    }

    private static boolean configuredMode() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("cuisinejournal-server.toml");
        try { return Files.isRegularFile(file) && Toml.read(Files.readString(file), ServerConfig.class).compatibility.unifiedMode; }
        catch (IOException | RuntimeException failure) {
            CuisineJournalLog.LOGGER.warn("Cannot read unification mode; keeping rules disabled", failure); return false;
        }
    }

    @Override public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && path[0].equals("pack.mcmeta")) return () -> new ByteArrayInputStream(
                "{\"pack\":{\"pack_format\":15,\"description\":\"Cuisine Journal Unification\"}}".getBytes(StandardCharsets.UTF_8));
        return null;
    }
    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        byte[] bytes = type == PackType.SERVER_DATA ? files.get(id) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }
    @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) return;
        files.forEach((id, bytes) -> { if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix + "/"))
            output.accept(id, () -> new ByteArrayInputStream(bytes)); });
    }
    @Override public Set<String> getNamespaces(PackType type) {
        if (type != PackType.SERVER_DATA) return Set.of();
        var namespaces = new HashSet<String>(); files.keySet().forEach(id -> namespaces.add(id.getNamespace())); return Set.copyOf(namespaces);
    }
    @Override public void close() {}
}
