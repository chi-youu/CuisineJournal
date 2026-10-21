package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.SchemeClientState;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The scheme editor for one kind (flavor or category): list on the left, the selected scheme
 * on the right with load / overwrite / copy / export / rename / delete, and "save current as
 * new" in the footer. Sub-steps (name input, confirmations) replace the right side.
 */
public final class SchemeModal extends JournalModal {
    private static final int LIST_W = 96, ROW = 13;

    private enum Mode { LIST, SAVE, RENAME, COPY, CONFIRM_LOAD, CONFIRM_OVERWRITE, CONFIRM_DELETE, CONFIRM_EXPORT }

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Font font;
    private final boolean category;
    private final IntConsumer requestStarted;
    private final List<Hit> hits = new ArrayList<>();
    private final JournalTextField name, description;
    private final JournalButton saveNew;
    private Mode mode = Mode.LIST;
    private String selected;
    private int pending;
    private int listScroll,listMaximum,listX,listY,listHeight;
    private java.util.Map<String, Long> expectedVersions = java.util.Map.of();

    public SchemeModal(Font font, boolean category, IntConsumer requestStarted) {
        super(0, 0, 420, 300, Component.translatable(category ? "cuisinejournal.scheme.title.category" : "cuisinejournal.scheme.title.flavor"));
        this.font = font;
        this.category = category;
        this.requestStarted = requestStarted;
        this.name = new JournalTextField(font, 0, 0, 100, Component.translatable("cuisinejournal.scheme.name"));
        this.name.setMaxLength(48);
        this.description = new JournalTextField(font, 0, 0, 100, Component.translatable("cuisinejournal.scheme.description"));
        this.description.setMaxLength(256);
        this.saveNew = new JournalButton(Component.translatable("cuisinejournal.scheme.save_new"), JournalButton.Variant.LINK,
                () -> enter(Mode.SAVE));
        this.selected = SchemeClientState.current(category);
        if (selected.isEmpty() || find(selected) == null) {
            List<Packets.SchemeView> list = SchemeClientState.list(category);
            selected = list.stream().filter(view -> view.source() == 0).map(Packets.SchemeView::id)
                    .findFirst().orElse(list.isEmpty() ? null : list.get(0).id());
        }
        setSubmitAction(this::submit);
    }

    @Override protected int maxModalWidth() { return 300; }
    @Override protected int maxModalHeight() { return 230; }
    @Override protected boolean hasCancelButton() { return mode != Mode.LIST; }
    @Override protected JournalButton footerButton() { return mode == Mode.LIST && SchemeClientState.canEdit() ? saveNew : null; }
    @Override protected boolean submitEnabled() {
        if (pending != 0) return false;
        return switch (mode) {
            case SAVE, RENAME, COPY -> !name.getValue().isBlank();
            default -> true;
        };
    }
    @Override protected Component submitLabel() {
        return Component.translatable(switch (mode) {
            case LIST -> "cuisinejournal.edit.close";
            case SAVE, COPY -> "cuisinejournal.scheme.save";
            case RENAME -> "cuisinejournal.scheme.rename";
            case CONFIRM_LOAD -> "cuisinejournal.scheme.load";
            case CONFIRM_OVERWRITE -> "cuisinejournal.scheme.overwrite";
            case CONFIRM_DELETE -> "cuisinejournal.scheme.delete";
            case CONFIRM_EXPORT -> "cuisinejournal.scheme.export";
        });
    }

    private Packets.SchemeView find(String id) {
        if (id == null) return null;
        for (Packets.SchemeView view : SchemeClientState.list(category)) if (view.id().equals(id)) return view;
        return null;
    }

    private Component displayName(Packets.SchemeView view) {
        return view.source() == 0 ? Component.translatable("cuisinejournal.scheme.default_name") : Component.literal(view.name());
    }

    private void enter(Mode next) {
        if (next != Mode.LIST) expectedVersions = com.chi__you.cuisinejournal.client.EditClientState.snapshot();
        mode = next;
        clearFocusables();
        if (next == Mode.SAVE || next == Mode.RENAME || next == Mode.COPY) {
            Packets.SchemeView view = find(selected);
            name.setValue(switch (next) {
                case RENAME -> view == null ? "" : view.name();
                case COPY -> view == null ? "" : Component.translatable("cuisinejournal.scheme.copy_of", displayName(view)).getString();
                default -> "";
            });
            description.setValue("");
            addFocusable(name);
            if (next == Mode.SAVE) addFocusable(description);
            name.setFocused(true);
        }
        refreshButtons();
    }

    @Override protected boolean onBack() {
        if (mode == Mode.LIST || pending != 0) return pending != 0;
        enter(Mode.LIST);
        return true;
    }

    private void submit() {
        if (!submitEnabled()) return;
        Packets.SchemeView view = find(selected);
        switch (mode) {
            case LIST -> close();
            case SAVE -> send(Packets.SchemeRequest.Action.SAVE_NEW, null, name.getValue(), description.getValue());
            case RENAME -> send(Packets.SchemeRequest.Action.RENAME, selected, name.getValue(), null);
            case COPY -> send(Packets.SchemeRequest.Action.COPY, selected, name.getValue(), null);
            case CONFIRM_LOAD -> send(Packets.SchemeRequest.Action.LOAD, selected, null, null);
            case CONFIRM_OVERWRITE -> send(Packets.SchemeRequest.Action.OVERWRITE, selected, null, null);
            case CONFIRM_DELETE -> send(Packets.SchemeRequest.Action.DELETE, selected, null, null);
            case CONFIRM_EXPORT -> send(Packets.SchemeRequest.Action.EXPORT, selected, null, null);
        }
    }

    private void send(Packets.SchemeRequest.Action action, String id, String name, String description) {
        pending = com.chi__you.cuisinejournal.client.FlavorClientState.nextRequestId();
        setSubmitWaiting(true);
        String key = com.chi__you.cuisinejournal.network.EditVersions.collection(category);
        var versions = java.util.Map.of(key, expectedVersions.getOrDefault(key, 0L));
        Network.toServer(new Packets.SchemeRequest(pending, category, action, id, name, description, versions));
        if (requestStarted != null) requestStarted.accept(pending);
    }

    public int pendingRequest() { return pending; }

    /** Host passes the result for {@link #pendingRequest()}; the list refreshes from the following sync. */
    public void accept(Packets.SchemeResult result) {
        pending = 0;
        setSubmitWaiting(false);
        if (result.success()) {
            if (!result.scheme().isEmpty()) selected = result.scheme();
            else if (mode == Mode.CONFIRM_DELETE) selected = null;
            enter(Mode.LIST);
        }
        refreshButtons();
    }

    // ------------------------------------------------------------------ rendering

    @Override protected void renderBody(GuiGraphics g, int bx, int by, int bw, int bh, int mx, int my, float tick) {
        by -= scrollOffset();
        hits.clear();
        if (mode == Mode.LIST && (selected == null || find(selected) == null)) {
            List<Packets.SchemeView> list = SchemeClientState.list(category);
            selected = list.isEmpty() ? null : list.get(0).id();
        }
        int cy = renderNow(g, bx, by, bw);
        int top = cy, height = Math.max(60, by + bh - 2 - top);
        renderList(g, bx, top, height, mx, my);
        int rx = bx + LIST_W + 6, rw = bw - LIST_W - 6;
        g.fill(rx, top, rx + rw, top + height, 0xFF140F0A);
        JournalTheme.border(g, rx, top, rw, height, 0xFF3A2C1F);
        int ix = rx + 6, iw = rw - 12;
        switch (mode) {
            case LIST -> renderInfo(g, ix, top + 6, iw, top + height - 6, mx, my);
            case SAVE, RENAME, COPY -> renderForm(g, ix, top + 6, iw, mx, my);
            default -> renderConfirm(g, ix, top + 6, iw);
        }
        setScrollContentHeight(bh);
    }

    /** "当前基于 X · 有未保存的修改 / 与方案一致". */
    private int renderNow(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 14, 0xFF140F0A);
        JournalTheme.border(g, x, y, w, 14, 0xFF3A2C1F);
        Packets.SchemeView current = find(SchemeClientState.current(category));
        MutableComponent line = Component.translatable("cuisinejournal.scheme.based_on").withStyle(s -> s.withColor(JournalTheme.TEXT_DIM));
        line.append(" ").append(current == null ? Component.translatable("cuisinejournal.scheme.none")
                .withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT))
                : displayName(current).copy().withStyle(s -> s.withColor(JournalTheme.TEXT).withBold(true)));
        boolean dirty = SchemeClientState.dirty(category);
        Component state = Component.translatable(dirty ? "cuisinejournal.scheme.dirty" : "cuisinejournal.scheme.clean");
        int sw = font.width(state) + (dirty ? 8 : 0);
        int sx = x + w - 5 - sw;
        if (dirty) g.fill(sx, y + 5, sx + 4, y + 9, JournalTheme.TEXT_ACCENT);
        g.drawString(font, state, sx + (dirty ? 8 : 0), y + 3, dirty ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_GOOD, false);
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                font.substrByWidth(line, Math.max(0, sx - x - 10))), x + 5, y + 3, JournalTheme.TEXT_DIM, false);
        return y + 18;
    }

    private void renderList(GuiGraphics g, int x, int y, int h, int mx, int my) {
        g.fill(x, y, x + LIST_W, y + h, 0xFF140F0A);
        JournalTheme.border(g, x, y, LIST_W, h, 0xFF3A2C1F);
        var schemes=SchemeClientState.list(category);
        int headers=(schemes.stream().anyMatch(s->s.source()!=2)?10:0)+(schemes.stream().anyMatch(s->s.source()==2)?10:0);
        listMaximum=Math.max(0,schemes.size()*ROW+headers+6-h);
        listScroll=Math.min(listMaximum,listScroll);listX=x;listY=y;listHeight=h;
        g.enableScissor(x+1,y+1,x+LIST_W-1,y+h-1);
        int cy = y + 3-listScroll;
        boolean ownHeader = false, builtHeader = false;
        String current = SchemeClientState.current(category);
        for (Packets.SchemeView view : schemes) {
            boolean own = view.source() == 2;
            if (!own && !builtHeader) { cy = groupHeader(g, x, cy, "cuisinejournal.scheme.group.builtin"); builtHeader = true; }
            if (own && !ownHeader) { cy = groupHeader(g, x, cy, "cuisinejournal.scheme.group.own"); ownHeader = true; }
            if(cy<y+1||cy+ROW>y+h-1){cy+=ROW;continue;}
            boolean on = view.id().equals(selected), hover = mode == Mode.LIST && mx >= x && mx < x + LIST_W && my >= cy && my < cy + ROW;
            if (on) { g.fill(x + 1, cy, x + LIST_W - 1, cy + ROW, 0x29E8A93C); g.fill(x + 1, cy, x + 3, cy + ROW, JournalTheme.TEXT_ACCENT); }
            else if (hover) g.fill(x + 1, cy, x + LIST_W - 1, cy + ROW, 0x14FFE9C7);
            boolean isCurrent = view.id().equals(current);
            Component mark = Component.translatable("cuisinejournal.scheme.current");
            int markW = isCurrent ? Math.round(font.width(mark) * 0.75F) + 3 : 0;
            String text = font.plainSubstrByWidth(displayName(view).getString(), LIST_W - 10 - markW);
            g.drawString(font, text, x + 6, cy + 3, on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT, false);
            if (isCurrent) small(g, Component.translatable("cuisinejournal.scheme.current"), x + LIST_W - 3 - markW + 1, cy + 4, JournalTheme.TEXT_GOOD);
            String id = view.id();
            if (mode == Mode.LIST) hits.add(new Hit(x, cy, LIST_W, ROW, () -> selected = id));
            cy += ROW;
        }
        g.disableScissor();
        if(listMaximum>0){
            int thumb=Math.max(8,(h-4)*(h-4)/(h+listMaximum));
            int ty=y+2+(h-4-thumb)*listScroll/listMaximum;
            g.fill(x+LIST_W-4,ty,x+LIST_W-2,ty+thumb,JournalTheme.TEXT_DIM);
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta) {
        if(isOpen()&&mx>=listX&&mx<listX+LIST_W&&my>=listY&&my<listY+listHeight){
            listScroll=Math.max(0,Math.min(listMaximum,listScroll-(int)(delta*ROW*2)));return true;
        }
        return super.mouseScrolled(mx,my,delta);
    }

    private int groupHeader(GuiGraphics g, int x, int y, String key) {
        Component text = Component.translatable(key);
        small(g, text, x + 5, y + 2, JournalTheme.TEXT_FAINT);
        int tw = Math.round(font.width(text) * 0.75F);
        g.fill(x + 8 + tw, y + 5, x + LIST_W - 5, y + 6, 0x33FFE9C7);
        return y + 10;
    }

    private void renderInfo(GuiGraphics g, int x, int y, int w, int bottom, int mx, int my) {
        Packets.SchemeView view = find(selected);
        if (view == null) {
            g.drawString(font, Component.translatable("cuisinejournal.scheme.empty"), x, y, JournalTheme.TEXT_FAINT, false);
            return;
        }
        Component title = displayName(view);
        Component badge = Component.translatable(view.source() == 2 ? "cuisinejournal.scheme.badge.own"
                : view.source() == 1 ? "cuisinejournal.scheme.badge.pack" : "cuisinejournal.scheme.badge.builtin");
        int badgeColor = view.source() == 2 ? JournalTheme.TEXT_GOOD : view.source() == 1 ? JournalTheme.ADJUSTED : JournalTheme.TEXT_DIM;
        int bw = Math.round(font.width(badge) * 0.75F) + 6;
        String shown = font.plainSubstrByWidth(title.getString(), w - bw - 6);
        g.drawString(font, Component.literal(shown).withStyle(s -> s.withBold(true)), x, y, JournalTheme.TEXT_ACCENT, false);
        int badgeX = x + font.width(Component.literal(shown).withStyle(s -> s.withBold(true))) + 5;
        JournalTheme.border(g, badgeX, y - 1, bw, 10, (badgeColor & 0x00FFFFFF) | 0x80000000);
        small(g, badge, badgeX + 3, y + 1, badgeColor);
        int cy = y + 12;
        Component desc = view.source() == 0 ? Component.translatable(category ? "cuisinejournal.scheme.default_desc.category"
                : "cuisinejournal.scheme.default_desc.flavor") : Component.literal(view.description());
        if (!desc.getString().isEmpty()) {
            for (var line : font.split(desc, w)) { g.drawString(font, line, x, cy, JournalTheme.TEXT_DIM, false); cy += 10; }
        }
        cy += 3;
        Component content = Component.translatable("cuisinejournal.scheme.content");
        small(g, content, x, cy, JournalTheme.TEXT_FAINT);
        int tw = Math.round(font.width(content) * 0.75F);
        g.fill(x + tw + 4, cy + 3, x + w, cy + 4, 0x33FFE9C7);
        cy += 9;
        cy = countRows(g, view.counts(), x, cy, w);
        String meta = view.source() == 2 && !view.saved().isEmpty()
                ? Component.translatable("cuisinejournal.scheme.saved_at", view.saved().replace('T', ' ')).getString()
                : view.source() == 1 ? Component.translatable("cuisinejournal.scheme.from_pack", view.id().split(":")[0]).getString()
                : Component.translatable("cuisinejournal.scheme.from_mod").getString();
        small(g, Component.literal(meta), x, cy + 2, JournalTheme.TEXT_FAINT);
        // Actions, wrapped from the bottom up.
        if (!SchemeClientState.canEdit()) return;
        List<Object[]> actions = new ArrayList<>();
        actions.add(new Object[]{"cuisinejournal.scheme.load", true, (Runnable) () -> enter(Mode.CONFIRM_LOAD)});
        if (view.source() == 2) actions.add(new Object[]{"cuisinejournal.scheme.overwrite_short", false, (Runnable) () -> enter(Mode.CONFIRM_OVERWRITE)});
        else actions.add(new Object[]{"cuisinejournal.scheme.copy", false, (Runnable) () -> enter(Mode.COPY)});
        actions.add(new Object[]{"cuisinejournal.scheme.export_short", false, (Runnable) () -> enter(Mode.CONFIRM_EXPORT)});
        int ay = bottom - 12 - (view.source() == 2 ? 13 : 0);
        int ax = x;
        for (Object[] a : actions) {
            Component label = Component.translatable((String) a[0]);
            int bwid = font.width(label) + 10;
            if (ax > x && ax + bwid > x + w) { ax = x; ay -= 14; }
            button(g, ax, ay, bwid, label, (Boolean) a[1], false, mx, my, (Runnable) a[2]);
            ax += bwid + 4;
        }
        if (view.source() == 2) {
            int ly = bottom - 10;
            Component rename = Component.translatable("cuisinejournal.scheme.rename");
            Component delete = Component.translatable("cuisinejournal.scheme.delete");
            link(g, x, ly, rename, JournalTheme.TEXT_DIM, mx, my, () -> enter(Mode.RENAME));
            link(g, x + font.width(rename) + 10, ly, delete, JournalTheme.DANGER, mx, my, () -> enter(Mode.CONFIRM_DELETE));
        }
    }

    private int countRows(GuiGraphics g, List<Integer> counts, int x, int y, int w) {
        List<Component[]> rows = new ArrayList<>();
        if (!category) {
            rows.add(new Component[]{Component.translatable("cuisinejournal.scheme.count.flavors"), countText(counts, 0, "cuisinejournal.scheme.unit.items")});
            rows.add(new Component[]{Component.translatable("cuisinejournal.scheme.count.bindings"), countText(counts, 1, "cuisinejournal.scheme.unit.kinds")});
        } else {
            int roots = get(counts, 0), subs = get(counts, 1);
            rows.add(new Component[]{Component.translatable("cuisinejournal.scheme.count.created"), roots + subs == 0 ? dash()
                    : Component.translatable("cuisinejournal.scheme.unit.created", roots, subs)});
            rows.add(new Component[]{Component.translatable("cuisinejournal.scheme.count.assigned"), countText(counts, 2, "cuisinejournal.scheme.unit.dishes")});
            rows.add(new Component[]{Component.translatable("cuisinejournal.scheme.count.hidden"), countText(counts, 3, "cuisinejournal.scheme.unit.items")});
            rows.add(new Component[]{Component.translatable("cuisinejournal.scheme.count.changed"), countText(counts, 4, "cuisinejournal.scheme.unit.items")});
        }
        for (Component[] row : rows) {
            g.drawString(font, row[0], x, y, JournalTheme.TEXT_DIM, false);
            JournalTheme.value(g, font, row[1], x + w, y, JournalTheme.TEXT);
            y += 10;
        }
        return y;
    }

    private static int get(List<Integer> counts, int index) { return index < counts.size() ? counts.get(index) : 0; }
    private static Component dash() { return Component.literal("—").withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)); }
    private static Component countText(List<Integer> counts, int index, String unit) {
        int value = get(counts, index);
        return value == 0 ? dash() : Component.translatable(unit, value);
    }

    private void renderForm(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String key = switch (mode) {
            case SAVE -> category ? "cuisinejournal.scheme.save_title.category" : "cuisinejournal.scheme.save_title.flavor";
            case RENAME -> "cuisinejournal.scheme.rename_title";
            default -> "cuisinejournal.scheme.copy_title";
        };
        g.drawString(font, Component.translatable(key), x, y, JournalTheme.TEXT, false);
        int cy = y + 14;
        name.setBounds(x, cy, w);
        name.render(g, mx, my);
        cy += name.totalBounds().height + 2;
        if (mode == Mode.SAVE) {
            description.setBounds(x, cy, w);
            description.render(g, mx, my);
            cy += description.totalBounds().height + 2;
            List<Integer> counts = SchemeClientState.localCounts(category);
            small(g, Component.translatable("cuisinejournal.scheme.will_save"), x, cy, JournalTheme.TEXT_FAINT);
            cy = countRows(g, counts, x, cy + 9, w);
        }
        if (mode != Mode.RENAME) {
            String typed = name.getValue().strip();
            for (Packets.SchemeView view : SchemeClientState.list(category)) {
                if (view.source() == 2 && view.name().equals(typed) && mode == Mode.SAVE) {
                    for (var line : font.split(Component.translatable("cuisinejournal.scheme.duplicate", typed), w)) {
                        g.drawString(font, line, x, cy + 2, 0xFFD9A05B, false); cy += 10;
                    }
                    break;
                }
            }
        }
    }

    private void renderConfirm(GuiGraphics g, int x, int y, int w) {
        Packets.SchemeView view = find(selected);
        if (view == null) return;
        Component name = displayName(view);
        String titleKey = switch (mode) {
            case CONFIRM_LOAD -> "cuisinejournal.scheme.confirm.load";
            case CONFIRM_OVERWRITE -> "cuisinejournal.scheme.confirm.overwrite";
            case CONFIRM_DELETE -> "cuisinejournal.scheme.confirm.delete";
            default -> "cuisinejournal.scheme.confirm.export";
        };
        int cy = y;
        for (var line : font.split(Component.translatable(titleKey, name), w)) {
            g.drawString(font, line, x, cy, mode == Mode.CONFIRM_DELETE ? JournalTheme.DANGER : JournalTheme.TEXT, false); cy += 11;
        }
        cy += 4;
        List<Component> body = new ArrayList<>();
        switch (mode) {
            case CONFIRM_LOAD -> {
                body.add(Component.translatable(category ? "cuisinejournal.scheme.load_body.category" : "cuisinejournal.scheme.load_body.flavor"));
                if (SchemeClientState.dirty(category)) body.add(Component.translatable("cuisinejournal.scheme.load_lose")
                        .withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT)));
            }
            case CONFIRM_OVERWRITE -> body.add(Component.translatable("cuisinejournal.scheme.overwrite_body"));
            case CONFIRM_DELETE -> body.add(Component.translatable("cuisinejournal.scheme.delete_body"));
            default -> {
                body.add(Component.translatable(category ? "cuisinejournal.scheme.export_body.category"
                        : "cuisinejournal.scheme.export_body.flavor", com.chi__you.cuisinejournal.recipe.FlavorPresets.CUSTOM_NAMESPACE));
                String current = SchemeClientState.current(category);
                if (view.id().equals(current) || find(current) == null && view.source() == 0) {
                    body.add(Component.translatable("cuisinejournal.scheme.export_current")
                            .withStyle(style -> style.withColor(JournalTheme.TEXT_ACCENT)));
                }
            }
        }
        for (Component part : body) {
            for (var line : font.split(part, w)) { g.drawString(font, line, x, cy, JournalTheme.TEXT_DIM, false); cy += 10; }
            cy += 4;
        }
    }

    // ------------------------------------------------------------------ widgets

    private void button(GuiGraphics g, int x, int y, int w, Component label, boolean primary, boolean danger, int mx, int my, Runnable run) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 12;
        g.fill(x, y, x + w, y + 12, primary ? (hover ? 0xFF6B4A1C : 0xFF4A3418) : (hover ? 0xFF2A2018 : 0xFF1B140E));
        JournalTheme.border(g, x, y, w, 12, primary ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER);
        g.drawString(font, label, x + 5, y + 2, danger ? JournalTheme.DANGER : primary || hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
        hits.add(new Hit(x, y, w, 12, run));
    }

    private void link(GuiGraphics g, int x, int y, Component label, int color, int mx, int my, Runnable run) {
        int w = font.width(label);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 10;
        g.drawString(font, label, x, y, hover ? JournalTheme.TEXT : color, false);
        if (hover) g.fill(x, y + 9, x + w, y + 10, hover ? JournalTheme.TEXT : color);
        hits.add(new Hit(x, y, w, 10, run));
    }

    private void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override protected boolean bodyClickedFirst(double mx, double my, int button) {
        if (button != 0 || pending != 0) return pending != 0;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
            hit.action().run();
            refreshButtons();
            return true;
        }
        return false;
    }

    @Override protected boolean bodyKeyPressed(int key, int scan, int modifiers) {
        refreshButtons();
        return false;
    }

    @Override protected boolean bodyCharTyped(char c, int modifiers) {
        refreshButtons();
        return false;
    }
}
