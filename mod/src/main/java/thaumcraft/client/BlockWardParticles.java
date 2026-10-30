package thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Original FXBlockWard: sixteen hemis frames, face alignment, random rotation and additive fade. */
@EventBusSubscriber(modid="thaumcraft_aop",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BlockWardParticles {
    private static SpriteSet sprites;
    private static final ParticleRenderType ADDITIVE=(tessellator,textures)->{RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);RenderSystem.setShader(GameRenderer::getParticleShader);RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);return tessellator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);};
    @SubscribeEvent public static void providers(RegisterParticleProvidersEvent e){e.registerSpriteSet(thaumcraft.effects.OwnedParticles.WARD.get(),set->{sprites=set;return (options,level,x,y,z,vx,vy,vz)->new Ward(level,new Vec3(x,y,z),Direction.UP,new Vec3(.5,.5,.5),set);});}
    @SubscribeEvent public static void blocks(RegisterClientExtensionsEvent e){e.registerBlock(new IClientBlockExtensions(){@Override public boolean addHitEffects(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,net.minecraft.world.phys.HitResult hit,ParticleEngine engine){if(sprites!=null&&hit instanceof net.minecraft.world.phys.BlockHitResult block&&level instanceof ClientLevel client){var p=block.getBlockPos();engine.add(new Ward(client,p.getCenter(),block.getDirection(),block.getLocation().subtract(Vec3.atLowerCornerOf(p)),sprites));}return true;}},thaumcraft.registry.R4Content.WARDED_GLASS.get());}
    private static final class Ward extends TextureSheetParticle {
        private final SpriteSet frames;
        private final Quaternionf rotation;
        Ward(ClientLevel l,Vec3 centre,Direction side,Vec3 hit,SpriteSet frames){super(l,centre.x,centre.y,centre.z,0,0,0);this.frames=frames;xd=yd=zd=0;gravity=0;hasPhysics=false;lifetime=12+random.nextInt(5);quadSize=(float)(1.4+random.nextGaussian()*.3);
            double sx=side.getStepX()==0?net.minecraft.util.Mth.clamp(hit.x-.6+random.nextFloat()*.2,-.4,.4):0,sy=side.getStepY()==0?net.minecraft.util.Mth.clamp(hit.y-.6+random.nextFloat()*.2,-.4,.4):0,sz=side.getStepZ()==0?net.minecraft.util.Mth.clamp(hit.z-.6+random.nextFloat()*.2,-.4,.4):0;setPos(centre.x+sx+side.getStepX()*.505,centre.y+sy+side.getStepY()*.505,centre.z+sz+side.getStepZ()*.505);xo=x;yo=y;zo=z;rotation=new Quaternionf().rotateAxis((float)Math.PI/2,side.getStepY(),-side.getStepX(),side.getStepZ()).rotateZ(random.nextInt(360)*(float)Math.PI/180);if(side==Direction.SOUTH)rotation.rotateY((float)Math.PI);setSprite(frames.get(0,15));
        }
        @Override public ParticleRenderType getRenderType(){return ADDITIVE;}
        @Override public void tick(){float threshold=lifetime/5f;alpha=age<=threshold?age/threshold:(lifetime-age)/(float)lifetime;if(age++>=lifetime)remove();}
        @Override public void render(VertexConsumer vertices,Camera camera,float partial){setSprite(frames.get(Math.min(15,(int)(15*(age+partial)/lifetime)),15));var c=camera.getPosition();var centre=new Vector3f((float)(x-c.x),(float)(y-c.y),(float)(z-c.z));float half=quadSize/2;float[][] corners={{-half,half,getU0(),getV1()},{half,half,getU1(),getV1()},{half,-half,getU1(),getV0()},{-half,-half,getU0(),getV0()}};for(var q:corners){var p=new Vector3f(q[0],q[1],0).rotate(rotation).add(centre);vertices.addVertex(p.x,p.y,p.z).setUv(q[2],q[3]).setColor(1,1,1,alpha/2).setUv2(240,240);}}
    }
    private BlockWardParticles(){}
}
