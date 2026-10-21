package com.chi__you.cuisinejournal.client.compat;

import com.chi__you.cuisinejournal.client.journal.StatsSidePanel;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;

/** Lets JEI reserve room for Cuisine Journal's workstation panel. */
@JeiPlugin
public final class CuisineJournalJeiPlugin implements IModPlugin {
    private static mezz.jei.api.runtime.IJeiRuntime runtime;
    private static final java.util.Set<net.minecraft.world.item.Item> hidden = new java.util.HashSet<>();

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value) {
        runtime = value;
        hidden.clear();
        // Compat may have removed these before our plugin receives the runtime callback.
        hidden.addAll(unifiedItems());
        refreshUnifiedItems();
    }

    @Override
    public void onRuntimeUnavailable() { runtime = null; hidden.clear(); }

    private static java.util.Set<net.minecraft.world.item.Item> unifiedItems() {
        var items = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (var id : List.of(com.chi__you.cuisinejournal.recipe.HostItemAvailability.UNITED,
                com.chi__you.cuisinejournal.compat.UnificationRules.HIDDEN)) {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag)
                    .forEach(holder -> items.add(holder.value()));
        }
        return items;
    }

    public static void refreshUnifiedItems() {
        if (runtime == null) return;
        var current = unifiedItems();
        var manager = runtime.getIngredientManager();
        var type = mezz.jei.api.constants.VanillaTypes.ITEM_STACK;
        var present = new java.util.HashSet<net.minecraft.world.item.Item>();
        manager.getAllIngredients(type).forEach(stack -> present.add(stack.getItem()));
        var restored = hidden.stream().filter(item -> !current.contains(item) && !present.contains(item))
                .map(net.minecraft.world.item.ItemStack::new).toList();
        if (!restored.isEmpty()) manager.addIngredientsAtRuntime(type, restored);
        if (!current.isEmpty()) manager.removeIngredientsAtRuntime(type,
                current.stream().map(net.minecraft.world.item.ItemStack::new).toList());
        hidden.clear(); hidden.addAll(current);
    }
    private static final ResourceLocation ID =
            new ResourceLocation("cuisinejournal", "jei_integration");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                var minecraft = Minecraft.getInstance();
                if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
                    return List.of();
                }
                Rect2i area = StatsSidePanel.extraArea(screen.getMenu(),
                        screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(),
                        screen.width, screen.height);
                return area == null ? List.of() : List.of(area);
            }
        });
    }
}
