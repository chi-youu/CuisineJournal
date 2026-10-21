package com.chi__you.cuisinejournal.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** These overrides must load after mod resources, not inside their merged default pack. */
@Mod.EventBusSubscriber(modid = "cuisinejournal", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BrazilianDinnerCompatPack extends AbstractPackResources {
    public static final String ID = "cuisinejournal:brazilian_dinner_fix";
    private static final Set<ResourceLocation> FILES = Set.of(
            ResourceLocation.parse("braziliandelight:recipes/brazilian_dinner.json"),
            ResourceLocation.parse("deltaboxlib:tags/items/fried_egg.json"));

    public BrazilianDinnerCompatPack() { super(ID, true); }

    @SubscribeEvent
    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded("braziliandelight")) return;
        Pack pack = Pack.readMetaAndCreate(ID, Component.literal("Cuisine Journal Brazilian Dinner Fix"), true,
                ignored -> new BrazilianDinnerCompatPack(), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    @Override public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && path[0].equals("pack.mcmeta")) return () -> new ByteArrayInputStream(
                "{\"pack\":{\"pack_format\":15,\"description\":\"Brazilian dinner ingredient correction\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        return null;
    }

    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.SERVER_DATA || !FILES.contains(location)) return null;
        return () -> {
            InputStream stream = BrazilianDinnerCompatPack.class.getResourceAsStream(
                    "/data/" + location.getNamespace() + "/" + location.getPath());
            if (stream == null) throw new FileNotFoundException(location.toString());
            return stream;
        };
    }

    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) return;
        for (ResourceLocation file : FILES)
            if (file.getNamespace().equals(namespace) && (path.isEmpty() || file.getPath().startsWith(path + "/")))
                output.accept(file, getResource(type, file));
    }

    @Override public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of("braziliandelight", "deltaboxlib") : Set.of();
    }
    @Override public void close() {}
}
