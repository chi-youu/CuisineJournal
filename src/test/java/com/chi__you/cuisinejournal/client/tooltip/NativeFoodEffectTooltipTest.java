package com.chi__you.cuisinejournal.client.tooltip;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.*;
import net.minecraft.ChatFormatting;
import java.util.*;

public final class NativeFoodEffectTooltipTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        directHealingTests();
        nutritionIconAssets();
        nutritionProviderHooks();
        check(NutritionTooltip.useOwnTooltip(true,false), "own nutrition tooltip without Obscure, including AppleSkin installations");
        check(!NutritionTooltip.useOwnTooltip(true,true), "Obscure exclusively owns nutrition tooltip when installed");
        check(!NutritionTooltip.useOwnTooltip(false,false), "nutrition switch disables own fallback");
        check(!NutritionTooltip.useOwnTooltip(false,true), "disabled nutrition never creates a second fallback for Obscure");
        check(NutritionTooltip.saturationText(0.4F).equals("40%"), "nutrition fallback uses saturation percentage");
        check(NutritionTooltip.saturationText(0.6F).equals("60%"), "float saturation values do not lose a percentage point");
        check(NutritionTooltip.saturationText(1.2F).equals("120%"), "nutrition fallback does not cap saturation modifiers");
        check(NutritionTooltip.saturationText(Float.NaN).equals("0%"), "invalid saturation cannot render broken text");
        check(NutritionTooltip.saturationText(-1).equals("0%"), "negative saturation is sanitized");
        check(NutritionTooltip.saturationText(0.409F).equals("40%"), "nutrition percentage truncates like Obscure rather than rounding");
        check(!NutritionTooltip.hasValues(0,0), "empty nutrition row is suppressed");
        check(NutritionTooltip.hasValues(0,0.4F), "saturation-only foods keep a row");
        check(NutritionTooltip.format(4,0.4F).getString().equals("\uE100\uE1024\uE10140%"), "nutrition row has no extra spaces or inter-group gap");
        check(NutritionTooltip.format(0,0.4F).getString().equals("\uE10140%"), "zero hunger icon and number are omitted");
        check(NutritionTooltip.format(4,0).getString().equals("\uE100\uE1024"), "zero saturation icon and number are omitted");
        check(NutritionTooltip.format(4,0.4F).getStyle().getFont().equals(NutritionTooltip.FONT), "numbers and icons share the fallback font like Obscure");
        MobEffect positive=effect("effect.test.good",MobEffectCategory.BENEFICIAL,false);
        MobEffect negative=effect("effect.test.bad",MobEffectCategory.HARMFUL,false);
        MobEffect instant=effect("effect.test.instant",MobEffectCategory.BENEFICIAL,true);
        var bonusLine=com.chi__you.cuisinejournal.food.CuisineFoodTooltip.levelBonusLine(positive,2).getString();
        check(bonusLine.endsWith("+II")&&!bonusLine.contains("00:"),"effect level bonus uses Roman increment without a timer");
        check(!com.chi__you.cuisinejournal.food.CuisineFoodTooltip.showLevelBonus(net.minecraft.resources.ResourceLocation.parse("minecraft:absorption"),1),
                "absorption bonus is represented by shield hearts rather than duplicated increment row");
        var good=instance(positive,1200,1);
        var bad=instance(negative,100,0);
        var formatted=NativeFoodEffectTooltip.format(good,1);
        check(key(formatted).equals("potion.withDuration"),"normal effects use vanilla duration wrapper");
        check(NativeFoodEffectTooltip.containsEffect(formatted,positive.getDescriptionId()),"nested potion level/time retains effect identity");
        check(formatted.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.BLUE)),"beneficial effects use native tooltip blue");
        check(NativeFoodEffectTooltip.format(bad,1).getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED)),"harmful effects use native tooltip red");
        var chance=NativeFoodEffectTooltip.format(bad,0.65F);
        check(key(chance).equals("cuisinejournal.tooltip.native_effect_chance")&&NativeFoodEffectTooltip.containsEffect(chance,negative.getDescriptionId()),"chance wrapper preserves translatable effect identity");
        check(key(NativeFoodEffectTooltip.format(instance(instant,1,0),1)).equals(instant.getDescriptionId()),"instant effect is not falsely shown as timed buff");
        check(key(NativeFoodEffectTooltip.format(instance(positive,-1,0),1)).equals("potion.withDuration"),"infinite native duration gets duration wrapper");
        var entries=List.of(Pair.of(good,1F),Pair.of(bad,0.65F));
        var missing=NativeFoodEffectTooltip.missing(entries,List.of(formatted));
        check(missing.size()==1&&NativeFoodEffectTooltip.containsEffect(missing.get(0),negative.getDescriptionId()),"host's existing effect line is not duplicated");
        check(NativeFoodEffectTooltip.missing(entries,List.of(Component.literal(positive.getDescriptionId()+" II (1:00)"),chance)).isEmpty(),"localized literal and chance effects are recognized");
        check(!NativeFoodEffectTooltip.containsEffect(Component.literal("Description: "+positive.getDescriptionId()),positive.getDescriptionId()),"prose mentioning effect is not mistaken for effect row");
        check(!NativeFoodEffectTooltip.containsEffect(Component.literal(positive.getDescriptionId()+"_other"),positive.getDescriptionId()),"effect prefix collision does not suppress another effect");
        check(NativeFoodEffectTooltip.missing(List.of(Pair.of(good,0F),Pair.of(bad,Float.NaN)),List.of()).isEmpty(),"impossible and invalid probabilities are omitted");
        check(NativeFoodEffectTooltip.missing(List.of(Pair.of(good,1F),Pair.of(good,1F)),List.of()).size()==1,"repeated food declarations only show once");
        System.out.println("NativeFoodEffectTooltipTest: "+checks+" checks passed");
    }
    private static void nutritionProviderHooks() throws Exception {
        var apple=Class.forName("com.chi__you.cuisinejournal.mixin.client.AppleSkinNutritionTooltipMixin");
        var gate=apple.getDeclaredMethod("cuisinejournal$nutrition",net.minecraft.world.item.ItemStack.class,
                net.minecraft.world.entity.player.Player.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class);
        gate.setAccessible(true);
        var result=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>("shouldShowTooltip",true,true);
        gate.invoke(null,null,null,result);
        check(result.isCancelled()&&!result.getReturnValue(),"AppleSkin nutrition tooltip always suppressed independently of nutrition switch");
        var quark=Class.forName("com.chi__you.cuisinejournal.mixin.client.QuarkNutritionTooltipMixin");
        var icons=quark.getDeclaredMethod("cuisinejournal$hideFoodIcons",boolean.class);icons.setAccessible(true);
        check(Boolean.FALSE.equals(icons.invoke(null,true))&&Boolean.FALSE.equals(icons.invoke(null,false)),
                "Quark icon argument always false without cancelling descriptive text");
        var config=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/mixins.cuisinejournal.json"))).getAsJsonObject();
        var clients=new java.util.HashSet<String>();config.getAsJsonArray("client").forEach(v->clients.add(v.getAsString()));
        check(clients.containsAll(java.util.Set.of("client.QuarkNutritionTooltipMixin","client.AppleSkinNutritionTooltipMixin","client.ObscureNutritionIconsMixin")),
                "all provider gates registered client-only");
    }
    private static void nutritionIconAssets() throws Exception {
        var loader = NativeFoodEffectTooltipTest.class.getClassLoader();
        try (var input = loader.getResourceAsStream("assets/cuisinejournal/textures/font/nutrition_icons.png")) {
            check(input != null, "standalone nutrition icon atlas is packaged");
            var atlas = javax.imageio.ImageIO.read(input);
            check(atlas.getWidth() == 22 && atlas.getHeight() == 11, "nutrition glyphs use 11px cells");
            int[] advances = new int[2];
            for (int glyph = 0; glyph < 2; glyph++) {
                int right = 0;
                for (int y = 0; y < 11; y++) for (int x = 0; x < 11; x++)
                    if ((atlas.getRGB(glyph * 11 + x,y) >>> 24) != 0) right = Math.max(right,x);
                advances[glyph] = right + 2;
            }
            check(advances[0] + 1 == 12 && advances[1] == 12, "both icons advance 12px including half-food padding");
            check(atlas.getRGB(14,0) == 0xFF000000, "saturation icon retains its outer black outline");
        }
        try (var input = loader.getResourceAsStream("assets/cuisinejournal/font/nutrition.json")) {
            var providers = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("providers");
            var bitmap = providers.get(3).getAsJsonObject();
            check(bitmap.get("height").getAsInt() == 11 && bitmap.get("ascent").getAsInt() == 9, "font uses Obscure-compatible height and baseline");
            check(providers.get(4).getAsJsonObject().getAsJsonObject("advances").get("\uE102").getAsInt() == 1, "half-food width padding matches font metrics");
        }
    }
    private static void directHealingTests() {
        var heal=net.minecraft.resources.ResourceLocation.parse("minecraft:instant_health");
        var strength=net.minecraft.resources.ResourceLocation.parse("minecraft:strength");
        var one=new com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.Effect(heal,1,1);
        var two=new com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.Effect(heal,2,1);
        float cake=com.chi__you.cuisinejournal.food.CakeSliceHealing.HEALTH_POINTS;
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(cake,List.of(),false)==2,"cake slice adds exactly one heart without native effects");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(cake+3,List.of(one),false)==9,"cake bonus, direct host heal and instant health add in one row");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(cake,List.of(two),true)==10,"cake bonus is not multiplied by potion or CYS scaling");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(3,List.of(),false)==3,"native healing survives without a potion effect");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(3,List.of(one),false)==7,"direct 1.5 hearts plus instant heal 2 hearts yields 3.5 hearts");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(3,List.of(two),false)==11,"instant healing scales without multiplying direct healing");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(1,List.of(one,two),false)==9,"one merged instant effect is not duplicated by display sources");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(3,List.of(new com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.Effect(heal,3,1)),true)==15,"CYS linear healing retains direct base");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(3,List.of(new com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.Effect(strength,3,200)),false)==3,"other effects do not become healing");
        check(com.chi__you.cuisinejournal.food.DirectFoodHealing.total(Float.NaN,List.of(),false)==0,"invalid direct values cannot produce broken numbers");
    }
    private static MobEffect effect(String key,MobEffectCategory category,boolean instant) {
        return new MobEffect(category,0) {
            @Override public String getDescriptionId(){return key;}
            @Override public boolean isInstantenous(){return instant;}
        };
    }
    private static MobEffectInstance instance(MobEffect effect,int duration,int amplifier) {
        // Resolve the supplied fixture directly; Forge's registry delegates require a running mod loader.
        return new MobEffectInstance(effect,duration,amplifier) {
            @Override public MobEffect getEffect(){return effect;}
            @Override public String getDescriptionId(){return effect.getDescriptionId();}
        };
    }
    private static String key(Component component){return ((TranslatableContents)component.getContents()).getKey();}
    private static void check(boolean value,String description){if(!value)throw new AssertionError(description);checks++;}
}
