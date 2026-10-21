package com.chi__you.cuisinejournal.data;

public final class CatalogueEditCoordinatorTest {
    public static void main(String[] args) throws Exception {
        deferredRebuilds();
        var edit = CatalogueEditCoordinator.begin(CatalogueEditCoordinator.Kind.EDIT);
        check(edit != null && edit.current(), "edit owns lease");
        check(CatalogueEditCoordinator.begin(CatalogueEditCoordinator.Kind.UNIFICATION) == null, "one lock across edit/reload/export");
        edit.phase(CatalogueEditCoordinator.Kind.EXPORT);
        check(CatalogueEditCoordinator.busy(CatalogueEditCoordinator.Kind.EXPORT), "busy display is a phase, not another lock");
        CatalogueEditCoordinator.stop();
        var next = CatalogueEditCoordinator.begin(CatalogueEditCoordinator.Kind.UNIFICATION);
        edit.close();
        check(next.current() && CatalogueEditCoordinator.busy(), "old completion cannot unlock a new world operation");
        boolean rejected = false;
        try { edit.phase(CatalogueEditCoordinator.Kind.EDIT); } catch (IllegalStateException expired) { rejected=true; }
        check(rejected, "expired lease cannot change new operation phase");
        next.close();next.close();
        check(!CatalogueEditCoordinator.busy(), "close is idempotent");
        var directory=java.nio.file.Files.createTempDirectory("cj-coordinated-edit");
        var original=directory.resolve("cuisinejournal-flavors.json");
        java.nio.file.Files.writeString(original,"before");
        boolean[] synced={false};
        try {
            CatalogueEditCoordinator.apply(directory,()->write(original,"after"),
                    ()->{throw new IllegalStateException("rebuild failed");},()->synced[0]=true);
            throw new AssertionError("rebuild failure must propagate");
        } catch(IllegalStateException expected) {
            check(java.nio.file.Files.readString(original).equals("before"),"shared transaction restores pre-mutation file");
            check(!synced[0],"failed rebuild cannot report successful synchronization");
        }
        System.out.println("CatalogueEditCoordinatorTest: leases, deferred/coalesced rebuilds, failure cleanup and stale callbacks passed");
    }
    private static void deferredRebuilds() {
        CatalogueEditCoordinator.stop();
        var events=new java.util.ArrayList<String>();
        CatalogueEditCoordinator.requestRebuild(()->{
            check(CatalogueEditCoordinator.busy(),"immediate rebuild holds a lease");events.add("immediate");
        });
        check(!CatalogueEditCoordinator.busy(),"immediate rebuild releases ownership");
        for(var phase:CatalogueEditCoordinator.Kind.values()){
            var lease=CatalogueEditCoordinator.begin(phase);
            CatalogueEditCoordinator.requestRebuild(()->events.add("superseded"));
            CatalogueEditCoordinator.requestRebuild(()->{
                check(CatalogueEditCoordinator.busy(),"deferred rebuild retains exclusive ownership");events.add(phase.name());
            });
            check(!events.contains(phase.name()),"reload cannot rebuild inside "+phase);
            lease.close();lease.close();
            check(events.stream().filter(phase.name()::equals).count()==1&&!events.contains("superseded"),
                    "repeated tag updates coalesce and close is idempotent");
        }
        var old=CatalogueEditCoordinator.begin(CatalogueEditCoordinator.Kind.EXPORT);
        CatalogueEditCoordinator.requestRebuild(()->events.add("stale"));
        CatalogueEditCoordinator.stop();
        var next=CatalogueEditCoordinator.begin(CatalogueEditCoordinator.Kind.EDIT);
        old.close();check(next.current()&&!events.contains("stale"),"world stop drops pending old-world rebuilds");next.close();
        try{CatalogueEditCoordinator.requestRebuild(()->{throw new IllegalStateException("test rebuild failed");});}
        catch(IllegalStateException expected){check(!CatalogueEditCoordinator.busy(),"failed rebuild releases ownership");}
        CatalogueEditCoordinator.stop();
    }
    private static void write(java.nio.file.Path file,String value) {
        try {java.nio.file.Files.writeString(file,value);} catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
