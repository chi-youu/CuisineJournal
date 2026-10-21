package com.chi__you.cuisinejournal.compat;

import java.util.*;

/** Keeps unrelated pack priority stable while replacing Compat's built-in selection. */
public final class UnificationPacks {
    private UnificationPacks() {}

    public static boolean managed(String id) {
        return id.equals(UnificationRulePack.ID)
                || id.startsWith("kaleidoscope_compat:");
    }

    public static List<String> replace(Collection<String> previous, Collection<String> replacement) {
        var result = new ArrayList<String>();
        boolean inserted = false;
        for (String id : previous) {
            if (managed(id)) {
                if (!inserted) { result.addAll(replacement); inserted = true; }
            } else result.add(id);
        }
        if (!inserted) {
            int at=0;
            while(at<result.size()&&!result.get(at).startsWith("file/"))at++;
            result.addAll(at,replacement);
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }
    public static List<net.minecraft.server.packs.repository.Pack> requiredOrder(
            Collection<String> requested,List<net.minecraft.server.packs.repository.Pack> selected) {
        if(requested.contains(UnificationRulePack.ID))return selected;
        var own=selected.stream().filter(pack->pack.getId().equals(UnificationRulePack.ID)).findFirst();
        if(own.isEmpty())return selected;
        var result=new ArrayList<>(selected);result.remove(own.get());
        int at=0;while(at<result.size()&&!result.get(at).getId().startsWith("file/"))at++;
        result.add(at,own.get());return List.copyOf(result);
    }
}
