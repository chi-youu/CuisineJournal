package com.chi__you.cuisinejournal.client.hud;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.journal.JournalText;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.progression.RecipeTracking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Cooking XP and persistent recipe tracking share an anchor, not a visibility timer. */
public final class CookingExperienceHud {
    private static long visibleUntilTick,lastKnownXp,clientTick,lastInventoryTick=Long.MIN_VALUE,lastRevision=-1;
    private static Map<ResourceLocation,Integer> inventoryCounts=Map.of();
    private static final Map<ResourceLocation,int[]> suppliedCounts=new HashMap<>();
    private CookingExperienceHud(){}
    public static void tick(){
        clientTick++;
        long xp=ClientJournalState.cookingXp();
        if(xp!=lastKnownXp){lastKnownXp=xp;visibleUntilTick=clientTick+ConfigManager.client().cookingHud.visibleTicks;}
    }
    public static void render(GuiGraphics g){
        var client=Minecraft.getInstance();
        if(client.player==null||client.options.hideGui||client.screen!=null)return;
        var config=ConfigManager.client().cookingHud;
        var features=ClientJournalState.journalFeatures();
        boolean level=config.enabled&&(!config.onlyWhileActive||clientTick<=visibleUntilTick);
        boolean tracking=config.showPinnedRecipes&&features!=null&&!features.tracks().isEmpty();
        if(!level&&!tracking)return;
        g.pose().pushPose();g.pose().scale(config.scale,config.scale,1);
        int sw=(int)(g.guiWidth()/config.scale),sh=(int)(g.guiHeight()/config.scale);
        int width=Math.min(170,Math.max(80,sw/3));
        int x=switch(config.anchor){case TOP_LEFT,BOTTOM_LEFT->config.offsetX;case TOP_RIGHT,BOTTOM_RIGHT->sw-width-config.offsetX;};
        x=Math.max(0,Math.min(sw-width,x));
        int height=level?20:0;
        if(tracking){
            for(var t:features.tracks())height+=11+(t.method()!=null?10:0)+t.slots().size()*11+4;
            height-=4; // no gap after the last track
        }
        int y=switch(config.anchor){case TOP_LEFT,TOP_RIGHT->config.offsetY;case BOTTOM_LEFT,BOTTOM_RIGHT->sh-height-config.offsetY;};
        y=Math.max(0,y);
        // Backing panel instead of text shadow: readable on a bright daytime sky, quiet at night.
        // Warm dark like the journal; opacity follows vanilla's "text background opacity" setting.
        int bottom=Math.min(sh,y+height);
        if(bottom>y){
            int alpha=Math.max(0x50,Math.min(0xD0,(int)Math.round(client.options.textBackgroundOpacity().get()*255)));
            g.fill(x-3,y-3,x+width+3,bottom+1,alpha<<24|0x1A140F);
            g.fill(x-3,y-3,x+width+3,y-2,(alpha/3)<<24|0xFFE9C7);
        }
        if(level){
            var label=Component.translatable("cuisinejournal.overview.level").append(" "+ClientJournalState.cookingLevel());
            g.drawString(client.font,label,x,y,JournalTheme.TEXT,true);
            long need=ClientJournalState.xpForCurrentLevel();
            JournalTheme.bar(g,x,y+10,width,4,need<=0?1:(float)ClientJournalState.xpIntoLevel()/need,need<0);y+=20;
        }
        if(tracking){
            refreshInventory();
            g.enableScissor(x,0,x+width,sh);
            for(var t:features.tracks()){
                if(y+24>sh)break;
                String title=JournalText.nameOf(t.dish()).getString()+" ×"+t.target();
                g.drawString(client.font,client.font.plainSubstrByWidth(title,width),x,y,JournalTheme.TEXT_ACCENT,false);y+=11;
                if(t.method()!=null){g.drawString(client.font,client.font.plainSubstrByWidth(JournalText.method(t.method()).getString(),width),x,y,JournalTheme.TEXT_DIM,false);y+=10;}
                var needs=t.slots().stream().map(s->new RecipeTracking.Need(s.items(),s.needed())).toList();
                int[] supplied=suppliedCounts.computeIfAbsent(t.dish(),ignored->RecipeTracking.allocate(needs,inventoryCounts));
                for(int i=0;i<t.slots().size();i++){
                    if(y+11>sh)break;
                    var slot=t.slots().get(i);
                    ResourceLocation icon=slot.items().stream().filter(k->inventoryCounts.getOrDefault(k,0)>0).findFirst()
                            .orElse(slot.items().get((int)(clientTick/20%slot.items().size())));
                    g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.625F,.625F,1);g.renderFakeItem(JournalText.stackOf(icon),0,0);g.pose().popPose();
                    String count=supplied[i]+" / "+slot.needed();int cw=client.font.width(count);
                    String name=JournalText.trackedIngredientName(slot.tag(),slot.items(),icon).getString();
                    g.drawString(client.font,client.font.plainSubstrByWidth(name,Math.max(0,width-15-cw)),x+12,y,JournalTheme.DISCOVERY,false);
                    g.drawString(client.font,count,x+width-cw,y,supplied[i]>=slot.needed()?JournalTheme.TEXT_GOOD:JournalTheme.TEXT_ACCENT,false);y+=11;
                }
                y+=4;
            }
            g.disableScissor();
        }
        g.pose().popPose();
    }
    private static void refreshInventory(){
        if(lastInventoryTick!=Long.MIN_VALUE&&clientTick-lastInventoryTick<20)return;
        lastInventoryTick=clientTick;lastRevision=ClientJournalState.revision();
        suppliedCounts.clear();
        var client=Minecraft.getInstance();var features=ClientJournalState.journalFeatures();
        if(client.player==null||features==null){inventoryCounts=Map.of();return;}
        Set<ResourceLocation> wanted=new HashSet<>();for(var t:features.tracks())for(var s:t.slots())wanted.addAll(s.items());
        Map<ResourceLocation,Integer> counts=new HashMap<>();
        for(int i=0;i<client.player.getInventory().getContainerSize();i++){
            var stack=client.player.getInventory().getItem(i);if(stack.isEmpty())continue;
            var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if(wanted.contains(id))counts.merge(id,stack.getCount(),Integer::sum);
        }
        inventoryCounts=counts;
    }
}
