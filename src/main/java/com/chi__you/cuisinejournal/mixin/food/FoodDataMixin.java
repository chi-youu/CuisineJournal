package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.chi__you.cuisinejournal.skills.VanillaSkillNutrition;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataMixin implements VanillaSkillNutrition.NutritionOwner {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Unique private Player cuisinejournal$player;
    public void cuisinejournal$owner(Player player) { cuisinejournal$player=player; }
    @Inject(method="getFoodLevel",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$procedureRead(CallbackInfoReturnable<Integer> cir){
        if(com.chi__you.cuisinejournal.food.PlacedFoodConsumption.procedureFood((FoodData)(Object)this)){
            if(CuisineFoodRuntime.activeForCurrentThread())cir.setReturnValue(0);
            else if(foodLevel<VanillaSkillNutrition.hungerCap(cuisinejournal$player))cir.setReturnValue(Math.min(19,foodLevel));
            else cir.setReturnValue(Math.max(20,foodLevel));
        }
    }
    @Inject(method="setFoodLevel",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$procedureFood(int value,CallbackInfo ci){
        if(!com.chi__you.cuisinejournal.food.PlacedFoodConsumption.procedureFood((FoodData)(Object)this))return;
        com.chi__you.cuisinejournal.food.PlacedFoodConsumption.foodApplied((FoodData)(Object)this);
        if(com.chi__you.cuisinejournal.food.PlacedFoodConsumption.variableProcedureFood((FoodData)(Object)this)){
            if(!CuisineFoodRuntime.activeForCurrentThread())foodLevel=Math.min(VanillaSkillNutrition.hungerCap(cuisinejournal$player),foodLevel
                    +com.chi__you.cuisinejournal.food.PlacedFoodConsumption.procedureNutrition((FoodData)(Object)this,foodLevel,value));
            ci.cancel();return;
        }
        if(!CuisineFoodRuntime.activeForCurrentThread()){
            ItemStack serving=com.chi__you.cuisinejournal.food.PlacedFoodConsumption.vanillaServing((FoodData)(Object)this);
            var adjusted=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(3,
                    serving.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("unusual_delight:glow_gummy")))?.2F/6F:1F/6F,serving);
            foodLevel=Math.min(VanillaSkillNutrition.hungerCap(cuisinejournal$player),foodLevel+adjusted.nutrition());
        }
        ci.cancel();
    }
    @Inject(method="setSaturation",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$procedureSaturation(float value,CallbackInfo ci){
        if(!com.chi__you.cuisinejournal.food.PlacedFoodConsumption.procedureFood((FoodData)(Object)this))return;
        if(com.chi__you.cuisinejournal.food.PlacedFoodConsumption.variableProcedureFood((FoodData)(Object)this)){
            if(!CuisineFoodRuntime.activeForCurrentThread())saturationLevel=Math.min(Math.min(foodLevel,VanillaSkillNutrition.saturationCap(cuisinejournal$player)),
                    saturationLevel+com.chi__you.cuisinejournal.food.PlacedFoodConsumption.procedureSaturation((FoodData)(Object)this,Math.max(0,value-saturationLevel)));
            ci.cancel();return;
        }
        if(!CuisineFoodRuntime.activeForCurrentThread()){
            ItemStack serving=com.chi__you.cuisinejournal.food.PlacedFoodConsumption.vanillaServing((FoodData)(Object)this);
            float gain=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(serving.getItem()).toString().equals("unusual_delight:glow_gummy")?.2F:1F;
            var adjusted=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(3,gain/6F,serving);
            saturationLevel=Math.min(Math.min(foodLevel,VanillaSkillNutrition.saturationCap(cuisinejournal$player)),saturationLevel+adjusted.nutrition()*adjusted.saturationModifier()*2);
        }
        ci.cancel();
    }
    @Inject(method = "eat(IF)V", at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$ignoreVanillaFood(int nutrition, float saturation, CallbackInfo ci) {
        com.chi__you.cuisinejournal.food.PlacedFoodConsumption.foodApplied((FoodData)(Object)this);
        if (CuisineFoodRuntime.activeForCurrentThread()) {
            ci.cancel();
        } else if (!com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.applyingItem()) {
            ItemStack serving=com.chi__you.cuisinejournal.food.PlacedFoodConsumption.vanillaServing((FoodData)(Object)this);
            if(!serving.isEmpty()) {
                var adjusted=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.calculate(nutrition,saturation,serving);
                foodLevel=Math.min(VanillaSkillNutrition.hungerCap(cuisinejournal$player),foodLevel+adjusted.nutrition());
                saturationLevel=Math.min(Math.min(foodLevel,VanillaSkillNutrition.saturationCap(cuisinejournal$player)),saturationLevel+adjusted.nutrition()*adjusted.saturationModifier()*2);
                ci.cancel();
            }
        }
    }

    @Redirect(method="eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;getFoodProperties(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/food/FoodProperties;",remap=false),remap=false)
    private net.minecraft.world.food.FoodProperties cuisinejournal$itemProperties(ItemStack stack,net.minecraft.world.entity.LivingEntity entity) {
        if(entity instanceof Player player)cuisinejournal$player=player;
        return com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.itemProperties(stack,entity);
    }

    @Redirect(method="eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;eat(IF)V",remap=true),remap=false)
    private void cuisinejournal$itemNutrition(FoodData data,int nutrition,float saturation) {
        com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.itemApplication(()->data.eat(nutrition,saturation));
    }

    @Inject(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$ignoreVanillaFoodItem(Item item, ItemStack stack, CallbackInfo ci) {
        com.chi__you.cuisinejournal.food.PlacedFoodConsumption.foodApplied(
                (FoodData) (Object) this, stack, item);
        if (CuisineFoodRuntime.activeForCurrentThread()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$ignoreVanillaTick(Player player, CallbackInfo ci) {
        cuisinejournal$player=player;
        if (player.level().isClientSide
                ? com.chi__you.cuisinejournal.food.CuisineFoodClientState.active()
                : CuisineFoodRuntime.active()) {
            ci.cancel();
        }
    }

    @ModifyArg(method="eat(IF)V",at=@At(value="INVOKE",target="Ljava/lang/Math;min(II)I"),index=1)
    private int cuisinejournal$hungerCap(int original) { return Math.max(original,VanillaSkillNutrition.hungerCap(cuisinejournal$player)); }

    @Redirect(method="eat(IF)V",at=@At(value="INVOKE",target="Ljava/lang/Math;min(FF)F"))
    private float cuisinejournal$saturationCap(float amount,float food) {
        return Math.min(amount,Math.min(food,VanillaSkillNutrition.saturationCap(cuisinejournal$player)));
    }

    @Inject(method="needsFood",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$needsFood(CallbackInfoReturnable<Boolean> result) {
        if(CuisineFoodRuntime.activeForCurrentThread()&&!com.chi__you.cuisinejournal.food.PlacedFoodConsumption.vanillaServing((FoodData)(Object)this).isEmpty()){
            result.setReturnValue(true);return;
        }
        if(cuisinejournal$player!=null&&!CuisineFoodRuntime.activeForCurrentThread())
            result.setReturnValue(foodLevel<VanillaSkillNutrition.hungerCap(cuisinejournal$player));
    }

    @ModifyConstant(method="tick",constant=@Constant(intValue=20))
    private int cuisinejournal$fullFood(int original) { return VanillaSkillNutrition.hungerCap(cuisinejournal$player); }

    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V",ordinal=0),index=0)
    private float cuisinejournal$saturatedHealing(float amount) {
        return amount*VanillaSkillNutrition.recovery(cuisinejournal$player);
    }
}
