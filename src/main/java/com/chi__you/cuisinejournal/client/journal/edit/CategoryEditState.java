package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.EditClientState;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** Local selections and one in-flight edit, scoped to one journal screen. */
public final class CategoryEditState {
    private static int nextRequest;
    public final Set<ResourceLocation> selected = new LinkedHashSet<>();
    public ResourceLocation lastClicked;
    public boolean addingFoods;
    private int pending;
    private int waitingTicks;
    private Runnable afterSuccess;
    private Map<String, Long> selectionVersions;

    public boolean waiting() { return pending != 0; }
    public void clearSelection() { selected.clear(); lastClicked = null; selectionVersions = null; }
    public void beginSelection() {
        if (selectionVersions == null) selectionVersions = Map.copyOf(EditClientState.snapshot());
    }
    public Map<String, Long> selectionVersions() {
        return selectionVersions == null ? Map.of() : selectionVersions;
    }
    public void reset() { clearSelection(); addingFoods = false; }
    public void select(ResourceLocation food, List<ResourceLocation> visible, boolean range) {
        if (food == null || visible == null) return;
        beginSelection();
        int start = lastClicked == null ? -1 : visible.indexOf(lastClicked);
        int end = visible.indexOf(food);
        if (range && start >= 0 && end >= 0) selected.addAll(visible.subList(Math.min(start, end), Math.max(start, end) + 1));
        else if (!selected.add(food)) selected.remove(food);
        lastClicked = food;
        if (selected.isEmpty()) selectionVersions = null;
    }
    public void submit(Packets.CategoryEditRequest request, Runnable success) {
        if (waiting() || !ClientJournalState.canEditCategories() || !ClientJournalState.categoryEditing()) return;
        pending = ++nextRequest; waitingTicks = 0; afterSuccess = success;
        Network.toServer(new Packets.CategoryEditRequest(pending, request.action(), request.targetId(),
                request.parent(), request.name(), request.icon(), request.items(), outgoingVersions(request)));
    }

    static Map<String, Long> outgoingVersions(Packets.CategoryEditRequest request) {
        boolean destructive = switch (request.action()) {
            case DELETE_ROOT, BLOCK_ROOT, DELETE_SUB, BLOCK_SUB -> true;
            default -> false;
        };
        Set<String> foods = new LinkedHashSet<>();
        request.items().forEach(id -> foods.add("category:food:" + id));
        Set<String> categories = new LinkedHashSet<>();
        var metadata = ClientJournalState.categoryEditor().categories();
        if (request.targetId() != null) {
            String target = request.targetId().toString();
            boolean root = request.action() == Packets.CategoryEditRequest.Action.CREATE_ROOT
                    || request.action() == Packets.CategoryEditRequest.Action.EDIT_ROOT
                    || request.action() == Packets.CategoryEditRequest.Action.RESET_ROOT
                    || request.action() == Packets.CategoryEditRequest.Action.RESTORE_ROOT
                    || metadata.stream().anyMatch(value -> value.id().equals(request.targetId()) && value.parent() == null);
            categories.add("category:" + (root ? "root:" : "sub:") + target);
        }
        if (request.parent() != null) categories.add("category:root:" + request.parent());
        metadata.stream().filter(value -> value.id().equals(request.targetId()) && value.parent() != null)
                .forEach(value -> categories.add("category:root:" + value.parent()));
        if (destructive && request.targetId() != null) {
            ResourceLocation target = request.targetId();
            boolean root = request.action() == Packets.CategoryEditRequest.Action.DELETE_ROOT
                    || request.action() == Packets.CategoryEditRequest.Action.BLOCK_ROOT
                    || metadata.stream().anyMatch(value -> value.id().equals(target) && value.parent() == null);
            categories.add("category:children:" + target);
            categories.add("category:members:" + target);
            for (var value : metadata) {
                if ((root && Objects.equals(value.parent(), target)) || (!root && value.id().equals(target))) {
                    categories.add("category:sub:" + value.id());
                    categories.add("category:members:" + value.id());
                }
            }
            for (var dish : ClientJournalState.dishes()) {
                if (Objects.equals(dish.category(), target) || Objects.equals(dish.subcategory(), target)
                        || metadata.stream().anyMatch(value -> value.id().equals(dish.subcategory())
                        && Objects.equals(value.parent(), target)))
                    foods.add("category:food:" + dish.target());
            }
        }
        Map<String, Long> result = new LinkedHashMap<>();
        request.expectedVersions().forEach((key, version) -> {
            if (key.startsWith("category:") && (destructive || categories.contains(key) || foods.contains(key)))
                result.put(key, version);
        });
        return Map.copyOf(result);
    }
    public Packets.CategoryEditResult poll() {
        if (!waiting()) return null;
        var result = ClientJournalState.lastCategoryEditResult();
        if (result != null && result.requestId() == pending) {
            pending = 0;
            Runnable success = afterSuccess; afterSuccess = null;
            if (result.success() && success != null) success.run();
            return result;
        }
        if (++waitingTicks > 200) {
            int id = pending; pending = 0; afterSuccess = null;
            return new Packets.CategoryEditResult(id, false, "cuisinejournal.edit.failed.timeout", List.of());
        }
        return null;
    }
}
