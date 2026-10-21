package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import com.chi__you.cuisinejournal.mixin.food.FoodEffectDurationAccess;

public final class CuisineEffects {
    public static final int FROZEN_TICKS = 140;
    private static final ResourceLocation NOURISHMENT = ResourceLocation.parse("farmersdelight:nourishment");

    private CuisineEffects() {}
    public static void register(IEventBus bus) {
        boolean youkai = ModList.get().isLoaded("youkaishomecoming");
        CuisineEffectIds.configure(youkai);
        var effects = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "cuisinejournal");
        effects.register("ice_ice_ice", IceEffect::new);
        if (!youkai) {
            effects.register("mature", MatureEffect::new);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CuisineEffects::onDamage);
        }
        effects.register(bus);
    }

    public static final class IceEffect extends InstantenousMobEffect {
        public IceEffect() { super(MobEffectCategory.NEUTRAL, 0xA4E6F5); }
        @Override public void applyEffectTick(LivingEntity entity, int amplifier) { freeze(entity); }
        @Override public void applyInstantenousEffect(Entity source, Entity indirect, LivingEntity entity,
                                                      int amplifier, double potency) { freeze(entity); }
        private static void freeze(LivingEntity entity) {
            if (entity instanceof Player && !entity.level().isClientSide) entity.setTicksFrozen(FROZEN_TICKS);
        }
    }

    public static final class MatureEffect extends MobEffect {
        public MatureEffect() { super(MobEffectCategory.BENEFICIAL, 0xFFFFFF); }
    }

    /** Matches Youkai 2.7.0's integer reduction and five-second nourishment cost per damage point. */
    public static int matureReduction(float damage, int amplifier, int nourishmentTicks) {
        return Math.min((int)(damage * 0.2F * (amplifier + 1)), nourishmentTicks / 100);
    }

    private static void onDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getAmount() <= 0) return;
        var source = event.getSource();
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        MobEffect mature = BuiltInRegistries.MOB_EFFECT.getOptional(CuisineEffectIds.MATURE).orElse(null);
        MobEffect nourishment = BuiltInRegistries.MOB_EFFECT.getOptional(NOURISHMENT).orElse(null);
        if (mature == null || nourishment == null) return;
        var maturity = entity.getEffect(mature);
        var reserve = entity.getEffect(nourishment);
        if (maturity == null || reserve == null) return;
        int reduction = matureReduction(event.getAmount(), maturity.getAmplifier(), reserve.getDuration());
        if (reduction <= 0) return;
        // Keep the live instance so the food ledger observes a duration debit rather than a dispel.
        if (entity instanceof ServerPlayer player) CuisineFoodEffects.observe(player, CuisineFoodService.state(player));
        ((FoodEffectDurationAccess)(Object)reserve).cuisinejournal$duration(reserve.getDuration() - reduction * 100);
        ((net.minecraft.server.level.ServerLevel)entity.level()).getChunkSource().broadcastAndSend(entity,
                new ClientboundUpdateMobEffectPacket(entity.getId(), reserve));
        if (entity instanceof ServerPlayer player) CuisineFoodEffects.observe(player, CuisineFoodService.state(player));
        event.setAmount(event.getAmount() - reduction);
    }
}
