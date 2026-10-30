package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.api.client.ParticleApi;

/** Native particle-engine adaptation of TC4 FXSparkle, untargeted FXWisp and FXBurst. */
@EventBusSubscriber(modid = "thaumcraft_aop", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AddonParticleEffects {
    public static void receiveSmoke(thaumcraft.network.SmokeSpiralPayload packet){
        var minecraft=Minecraft.getInstance();var level=minecraft.level;
        if(level!=null&&level.dimension().location().equals(packet.dimension()))ParticleApi.smokeSpiral(level,packet.effect());
    }
    public static void receive(thaumcraft.network.ParticleEffectPayload packet) {
        var minecraft=Minecraft.getInstance();var level=minecraft.level;
        if(level!=null&&level.dimension().location().equals(packet.dimension()))ParticleApi.emit(level,packet.effect());
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->ParticleApi.installNodeBolts(AddonParticleEffects::nodeBolt));
        event.enqueueWork(()->ParticleApi.installSmokeSpirals((source,effect)->{
            var minecraft=Minecraft.getInstance();var displayed=minecraft.level;
            if(displayed==null||source!=displayed)return;
            minecraft.execute(()->{if(minecraft.level==displayed)minecraft.particleEngine.add(new SmokeSpiralParticle(displayed,effect));});
        }));
        event.enqueueWork(()->ParticleApi.installEssentiaTrails((source,effect)->{
            var minecraft=Minecraft.getInstance();var displayed=minecraft.level;if(displayed==null)return;
            if(source.isClientSide()?source!=displayed:minecraft.getSingleplayerServer()!=source.getServer()||!source.dimension().equals(displayed.dimension()))return;
            minecraft.execute(()->{if(minecraft.level==displayed)LegacyParticles.essentia(effect.from().getCenter(),effect.to().getCenter(),effect.count(),effect.color(),effect.scale());});
        }));
        event.enqueueWork(()->ParticleApi.installBlockRunes((source,effect)->{
            var minecraft=Minecraft.getInstance();var displayed=minecraft.level;if(displayed==null)return;
            if(source.isClientSide()?source!=displayed:minecraft.getSingleplayerServer()!=source.getServer()||!source.dimension().equals(displayed.dimension()))return;
            minecraft.execute(()->{if(minecraft.level==displayed)minecraft.particleEngine.add(new PavingRuneParticle(new thaumcraft.blocks.PavingStoneBlock.Rune(effect.x(),effect.y(),effect.z(),effect.red(),effect.green(),effect.blue(),effect.duration(),effect.gravity())));});
        }));
        event.enqueueWork(() -> ParticleApi.installBlockSparkles((source, effect) -> {
            var minecraft = Minecraft.getInstance(); var displayed = minecraft.level;
            if (displayed == null) return;
            if (source.isClientSide() ? source != displayed : minecraft.getSingleplayerServer() != source.getServer()
                    || !source.dimension().equals(displayed.dimension())) return;
            minecraft.execute(() -> {
                if (minecraft.level == displayed) LegacyParticles.blockSparkles(effect.pos(), effect.color(), effect.count());
            });
        }));
        event.enqueueWork(() -> ParticleApi.install((source, effect) -> {
            var minecraft = Minecraft.getInstance();
            var displayed = minecraft.level;
            if (displayed == null) return;
            if (source.isClientSide() ? source != displayed : minecraft.getSingleplayerServer() != source.getServer()
                    || !source.dimension().equals(displayed.dimension())) return;
            minecraft.execute(() -> {
                if (minecraft.level != displayed) return;
                if (effect.kind() == ParticleApi.Kind.SPARKLE && displayed.random.nextInt(6) >= LegacyParticles.particleCount(2)) return;
                minecraft.particleEngine.add(switch(effect.kind()) {
                    case BURST -> new LegacyBurstParticle(displayed,effect);
                    case SHADOW_SPARKLE -> new ShadowSparkleParticle(displayed,effect);
                    default -> new ProxyParticle(displayed,effect);
                });
            });
        }));
    }
    private static void nodeBolt(net.minecraft.world.level.Level source,ParticleApi.NodeBolt effect){
        var minecraft=Minecraft.getInstance();
        if(source.isClientSide()&&!minecraft.isSameThread()){
            minecraft.execute(()->nodeBolt(source,effect));return;
        }
        var displayed=minecraft.level;if(displayed==null)return;
        var integrated=source.isClientSide()?null:minecraft.getSingleplayerServer();
        if(source.isClientSide()?source!=displayed:integrated==null||integrated!=source.getServer()
                ||!source.dimension().equals(displayed.dimension()))return;
        if(integrated!=null&&!integrated.isSameThread())throw new IllegalStateException("Server thread required");
        long seed=source.random.nextLong();
        minecraft.execute(()->{
            if(minecraft.level!=displayed||integrated!=null&&minecraft.getSingleplayerServer()!=integrated)return;
            LegacyLightningParticle.node(new Vec3(effect.fromX(),effect.fromY(),effect.fromZ()),
                    new Vec3(effect.toX(),effect.toY(),effect.toZ()),seed);
        });
    }
    private static final class ProxyParticle extends TextureSheetParticle {
        private final ParticleApi.Effect effect;
        private final float size;
        ProxyParticle(ClientLevel level, ParticleApi.Effect effect) {
            super(level, effect.x(), effect.y(), effect.z());
            this.effect = effect;
            boolean sparkle = effect.kind() == ParticleApi.Kind.SPARKLE;
            size = (random.nextFloat() * .5f + .5f) * 2 * effect.size();
            lifetime = sparkle ? 18 : (int) (36 / (Math.random() * .3 + .7));
            xd = yd = zd = 0; gravity = effect.gravity(); alpha = sparkle ? 1 : .5f;
            hasPhysics = !effect.noClip(); setSize(sparkle ? .01f : .1f, sparkle ? .01f : .1f);
            setColor(effect.red() == 0 ? 1 : effect.red(), effect.green(), effect.blue());
            switch (effect.palette()) {
                case 0 -> setColor(.75f + level.random.nextFloat() * .25f, .25f + level.random.nextFloat() * .25f, .75f + level.random.nextFloat() * .25f);
                case 1 -> setColor(.5f + level.random.nextFloat() * .3f, .5f + level.random.nextFloat() * .3f, .2f);
                case 2 -> setColor(.2f, .2f, .7f + level.random.nextFloat() * .3f);
                case 3 -> setColor(.2f, .7f + level.random.nextFloat() * .3f, .2f);
                case 4 -> setColor(.7f + level.random.nextFloat() * .3f, .2f, .2f);
                case 5 -> setColor(level.random.nextFloat() * .1f, level.random.nextFloat() * .1f, level.random.nextFloat() * .1f);
                case 6 -> setColor(.8f + level.random.nextFloat() * .2f, .8f + level.random.nextFloat() * .2f, .8f + level.random.nextFloat() * .2f);
                case 7 -> {
                    if (sparkle) setColor(.2f, .5f + level.random.nextFloat() * .3f, .6f + level.random.nextFloat() * .3f);
                    else setColor(.7f + level.random.nextFloat() * .3f, .5f + level.random.nextFloat() * .2f, .3f + level.random.nextFloat() * .1f);
                }
                default -> { }
            }
            var camera = Minecraft.getInstance().getCameraEntity();
            if (!sparkle && camera != null && camera.distanceToSqr(effect.x(), effect.y(), effect.z()) > (Minecraft.useFancyGraphics() ? 2500 : 625)) lifetime = 0;
        }
        @Override public ParticleRenderType getRenderType() { return effect.palette() == 5 ? LegacyParticles.TRAIL : LegacyParticles.GLOW; }
        @Override protected int getLightColor(float partial) { return 0xf000f0; }
        @Override public float getQuadSize(float partial) {
            if (effect.kind() == ParticleApi.Kind.SPARKLE) return .1f * size * (effect.shrink() ? (lifetime - age + 1) / (float) Math.max(1, lifetime) : 1);
            float scale = effect.shrink() ? (lifetime - age) / (float) Math.max(1, lifetime) : age / (float) Math.max(1, lifetime / 2);
            if (!effect.shrink() && scale > 1) scale = 2 - scale;
            return .5f * size * scale;
        }
        @Override protected float getU0() { return effect.kind() == ParticleApi.Kind.SPARKLE ? (age / 6 % 4) / 16f : 0; }
        @Override protected float getU1() { return getU0() + (effect.kind() == ParticleApi.Kind.SPARKLE ? .0624375f : .125f); }
        @Override protected float getV0() { return effect.kind() == ParticleApi.Kind.SPARKLE ? .25f : .875f; }
        @Override protected float getV1() { return getV0() + (effect.kind() == ParticleApi.Kind.SPARKLE ? .0624375f : .125f); }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) remove();
            yd -= .04 * gravity;
            if (!effect.noClip()) {
                var velocity = LegacyWispPhysics.pushOut(level, random, new Vec3(x, y, z), getBoundingBox(), new Vec3(xd, yd, zd));
                xd = velocity.x; yd = velocity.y; zd = velocity.z;
            }
            if (effect.kind() == ParticleApi.Kind.SPARKLE) {
                setPos(x + xd, y + yd, z + zd); xd *= .9080000019073486; yd *= .9080000019073486; zd *= .9080000019073486;
            } else { move(xd, yd, zd); xd *= (double) .98f; yd *= (double) .98f; zd *= (double) .98f; }
            if (onGround) { xd *= (double) .7f; zd *= (double) .7f; }
        }
    }
    private AddonParticleEffects() { }
}
