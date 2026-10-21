package com.chi__you.cuisinejournal.progression;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Counts alternatives with residual-flow allocation: an inventory item cannot satisfy two slots. */
public final class RecipeTracking {
    private RecipeTracking() {}
    public record Need(List<ResourceLocation> alternatives,int count) {
        public Need { alternatives=List.copyOf(alternatives);if(count<1)throw new IllegalArgumentException("Invalid requirement"); }
    }
    public static int batches(int target,int output) { return (target+Math.max(1,output)-1)/Math.max(1,output); }
    public static int[] allocate(List<Need> needs,Map<ResourceLocation,Integer> inventory) {
        List<ResourceLocation> items=inventory.keySet().stream().filter(k->inventory.get(k)>0).sorted().toList();
        int slots=needs.size(),source=0,itemStart=1,slotStart=itemStart+items.size(),sink=slotStart+slots,size=sink+1;
        int[][] residual=new int[size][size];
        for(int i=0;i<items.size();i++) {
            residual[source][itemStart+i]=Math.min(1_000_000,Math.max(0,inventory.get(items.get(i))));
            for(int j=0;j<slots;j++)if(needs.get(j).alternatives().contains(items.get(i)))residual[itemStart+i][slotStart+j]=1_000_000;
        }
        for(int j=0;j<slots;j++)residual[slotStart+j][sink]=needs.get(j).count();
        while(true) {
            int[] parent=new int[size];Arrays.fill(parent,-1);parent[source]=source;var queue=new ArrayDeque<Integer>();queue.add(source);
            while(!queue.isEmpty()&&parent[sink]<0) {
                int from=queue.remove();
                for(int to=0;to<size;to++)if(parent[to]<0&&residual[from][to]>0){parent[to]=from;queue.add(to);}
            }
            if(parent[sink]<0)break;
            int amount=Integer.MAX_VALUE;
            for(int v=sink;v!=source;v=parent[v])amount=Math.min(amount,residual[parent[v]][v]);
            for(int v=sink;v!=source;v=parent[v]){int u=parent[v];residual[u][v]-=amount;residual[v][u]+=amount;}
        }
        int[] supplied=new int[slots];
        for(int j=0;j<slots;j++)supplied[j]=needs.get(j).count()-residual[slotStart+j][sink];
        return supplied;
    }
}
