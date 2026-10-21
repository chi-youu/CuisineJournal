package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineAquaEffectWindow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStack.class)
public abstract class AquaItemStackMixin {
    @Redirect(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cuisinejournal$wrapAquaFinish(Item item, ItemStack stack, Level level,
                                                     LivingEntity consumer) {
        if(com.chi__you.cuisinejournal.food.LetsDoFood.roasting(stack)||com.chi__you.cuisinejournal.food.GrillingFoodCompat.nested())return item.finishUsingItem(stack,level,consumer);
        if(com.chi__you.cuisinejournal.food.CuisineFoodRuntime.activeForCurrentThread()
                &&consumer instanceof net.minecraft.server.level.ServerPlayer player
                &&com.chi__you.cuisinejournal.food.CuisineFoodService.definition(stack)!=null
                &&!com.chi__you.cuisinejournal.food.CuisineFoodService.canConsume(player,stack)){
            com.chi__you.cuisinejournal.food.KaleidoscopeFood.reject(player,stack);return stack;
        }
        CuisineAquaEffectWindow.begin(stack, consumer);
        com.chi__you.cuisinejournal.food.FoodUseContext.begin(stack);
        try {
            com.chi__you.cuisinejournal.food.CuisineEffectAdapters.beginNativeFood(stack,consumer);
            try {
                ItemStack before=stack.copy();
                java.util.List<ItemStack> oldSlots=new java.util.ArrayList<>();
                if(com.chi__you.cuisinejournal.food.LetsDoFood.kitchenStage(before)&&consumer instanceof Player player)
                    for(int i=0;i<player.getInventory().getContainerSize();i++)oldSlots.add(player.getInventory().getItem(i));
                ItemStack result=item.finishUsingItem(stack, level, consumer);
                com.chi__you.cuisinejournal.food.GrillingFoodCompat.finishHot(before,consumer);
                if(com.chi__you.cuisinejournal.food.LetsDoFood.kitchenStage(before)&&consumer instanceof Player player){
                    int oldStage=before.hasTag()?before.getTag().getInt("CustomModelData"):0;
                    for(int i=0;i<player.getInventory().getContainerSize();i++){var next=player.getInventory().getItem(i);
                        if(ItemStack.isSameItem(next,before)&&next.hasTag()&&next.getTag().getInt("CustomModelData")==oldStage+1
                                &&i<oldSlots.size()&&next!=oldSlots.get(i)
                                &&com.chi__you.cuisinejournal.culinary.MealStamp.read(next)==null)
                            player.getInventory().setItem(i,com.chi__you.cuisinejournal.food.LetsDoFood.preserveStage(before,next));
                    }
                    result=com.chi__you.cuisinejournal.food.LetsDoFood.preserveStage(before,result);
                }
                return result;
            }
            finally { com.chi__you.cuisinejournal.food.CuisineEffectAdapters.endNativeFood(); }
        } finally {
            com.chi__you.cuisinejournal.food.FoodUseContext.end();
            CuisineAquaEffectWindow.end();
        }
    }

    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"))
    private InteractionResultHolder<ItemStack> cuisinejournal$wrapAquaUse(Item item, Level level,
                                                       Player player, InteractionHand hand) {
        CuisineAquaEffectWindow.begin((ItemStack) (Object) this, player);
        try {
            if(com.chi__you.cuisinejournal.food.LetsDoFood.host((ItemStack)(Object)this)
                    &&!com.chi__you.cuisinejournal.food.LetsDoFood.flask((ItemStack)(Object)this)
                    &&!com.chi__you.cuisinejournal.food.LetsDoFood.useAllowed(player,(ItemStack)(Object)this))
                return InteractionResultHolder.fail((ItemStack)(Object)this);
            return item.use(level, player, hand);
        } finally {
            CuisineAquaEffectWindow.end();
        }
    }
}
