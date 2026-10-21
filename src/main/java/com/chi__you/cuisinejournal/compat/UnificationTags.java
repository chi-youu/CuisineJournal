package com.chi__you.cuisinejournal.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import java.util.*;

/** Preserves real tag identities and nested branches while redirecting explicit item members. */
public final class UnificationTags {
    private UnificationTags() {}
    public static void apply(Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags, UnificationContent.Plan plan) {
        if (!plan.enabled()) return;
        tags.forEach((tag, entries) -> {
            if (tag.equals(UnificationRules.HIDDEN) || tag.equals(ResourceLocation.parse("kaleidoscope_cookery:united"))) return;
            entries.replaceAll(source -> {
                var entry = source.entry();
                if (entry.isTag() || plan.excluded().contains(entry.getId())) return source;
                var target = plan.replace(entry.getId());
                return target.equals(entry.getId()) ? source : new TagLoader.EntryWithSource(
                        entry.isRequired() ? TagEntry.element(target) : TagEntry.optionalElement(target), source.source());
            });
            entries.removeIf(source -> !source.entry().isTag() && plan.excluded().contains(source.entry().getId()));
        });
        if(plan.winner("cuisinejournal:bell_pepper")!=null)
            for(String tag:List.of("forge:crops/bellpepper","forge:vegetables/bellpepper")){
                var entries=tags.computeIfAbsent(ResourceLocation.parse(tag),unused->new ArrayList<>());
                var target=ResourceLocation.parse(UnificationIngredientAliases.BELL_TAG);
                if(entries.stream().noneMatch(row->row.entry().isTag()&&row.entry().getId().equals(target)))
                    entries.add(new TagLoader.EntryWithSource(TagEntry.optionalTag(target),"cuisinejournal"));
            }
    }
}
