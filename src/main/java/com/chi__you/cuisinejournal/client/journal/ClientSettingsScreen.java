package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat;
import com.chi__you.cuisinejournal.config.ClientConfig;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings (design: 百味纪设置页设计稿.html), two tabs:
 * <ul>
 *   <li>客户端 — this computer only: growth notifications as one row of chips, XP popups, sounds,
 *       the cooking-level HUD (off / on change / always), food icon size, the workstation side
 *       panel and the Shift fold for production details. Pinned recipes stay hidden until done.</li>
 *   <li>服务器 — admins change the world's rules: food mode, its defaults and independent toggles
 *       (with "随模式" / "已手动修改 · 恢复"), stars (off / per subcategory / per dish) and
 *       discovery. Everyone else sees the same rows greyed out.</li>
 * </ul>
 * One row = label, faint hint, control on the right; the whole row is not a button, only the
 * control is.
 */
public final class ClientSettingsScreen extends Screen {
    private static final int PAD = 10;
    /** Row pitch, section-header height and gap before a section; widened when the frame has room. */
    private int row = 13, sectionHeight = 10, sectionGap = 3;
    /** Control sizing: text scale, box height, horizontal padding, switch size and label offset. */
    private boolean large;
    private float ctl = SMALL;
    private int ctlH = 10, ctlPad = 8, tw = 14, th = 7, labelOff = 2;
    private static final float SMALL = 0.75F;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private boolean serverTab;
    private int frameX, frameY, frameWidth, frameHeight;
    /** Dropdown being shown (0 none, 1 discovery mode, 2 sharing) and where. */
    private int openMenu, menuX, menuY, menuW;
    private long saveAfter;

    public ClientSettingsScreen(Screen parent) {
        super(Component.translatable("cuisinejournal.settings.title"));
        this.parent = parent;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("cuisinejournal.settings." + key, args);
    }

    @Override
    protected void init() {
        frameWidth = Math.max(300, Math.min(400, width - 40));
        frameHeight = Math.max(224, Math.min(270, height - 40));
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;
        // Tallest tab: locked note + 3 sections + 2 gaps + 9 rows = 14r - 19.
        int room = frameHeight - 27 - 24;
        row = Math.max(13, Math.min(17, (room + 19) / 14));
        sectionHeight = row - 3;
        sectionGap = row - 10;
        // Once rows are loose enough, controls switch to full-size text and taller boxes.
        large = row >= 16 && frameWidth >= 360;
        ctl = large ? 1F : SMALL;
        ctlH = large ? 13 : 10;
        ctlPad = large ? 10 : 8;
        tw = large ? 18 : 14;
        th = large ? 9 : 7;
        labelOff = (row - 9) / 2;
    }

    /** Top of a control box centred in the row starting at {@code y}. */
    private int boxTop(int y) { return y + (row - ctlH) / 2; }

    /** Text baseline inside a control box whose top is {@code top}. */
    private int textIn(int top) { return top + (large ? 3 : 2); }

    private int ctlWidth(Component text) { return (int) Math.ceil(font.width(text) * ctl); }

    /** Control text: full size in the large layout, otherwise the small 0.75 scale. */
    private void ctlText(GuiGraphics g, Component text, int x, int y, int color) {
        if (large) g.drawString(font, text, x, y, color, false);
        else small(g, text, x, y, color);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        renderBackground(g);
        JournalTheme.panel(g, frameX, frameY, frameWidth, frameHeight);
        g.drawString(font, title, frameX + PAD, frameY + 8, JournalTheme.TEXT_ACCENT, false);
        // Tabs on the right of the title bar.
        int tx = frameX + frameWidth - PAD;
        for (int i = 1; i >= 0; i--) {
            boolean on = (i == 1) == serverTab;
            Component label = tr(i == 1 ? "tab.server" : "tab.client");
            int w = font.width(label);
            tx -= w;
            boolean hover = inside(mx, my, tx - 2, frameY + 4, w + 4, 16);
            g.drawString(font, label, tx, frameY + 8, on ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
            if (on) g.fill(tx, frameY + 18, tx + w, frameY + 19, JournalTheme.TEXT_ACCENT);
            boolean server = i == 1;
            hits.add(new Hit(tx - 2, frameY + 4, w + 4, 16, () -> { serverTab = server; openMenu = 0; }));
            tx -= 12;
        }
        JournalTheme.divider(g, frameX + 1, frameY + 21, frameWidth - 2);

        int x = frameX + PAD, w = frameWidth - PAD * 2, y = frameY + 27;
        if (serverTab) serverRows(g, x, y, w, mx, my); else clientRows(g, x, y, w, mx, my);

        Component done = tr("done");
        int bw = font.width(done) + 20, bx = frameX + frameWidth - PAD - bw, by = frameY + frameHeight - 20;
        boolean hover = inside(mx, my, bx, by, bw, 14);
        g.fill(bx, by, bx + bw, by + 14, hover ? 0x33E8A93C : 0x40000000);
        JournalTheme.border(g, bx, by, bw, 14, hover ? JournalTheme.TEXT_ACCENT : JournalTheme.PANEL_BORDER);
        g.drawString(font, done, bx + 10, by + 3, hover ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT, false);
        hits.add(new Hit(bx, by, bw, 14, this::onClose));
        if (!serverTab) small(g, tr("client_hint"), frameX + PAD, by + 4, JournalTheme.TEXT_FAINT);

        if (openMenu != 0) renderMenu(g, mx, my);
    }

    // ------------------------------------------------------------------ client tab

    private void clientRows(GuiGraphics g, int x, int y, int w, int mx, int my) {
        ClientConfig config = ConfigManager.client();
        ClientConfig.Notifications n = config.notifications;
        y = section(g, tr("sec.notifications"), x, y, w);
        // Five growth notifications as chips on one row.
        label(g, tr("growth"), null, x, y);
        String[] keys = {"discover", "star", "level", "challenge", "milestone"};
        boolean[] values = {n.discoveries, n.masteryRanks, n.levelUps, n.challenges, n.milestones};
        int cx = x + w;
        for (int i = keys.length - 1; i >= 0; i--) {
            Component chip = tr("chip." + keys[i]);
            int cw = ctlWidth(chip) + ctlPad;
            cx -= cw;
            int index = i;
            chip(g, chip, cx, boxTop(y), cw, values[i], mx, my, () -> {
                switch (index) {
                    case 0 -> n.discoveries = !n.discoveries;
                    case 1 -> n.masteryRanks = !n.masteryRanks;
                    case 2 -> n.levelUps = !n.levelUps;
                    case 3 -> n.challenges = !n.challenges;
                    default -> n.milestones = !n.milestones;
                }
                save();
            });
            cx -= large ? 4 : 3;
        }
        y += row;
        y = toggleRow(g, tr("xp"), tr("xp.hint"), x, y, w, n.xpPopups, mx, my, () -> { n.xpPopups = !n.xpPopups; save(); });
        y = toggleRow(g, tr("sound"), tr("sound.hint"), x, y, w, n.playSounds, mx, my, () -> { n.playSounds = !n.playSounds; save(); });

        y = section(g, tr("sec.interface"), x, y + sectionGap, w);
        ClientConfig.CookingHud hud = config.cookingHud;
        int hudMode = !hud.enabled ? 0 : hud.onlyWhileActive ? 1 : 2;
        y = segmentRow(g, tr("hud"), null, x, y, w, new String[]{"off", "hud.change", "hud.always"}, hudMode, mx, my, i -> {
            hud.enabled = i != 0;
            if (i != 0) hud.onlyWhileActive = i == 1;
            save();
        });
        y = segmentRow(g, tr("icons"), null, x, y, w, new String[]{"icons.large", "icons.small"},
                config.foodHud.useLargeFoodIcons ? 0 : 1, mx, my, i -> { config.foodHud.useLargeFoodIcons = i == 0; save(); });
        ClientConfig.SidePanel[] panels = {ClientConfig.SidePanel.OFF, ClientConfig.SidePanel.CULINARY, ClientConfig.SidePanel.ALWAYS};
        int panel = java.util.Arrays.asList(panels).indexOf(config.journal.sidePanel);
        y = segmentRow(g, tr("panel"), tr("panel.hint"), x, y, w, new String[]{"off", "panel.culinary", "panel.always"},
                Math.max(0, panel), mx, my, i -> { config.journal.sidePanel = panels[i]; save(); });
        y=toggleRow(g,tr("tracking"),null,x,y,w,hud.showPinnedRecipes,mx,my,()->{hud.showPinnedRecipes=!hud.showPinnedRecipes;save();});
        int shiftY = y;
        toggleRow(g, tr("shift"), null, x, y, w, config.tooltips.requireModifier, mx, my,
                () -> { config.tooltips.requireModifier = !config.tooltips.requireModifier; save(); });
        small(g, tr("shift.hint"), x, shiftY + labelOff + 10, JournalTheme.TEXT_FAINT);
    }

    // ------------------------------------------------------------------ server tab

    private static final String[] DISCOVERY = {"COOK_RECIPE", "EAT_OUTPUT", "OBTAIN_OUTPUT"};
    private static final String[] SHARING = {"PERSONAL", "TEAM", "SERVER"};

    private void serverRows(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Packets.SyncServerSettings s = ClientJournalState.serverSettings();
        if (s == null) { small(g, tr("server.wait"), x, y + 4, JournalTheme.TEXT_FAINT); return; }
        boolean edit = s.canEdit();
        if (!edit) { small(g, tr("server.locked"), x, y, JournalTheme.TEXT_FAINT); y += 10; }
        int mxe = edit && !s.unificationBusy() ? mx : Integer.MIN_VALUE;
        int mye = edit && !s.unificationBusy() ? my : Integer.MIN_VALUE;
        if (!edit) g.setColor(1F, 1F, 1F, 0.55F);

        y = section(g, tr("sec.food"), x, y, w);
        boolean valheim = s.foodMode().equals("valheim");
        y = segmentRow(g, tr("mode"), s.modeLocked() ? tr("mode.locked") : null, x, y, w,
                new String[]{"mode.valheim", "mode.vanilla"}, valheim ? 0 : 1, s.modeLocked() ? Integer.MIN_VALUE : mxe, mye,
                i -> send(Packets.SetServerSetting.Key.FOOD_MODE, i == 0 ? "valheim" : "vanilla"));
        y = followRow(g, tr("flavors"), tr("flavors.hint"), x, y, w, s.flavors(), (s.manual() & 1) != 0, mxe, mye,
                Packets.SetServerSetting.Key.FLAVORS, Packets.SetServerSetting.Key.RESET_FLAVORS);
        // Nutrition sits above the native-potion switch (user-requested order).
        y = followRow(g, tr("nutrition"), tr("nutrition.hint"), x, y, w, s.nutrition(), (s.manual() & 4) != 0, mxe, mye,
                Packets.SetServerSetting.Key.NUTRITION, Packets.SetServerSetting.Key.RESET_NUTRITION);
        y = toggleRow(g, tr("potions"), tr("potions.hint"), x, y, w, s.potions(), mxe, mye,
                () -> send(Packets.SetServerSetting.Key.POTIONS, String.valueOf(!s.potions())));
        // Both buttons get the wider label's width, so "默认" and "开" form an even pair.
        y = segmentRow(g, tr("unification"), tr(s.unificationBusy() ? "unification.applying"
                        : s.compatInstalled() ? "unification.hint" : "unification.absent"),
                x, y, w, new String[]{"unification.default", "unification.on"}, s.unification() ? 1 : 0, mxe, mye, true,
                i -> send(Packets.SetServerSetting.Key.UNIFICATION, String.valueOf(i == 1)));

        y = section(g, tr("sec.stars"), x, y + sectionGap, w);
        y = segmentRow(g, tr("stars"), tr("stars.hint"), x, y, w, new String[]{"off", "stars.sub", "stars.dish"},
                s.starMode(), mxe, mye, i -> send(Packets.SetServerSetting.Key.STAR_MODE, String.valueOf(i)));

        y = section(g, tr("sec.discovery"), x, y + sectionGap, w);
        y = dropdownRow(g, tr("discovery"), null, x, y, w, "discovery." + s.discovery().toLowerCase(java.util.Locale.ROOT),
                mxe, mye, 1);
        y = dropdownRow(g, tr("sharing"), tr("sharing.hint"), x, y, w, "sharing." + s.sharing().toLowerCase(java.util.Locale.ROOT),
                mxe, mye, 2);
        y = toggleRow(g, tr("hide"), tr("hide.hint"), x, y, w, s.hideUndiscovered(), mxe, mye,
                () -> send(Packets.SetServerSetting.Key.HIDE_UNDISCOVERED, String.valueOf(!s.hideUndiscovered())));
        if (!edit) g.setColor(1F, 1F, 1F, 1F);
    }

    /** A setting that follows the food mode: blue "随模式", or gold "已手动修改" plus a "恢复" link. */
    private int followRow(GuiGraphics g, Component label, Component hint, int x, int y, int w, boolean on, boolean manual,
                          int mx, int my, Packets.SetServerSetting.Key key, Packets.SetServerSetting.Key reset) {
        label(g, label, hint, x, y);
        int sx = x + w - tw - 2;
        toggle(g, sx, y + (row - th) / 2, on, mx, my, () -> send(key, String.valueOf(!on)));
        int ax = sx - (large ? 8 : 6);
        int top = boxTop(y), ty = textIn(top), lineH = large ? 9 : 7;
        if (manual) {
            Component undo = tr("restore");
            int uw = ctlWidth(undo);
            ax -= uw;
            boolean hover = inside(mx, my, ax, top, uw, ctlH);
            ctlText(g, undo, ax, ty, hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
            if (hover) g.fill(ax, ty + lineH - 1, ax + uw, ty + lineH, JournalTheme.TEXT_DIM);
            hits.add(new Hit(ax, top, uw, ctlH, () -> send(reset, "")));
            ax -= large ? 6 : 4;
            Component tag = tr("manual");
            ax -= ctlWidth(tag);
            ctlText(g, tag, ax, ty, JournalTheme.TEXT_ACCENT);
        } else {
            Component tag = tr("follow");
            ax -= ctlWidth(tag);
            ctlText(g, tag, ax, ty, JournalTheme.ADJUSTED);
        }
        return y + row;
    }

    private void send(Packets.SetServerSetting.Key key, String value) {
        var settings = ClientJournalState.serverSettings();
        if (settings == null || !settings.canEdit() || settings.unificationBusy()) return;
        Network.toServer(new Packets.SetServerSetting(key, value));
    }

    // ------------------------------------------------------------------ widgets

    private int section(GuiGraphics g, Component title, int x, int y, int w) {
        small(g, title, x, y + 2, JournalTheme.TEXT_FAINT);
        int lineX = x + (int) Math.ceil(font.width(title) * SMALL) + 4;
        if (lineX < x + w) JournalTheme.divider(g, lineX, y + 4, x + w - lineX);
        return y + sectionHeight;
    }

    private void label(GuiGraphics g, Component label, Component hint, int x, int y) {
        g.drawString(font, label, x, y + labelOff, JournalTheme.TEXT, false);
        if (hint != null) small(g, hint, x + font.width(label) + 5, y + labelOff + 1, JournalTheme.TEXT_FAINT);
    }

    private int toggleRow(GuiGraphics g, Component label, Component hint, int x, int y, int w, boolean on,
                          int mx, int my, Runnable action) {
        label(g, label, hint, x, y);
        toggle(g, x + w - tw - 2, y + (row - th) / 2, on, mx, my, action);
        return y + row;
    }

    /** The journal's small pill switch; the hit box is a little larger than the pill. */
    private void toggle(GuiGraphics g, int sx, int sy, boolean on, int mx, int my, Runnable action) {
        boolean hover = inside(mx, my, sx - 2, sy - 2, tw + 4, th + 4);
        g.fill(sx, sy, sx + tw, sy + th, on ? 0x66E8A93C : 0xFF120D09);
        JournalTheme.border(g, sx, sy, tw, th, on ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT_DIM : 0xFF3A2C1F);
        int knob = on ? sx + tw - th + 1 : sx + 1;
        g.fill(knob, sy + 1, knob + th - 2, sy + th - 1, on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_FAINT);
        hits.add(new Hit(sx - 2, sy - 2, tw + 4, th + 4, action));
    }

    private void chip(GuiGraphics g, Component text, int cx, int cy, int cw, boolean on, int mx, int my, Runnable action) {
        boolean hover = inside(mx, my, cx, cy, cw, ctlH);
        if (on) g.fill(cx, cy, cx + cw, cy + ctlH, 0x1FE8A93C);
        else if (hover) g.fill(cx, cy, cx + cw, cy + ctlH, 0x14FFE9C7);
        JournalTheme.border(g, cx, cy, cw, ctlH, on ? JournalTheme.TEXT_ACCENT : 0xFF4A3A2A);
        ctlText(g, text, cx + ctlPad / 2, textIn(cy), on ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT);
        hits.add(new Hit(cx, cy, cw, ctlH, action));
    }

    /** Joined buttons on the right; the chosen one is gold. */
    private int segmentRow(GuiGraphics g, Component label, Component hint, int x, int y, int w, String[] keys, int current,
                           int mx, int my, java.util.function.IntConsumer pick) {
        return segmentRow(g, label, hint, x, y, w, keys, current, mx, my, false, pick);
    }

    /**
     * {@code equal} gives every button the widest label's width, so a two-word pair like
     * 默认 / 开 reads as one control instead of two differently sized boxes.
     */
    private int segmentRow(GuiGraphics g, Component label, Component hint, int x, int y, int w, String[] keys, int current,
                           int mx, int my, boolean equal, java.util.function.IntConsumer pick) {
        label(g, label, hint, x, y);
        int[] widths = new int[keys.length];
        int total = 0, widest = 0;
        int top = boxTop(y);
        for (int i = 0; i < keys.length; i++) {
            widths[i] = ctlWidth(tr(keys[i])) + ctlPad;
            widest = Math.max(widest, widths[i]);
        }
        if (equal) for (int i = 0; i < keys.length; i++) widths[i] = widest;
        for (int i = 0; i < keys.length; i++) total += widths[i] - 1;
        int sx = x + w - total - 1;
        for (int i = 0; i < keys.length; i++) {
            boolean on = i == current;
            boolean hover = !on && inside(mx, my, sx, top, widths[i], ctlH);
            if (on) g.fill(sx, top, sx + widths[i], top + ctlH, 0x1FE8A93C);
            else if (hover) g.fill(sx, top, sx + widths[i], top + ctlH, 0x14FFE9C7);
            JournalTheme.border(g, sx, top, widths[i], ctlH, on ? JournalTheme.TEXT_ACCENT : 0xFF4A3A2A);
            // Centre the caption in its cell: with the equal-width pair the narrower label
            // ("开") would otherwise sit at the left edge of a box sized for "默认".
            int labelW = ctlWidth(tr(keys[i]));
            ctlText(g, tr(keys[i]), sx + (widths[i] - labelW) / 2, textIn(top), on ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
            int index = i;
            if (!on && mx != Integer.MIN_VALUE) hits.add(new Hit(sx, top, widths[i], ctlH, () -> pick.accept(index)));
            sx += widths[i] - 1;
        }
        if (current >= 0 && current < keys.length) {
            // Redraw the chosen border on top so its left edge is not covered by the neighbour.
            int ox = x + w - total - 1;
            for (int i = 0; i < current; i++) ox += widths[i] - 1;
            JournalTheme.border(g, ox, top, widths[current], ctlH, JournalTheme.TEXT_ACCENT);
        }
        return y + row;
    }

    private int dropdownRow(GuiGraphics g, Component label, Component hint, int x, int y, int w, String valueKey,
                            int mx, int my, int menu) {
        label(g, label, hint, x, y);
        int dw = Math.max(large ? 96 : 74, ctlWidth(tr(valueKey)) + ctlPad + 12), dx = x + w - dw, by = boxTop(y);
        boolean hover = inside(mx, my, dx, by, dw, ctlH);
        g.fill(dx, by, dx + dw, by + ctlH, hover || openMenu == menu ? 0x14FFE9C7 : 0x26000000);
        JournalTheme.border(g, dx, by, dw, ctlH, openMenu == menu ? JournalTheme.TEXT_ACCENT : 0xFF4A3A2A);
        ctlText(g, tr(valueKey), dx + ctlPad / 2, textIn(by), JournalTheme.TEXT);
        ctlText(g, Component.literal("▾"), dx + dw - (large ? 10 : 8), textIn(by), JournalTheme.TEXT_FAINT);
        int top = by + ctlH;
        hits.add(new Hit(dx, by, dw, ctlH, () -> {
            openMenu = openMenu == menu ? 0 : menu;
            menuX = dx; menuY = top; menuW = dw;
        }));
        return y + row;
    }

    /** The open dropdown's options, drawn above everything; its hits are checked first. */
    private final List<Hit> menuHits = new ArrayList<>();

    private void renderMenu(GuiGraphics g, int mx, int my) {
        menuHits.clear();
        Packets.SyncServerSettings s = ClientJournalState.serverSettings();
        if (s == null || !s.canEdit()) { openMenu = 0; return; }
        String[] values = openMenu == 1 ? DISCOVERY : SHARING;
        String current = openMenu == 1 ? s.discovery() : s.sharing();
        String prefix = openMenu == 1 ? "discovery." : "sharing.";
        Packets.SetServerSetting.Key key = openMenu == 1 ? Packets.SetServerSetting.Key.DISCOVERY : Packets.SetServerSetting.Key.SHARING;
        int item = large ? 14 : 11;
        int h = values.length * item + 2;
        int top = menuY + h > frameY + frameHeight - 2 ? menuY - ctlH - h : menuY;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(menuX, top, menuX + menuW, top + h, JournalTheme.PANEL_BOTTOM);
        JournalTheme.border(g, menuX, top, menuW, h, JournalTheme.PANEL_BORDER);
        for (int i = 0; i < values.length; i++) {
            int ry = top + 1 + i * item;
            boolean on = values[i].equals(current);
            boolean hover = inside(mx, my, menuX + 1, ry, menuW - 2, item);
            if (hover) g.fill(menuX + 1, ry, menuX + menuW - 1, ry + item, 0x21FFE9C7);
            ctlText(g, tr(prefix + values[i].toLowerCase(java.util.Locale.ROOT)), menuX + 5, ry + 3,
                    on ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
            String value = values[i];
            menuHits.add(new Hit(menuX + 1, ry, menuW - 2, item, () -> { send(key, value); openMenu = 0; }));
        }
        g.pose().popPose();
    }

    private void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL, SMALL, 1F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void save() {
        saveAfter=System.nanoTime()+250_000_000L;
    }
    @Override public void tick(){
        super.tick();
        if(saveAfter!=0&&System.nanoTime()>=saveAfter)flushSettings();
    }
    private void flushSettings(){
        if(saveAfter!=0){saveAfter=0;ConfigManager.saveClient(ConfigManager.client());}
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (openMenu != 0) {
            for (Hit hit : menuHits) if (hit.contains(mouseX, mouseY)) { CuisineUiSoundCompat.click(); hit.action().run(); return true; }
            openMenu = 0;
            return true;
        }
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                CuisineUiSoundCompat.click();
                hit.action().run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (openMenu != 0) { openMenu = 0; return true; }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        flushSettings();
        super.removed();
    }

    @Override
    public void onClose() {
        save();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
