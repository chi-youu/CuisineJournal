package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.culinary.ActivityEntry;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.network.Packets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Activity quantities and quality snapshots, independent of rendering. */
public final class ActivityPresentation {
    public static final int PERFECT_COLOR = 0xFFD08CFF;
    public record Folded(Packets.ActivityView entry, long count) {}
    private ActivityPresentation() {}

    public static boolean preparation(Packets.ActivityView entry) {
        return ActivityEntry.Type.MEAL_PREPARED.id().equals(entry.type());
    }

    public static boolean sameLine(Packets.ActivityView left, Packets.ActivityView right) {
        if (!left.type().equals(right.type()) || !Objects.equals(left.subject(), right.subject())) return false;
        return preparation(left) ? left.stars() == right.stars() && left.perfect() == right.perfect()
                && left.starCap() == right.starCap() && Objects.equals(left.cookId(), right.cookId())
                && left.cookName().equals(right.cookName()) : left.detail() == right.detail();
    }

    public static List<Folded> fold(List<Packets.ActivityView> entries) {
        List<Folded> result = new ArrayList<>();
        for (var entry : entries) {
            long amount = preparation(entry) ? entry.amount() : 1;
            Folded last = result.isEmpty() ? null : result.get(result.size() - 1);
            if (last != null && sameLine(last.entry(), entry)) {
                long total = last.count() > Long.MAX_VALUE - amount ? Long.MAX_VALUE : last.count() + amount;
                result.set(result.size() - 1, new Folded(last.entry(), total));
            } else result.add(new Folded(entry, amount));
        }
        return List.copyOf(result);
    }

    public static MealStamp stampFor(Packets.ActivityView entry) {
        return preparation(entry) ? new MealStamp(entry.stars(), entry.perfect(), entry.starCap(),
                entry.cookId(), entry.cookName()) : null;
    }
}
