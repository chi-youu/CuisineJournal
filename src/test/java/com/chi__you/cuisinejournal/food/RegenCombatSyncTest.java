package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.client.hud.CombatCountdown;
import com.chi__you.cuisinejournal.network.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Shared countdown state and transport, including grace/effect changes and client pause. */
public final class RegenCombatSyncTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var timing=new FoodCombatTiming();
        var clock=new CombatCountdown();
        check(timing.remaining(1000,120,false)==0,"no attack starts no recovery wait");
        timing.enter(1000);
        clock.accept(roundTrip(timing.remaining(1000,120,false)));
        check(clock.remaining()==120,"server combat entry supplies six seconds including projectile entry");
        for(int i=0;i<20;i++)clock.tick(false);
        check(clock.remaining()==100,"client interpolates each unpaused tick");
        for(int i=0;i<30;i++)clock.tick(true);
        check(clock.remaining()==100,"single-player pause freezes the countdown");
        clock.accept(roundTrip(timing.remaining(1020,120,true)));
        check(clock.remaining()==40,"warm heart arrival uses server's original combat timestamp");
        clock.accept(roundTrip(timing.remaining(1020,120,false)));
        check(clock.remaining()==100,"warm heart removal restores the server delay");
        timing.enter(1020);clock.accept(roundTrip(timing.remaining(1020,120,true)));
        check(clock.remaining()==60,"a new attack restarts three-second warm-heart wait");
        timing.saturation(1021);clock.accept(roundTrip(timing.remaining(1021,120,false)));
        check(clock.remaining()==0,"saturation clears authoritative and client countdown immediately");
        timing.enter(1022);
        check(timing.remaining(1022,120,false)==0,"attack during server saturation grace cannot restart HUD wait");
        timing.enter(1141);
        check(timing.remaining(1141,120,false)==120,"attack at grace deadline restarts normally");
        check(timing.remaining(1141,31,true)==16,"odd server delays round up");
        check(timing.remaining(1141,0,false)==0,"zero configured delay has no waiting HUD");
        clock.accept(1);clock.tick(false);clock.tick(false);
        check(clock.remaining()==0,"local interpolation cannot become negative");
        clock.accept(120);clock.clear();
        check(clock.remaining()==0,"logout or mode change drops old server countdown");
        check(!PacketSafety.allowed(CuisineRegenPackets.Combat.ID,true)
                &&PacketSafety.allowed(CuisineRegenPackets.Combat.ID,false),"combat sync cannot be submitted as a client request");
        for(int invalid:new int[]{-1,72001,Integer.MAX_VALUE}){
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{buffer.writeVarInt(invalid);buffer.writeVarInt(5);boolean rejected=false;
                try{rejected=Network.decode(CuisineRegenPackets.Combat.ID,buffer)==null;}catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"invalid recovery delay rejected before client state: "+invalid);
            }finally{buffer.release();}
        }
        check(roundTrip(72000)==72000,"maximum configured delay supported");
        for(int interval:new int[]{1,5,500}){
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {var packet=new CuisineRegenPackets.Combat(0,interval);packet.write(buffer);
                check(Network.decode(packet.id(),buffer).equals(packet)&&buffer.readableBytes()==0,
                        "actual server regeneration interval round trip: "+interval);
            }finally{buffer.release();}
        }
        for(int interval:new int[]{0,-1,501}){
            boolean rejected=false;try{new CuisineRegenPackets.Combat(0,interval);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"invalid regeneration interval rejected");
        }
        var server=compiled("food/CuisineFoodEvents");
        check(calls(server,"enteredCombat","syncCombat")&&calls(server,"saturation","syncCombat"),
                "server entry and saturation push authoritative corrections");
        check(calls(server,"tick","syncCombat")&&calls(server,"combatTicksLeft","warmHeart"),
                "effect changes and timer expiry are checked on server ticks");
        check(calls(compiled("food/CuisineFoodSync"),"send","syncCombat"),"join/full food synchronization includes timer");
        var bridge=compiled("forge/ForgeEventBridge");
        check(calls(bridge,"onFoodDamage","enteredCombat")&&calls(bridge,"onFoodDamage","getSource"),
                "projectile attacks share the server combat entry");
        var hud=compiled("client/hud/CuisineRegenHud");
        check(!calls(hud,"tick","combat")&&!calls(hud,"tick","hasEffect")
                &&hud.fields.stream().noneMatch(f->Set.of("lastHurtTime","lastCombat","serverReadyAt").contains(f.name)),
                "client hit animation and local clock no longer override server combat state");
        var client=compiled("client/ClientPacketHandler");
        check(calls(client,"handle","acceptCombat"),"client routes packet to authoritative HUD countdown");
        check(calls(compiled("forge/ForgeClientBridge"),"onLoggedOut","clear"),"client disconnect clears readout state");
        System.out.println("RegenCombatSyncTest: "+checks+" checks passed");
    }
    private static int roundTrip(int value){
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try{new CuisineRegenPackets.Combat(value).write(b);
            var result=(CuisineRegenPackets.Combat)Network.decode(CuisineRegenPackets.Combat.ID,b);
            check(b.readableBytes()==0&&result.ticksLeft()==value,"combat packet round trip");return result.ticksLeft();
        }finally{b.release();}
    }
    private static ClassNode compiled(String path)throws Exception{
        var node=new ClassNode();try(var in=RegenCombatSyncTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/"+path+".class")){new ClassReader(Objects.requireNonNull(in,path)).accept(node,0);}return node;
    }
    private static boolean calls(ClassNode node,String method,String target){
        for(var m:node.methods)if(m.name.equals(method))for(var instruction:m.instructions)
            if(instruction instanceof MethodInsnNode call&&call.name.equals(target))return true;return false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
