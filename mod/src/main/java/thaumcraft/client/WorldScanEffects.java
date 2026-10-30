package thaumcraft.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.items.ThaumometerItem;
import thaumcraft.network.BookBirthPayload;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class WorldScanEffects {
    private record Birth(int entityId,Vec3 position,long start){}
    private static final List<Birth> births=new ArrayList<>();
    public static void blockSparkle(thaumcraft.network.BlockSparklePayload cue){LegacyParticles.blockSparkles(cue.position(),cue.color());}
    public static void birth(BookBirthPayload cue){var mc=Minecraft.getInstance();if(mc.level!=null)births.add(new Birth(cue.entityId(),cue.position(),mc.level.getGameTime()));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){births.clear();return;}
        if(!mc.isPaused()&&mc.player.isUsingItem()&&mc.player.getUseItem().getItem() instanceof ThaumometerItem){
            var hit=mc.player.pick(5,0,true);if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK){
                // Original TC4 pace: a rune every other tick (half the current rate).
                if(mc.level.getGameTime()%2==0){
                    var r=mc.level.random;var face=block.getDirection();var centre=block.getBlockPos().getCenter().add(face.getStepX()*.501,face.getStepY()*.501,face.getStepZ()*.501);
                    double a=(r.nextDouble()-.5)*.65,b=(r.nextDouble()-.5)*.65;centre=face.getAxis()==Direction.Axis.Y?centre.add(a,0,b):face.getAxis()==Direction.Axis.X?centre.add(0,a,b):centre.add(a,b,0);
                    int colour=Math.round((.3f+r.nextFloat()*.7f)*255)<<16|Math.round((.3f+r.nextFloat()*.7f)*255);
                    if(face==Direction.UP){
                        // On the top face runes slide sideways along the player's view
                        // instead of sinking into the block: gravity would kill them instantly.
                        var look=mc.player.getLookAngle();var right=new Vec3(look.z,0,-look.x);
                        if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);
                        right=right.normalize().scale(.012+r.nextDouble()*.012);
                        if(r.nextBoolean())right=right.reverse();
                        LegacyParticles.rune(centre.add(0,.02,0),face,colour,right,0,15);
                    }else LegacyParticles.rune(centre,face,colour,15,.03f);
                }
            }
        }
        long now=mc.level.getGameTime();births.removeIf(b->now-b.start>=60);for(var birth:births){
            var entity=mc.level.getEntity(birth.entityId);var centre=entity==null?birth.position:entity.position();double age=now-birth.start;
            // Narrow purple strands rise around the new book, following the supplied TC4 frames.
            for(int n=0;n<3;n++){double angle=age*.25+n*Math.PI*2/3,radius=.08+Math.min(age,20)*.007;var at=centre.add(Math.cos(angle)*radius,.1+Math.sin(age*.2+n)*.06,Math.sin(angle)*radius);LegacyParticles.spark(at,new Vec3(Math.cos(angle+.8)*.008,.035,Math.sin(angle+.8)*.008),0xa848df,20);}
        }
    }
    private WorldScanEffects(){}
}
