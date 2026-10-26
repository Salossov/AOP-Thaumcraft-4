package thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.*;

/** The original large/small bolt passes, expressed in Minecraft's native particle buffers. */
final class LegacyLightningParticle extends Particle {
    private static final ParticleRenderType LARGE=sheet("misc/p_large"),SMALL=sheet("misc/p_small");
    private final LegacyLightningGraph graph;
    private final float width;
    private final boolean small,node;
    private int travelAge;
    private static ParticleRenderType sheet(String texture){return (tessellator,textures)->{
        RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE);
        RenderSystem.setShader(GameRenderer::getParticleShader);RenderSystem.setShaderTexture(0,ResearchGraphics.texture(texture));
        return tessellator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
    };}
    static void shock(Vec3 from,Vec3 to){
        var mc=Minecraft.getInstance();if(mc.level==null||from.distanceToSqr(to)<1e-12)return;
        var graph=new LegacyLightningGraph(from,to,mc.level.random.nextLong(),6,.5f,8);
        mc.particleEngine.add(new LegacyLightningParticle(from,to,graph,.125f,false,false));mc.particleEngine.add(new LegacyLightningParticle(from,to,graph,.125f,true,false));
    }
    static void node(Vec3 from,Vec3 to){
        var mc=Minecraft.getInstance();if(mc.level==null||from.distanceToSqr(to)<1e-12)return;
        node(from,to,mc.level.random.nextLong());
    }
    static void node(Vec3 from,Vec3 to,long seed){
        var mc=Minecraft.getInstance();if(mc.level==null||from.distanceToSqr(to)<1e-12)return;
        // ClientProxy.nodeBolt: duration 10, fractal multiplier 4, speed 5, type 0.
        var graph=new LegacyLightningGraph(from,to,seed,10,4,5);
        mc.particleEngine.add(new LegacyLightningParticle(from,to,graph,.03f,false,true));mc.particleEngine.add(new LegacyLightningParticle(from,to,graph,.03f,true,true));
    }
    private LegacyLightningParticle(Vec3 from,Vec3 to,LegacyLightningGraph graph,float width,boolean small,boolean node){
        super(Minecraft.getInstance().level,from.x,from.y,from.z);this.graph=graph;this.width=width;this.small=small;this.node=node;travelAge=graph.age;hasPhysics=false;xd=yd=zd=0;
        setBoundingBox(new AABB(from,to).inflate(graph.length*.5+1));
    }
    @Override public ParticleRenderType getRenderType(){return small?SMALL:LARGE;}
    @Override public void tick(){travelAge=Math.min(graph.maximumAge,travelAge+graph.increment);if(travelAge>=graph.maximumAge)remove();}
    @Override public void render(VertexConsumer vertices,Camera camera,float partial){
        var player=Minecraft.getInstance().player;if(player==null||player.distanceToSqr(x,y,z)>(Minecraft.useFancyGraphics()?10000:2500))return;
        float ageFraction=travelAge<0?0:travelAge/(float)graph.maximumAge,opacity=small?1-ageFraction*.5f:(1-ageFraction)*.4f;
        int travelLength=Math.max(1,(int)(graph.length*3));int visible=(int)((travelAge+partial+travelLength)/travelLength*graph.mainSegments);
        var view=new LegacyLightningGraph.V(camera.getLookVector().x(),camera.getLookVector().y(),camera.getLookVector().z());
        var eye=camera.getPosition();
        for(var segment:graph.segments){
            if(segment.number>visible)continue;
            var relative=new LegacyLightningGraph.V((float)player.getX()-segment.start.point.x,(float)player.getY()-segment.start.point.y,(float)player.getZ()-segment.start.point.z);
            float extent=width*(relative.length()/5+1)*(1+segment.light)*.5f;
            var startSide=LegacyLightningGraph.V.cross(view,segment.previousDiff).scale(extent/Math.max(1e-6f,segment.previousSine)).vec();
            var endSide=LegacyLightningGraph.V.cross(view,segment.nextDiff).scale(extent/Math.max(1e-6f,segment.nextSine)).vec();
            var a=segment.start.point.vec().subtract(eye);var b=segment.end.point.vec().subtract(eye);float alpha=opacity*segment.light;
            quad(vertices,b.subtract(endSide),a.subtract(startSide),a.add(startSide),b.add(endSide),.5f,.5f,alpha);
            if(segment.next==null){var end=segment.end.point.copy().add(segment.diff.copy().normalize().scale(extent)).vec().subtract(eye);quad(vertices,end.subtract(endSide),b.subtract(endSide),b.add(endSide),end.add(endSide),0,.5f,alpha);}
            if(segment.previous==null){var start=segment.start.point.copy().sub(segment.diff.copy().normalize().scale(extent)).vec().subtract(eye);quad(vertices,a.subtract(startSide),start.subtract(startSide),start.add(startSide),a.add(startSide),.5f,0,alpha);}
        }
    }
    private void quad(VertexConsumer vertices,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float u1,float alpha){
        Vec3[] points={a,b,c,d};float[] u={u0,u1,u1,u0},v={0,0,1,1};
        float red=node?(small?1:.6f):.1f,green=node?(small?.6f:.3f):.1f,blue=node?(small?1:.6f):(small?1:.6f);
        for(int i=0;i<4;i++)vertices.addVertex((float)points[i].x,(float)points[i].y,(float)points[i].z).setUv(u[i],v[i]).setColor(red,green,blue,alpha).setLight(0xf000f0);
    }
}
