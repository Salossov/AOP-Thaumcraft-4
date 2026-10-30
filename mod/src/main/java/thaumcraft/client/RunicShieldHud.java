package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.network.RunicShieldPayload;

@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class RunicShieldHud {
    private static int charge,maximum;
    public static void accept(RunicShieldPayload payload){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(payload.entityId()==mc.player.getId()){charge=payload.charge();maximum=payload.maximum();}
        var entity=mc.level.getEntity(payload.entityId());
        if(payload.struck()&&entity!=null)for(int i=0;i<32;i++){
            double angle=i*Math.PI/16,dx=Math.sin(angle)*entity.getBbWidth(),dz=Math.cos(angle)*entity.getBbWidth();
            mc.level.addParticle(ParticleTypes.ENCHANT,entity.getX()+dx,entity.getY()+entity.getBbHeight()*.6,entity.getZ()+dz,dx*.15,.05,dz*.15);
        }
    }
    private static void renderHearts(net.minecraft.client.gui.GuiGraphics g){
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive()||mc.options.hideGui||maximum<=0||!mc.gameMode.canHurtPlayer())return;
        int hearts=Math.min(40,(maximum+1)/2),rows=(hearts+9)/10,x=g.guiWidth()/2-91,y=g.guiHeight()-mc.gui.leftHeight;
        for(int heart=0;heart<hearts;heart++)heart(g,x+heart%10*8,y-heart/10*10,Math.clamp(charge-heart*2,0,2));
        mc.gui.leftHeight+=rows*10;
    }
    private static void heart(net.minecraft.client.gui.GuiGraphics g,int x,int y,int filled){
        String[] shape={"  ## ##  "," ####### ","#########","#########"," ####### ","  #####  ","   ###   ","    #    "};
        for(int row=0;row<shape.length;row++)for(int col=0;col<9;col++)if(shape[row].charAt(col)=='#'){
            boolean edge=row==0||row==shape.length-1||col==0||col==8||shape[Math.max(0,row-1)].charAt(col)!='#'||shape[Math.min(shape.length-1,row+1)].charAt(col)!='#'||shape[row].charAt(Math.max(0,col-1))!='#'||shape[row].charAt(Math.min(8,col+1))!='#';
            int color=edge?0xff291531:filled==2||filled==1&&col<4?row<3?0xffe4a1ff:0xffb950de:0xff49354f;g.fill(x+col,y+row,x+col+1,y+row+1,color);
        }
    }
    @EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event){event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH,net.minecraft.resources.ResourceLocation.parse("thaumcraft:runic_hearts"),(graphics,partial)->renderHearts(graphics));}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){charge=maximum=0;}
    @SubscribeEvent public static void respawn(ClientPlayerNetworkEvent.Clone event){charge=maximum=0;}
    private RunicShieldHud(){}
}
