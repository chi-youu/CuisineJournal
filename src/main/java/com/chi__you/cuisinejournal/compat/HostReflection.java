package com.chi__you.cuisinejournal.compat;

import java.lang.reflect.Field;

/** Optional host access without a dependency on workstation layouts or a particular host. */
public final class HostReflection {
    private HostReflection() {}
    private static final ClassValue<java.util.concurrent.ConcurrentMap<String,java.util.Optional<Field>>> FIELDS = new ClassValue<>() {
        protected java.util.concurrent.ConcurrentMap<String,java.util.Optional<Field>> computeValue(Class<?> type) {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }
    };
    public static Object field(Object object,String name) {
        if(object==null)return null;
        var cached=FIELDS.get(object.getClass()).computeIfAbsent(name,key->{
            for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) {
                try { Field field=type.getDeclaredField(key);field.setAccessible(true);return java.util.Optional.of(field); }
                catch(NoSuchFieldException ignored) {}
                catch(RuntimeException denied){return java.util.Optional.empty();}
            }
            return java.util.Optional.empty();
        });
        try {return cached.isEmpty()?null:cached.get().get(object);}
        catch(ReflectiveOperationException | RuntimeException failure){return null;}
    }
    public static Object inventory(Object object) {
        if(object==null)return null;
        for(String method:new String[]{"getInventory","getItemHandler"}) {
            try { return object.getClass().getMethod(method).invoke(object); }
            catch(ReflectiveOperationException ignored) {}
        }
        Object inventory=field(object,"items");
        if(inventory==null)inventory=field(object,"inventory");
        if(inventory==null)inventory=field(object,"itemHandler");
        return inventory;
    }
    public static Object invoke(Object object,String name,Object... args) {
        if(object==null)return null;
        for(java.lang.reflect.Method method:object.getClass().getMethods()) {
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            try { return method.invoke(object,args); }
            catch(ReflectiveOperationException | RuntimeException ignored) {}
        }
        return null;
    }
    public static Object invokeDeclared(Object object,String name,Object... args){
        if(object==null)return null;
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())for(var method:type.getDeclaredMethods()){
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            try{method.setAccessible(true);return method.invoke(object,args);}
            catch(ReflectiveOperationException|RuntimeException failure){return null;}
        }
        return null;
    }
}
