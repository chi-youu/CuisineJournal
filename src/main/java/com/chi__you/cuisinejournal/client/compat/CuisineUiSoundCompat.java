package com.chi__you.cuisinejournal.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional bridge for the inventory open/close sounds used by sound mods. */
public final class CuisineUiSoundCompat {
    private static final String EXTRA_SOUNDS = "extrasounds";
    private static final String SOUNDS = "sounds";
    private static Object scrollSound;
    private static Method playScroll;
    private static boolean scrollResolved;

    private CuisineUiSoundCompat() {
    }

    public static void playOpen() {
        play(true);
    }

    public static void playClose() {
        play(false);
    }

    public static void playOpenMessage() {
        if (!ModList.get().isLoaded(EXTRA_SOUNDS)) return;
        SoundEvent event = SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(EXTRA_SOUNDS, "chat.message"));
        float volume = 1.0F;
        try {
            Class<?> entry = Class.forName("dev.arbor.extrasoundsnext.sounds.SoundEntry");
            Class<?> config = Class.forName("dev.arbor.extrasoundsnext.sounds.VolumeConfig");
            Class<?> mixers = Class.forName("dev.arbor.extrasoundsnext.sounds.Mixers");
            if (!Boolean.TRUE.equals(config.getMethod("isSoundEnabled", entry)
                    .invoke(null, entry.getField("CHAT").get(null)))) return;
            volume = ((Number) config.getMethod("getVolume", mixers)
                    .invoke(null, mixers.getField("MASTER").get(null))).floatValue();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Keep the optional sound usable if its configuration API changes.
        }
        // The screen is already on the client thread; no tick delay or ExtraSounds task queue.
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, volume));
    }

    public static void click() {
        event("generic.click", SoundEvents.UI_BUTTON_CLICK.value());
    }

    public static void page() {
        event("item.category.paper", SoundEvents.BOOK_PAGE_TURN);
    }

    public static void toggle(boolean enabled) {
        event(enabled ? "action.repeater.add" : "action.repeater.reset",
                enabled ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF);
    }

    public static void selectAll() {
        event("item.pickup_all", SoundEvents.ITEM_PICKUP);
    }

    public static void scroll(double previous, double current) {
        if (previous == current || ModList.get() == null || !ModList.get().isLoaded(EXTRA_SOUNDS)) return;
        try {
            if (!scrollResolved) {
                scrollResolved = true;
                Class<?> type = Class.forName("dev.arbor.extrasoundsnext.sounds.ScrollSound");
                scrollSound = type.getConstructor().newInstance();
                playScroll = type.getMethod("play");
            }
            if (playScroll != null) playScroll.invoke(scrollSound);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Older ExtraSounds versions can lack this optional scrolling API.
            playScroll = null;
        }
    }

    public static void item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) { click(); return; }
        if (ModList.get().isLoaded(EXTRA_SOUNDS)) {
            try {
                Class<?> manager = Class.forName("dev.arbor.extrasoundsnext.sounds.SoundManager");
                Class<?> type = Class.forName("dev.arbor.extrasoundsnext.sounds.SoundType");
                manager.getMethod("playSound", ItemStack.class, type)
                        .invoke(null, stack, type.getField("PICKUP").get(null));
                return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Keep the journal usable when an optional sound API changes.
            }
        }
        vanilla(SoundEvents.UI_BUTTON_CLICK.value());
    }

    private static void event(String path, SoundEvent fallback) {
        if (ModList.get().isLoaded(EXTRA_SOUNDS) && playExtraEvent(
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(EXTRA_SOUNDS, path)))) return;
        vanilla(fallback);
    }

    private static void vanilla(SoundEvent event) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F));
    }

    private static void play(boolean open) {
        if (ModList.get().isLoaded(EXTRA_SOUNDS) && playExtraSounds(open)) {
            return;
        }
        if (ModList.get().isLoaded(SOUNDS) && playSounds(open)) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(open ? SoundEvents.UI_TOAST_IN : SoundEvents.UI_TOAST_OUT, 1.0F));
    }

    private static boolean playExtraSounds(boolean open) {
        return playExtraSounds(open ? "INVENTORY_OPEN" : "INVENTORY_CLOSE");
    }

    private static boolean playExtraSounds(String field) {
        try {
            Class<?> soundsClass = Class.forName("dev.arbor.extrasoundsnext.sounds.Sounds");
            SoundEvent event = (SoundEvent) soundsClass
                    .getField(field).get(null);
            return playExtraEvent(event);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean playExtraEvent(SoundEvent event) {
        try {
            Class<?> soundManager = Class.forName("dev.arbor.extrasoundsnext.sounds.SoundManager");
            Class<?> mixers = Class.forName("dev.arbor.extrasoundsnext.sounds.Mixers");
            Method play = soundManager.getMethod("playSound", SoundEvent.class, float.class,
                    Array.newInstance(mixers, 0).getClass());
            play.invoke(null, event, 1.0F, Array.newInstance(mixers, 0));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean playSounds(boolean open) {
        try {
            Class<?> configClass = Class.forName("dev.imb11.sounds.config.SoundsConfig");
            Class<?> uiConfigClass = Class.forName("dev.imb11.sounds.config.UISoundsConfig");
            Method get = configClass.getMethod("get", Class.class);
            Object uiConfig = get.invoke(null, uiConfigClass);
            Field soundField = uiConfigClass.getField(open
                    ? "inventoryOpenSoundEffect" : "inventoryCloseSoundEffect");
            Object configuredSound = soundField.get(uiConfig);
            Method play = configuredSound.getClass().getMethod("playDynamicSound", SoundEvent.class);
            play.invoke(configuredSound, open ? SoundEvents.UI_TOAST_IN : SoundEvents.UI_TOAST_OUT);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
