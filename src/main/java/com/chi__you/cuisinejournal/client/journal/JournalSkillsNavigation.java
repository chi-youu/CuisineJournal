package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "cuisinejournal", value = Dist.CLIENT)
public final class JournalSkillsNavigation {
    private static final ScreenReturnSession<Screen> RETURN = new ScreenReturnSession<>();
    private JournalSkillsNavigation() {}
    public static void open(JournalScreen journal) {
        RETURN.request(journal, net.minecraft.Util.getMillis());
        com.chi__you.cuisinejournal.network.Network.toServer(new com.chi__you.cuisinejournal.skills.SkillPackets.Open());
    }
    private static boolean skills(Screen screen) {
        return screen != null && screen.getClass().getName().equals("net.puffish.skillsmod.client.gui.SkillsScreen");
    }
    public static Screen destination(Screen next) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.player.isDeadOrDying()) { RETURN.clear(); return next; }
        Screen current = client.screen;
        if (skills(next)) {
            if (RETURN.enter(current, true, net.minecraft.Util.getMillis()) && current instanceof JournalScreen journal)
                journal.leavingForRecipeViewer(true);
            return next;
        }
        Screen parent = RETURN.closing(skills(current), next == null);
        return parent == null ? next : parent;
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) RETURN.expire(Minecraft.getInstance().screen, net.minecraft.Util.getMillis());
    }
    @SubscribeEvent
    public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) { RETURN.clear(); }
}
