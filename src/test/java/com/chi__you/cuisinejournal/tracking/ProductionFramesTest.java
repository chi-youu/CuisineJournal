package com.chi__you.cuisinejournal.tracking;

import java.util.Deque;

public final class ProductionFramesTest {
    private record Frame(Object owner, String value) {}
    public static void main(String[] args) throws Exception {
        hotSpringOwners();
        var frames = ProductionFrames.<Frame>stack();
        var values = ProductionFrames.<String>value();
        Object outer = new Object(), inner = new Object();
        frames.get().push(new Frame(outer,"outer"));
        check(ProductionFrames.take(frames.get(), value -> value.owner == inner) == null, "absent inner owner cannot pop outer");
        check(frames.get().peek().owner == outer, "outer survives skipped begin/end");
        frames.get().push(new Frame(inner,null));
        check(frames.get().peek().value == null, "inactive inner barrier hides outer producer");
        check(ProductionFrames.take(frames.get(), value -> value.owner == inner).value == null, "inactive end consumes only its own frame");
        check(frames.get().peek().owner == outer, "outer restored after inactive inner");
        values.set("outer");
        try { values.run("inner", () -> { throw new IllegalStateException("host failure"); }); }
        catch (IllegalStateException expected) { check(values.get().equals("outer"), "finally restores prior context"); }
        ProductionFrames.expireAll();
        check(frames.get().isEmpty() && values.get() == null, "tick cleanup covers every registered stack and singleton");
        System.out.println("ProductionFramesTest: nesting, skipped begin, exceptions and registered cleanup passed");
    }
    @SuppressWarnings("unchecked")
    private static void hotSpringOwners() throws Exception {
        var field=HotSpringProduction.class.getDeclaredField("BATCH");field.setAccessible(true);
        var state=(ProductionFrames<Deque<Object>>)field.get(null);
        var type=Class.forName(HotSpringProduction.class.getName()+"$Context");
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var outer=new net.minecraft.core.BlockPos(1,2,3);var inner=new net.minecraft.core.BlockPos(4,5,6);
        Object frame=constructor.newInstance(null,outer,java.util.List.of());
        state.get().push(frame);HotSpringProduction.end(null,inner);
        check(state.get().peek()==frame,"unmatched hot spring end preserves the outer location");
        state.get().push(constructor.newInstance(null,inner,java.util.List.of()));
        HotSpringProduction.end(null,inner);check(state.get().peek()==frame,"hot spring end restores its own outer frame");
        HotSpringProduction.end(null,outer);check(state.get().isEmpty(),"hot spring stack drains when matching outer ends");state.remove();
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
