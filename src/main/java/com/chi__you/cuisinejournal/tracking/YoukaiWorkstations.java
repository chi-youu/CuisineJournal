package com.chi__you.cuisinejournal.tracking;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Both editions retain Java packages; method identities follow the registered host block. */
public final class YoukaiWorkstations {
    private YoukaiWorkstations() {}

    public static String method(BlockEntity owner, String path) {
        return method(BuiltInRegistries.BLOCK.getKey(owner.getBlockState().getBlock()), path).toString();
    }

    public static ResourceLocation method(ResourceLocation block, String path) {
        String namespace = block != null && block.getNamespace().equals("youkaisfeasts")
                ? "youkaisfeasts" : "youkaishomecoming";
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
