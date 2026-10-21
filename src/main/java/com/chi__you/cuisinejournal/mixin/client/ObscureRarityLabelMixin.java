package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.food.CuisineFoodTooltip;
import com.chi__you.cuisinejournal.food.tooltip.TooltipText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Obscure Tooltips integration: rarity + Cuisine classification. */
@Pseudo
@Mixin(targets = "dev.obscuria.tooltips.client.tooltip.label.RarityLabelProvider", remap = false)
public abstract class ObscureRarityLabelMixin {
    @Inject(method = "create", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void cuisinejournal$appendClassification(ItemStack stack,
            CallbackInfoReturnable<ClientTooltipComponent> cir) {
        Component classification = CuisineFoodTooltip.obscureClassification(stack);
        if (classification == null) return;
        MutableComponent label = Component.translatable(rarityKey(stack)).withStyle(ChatFormatting.GRAY);
        label.append(TooltipText.colored(Component.literal(" · "), TooltipText.MUTED))
                .append(classification);
        cir.setReturnValue(ClientTooltipComponent.create(label.getVisualOrderText()));
    }

    private static String rarityKey(ItemStack stack) {
        String key = stack.getRarity().toString().toLowerCase(java.util.Locale.US).replace(':', '.');
        if (I18n.exists(key)) return key;
        String nameKey = "rarity.%s.name".formatted(key);
        if (I18n.exists(nameKey)) return nameKey;
        return "rarity." + key;
    }
}
