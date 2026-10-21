package com.chi__you.cuisinejournal.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.UUID;

/** Temporary input provenance is separate from the permanent cooked-food stamp. */
public final class HotSpringSource {
    public static final String KEY="cuisinejournal:hot_spring_input";
    private static final String AMBIGUOUS="cuisinejournal:hot_spring_mixed";
    private HotSpringSource() {}
    public static UUID thrower(ItemEntity entity) {
        if(entity.getPersistentData().getBoolean(AMBIGUOUS))return null;
        CompoundTag saved=new CompoundTag();entity.saveWithoutId(saved);
        return saved.hasUUID("Thrower")?saved.getUUID("Thrower"):null;
    }
    public static void mixed(ItemEntity first,ItemEntity second) {
        if(!Objects.equals(thrower(first),thrower(second))) {
            first.getPersistentData().putBoolean(AMBIGUOUS,true);second.getPersistentData().putBoolean(AMBIGUOUS,true);
        }
    }
    public static void mark(ItemEntity entity) {
        ItemStack stack=entity.getItem();if(stack.isEmpty()||marked(stack))return;
        ItemStack copy=stack.copy();CompoundTag source=new CompoundTag();UUID owner=thrower(entity);
        if(owner!=null)source.putUUID("thrower",owner);copy.getOrCreateTag().put(KEY,source);entity.setItem(copy);
    }
    public static boolean marked(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(KEY,10);}
    public static UUID owner(ItemStack stack){return marked(stack)&&stack.getTag().getCompound(KEY).hasUUID("thrower")?stack.getTag().getCompound(KEY).getUUID("thrower"):null;}
    public static void clean(ItemStack stack){if(marked(stack))stack.removeTagKey(KEY);}
    public static java.util.Set<UUID> contributors(java.util.Collection<UUID> consumed) {
        for(UUID owner:consumed)if(owner==null)return java.util.Set.of();
        return java.util.Set.copyOf(consumed);
    }
    public static void restoreSource(ItemStack rest,ItemStack original) {
        if(!rest.isEmpty()&&marked(original))rest.getOrCreateTag().put(KEY,original.getTag().getCompound(KEY).copy());
    }
}
