package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.class)
public abstract class UnificationLootMixin {
    @Inject(method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;J)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;", at = @At("RETURN"))
    private void cuisinejournal$loot(LootParams params, long seed, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        // Entity drops are filtered after all host event additions, while their original IDs still identify the source.
        if (((LootTable) (Object) this).getParamSet() == LootContextParamSets.ENTITY) return;
        var plan = UnificationRuntime.plan(params.getLevel().getServer());
        cir.getReturnValue().replaceAll(stack -> UnificationRuntime.replace(stack, plan));
        cir.getReturnValue().removeIf(stack -> plan.excluded().contains(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }
}
