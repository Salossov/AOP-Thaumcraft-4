package thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import thaumcraft.entities.TaintSwarm;
import thaumcraft.registry.R2Sounds;

/** Original FXSwarm steering, eight animation frames and fifty-tick death fade. */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class SwarmParticles {
    private static final Map<TaintSwarm,List<Swarm>> ACTIVE=new IdentityHashMap<>();
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/misc/particles.png");
    private static final ParticleRenderType TYPE=new ParticleRenderType(){@Override public BufferBuilder begin(Tesselator tesselator,TextureManager textures){RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderTexture(0,TEXTURE);return tesselator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);}@Override public String toString(){return "THAUMCRAFT_SWARM";}};
    private static final ArrayDeque<Long> BUZZ=new ArrayDeque<>();
    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof TaintSwarm swarm)||!swarm.level().isClientSide)return;var mc=Minecraft.getInstance();if(mc.level!=swarm.level())return;
        var particles=ACTIVE.computeIfAbsent(swarm,key->new ArrayList<>());particles.removeIf(p->!p.isAlive());int count=switch(mc.options.particles().get()){case ALL->25;case DECREASED->12;case MINIMAL->10;};
        var camera=mc.getCameraEntity();double range=mc.options.graphicsMode().get()==GraphicsStatus.FAST?32:64;
        if(particles.size()<count&&camera!=null&&camera.distanceToSqr(swarm)<range*range){var particle=new Swarm(mc.level,swarm);particles.add(particle);mc.particleEngine.add(particle);}
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event){var level=Minecraft.getInstance().level;ACTIVE.keySet().removeIf(entity->entity.level()!=level||entity.isRemoved());while(!BUZZ.isEmpty()&&BUZZ.peekFirst()<System.nanoTime())BUZZ.removeFirst();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){ACTIVE.clear();BUZZ.clear();}
    private static final class Swarm extends Particle {
        private final TaintSwarm target;private final float scale;private float yaw,pitch;private int dying;
        Swarm(ClientLevel level,TaintSwarm target){super(level,target.getX()+(level.random.nextFloat()-level.random.nextFloat())*2,target.getY()+(level.random.nextFloat()-level.random.nextFloat())*2,target.getZ()+(level.random.nextFloat()-level.random.nextFloat())*2);this.target=target;scale=random.nextFloat()*0.5f+1;rCol=0.8f+random.nextFloat()*0.2f;gCol=random.nextFloat()*0.4f;bCol=1-random.nextFloat()*0.2f;gravity=0.08f;xd=(random.nextFloat()-random.nextFloat())*0.2;yd=(random.nextFloat()-random.nextFloat())*0.2;zd=(random.nextFloat()-random.nextFloat())*0.2;setSize(0.2f,0.2f);}
        @Override public ParticleRenderType getRenderType(){return TYPE;}
        @Override public void tick(){
            xo=x;yo=y;zo=z;age++;boolean dead=target.isRemoved()||target.deathTime>0;
            if(dead){dying++;yd-=gravity/2;if(dying>50)remove();}else yd+=gravity;
            move(xd,yd,zd);xd*=0.985;yd*=0.985;zd*=0.985;
            if(!dead){double dx=target.getX()-x,dz=target.getZ()-z,dy=target.getBoundingBox().getCenter().y-getBoundingBox().getCenter().y;float targetYaw=(float)(Math.atan2(dz,dx)*Mth.RAD_TO_DEG)-90,targetPitch=(float)(-Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))*Mth.RAD_TO_DEG);boolean toward=target.distanceToSqr(x,y,z)>target.getBbWidth()&&target.hurtTime<=0;float yawStep=7.5f+random.nextInt(7),pitchStep=7.5f+random.nextInt(7);yaw=rotate(yaw,targetYaw,toward?yawStep:-yawStep);pitch=rotate(pitch,targetPitch,toward?pitchStep:-pitchStep);var direction=new Vec3(-Mth.sin(yaw*Mth.DEG_TO_RAD)*Mth.cos(pitch*Mth.DEG_TO_RAD),-Mth.sin(pitch*Mth.DEG_TO_RAD),Mth.cos(yaw*Mth.DEG_TO_RAD)*Mth.cos(pitch*Mth.DEG_TO_RAD));xd=(direction.x+random.nextGaussian()*(random.nextBoolean()?-1:1)*0.0075*15)*0.22;yd=(direction.y+random.nextGaussian()*(random.nextBoolean()?-1:1)*0.0075*15)*0.22;zd=(direction.z+random.nextGaussian()*(random.nextBoolean()?-1:1)*0.0075*15)*0.22;}
            if(BUZZ.size()<3&&random.nextInt(50)==0&&level.getNearestPlayer(x,y,z,8,false)!=null){level.playLocalSound(x,y,z,R2Sounds.FLY.get(),net.minecraft.sounds.SoundSource.HOSTILE,0.03f,0.5f+random.nextFloat()*0.4f,false);BUZZ.addLast(System.nanoTime()+1500000L);}
        }
        private static float rotate(float angle,float desired,float step){float delta=Mth.wrapDegrees(desired-angle);if(delta>step)delta=step;if(delta<-step)delta=-step;return angle+delta;}
        @Override public void render(VertexConsumer vertices,Camera camera,float partial){var at=new Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z)).subtract(camera.getPosition());float extent=0.1f*scale*(1+Mth.sin(age/3f)*0.25f),u=(7+age%8)/16f,v=0.25f;float green=target.hurtTime<=0?gCol:gCol/2,blue=target.hurtTime<=0?bCol:bCol/2;
            for(int i=0;i<4;i++){float xSide=i==0||i==3?-extent:extent,ySide=i<2?-extent:extent;var point=new org.joml.Vector3f(xSide,ySide,0).rotate(camera.rotation());vertices.addVertex((float)at.x+point.x,(float)at.y+point.y,(float)at.z+point.z).setUv(i==0||i==3?u+0.0624375f:u,i<2?v+0.0624375f:v).setColor(rCol,green,blue,(50-dying)/50f).setLight(LightTexture.FULL_BRIGHT);}
        }
    }
    private SwarmParticles(){}
}
