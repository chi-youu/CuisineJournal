package com.chi__you.cuisinejournal.food;

/** Actual thread-local scope isolation, nested calls and exception cleanup. */
public final class ConsumptionScopeTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        ConsumptionScope<String> scope=new ConsumptionScope<>();
        check(scope.current()==null,"no active context before a host call");
        String result=scope.run("A",frame->{
            check(scope.current().equals("A"),"outer player selected");
            scope.run("B",inner->{check(scope.current().equals("B"),"inner player selected");return null;});
            check(scope.current().equals("A"),"nested return restores outer player");
            try {scope.run("C",inner->{throw new IllegalArgumentException("host failed");});}
            catch(IllegalArgumentException expected){}
            check(scope.current().equals("A"),"nested exception restores outer player");
            return "ok";
        });
        check(result.equals("ok")&&scope.current()==null,"normal return has no stale context");
        try{scope.run("D",frame->{throw new IllegalStateException("failure");});}
        catch(IllegalStateException expected){}
        check(scope.current()==null,"host exception removes context");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        scope.run("server",frame->{
            Thread other=new Thread(()->{
                try {
                    if(scope.current()!=null)throw new AssertionError("cross-thread context leaked");
                    scope.run("client",inner->{if(!scope.current().equals("client"))throw new AssertionError();return null;});
                    if(scope.current()!=null)throw new AssertionError("client cleanup failed");
                }catch(Throwable error){failure.set(error);}
            });
            other.start();try{other.join();}catch(InterruptedException error){throw new AssertionError(error);}
            check(failure.get()==null&&scope.current().equals("server"),"logical client/server threads remain isolated");
            return null;
        });
        check(scope.current()==null,"no context after cross-thread test");
        System.out.println("ConsumptionScopeTest: "+checks+" checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
