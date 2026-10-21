package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(TagLoader.class)
public abstract class UnificationTagMixin {
    @Shadow @Final private String directory;
    @Inject(method = "load", at = @At("RETURN"))
    private void cuisinejournal$items(ResourceManager resources,
            CallbackInfoReturnable<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> cir) {
        if (!directory.equals("tags/items")) return;
        UnificationTags.apply(cir.getReturnValue(), UnificationContent.read(resources));
    }
}
