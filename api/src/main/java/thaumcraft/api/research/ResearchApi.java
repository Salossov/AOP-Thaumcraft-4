package thaumcraft.api.research;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.api.book.BookCatalog;
import thaumcraft.api.knowledge.PlayerKnowledge;

/** The core installs the server adapter; client snapshots are replaced atomically on synchronization. */
public final class ResearchApi {
    public enum WarpKind { PERMANENT, STICKY, TEMPORARY }
    public interface Access {PlayerKnowledge knowledge(ServerPlayer player);void sync(ServerPlayer player);boolean exists(ServerPlayer player,String research);boolean grant(ServerPlayer player,String research);
        default void addWarp(ServerPlayer player,WarpKind kind,int amount){throw new UnsupportedOperationException("Warp mutation is unavailable");}
        default boolean completeAspect(ServerPlayer player,thaumcraft.api.aspects.Aspect aspect,short amount){throw new UnsupportedOperationException("Aspect completion is unavailable");}
        default Map<String,ResearchDefinition> definitions(ServerPlayer player){throw new UnsupportedOperationException("Server research catalog is unavailable");}
    }
    private static Access access;
    private record Snapshot(Map<String,ResearchDefinition> definitions,BookCatalog books,PlayerKnowledge knowledge,boolean easyMode){}
    private static volatile Snapshot snapshot=new Snapshot(Map.of(),BookCatalog.EMPTY,new PlayerKnowledge(),false);
    private static final Map<net.minecraft.resources.ResourceLocation,java.util.function.BooleanSupplier> EASY_MODE_RULES=new LinkedHashMap<>();
    private static boolean modesFrozen;
    /** Startup-only, pure configuration requests. Any enabled request selects TC4's global easy mode. */
    public static synchronized void registerEasyMode(net.minecraft.resources.ResourceLocation id,java.util.function.BooleanSupplier enabled){
        if(modesFrozen)throw new IllegalStateException("Research mode registration is closed");
        if(EASY_MODE_RULES.putIfAbsent(Objects.requireNonNull(id),Objects.requireNonNull(enabled))!=null)throw new IllegalArgumentException("Duplicate research mode rule "+id);
    }
    public static synchronized void freezeModes(){modesFrozen=true;}
    /** Server authority; clients must use clientEasyMode(), which comes from their current server. */
    public static synchronized boolean serverEasyMode(){return EASY_MODE_RULES.values().stream().anyMatch(java.util.function.BooleanSupplier::getAsBoolean);}
    public static boolean clientEasyMode(){return snapshot.easyMode;}
    /** Matches TC4's book purchase branch; an empty aspect list is not a purchase. */
    public static boolean usesAspectPurchase(ResearchDefinition research,boolean easyMode){return !research.aspects().isEmpty()&&(easyMode||research.secondary());}
    public static void install(Access value){if(access!=null)throw new IllegalStateException("Research API already installed");access=Objects.requireNonNull(value);}
    public static PlayerKnowledge knowledge(ServerPlayer player){return access.knowledge(player).copy();}
    public static boolean knows(ServerPlayer player,String research){return research.isEmpty()||access.knowledge(player).completedResearch().contains(research);}
    public static boolean exists(ServerPlayer player,String research){return access.exists(player,research);}
    public static boolean grant(ServerPlayer player,String research){if(!player.server.isSameThread())throw new IllegalStateException("Server thread required");if(!access.exists(player,research))throw new IllegalArgumentException("Unknown research "+research);return access.grant(player,research);}
    /** TC4 completeAspect: discover directly and SET the pool, without prerequisite checks. */
    public static boolean completeAspect(ServerPlayer player,thaumcraft.api.aspects.Aspect aspect,short amount){
        if(!player.server.isSameThread())throw new IllegalStateException("Server thread required");
        return aspect!=null&&access.completeAspect(player,aspect,amount);
    }
    /** Signed server-side delta; native Wuss Mode, persistence, counter and notices apply. */
    public static void addWarp(ServerPlayer player,WarpKind kind,int amount){
        Objects.requireNonNull(player);Objects.requireNonNull(kind);
        if(!player.server.isSameThread())throw new IllegalStateException("Server thread required");
        if(amount==0)return;
        var state=knowledge(player);
        int current=switch(kind){case PERMANENT->state.permanentWarp();case STICKY->state.stickyWarp();case TEMPORARY->state.temporaryWarp();};
        long next=Math.max(0L,(long)current+amount);
        if(next>Integer.MAX_VALUE||(long)state.permanentWarp()+state.stickyWarp()+state.temporaryWarp()-current+next>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Warp total exceeds supported range");
        access.addWarp(player,kind,amount);
    }
    public static Map<String,ResearchDefinition> definitions(){return snapshot.definitions;}
    /** Effective server catalog, including dynamic requirements and config/tag filtering. */
    public static Map<String,ResearchDefinition> definitions(ServerPlayer player){
        if(!player.server.isSameThread())throw new IllegalStateException("Server thread required");
        return Collections.unmodifiableMap(new LinkedHashMap<>(access.definitions(player)));
    }
    public static BookCatalog books(){return snapshot.books;}
    public static PlayerKnowledge clientKnowledge(){return snapshot.knowledge.copy();}
    public static void accept(Map<String,ResearchDefinition> research,BookCatalog catalog,PlayerKnowledge state){accept(research,catalog,state,false);}
    public static void accept(Map<String,ResearchDefinition> research,BookCatalog catalog,PlayerKnowledge state,boolean easyMode){snapshot=new Snapshot(Map.copyOf(research),Objects.requireNonNull(catalog),state.copy(),easyMode);}
    private ResearchApi(){}
}
