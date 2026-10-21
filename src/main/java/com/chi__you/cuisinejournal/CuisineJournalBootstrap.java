package com.chi__you.cuisinejournal;

import com.chi__you.cuisinejournal.platform.Services;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loader-independent bootstrap.
 *
 * <p>Farmer's Delight is optional. Host-specific adapters must not make its
 * absence disable the journal's other food and recipe support.</p>
 */
public final class CuisineJournalBootstrap {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private CuisineJournalBootstrap() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        CuisineJournalLog.LOGGER.info(
                "{} {} starting on {} {} (Minecraft {})",
                Constants.MOD_NAME,
                Services.PLATFORM.isDevelopmentEnvironment() ? "[dev]" : "",
                Services.PLATFORM.loaderName(),
                Services.PLATFORM.loaderVersion(),
                Services.PLATFORM.minecraftVersion());

        if (!Services.PLATFORM.isModLoaded(Constants.FARMERS_DELIGHT_ID)) {
            CuisineJournalLog.LOGGER.info("Farmer's Delight was not detected; other food recipes remain available.");
        }
    }
}
