package com.chi__you.cuisinejournal.food;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/** Nested host calls and exceptions cannot leave another player's consumption context active. */
final class ConsumptionScope<T> {
    private final ThreadLocal<Deque<T>> frames=new ThreadLocal<>();

    T current() {
        Deque<T> stack=frames.get();return stack==null?null:stack.peek();
    }

    <R> R run(T frame,Function<T,R> host) {
        Deque<T> stack=frames.get();
        if(stack==null){stack=new ArrayDeque<>();frames.set(stack);}
        stack.push(frame);
        try {return host.apply(frame);}
        finally {stack.pop();if(stack.isEmpty())frames.remove();}
    }
}
