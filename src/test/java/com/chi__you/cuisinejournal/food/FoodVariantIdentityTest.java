package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FoodVariantIdentityTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);bootstrap.setBoolean(null,true);BuiltInRegistries.bootStrap();
        var original=new ItemStack(Items.APPLE);var result=original.copy();
        original.getOrCreateTag().putInt(KaleidoscopeFood.QUALITY,1);
        original.getOrCreateTag().putInt(LetsDoFood.BEER_QUALITY,3);
        original.getOrCreateTag().putString(FoodVariantIdentity.PENDING_PRODUCTION,"pending");
        original.getOrCreateTag().putString(IngredientProvenance.KEY,"sources");
        FoodVariantIdentity.copyKeys(original,result,FoodVariantIdentity.CLAIMED_RESULT);
        check(result.getTag().getInt(KaleidoscopeFood.QUALITY)==1&&result.getTag().getInt(LetsDoFood.BEER_QUALITY)==3,
                "claimed output inherits known host grades");
        check(!result.getTag().contains(FoodVariantIdentity.PENDING_PRODUCTION),"claimed output does not clone pending credit");
        var serving=new ItemStack(Items.APPLE);
        FoodVariantIdentity.copyKeys(original,serving,FoodVariantIdentity.PLATE_SERVING);
        check(!serving.getTag().contains(LetsDoFood.BEER_QUALITY),"plate policy does not indiscriminately clone all host data");
        check(!FoodVariantIdentity.sameProduct(original,new ItemStack(Items.APPLE),FoodVariantIdentity.PRODUCTION_BOOKKEEPING),
                "native grade remains part of product equality");
        var same=original.copy();same.getTag().putString(FoodVariantIdentity.PENDING_PRODUCTION,"different claim");
        check(FoodVariantIdentity.sameProduct(original,same,FoodVariantIdentity.PRODUCTION_BOOKKEEPING),
                "bookkeeping differences do not masquerade as new native products");
        System.out.println("FoodVariantIdentityTest: claimed/plate/pending/equality policy distinctions passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
