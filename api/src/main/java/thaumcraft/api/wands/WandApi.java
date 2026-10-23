package thaumcraft.api.wands;

import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;

/** Storage and focus costs are centivis; recipe whole-vis costs have an explicit separate operation. */
public final class WandApi {
    public enum Kind { NONE, WAND, STAFF, SCEPTRE }
    public interface Access {
        int get(ItemStack stack,Aspect aspect);int capacity(ItemStack stack);void set(ItemStack stack,Aspect aspect,int centivis);
        Map<Aspect,Integer> quote(ItemStack stack,Player player,Map<Aspect,Integer> baseCentivis);
        boolean consume(ItemStack stack,Player player,Map<Aspect,Integer> baseCentivis,boolean commit);
        ItemStack focus(ItemStack stack);void focus(ItemStack wand,ItemStack focus);void parts(ItemStack stack,WandParts parts);
        Kind kind(ItemStack stack);WandParts parts(ItemStack stack);
        Map<Aspect,Integer> quoteCrafting(ItemStack stack,Player player,Map<Aspect,Integer> wholeVis);
        default float craftingModifier(ItemStack stack,Player player,Aspect aspect){throw new UnsupportedOperationException("Crafting modifier unavailable");}
    }
    private static Access access;
    public static void install(Access value){if(access!=null)throw new IllegalStateException("Wand API already installed");access=Objects.requireNonNull(value);}
    public static int centivis(int wholeVis){return Math.multiplyExact(wholeVis,100);}
    public static int stored(ItemStack wand,Aspect aspect){return access.get(wand,aspect);}
    public static int capacity(ItemStack wand){return access.capacity(wand);}
    public static void set(ItemStack wand,Aspect aspect,int centivis){access.set(wand,aspect,centivis);}
    public static Map<Aspect,Integer> quote(ItemStack wand,Player player,Map<Aspect,Integer> baseCentivis){return Map.copyOf(access.quote(wand,player,baseCentivis));}
    public static boolean consume(ItemStack wand,Player player,Map<Aspect,Integer> baseCentivis,boolean commit){return access.consume(wand,player,baseCentivis,commit);}
    public static ItemStack focus(ItemStack wand){return access.focus(wand).copy();}
    public static void focus(ItemStack wand,ItemStack focus){access.focus(wand,focus.copy());}
    public static void parts(ItemStack wand,WandParts parts){access.parts(wand,parts);}
    public static Kind kind(ItemStack wand){return access.kind(wand);}
    public static WandParts parts(ItemStack wand){return access.parts(wand);}
    /** Whole-vis crafting costs, without focus frugal. A null player means no equipment discounts. */
    public static Map<Aspect,Integer> quoteCrafting(ItemStack wand,Player player,Map<Aspect,Integer> wholeVis){return Map.copyOf(access.quoteCrafting(wand,player,wholeVis));}
    /** Exact native crafting coefficient before integer rounding; null player omits worn equipment.
     * Read-only. The adapter receives a copy, including during wand-kind validation.
     */
    public static float craftingModifier(ItemStack wand,Player player,Aspect aspect){
        Objects.requireNonNull(wand);Objects.requireNonNull(aspect);
        if(access==null)throw new IllegalStateException("Wand API not installed");
        var copy=wand.copy();if(!aspect.isPrimal()||copy.isEmpty()||access.kind(copy)==Kind.NONE)throw new IllegalArgumentException("Casting wand and primal aspect required");
        float modifier=access.craftingModifier(copy,player,aspect);
        if(!Float.isFinite(modifier)||modifier<=0)throw new IllegalStateException("Invalid crafting modifier");
        return modifier;
    }
    /** Server-thread debit for unattended crafting; the level supplies context when player is null. */
    public static boolean consumeCrafting(net.minecraft.world.level.Level level,ItemStack wand,Player player,Map<Aspect,Integer> wholeVis,boolean commit){
        Objects.requireNonNull(level);if(player!=null&&player.level()!=level)throw new IllegalArgumentException("Player in another level");
        if(commit&&(level.isClientSide()||level.getServer()==null||!level.getServer().isSameThread()))throw new IllegalStateException("Server thread required");
        if(kind(wand)==Kind.NONE||wholeVis.isEmpty())return false;
        var costs=quoteCrafting(wand,player,wholeVis);for(var entry:costs.entrySet())if(stored(wand,entry.getKey())<entry.getValue())return false;
        if(commit)costs.forEach((aspect,n)->set(wand,aspect,stored(wand,aspect)-n));return true;
    }
    private WandApi(){}
}
