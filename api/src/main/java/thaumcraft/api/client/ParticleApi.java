package thaumcraft.api.client;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.world.level.Level;

/**
 * Public access to the original TC4 client-proxy sparkle/wisp effects.
 * Legacy emit/sparkle calls send no packets: dedicated servers do nothing, while an integrated server may
 * emit into its matching local client world, as the original ClientProxy did.
 * The implementation schedules rendering on the client thread. Explicit broadcast
 * methods instead deliver effects to nearby clients of a server level.
 */
public final class ParticleApi {
    /** Original six-float ClientProxy.nodeBolt; a fresh fractal bolt, not a wand beam. */
    public record NodeBolt(float fromX,float fromY,float fromZ,float toX,float toY,float toZ){
        public NodeBolt{
            if(!Float.isFinite(fromX)||!Float.isFinite(fromY)||!Float.isFinite(fromZ)
                    ||!Float.isFinite(toX)||!Float.isFinite(toY)||!Float.isFinite(toZ))
                throw new IllegalArgumentException("Invalid node bolt coordinates");
        }
    }
    private static volatile BiConsumer<Level,NodeBolt> nodeBoltAccess=(level,effect)->{};
    public static void installNodeBolts(BiConsumer<Level,NodeBolt> implementation){
        nodeBoltAccess=Objects.requireNonNull(implementation);
    }
    /**
     * Local legacy proxy semantics. Dedicated servers do nothing and consume no RNG.
     * The client adapter owns scheduling, world checks and the one source-world seed.
     * This method never sends packets or loads chunks.
     */
    public static void nodeBolt(Level level,float fromX,float fromY,float fromZ,float toX,float toY,float toZ){
        nodeBoltAccess.accept(Objects.requireNonNull(level),new NodeBolt(fromX,fromY,fromZ,toX,toY,toZ));
    }
    public enum Kind { SPARKLE, WISP, BURST, SHADOW_SPARKLE }
    public record Effect(Kind kind, double x, double y, double z, float size, int palette,
                         float red, float green, float blue, boolean shrink, boolean noClip, float gravity) {
        public Effect {
            Objects.requireNonNull(kind);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(size)
                    || size < 0 || palette < -1 || palette > 7 || !Float.isFinite(red) || !Float.isFinite(green)
                    || !Float.isFinite(blue) || !Float.isFinite(gravity)) throw new IllegalArgumentException("Invalid TC4 particle");
        }
    }
    private static volatile BiConsumer<Level, Effect> access = (level, effect) -> { };
    private static volatile BiConsumer<net.minecraft.server.level.ServerLevel, Effect> broadcastAccess =
            (level, effect) -> { throw new IllegalStateException("Server particle transport unavailable"); };
    /** Explicit server transport; legacy emit/sparkle retain their original local-only semantics. */
    public static void installBroadcast(BiConsumer<net.minecraft.server.level.ServerLevel, Effect> implementation) {
        broadcastAccess = Objects.requireNonNull(implementation);
    }
    /** Send one effect to nearby clients of this level, including the integrated-server host exactly once. */
    public static void broadcast(net.minecraft.server.level.ServerLevel level, Effect effect) {
        Objects.requireNonNull(level); Objects.requireNonNull(effect);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        broadcastAccess.accept(level, effect);
    }
    public static void broadcastSparkle(net.minecraft.server.level.ServerLevel level, double x, double y, double z, int color) {
        broadcast(level, new Effect(Kind.SPARKLE, x, y, z, 1.5f, color, 0, 0, 0, true, true, 0));
    }
    /** TT Shadowbeam's five-tick, unshrinking, alpha-blended FXSparkle variant; coordinates retain the original float conversion. */
    public static void broadcastShadowSparkle(net.minecraft.server.level.ServerLevel level, float x, float y, float z, float size) {
        broadcast(level, new Effect(Kind.SHADOW_SPARKLE, x, y, z, size, -1,
                .001f, .001f, .001f, false, true, -.7f));
    }
    /** Original stationary FXSmokeSpiral: radius, starting angle, lower Y clamp and packed RGB. */
    public record SmokeSpiral(double x,double y,double z,float radius,int start,int minY,int color){
        public SmokeSpiral{
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(radius)||radius<0)
                throw new IllegalArgumentException("Invalid smoke spiral");
        }
    }
    private static volatile BiConsumer<Level,SmokeSpiral> smokeAccess=(level,effect)->{};
    private static volatile BiConsumer<net.minecraft.server.level.ServerLevel,SmokeSpiral> smokeBroadcastAccess=
            (level,effect)->{throw new IllegalStateException("Server smoke transport unavailable");};
    public static void installSmokeSpirals(BiConsumer<Level,SmokeSpiral> implementation){smokeAccess=Objects.requireNonNull(implementation);}
    public static void installSmokeBroadcast(BiConsumer<net.minecraft.server.level.ServerLevel,SmokeSpiral> implementation){smokeBroadcastAccess=Objects.requireNonNull(implementation);}
    /** Client-only emission; server callers use explicit broadcast to avoid integrated-server duplicates. */
    public static void smokeSpiral(Level level,SmokeSpiral effect){
        Objects.requireNonNull(level);Objects.requireNonNull(effect);
        if(level.isClientSide())smokeAccess.accept(level,effect);
    }
    public static void broadcastSmokeSpiral(net.minecraft.server.level.ServerLevel level,SmokeSpiral effect){
        Objects.requireNonNull(level);Objects.requireNonNull(effect);
        if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
        smokeBroadcastAccess.accept(level,effect);
    }
    public record BlockSparkle(net.minecraft.core.BlockPos pos, int color, int count) {
        public BlockSparkle { pos = Objects.requireNonNull(pos).immutable(); if (count < 0) throw new IllegalArgumentException("Negative particle count"); }
    }
    private static volatile BiConsumer<Level, BlockSparkle> blockAccess = (level, effect) -> { };
    public record BlockRune(double x,double y,double z,float red,float green,float blue,int duration,float gravity) { }
    private static volatile BiConsumer<Level,BlockRune> runeAccess=(level,effect)->{};
    public static void installBlockRunes(BiConsumer<Level,BlockRune> implementation){runeAccess=Objects.requireNonNull(implementation);}
    public record EssentiaTrail(net.minecraft.core.BlockPos from,net.minecraft.core.BlockPos to,int count,int color,float scale){
        public EssentiaTrail{from=Objects.requireNonNull(from).immutable();to=Objects.requireNonNull(to).immutable();}
    }
    private static volatile BiConsumer<Level,EssentiaTrail> essentiaAccess=(level,effect)->{};
    public static void installEssentiaTrails(BiConsumer<Level,EssentiaTrail> implementation){essentiaAccess=Objects.requireNonNull(implementation);}
    /** Original proxy centers both block positions; local client effect, no network packets. */
    public static void essentiaTrail(Level level,net.minecraft.core.BlockPos from,net.minecraft.core.BlockPos to,int count,int color,float scale){essentiaAccess.accept(Objects.requireNonNull(level),new EssentiaTrail(from,to,count,color,scale));}
    /** Original blockRunes proxy, local client only, with the original three-times-duration lifetime. */
    public static void blockRunes(Level level,double x,double y,double z,float red,float green,float blue,int duration,float gravity){runeAccess.accept(Objects.requireNonNull(level),new BlockRune(x,y,z,red,green,blue,duration,gravity));}
    public static void install(BiConsumer<Level, Effect> implementation) { access = Objects.requireNonNull(implementation); }
    public static void installBlockSparkles(BiConsumer<Level, BlockSparkle> implementation) { blockAccess = Objects.requireNonNull(implementation); }
    /** Original blockSparkle proxy: packed RGB (or -9999 for random color), base count before client particle settings. */
    public static void blockSparkle(Level level, net.minecraft.core.BlockPos pos, int color, int count) { blockAccess.accept(Objects.requireNonNull(level), new BlockSparkle(pos, color, count)); }
    public static void emit(Level level, Effect effect) { access.accept(Objects.requireNonNull(level), Objects.requireNonNull(effect)); }
    /** Original stationary, full-bright FXBurst on the 32-frame node texture. */
    public static void burst(Level level,double x,double y,double z,float size){
        emit(level,new Effect(Kind.BURST,x,y,z,size,-1,1,1,1,false,false,0));
    }
    public static void sparkle(Level level, double x, double y, double z, int color) { sparkle(level, x, y, z, 1.5f, color, 0); }
    public static void sparkle(Level level, double x, double y, double z, float size, int color, float gravity) {
        emit(level, new Effect(Kind.SPARKLE, x, y, z, size, color, 0, 0, 0, true, true, gravity));
    }
    public static void wisp(Level level, double x, double y, double z, float size, int color, boolean shrink, boolean noClip, float gravity) {
        emit(level, new Effect(Kind.WISP, x, y, z, size, color, 0, 0, 0, shrink, noClip, gravity));
    }
    public static void wisp(Level level, double x, double y, double z, float size, float red, float green, float blue) {
        emit(level, new Effect(Kind.WISP, x, y, z, size, -1, red, green, blue, false, false, .02f));
    }
    private ParticleApi() { }
}
