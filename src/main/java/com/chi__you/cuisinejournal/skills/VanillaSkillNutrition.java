package com.chi__you.cuisinejournal.skills;

import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/** Vanilla skill values are independent of the Valheim food timeline. */
public final class VanillaSkillNutrition {
    private static final UUID HEALTH = UUID.fromString("7bccf463-cbc3-4bd5-b08a-27fdc4cd7059");
    private static final UUID HALF_HEALTH = UUID.fromString("10e0f3db-1a4b-448f-b0f2-e071bce935a5");
    private static final Map<UUID,SkillPackets.Nutrition> LAST = new HashMap<>();
    private VanillaSkillNutrition() {}
    public static int cap(double bonus) { return Math.max(20,Math.min(1024,20+(int)bonus)); }
    public static double recovery(double bonus, boolean doubled) { return Math.max(0,1+bonus)*(doubled?2:1); }
    public static double value(Player player,String key) {
        if (player == null) return 0;
        if (player instanceof ServerPlayer server) return CuisineFoodRuntime.active()?0:CookingSkills.value(server,key);
        var state=com.chi__you.cuisinejournal.food.ClientFoodRuleView.current().skills();
        if(state==null||!state.active()||com.chi__you.cuisinejournal.food.CuisineFoodClientState.active())return 0;
        return switch(key){case "hunger_cap"->state.hungerCap()-20;case "saturation_cap"->state.saturationCap()-20;default->0;};
    }
    public static int higherCap(int own,int other){return Math.max(own,other);}
    public static int ownHungerCap(Player player){return cap(value(player,"hunger_cap"));}
    public static int ownSaturationCap(Player player){return cap(value(player,"saturation_cap"));}
    public static int hungerCap(Player player){return higherCap(ownHungerCap(player),TwilightNutritionCompat.cap(player,false));}
    public static int saturationCap(Player player){return higherCap(ownSaturationCap(player),TwilightNutritionCompat.cap(player,true));}
    public static float recovery(Player player){return (float)recovery(value(player,"saturation_regen"),value(player,"regen_double")>0);}
    public static void sendIfChanged(ServerPlayer player) {
        var food=player.getFoodData();
        var current=new SkillPackets.Nutrition(food.getFoodLevel(),food.getSaturationLevel());
        if(!current.equals(LAST.put(player.getUUID(),current))) com.chi__you.cuisinejournal.network.Network.toPlayer(player,current);
    }
    public static void forget(ServerPlayer player){LAST.remove(player.getUUID());}
    public static void clear(){LAST.clear();}
    public static void reconcile(ServerPlayer player){
        boolean enabled=!CuisineFoodRuntime.active()&&ProfileManager.get()!=null&&ProfileManager.get().of(player).skillsActive();
        var attribute=player.getAttribute(Attributes.MAX_HEALTH);
        if(attribute!=null){
            modifier(attribute,HEALTH,enabled?CookingSkills.value(player,"health"):0,AttributeModifier.Operation.ADDITION);
            modifier(attribute,HALF_HEALTH,enabled?CookingSkills.value(player,"health_multiplier"):0,AttributeModifier.Operation.MULTIPLY_TOTAL);
            if(player.getHealth()>player.getMaxHealth())player.setHealth(player.getMaxHealth());
        }
        if(enabled){
            var food=player.getFoodData();
            food.setFoodLevel(Math.max(0,Math.min(hungerCap(player),food.getFoodLevel())));
            food.setSaturation(Math.max(0,Math.min(saturationCap(player),food.getSaturationLevel())));
        }
        ((NutritionOwner)player.getFoodData()).cuisinejournal$owner(player);
    }
    private static void modifier(net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,UUID id,double amount,AttributeModifier.Operation operation){
        var current=attribute.getModifier(id);
        if(current!=null&&Math.abs(current.getAmount()-amount)<1e-8)return;
        if(current!=null)attribute.removeModifier(id);
        if(amount!=0)attribute.addTransientModifier(new AttributeModifier(id,"cuisinejournal_vanilla_skill",amount,operation));
    }
    public interface NutritionOwner { void cuisinejournal$owner(Player player); }
}
