package com.chi__you.cuisinejournal.client.tooltip;

import com.chi__you.cuisinejournal.food.CuisineEdibility;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Standard FoodProperties effects belong in the normal text tooltip, not the Valheim effect panel. */
public final class NativeFoodEffectTooltip {
    private NativeFoodEffectTooltip() {}

    public static void append(ItemStack stack, List<Component> lines) {
        if (!stack.isEmpty() && !com.chi__you.cuisinejournal.food.CuisineFoodTooltip.hasPanel(stack)
                && com.chi__you.cuisinejournal.food.DirectFoodHealing.points(stack) > 0) {
            float healing = com.chi__you.cuisinejournal.food.DirectFoodHealing.total(stack, null);
            lines.add(com.chi__you.cuisinejournal.food.tooltip.TooltipText.colored(
                    com.chi__you.cuisinejournal.food.tooltip.TooltipText.text("instant_heal"),
                    com.chi__you.cuisinejournal.food.tooltip.TooltipText.LABEL).append(" ")
                    .append(com.chi__you.cuisinejournal.food.tooltip.TooltipText.colored(
                            com.chi__you.cuisinejournal.food.tooltip.TooltipText.text("added_hearts", formatHearts(healing)),
                            com.chi__you.cuisinejournal.food.tooltip.TooltipText.HEALTH))
                    .append(com.chi__you.cuisinejournal.food.tooltip.TooltipText.heart(com.chi__you.cuisinejournal.food.tooltip.TooltipText.HEALTH)));
        }
        if(stack.isEmpty() || !com.chi__you.cuisinejournal.food.CuisineGameplayRules.nativeEffectsEnabled()) return;
        var food=stack.getFoodProperties(null);
        if(!CuisineEdibility.isFood(food)) return;
        int insertion=lines.size();
        // Leave the usual mod-name footer and advanced item identity at the bottom.
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String modName=net.minecraftforge.fml.ModList.get().getModContainerById(id.getNamespace())
                .map(mod->mod.getModInfo().getDisplayName()).orElse("");
        for(int i=1;i<lines.size();i++) {
            String text=lines.get(i).getString();
            if((!modName.isEmpty()&&text.equals(modName))||text.equals(id.toString())) {insertion=i;break;}
        }
        List<Pair<MobEffectInstance,Float>> effects=new ArrayList<>();
        effects.addAll(food.getEffects());
        for(var effect:com.chi__you.cuisinejournal.food.ModFoodCompat.previewEffects(stack))effects.add(Pair.of(effect,1F));
        List<Component> added=missing(effects,lines);
        lines.addAll(insertion,added);
    }

    public static List<Component> missing(List<Pair<MobEffectInstance,Float>> effects,List<Component> existing) {
        List<Component> result=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(var pair:effects) {
            var effect=pair.getFirst();float chance=pair.getSecond();
            if(effect==null||!Float.isFinite(chance)||chance<=0)continue;
            String key=effect.getDescriptionId();
            if(!seen.add(key)||existing.stream().anyMatch(line->containsEffect(line,key)))continue;
            result.add(format(effect,Math.min(1,chance)));
        }
        return result;
    }

    private static String formatHearts(float points) {
        return java.math.BigDecimal.valueOf(points / 2D).stripTrailingZeros().toPlainString();
    }

    public static Component format(MobEffectInstance effect,float chance) {
        MutableComponent name=Component.translatable(effect.getDescriptionId());
        if(effect.getAmplifier()>0) name=Component.translatable("potion.withAmplifier",name,
                Component.translatable("potion.potency."+effect.getAmplifier()));
        if(!effect.getEffect().isInstantenous()&&(effect.getDuration()>20||effect.isInfiniteDuration()))
            name=Component.translatable("potion.withDuration",name,MobEffectUtil.formatDuration(effect,1));
        if(chance<1) name=Component.translatable("cuisinejournal.tooltip.native_effect_chance",name,Math.round(chance*100));
        return name.withStyle(effect.getEffect().getCategory().getTooltipFormatting());
    }

    public static boolean containsEffect(Component line,String key) {
        if(line.getContents() instanceof TranslatableContents translated) {
            if(translated.getKey().equals(key))return true;
            for(Object arg:translated.getArgs())if(arg instanceof Component child&&containsEffect(child,key))return true;
        }
        for(var child:line.getSiblings())if(containsEffect(child,key))return true;
        // Some hosts emit localized literals with section-sign colors rather than translatable components.
        String label=Component.translatable(key).getString();
        String plain=stripFormatting(line.getString()).strip();
        if(!plain.startsWith(label))return false;
        if(plain.length()==label.length())return true;
        char next=plain.charAt(label.length());
        return Character.isWhitespace(next)||next=='('||next=='（';
    }
    private static String stripFormatting(String text) {
        StringBuilder result=new StringBuilder();
        for(int i=0;i<text.length();i++) {
            if(text.charAt(i)==167&&i+1<text.length()){i++;continue;}
            result.append(text.charAt(i));
        }
        return result.toString();
    }
}
