package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.data.NbtUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's progress through one challenge.
 *
 * <p>Objectives that count distinct subjects keep the subjects themselves, not
 * just a number, so "prepare 10 unique recipes you have never cooked" cannot be
 * satisfied by cooking the same dish ten times.</p>
 */
public final class ChallengeState {
    private int[] counters;
    private final List<Set<ResourceLocation>> seen;
    private boolean completed;
    private long completedDay = Long.MIN_VALUE;
    private long period=Long.MIN_VALUE, started=Long.MIN_VALUE, deadline=Long.MAX_VALUE;
    private boolean failed;
    private final java.util.Deque<ResourceLocation> variety=new java.util.ArrayDeque<>();
    private long uniqueDay=Long.MIN_VALUE;
    private final Set<ResourceLocation> dailySeen=new LinkedHashSet<>();

    public long deadline(){return deadline;}
    public boolean started(){return started!=Long.MIN_VALUE;}
    public long period(){return period;}
    public boolean prepareAutomatic(long day,long now,long end,int duration,boolean perDay){
        if(completed)return false;
        if (!started() && (java.util.Arrays.stream(counters).anyMatch(n->n>0) || seen.stream().anyMatch(s->!s.isEmpty()))) {
            started=now; deadline=perDay?end:Long.MAX_VALUE; period=day; failed=false; return true;
        }
        if(perDay){
            if(!started()||period!=day){reset();period=day;started=now;deadline=end;return true;}
            boolean changed=failed||deadline!=end;failed=false;deadline=end;return changed;
        }
        if(duration==0&&started()){
            boolean changed=failed||deadline!=Long.MAX_VALUE;failed=false;deadline=Long.MAX_VALUE;return changed;
        }
        if(!started()||failed||now>=deadline){start(now,duration);return true;}
        return false;
    }
    public void limitDeadline(long end){deadline=Math.min(deadline,end);}
    public boolean failed(){return failed;}
    public boolean expire(long now){if(started()&&!completed&&!failed&&now>=deadline){failed=true;return true;}return false;}
    public void start(long now,int duration){reset();started=now;deadline=duration>0?now+duration:Long.MAX_VALUE;}
    public boolean preparePeriod(long next,long now,long end,boolean auto){
        if(period!=next){
            boolean legacy=period==Long.MIN_VALUE&&(completed||java.util.Arrays.stream(counters).anyMatch(n->n>0));
            if(!legacy)reset();period=next;started=auto?now:Long.MIN_VALUE;deadline=end;
            return true;
        }
        boolean changed=deadline!=end||failed||auto&&!started();
        deadline=end;failed=false;if(auto&&!started())started=now;
        return changed;
    }
    public int variety(ResourceLocation meal){if(variety.contains(meal))variety.clear();variety.addLast(meal);return variety.size();}
    public int dailyVariety(ResourceLocation meal,long day){if(uniqueDay!=day){dailySeen.clear();uniqueDay=day;}dailySeen.add(meal);return dailySeen.size();}

    public ChallengeState(int objectiveCount) {
        this.counters = new int[Math.max(1, objectiveCount)];
        this.seen = new ArrayList<>(counters.length);
        for (int i = 0; i < counters.length; i++) {
            seen.add(new LinkedHashSet<>());
        }
    }

    public boolean isCompleted() {
        return completed;
    }
    public void ensureObjectives(int count) {
        int size = Math.max(1,Math.min(16,count));
        if (size <= counters.length) return;
        counters = java.util.Arrays.copyOf(counters,size);
        while (seen.size()<size) seen.add(new LinkedHashSet<>());
    }

    public long completedDay() {
        return completedDay;
    }

    public int progress(int objectiveIndex) {
        return objectiveIndex >= 0 && objectiveIndex < counters.length
                ? counters[objectiveIndex] : 0;
    }

    /** @return {@code true} when this call actually moved the objective forward */
    public boolean advance(int objectiveIndex, int amount) {
        if (completed || objectiveIndex < 0 || objectiveIndex >= counters.length || amount <= 0) {
            return false;
        }
        counters[objectiveIndex] = (int)Math.min(Integer.MAX_VALUE,(long)counters[objectiveIndex]+amount);
        return true;
    }

    /** @return {@code true} when {@code subject} had not been counted before */
    public boolean advanceUnique(int objectiveIndex, ResourceLocation subject) {
        if (completed || objectiveIndex < 0 || objectiveIndex >= counters.length
                || subject == null) {
            return false;
        }
        if (!seen.get(objectiveIndex).add(subject)) {
            return false;
        }
        counters[objectiveIndex] = seen.get(objectiveIndex).size();
        return true;
    }

    /** Used by objectives that track a best-so-far rather than a running total. */
    public boolean raise(int objectiveIndex, int value) {
        if (completed || objectiveIndex < 0 || objectiveIndex >= counters.length
                || value <= counters[objectiveIndex]) {
            return false;
        }
        counters[objectiveIndex] = value;
        return true;
    }

    public boolean satisfies(List<ChallengeObjective> objectives) {
        for (int i = 0; i < objectives.size() && i < counters.length; i++) {
            if (counters[i] < objectives.get(i).amount()) {
                return false;
            }
        }
        return objectives.size() <= counters.length;
    }

    public void complete(long day) {
        completed = true;
        completedDay = day;
    }

    public void reset() {
        java.util.Arrays.fill(counters, 0);
        seen.forEach(Set::clear);
        completed = false;
        completedDay = Long.MIN_VALUE;
        started=Long.MIN_VALUE;deadline=Long.MAX_VALUE;failed=false;variety.clear();dailySeen.clear();uniqueDay=Long.MIN_VALUE;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("period",period);tag.putLong("started",started);tag.putLong("deadline",deadline);tag.putBoolean("failed",failed);
        var order=new ListTag();variety.forEach(id->order.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));tag.put("variety",order);
        tag.put("dailySeen",NbtUtil.writeIds(dailySeen));tag.putLong("uniqueDay",uniqueDay);
        tag.putIntArray("counters", counters.clone());
        NbtUtil.putIfTrue(tag, "completed", completed);
        if (completedDay != Long.MIN_VALUE) {
            tag.putLong("completedDay", completedDay);
        }
        boolean anySeen = seen.stream().anyMatch(set -> !set.isEmpty());
        if (anySeen) {
            ListTag list = new ListTag();
            for (Set<ResourceLocation> subjects : seen) {
                CompoundTag entry = new CompoundTag();
                entry.put("ids", NbtUtil.writeIds(subjects));
                list.add(entry);
            }
            tag.put("seen", list);
        }
        return tag;
    }

    public static ChallengeState load(CompoundTag tag, int objectiveCount) {
        int[] counters = tag.getIntArray("counters");
        ChallengeState state = new ChallengeState(Math.max(objectiveCount, counters.length));
        state.period=tag.contains("period")?tag.getLong("period"):Long.MIN_VALUE;
        state.started=tag.contains("started")?tag.getLong("started"):Long.MIN_VALUE;
        state.deadline=tag.contains("deadline")?tag.getLong("deadline"):Long.MAX_VALUE;
        state.failed=tag.getBoolean("failed");state.uniqueDay=tag.contains("uniqueDay")?tag.getLong("uniqueDay"):Long.MIN_VALUE;
        NbtUtil.forEachId(tag,"variety",state.variety::add);NbtUtil.forEachId(tag,"dailySeen",state.dailySeen::add);
        System.arraycopy(counters, 0, state.counters, 0,
                Math.min(counters.length, state.counters.length));
        state.completed = tag.getBoolean("completed");
        state.completedDay = tag.contains("completedDay") ? tag.getLong("completedDay")
                : Long.MIN_VALUE;
        if (tag.contains("seen", Tag.TAG_LIST)) {
            ListTag list = tag.getList("seen", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && i < state.seen.size(); i++) {
                final int index = i;
                NbtUtil.forEachId(list.getCompound(i), "ids",
                        id -> state.seen.get(index).add(id));
            }
        }
        return state;
    }
}
