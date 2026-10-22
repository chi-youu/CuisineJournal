package com.chi__you.cuisinejournal.forge;

import com.mojang.blaze3d.platform.InputConstants;
import com.chi__you.cuisinejournal.Constants;
import com.chi__you.cuisinejournal.client.CuisineJournalClient;
import com.chi__you.cuisinejournal.client.hud.CookingExperienceHud;
import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import com.chi__you.cuisinejournal.food.CuisineFoodHud;
import com.chi__you.cuisinejournal.food.CuisineFoodTooltip;
import com.chi__you.cuisinejournal.client.journal.StatsSidePanel;
import com.chi__you.cuisinejournal.client.tooltip.MasteryTooltip;
import com.chi__you.cuisinejournal.food.tooltip.FoodTooltipComponent;
import com.chi__you.cuisinejournal.food.client.tooltip.ClientFoodTooltipComponent;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Forge's client wiring: keybind, HUD overlay, tooltips and the tick pump.
 *
 * <p>Split across the two Forge buses — mod-bus registrations in the nested
 * class, game events out here — and confined to {@link Dist#CLIENT} so a
 * dedicated server never touches any of it.</p>
 */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class ForgeClientBridge {
    static KeyMapping openJournal;

    private ForgeClientBridge() {
    }

    /** The journal key, so the open journal can close itself with the same key. */
    public static KeyMapping openJournalKey() {
        return openJournal;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CuisineFoodClientState.tick();
        com.chi__you.cuisinejournal.client.hud.CuisineRegenHud.tick(net.minecraft.client.Minecraft.getInstance());
        var nutritionPlayer=net.minecraft.client.Minecraft.getInstance().player;
        if(nutritionPlayer!=null)((com.chi__you.cuisinejournal.skills.VanillaSkillNutrition.NutritionOwner)nutritionPlayer.getFoodData()).cuisinejournal$owner(nutritionPlayer);
        CuisineJournalClient.tick();
        if (openJournal != null) {
            while (openJournal.consumeClick()) {
                CuisineJournalClient.openJournal();
            }
        }
    }

    /** Docks the stats panel beside any container screen. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
            StatsSidePanel.render(event.getGuiGraphics(),
                    screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(),
                    screen.width, screen.height, screen.getMenu());
        }
    }

    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        com.chi__you.cuisinejournal.client.tooltip.NativeFoodEffectTooltip.append(event.getItemStack(),event.getToolTip());
        if (!CuisineFoodTooltip.hasPanel(event.getItemStack())) {
            MasteryTooltip.append(event.getItemStack(), event.getToolTip());
        }
        CuisineFoodTooltip.append(event.getItemStack(), event.getToolTip());
        com.chi__you.cuisinejournal.client.tooltip.NutritionTooltip.append(event.getItemStack(), event.getToolTip());
    }

    @SubscribeEvent
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        if (event.getItemStack().isEmpty()) return;
        var iterator = event.getTooltipElements().listIterator();
        while (iterator.hasNext()) {
            Either<FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent> element = iterator.next();
            FormattedText text = element.left().orElse(null);
            if (!(text instanceof net.minecraft.network.chat.Component component)) continue;
            if (CuisineFoodTooltip.PANEL_MARKER.equals(component.getStyle().getInsertion())) {
                iterator.remove();
                for (var row : CuisineFoodTooltip.panel(event.getItemStack())) {
                    iterator.add(Either.right(row));
                }
            } else if (com.chi__you.cuisinejournal.client.tooltip.NutritionTooltip.MARKER.equals(component.getStyle().getInsertion())) {
                iterator.remove();
                var row = com.chi__you.cuisinejournal.client.tooltip.NutritionTooltip.row(event.getItemStack());
                if (row != null) iterator.add(Either.right(row));
            } else if (MasteryTooltip.HINT_MARKER.equals(component.getStyle().getInsertion())) {
                // Same keycap "按住 Shift 查看制作详情" row as the Valheim panel.
                iterator.remove();
                var hint = MasteryTooltip.hint(event.getItemStack());
                if (hint != null) iterator.add(Either.right(hint));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderFoodLevel(RenderGuiOverlayEvent.Pre event) {
        if (CuisineFoodClientState.active()
                && event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        com.chi__you.cuisinejournal.client.hud.CuisineRegenHud.clear();
        com.chi__you.cuisinejournal.client.compat.UnificationDisplayRefresh.clear();
        CuisineFoodClientState.clear();
        CuisineFoodTooltip.clear();
        CuisineJournalClient.onDisconnect();
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Reads the client config; the server config is loaded separately
            // when a world starts.
            event.enqueueWork(CuisineJournalClient::initialize);
        }

        @SubscribeEvent
        public static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(FoodTooltipComponent.TextLine.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.NutritionRow.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.Spacer.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.StatBar.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.NoStackGroup.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.EffectGrid.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.ValueRow.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.Divider.class, ClientFoodTooltipComponent::new);
            event.register(FoodTooltipComponent.HintLine.class, ClientFoodTooltipComponent::new);
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            // No journal item is needed; the keybind is the whole entry point.
            openJournal = new KeyMapping(
                    "cuisinejournal.key.open_journal",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_J,
                    "cuisinejournal.key.category");
            event.register(openJournal);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(),"vanilla_skill_nutrition",
                    (gui,graphics,partialTick,width,height)->com.chi__you.cuisinejournal.client.hud.VanillaNutritionHud.render(gui,graphics));
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "cooking_progress_hud",
                    (gui, graphics, partialTick, width, height) -> CookingExperienceHud.render(graphics));
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "cuisine_food_hud",
                    (gui, graphics, partialTick, width, height) -> CuisineFoodHud.render(gui, graphics, width, height));
        }
    }
}
