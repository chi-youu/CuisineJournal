package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.food.ExquisitoParfaits;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Display-only recognition; do not change the parfait's native eating contract. */
@Pseudo
@Mixin(targets="com.obscuria.obscureapi.client.TooltipBuilder$Modules", remap=false)
public abstract class ObscureFoodRecognitionMixin {
    @Redirect(method="buildIcons", at=@At(value="INVOKE",
            target="Lnet/minecraft/world/item/Item;m_41472_()Z", remap=false), remap=false)
    private static boolean cuisinejournal$foodIcons(Item item, ItemStack stack, List<Component> lines) {
        return item.isEdible() || ExquisitoParfaits.matches(stack);
    }
}
