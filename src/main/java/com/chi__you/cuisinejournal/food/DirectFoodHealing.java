package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Display-only sum of Cuisine Journal bonuses and verified unconditional host healing. */
public final class DirectFoodHealing {
    private DirectFoodHealing() {}

    public static float points(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return CakeSliceHealing.points(stack) + nativePoints(stack);
    }

    private static float nativePoints(ItemStack stack) {
        var item = stack.getItem();
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(item,"lekavar.lma.drinkbeer.items.MixedBeerBlockItem"))return DrinkBeerFoodCompat.healing(stack);
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(item,"com.sihenzhang.crockpot.item.food.CrockPotFoodItem")){
            Object props=HostReflection.field(item,"foodProperties"),heal=HostReflection.field(props,"heal");
            if(heal instanceof Number n)return positive(n.floatValue());
        }
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(item,"com.mao.barbequesdelight.content.item.BBQSkewerItem")
                &&stack.hasTag()&&stack.getTag().getString("seasoning").equals("CUMIN"))return 2;
        if (AbnormalsPlacedFoods.is(item,
                "com.teamabnormals.neapolitan.common.item.HealingItem",
                "com.teamabnormals.neapolitan.common.item.HealingDrinkItem",
                "com.teamabnormals.neapolitan.common.item.HealingIceCreamItem",
                "com.teamabnormals.neapolitan.common.item.HealingMilkshakeItem")) {
            Object amount = HostReflection.field(item, "healAmount");
            return amount instanceof Number value ? positive(value.floatValue()) : 0;
        }
        if (item instanceof BlockItem block && AbnormalsPlacedFoods.is(block.getBlock(), AbnormalsPlacedFoods.CAKE)
                && BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString().equals("neapolitan:strawberry_cake")) return 1;
        return 0;
    }

    public static float instantPoints(int level, boolean linear) {
        if (level <= 0) return 0;
        return linear ? 4F * level : (float)Math.min(Integer.MAX_VALUE, 4L << Math.min(29, level - 1));
    }

    public static float total(float direct, List<CuisineFoodInfoPackets.Effect> effects, boolean linear) {
        float instant = 0;
        for (var effect : effects) if (effect.id().toString().equals("minecraft:instant_health"))
            instant = Math.max(instant, instantPoints(effect.level(), linear));
        return positive(direct) + instant;
    }

    public static float total(ItemStack stack, CuisineFoodInfoPackets.Response data) {
        List<CuisineFoodInfoPackets.Effect> effects = new java.util.ArrayList<>();
        if (CuisineGameplayRules.nativeEffectsEnabled()) {
            var food = stack.getFoodProperties(null);
            if (food != null) for (var pair : food.getEffects()) {
                var effect = pair.getFirst();
                if (effect == null || pair.getSecond() < 1F) continue;
                var id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
                if (id != null && id.toString().equals("minecraft:instant_health")) {
                    effects.add(new CuisineFoodInfoPackets.Effect(id, effect.getAmplifier() + 1, 1));
                }
            }
            for(var effect:ModFoodCompat.previewEffects(stack)){
                var id=BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
                if(id!=null&&id.toString().equals("minecraft:instant_health"))
                    effects.add(new CuisineFoodInfoPackets.Effect(id,effect.getAmplifier()+1,1));
            }
        }
        boolean linear=net.minecraftforge.fml.ModList.get().isLoaded("cys_effectpack");
        return total(total(points(stack),effects,linear),data==null?List.of():data.effects(),linear);
    }

    private static float positive(float value) { return Float.isFinite(value) && value > 0 ? value : 0; }
}
