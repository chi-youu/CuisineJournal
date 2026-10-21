package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;

/** Client startup installs a read-only projection; core rules never import client caches. */
public interface ClientFoodRuleView {
    boolean flavors();
    boolean nutrition();
    boolean stars();
    boolean nativeEffects();
    boolean craftable(ResourceLocation item);
    ServerConfig.Bonuses vanillaBonuses();
    default com.chi__you.cuisinejournal.skills.SkillPackets.Sync skills() { return null; }

    static ClientFoodRuleView current() { return Holder.current; }
    static void install(ClientFoodRuleView view) { Holder.current = java.util.Objects.requireNonNull(view); }

    final class Holder {
        private static volatile ClientFoodRuleView current = new ClientFoodRuleView() {
            public boolean flavors() { return true; }
            public boolean nutrition() { return true; }
            public boolean stars() { return true; }
            public boolean nativeEffects() { return true; }
            public boolean craftable(ResourceLocation item) { return false; }
            public ServerConfig.Bonuses vanillaBonuses() { return null; }
        };
        private Holder() {}
    }
}
