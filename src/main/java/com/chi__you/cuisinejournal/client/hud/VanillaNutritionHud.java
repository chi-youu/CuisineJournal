package com.chi__you.cuisinejournal.client.hud;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.chi__you.cuisinejournal.client.compat.NutritionHudCompat;
import com.chi__you.cuisinejournal.food.CuisineGameplayRules;
import com.chi__you.cuisinejournal.skills.VanillaSkillNutrition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Overflow sprites share the vanilla bar; no duplicate numeric reserves. */
public final class VanillaNutritionHud {
    private static final ResourceLocation HUNGER = ResourceLocation.parse("cuisinejournal:textures/gui/overflow_hunger.png");
    private static final ResourceLocation SATURATION = ResourceLocation.parse("cuisinejournal:textures/gui/overflow_saturation.png");
    private VanillaNutritionHud() {}

    public static boolean replacesTwilightOverlay() {
        var client = Minecraft.getInstance();
        return client.player != null && client.gameMode != null && !CuisineFoodClientState.active()
                && !client.options.hideGui && client.gameMode.canHurtPlayer()
                && (client.player.getVehicle() == null || !client.player.getVehicle().showVehicleHealth());
    }

    public static void render(ForgeGui gui, GuiGraphics graphics) {
        var client=Minecraft.getInstance();var skills=ClientJournalState.skills();
        if(!replacesTwilightOverlay())return;
        var player = client.player;
        var food = player.getFoodData();
        int hunger = food.getFoodLevel();
        float saturation = food.getSaturationLevel();
        int right=graphics.guiWidth()/2+91, barY=graphics.guiHeight()-gui.rightHeight+10;
        boolean hungry = player.hasEffect(MobEffects.HUNGER);
        boolean showSaturation = CuisineGameplayRules.nutritionDisplayEnabled() && NutritionHudCompat.saturationVisible();
        var preview = CuisineGameplayRules.nutritionDisplayEnabled() ? NutritionHudCompat.preview(player) : null;
        int nextHunger = preview == null ? hunger : Math.min(VanillaSkillNutrition.hungerCap(player), hunger + preview.hunger());
        float nextSaturation = preview == null ? saturation : Math.min(
                Math.min(nextHunger, VanillaSkillNutrition.saturationCap(player)), saturation + preview.saturation());
        int rows = NutritionOverflow.rows(Math.max(nextHunger, showSaturation ? nextSaturation : 0));
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        try {
            for (int row=0;row<rows;row++) for (int column=0;column<10;column++) {
                int x=right-column*8-9, y=barY-row*10;
                int h=NutritionOverflow.hungerSprite(hunger,row,column,hungry);
                int s=showSaturation ? NutritionOverflow.saturationSprite(saturation,row,column) : -1;
                if (h>=0) { sprite(graphics,HUNGER,0,x,y,45); sprite(graphics,HUNGER,h,x,y,45); }
                if (s>=0) sprite(graphics,SATURATION,s,x,y,36);
                if (preview == null) continue;
                int ph=NutritionOverflow.hungerSprite(nextHunger,row,column,hungry);
                int ps=showSaturation ? NutritionOverflow.saturationSprite(nextSaturation,row,column) : -1;
                float alpha=.3F+.25F*(float)Math.sin(player.tickCount/5.0);
                graphics.setColor(1,1,1,alpha);
                if (ph>h && ph>=0) sprite(graphics,HUNGER,ph,x,y,45);
                if (ps>s && ps>=0) sprite(graphics,SATURATION,ps,x,y,36);
                graphics.setColor(1,1,1,1);
            }
        } finally { graphics.setColor(1,1,1,1); com.mojang.blaze3d.systems.RenderSystem.disableBlend(); }
        gui.rightHeight += Math.max(0, rows-1)*10;
        int y=barY-Math.max(1,rows)*10-2;
        if(skills!=null&&skills.active()&&skills.shield()>0&&client.screen==null){
            String text=String.format(java.util.Locale.ROOT,"%.1f",skills.shield()/2);
            graphics.renderFakeItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD),right-38,y-4);
            graphics.drawString(client.font,text,right-client.font.width(text),y,0xFFD4EAF4,true);
        }
    }

    private static void sprite(GuiGraphics graphics, ResourceLocation atlas, int index, int x, int y, int width) {
        graphics.blit(atlas,x,y,index*9,0,9,9,width,9);
    }
}
