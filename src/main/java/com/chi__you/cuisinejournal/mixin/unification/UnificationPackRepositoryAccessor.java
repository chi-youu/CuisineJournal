package com.chi__you.cuisinejournal.mixin.unification;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;

@Mixin(PackRepository.class)
public interface UnificationPackRepositoryAccessor {
    @Accessor("sources") Set<RepositorySource> cuisinejournal$sources();
}
