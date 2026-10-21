package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.SquidLootCompat;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SquidLootCompatMixin {
    @Inject(method = "scanDirectory", at = @At("RETURN"))
    private static void cuisinejournal$squidLoot(ResourceManager resources, String directory, Gson gson,
                                                Map<ResourceLocation, JsonElement> values, CallbackInfo ci) {
        if (!directory.equals("loot_tables")) return;
        int changed = SquidLootCompat.apply(values, resources, ModList.get()::isLoaded);
        if (changed > 0) com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.info(
                "Repaired {} conflicting squid loot tables", changed);
    }
}
