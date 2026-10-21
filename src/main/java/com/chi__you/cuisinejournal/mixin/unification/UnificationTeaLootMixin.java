package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class UnificationTeaLootMixin {
    @Inject(method="scanDirectory",at=@At("RETURN"))
    private static void cuisinejournal$tea(ResourceManager resources,String directory,Gson gson,
                                         Map<ResourceLocation,JsonElement> values,CallbackInfo ci){
        if(directory.equals("loot_tables"))UnificationTeaBlocks.loot(values,UnificationContent.read(resources));
    }
}
