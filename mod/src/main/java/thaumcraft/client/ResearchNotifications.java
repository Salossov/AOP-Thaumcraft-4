package thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import thaumcraft.api.aspects.Aspect;

/**
 * Original REHNotifyHandler/PlayerNotifications: on aspect gain an aspect icon flies from a
 * random screen position along a bezier toward the Thaumonomicon in the top-right corner,
 * alongside "Learn More" style notifications stacked on the right edge.
 */
@EventBusSubscriber(modid="thaumcraft_aop",value=Dist.CLIENT)
public final class ResearchNotifications {
    private static final long NOTIFICATION_DELAY=6000,ASPECT_LIFE=1500;
    private static final ResearchNotificationQueue<Entry> TEXT=new ResearchNotificationQueue<>(8,NOTIFICATION_DELAY);
    private static final List<Fly> FLYING=new ArrayList<>();
    private static final List<Leaving> LEAVING=new ArrayList<>();
    private static final Map<ResearchNotificationQueue.Row<Entry>,Move> MOVING=new IdentityHashMap<>();
    private record Leaving(Entry entry,float y,float alpha,long started){}
    private record Move(float offset,long started){}
    private static float remaining(long started,long now){return (float)Math.pow(1-Math.clamp((now-started)/250f,0,1),3);}
    private static float rowY(ResearchNotificationQueue.Row<Entry> row,int index,long now,int sh){var move=MOVING.get(row);return sh-index*8-8-TEXT.shift(now)+(move==null?0:move.offset*remaining(move.started,now));}
    private record Entry(String text,Aspect aspect,int color){}
    private record Fly(Aspect aspect,float startX,float startY,long created,long expire){}

    public static void aspect(Aspect aspect){
        var level=Minecraft.getInstance().level;if(level==null)return;
        long now=System.nanoTime()/1000000+level.random.nextInt(1000);
        float x=.4f+level.random.nextFloat()*.2f,y=.4f+level.random.nextFloat()*.2f;
        FLYING.add(new Fly(aspect,x,y,now,now+ASPECT_LIFE));
    }
    public static void text(String text,Aspect aspect){text(text,aspect!=null?aspect.getColor():0xffffff,aspect);}
    public static void text(String text,int color){text(text,color,null);}
    public static void text(String text,int color,Aspect aspect){long now=System.nanoTime()/1000000;TEXT.add(new Entry(text,aspect,color),now);}

    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
        long now=System.nanoTime()/1000000;var g=event.getGuiGraphics();int sw=g.guiWidth(),sh=g.guiHeight()-(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen?14:0);
        text(g,mc,now,sw,sh);flying(g,mc,now,sw,sh);
    }
    private static void text(net.minecraft.client.gui.GuiGraphics g,Minecraft mc,long now,int sw,int sh){
        TEXT.advance(now);
        if(TEXT.remaining()>0&&!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)){var more=net.minecraft.network.chat.Component.translatable("thaumcraft.notifications.remaining",TEXT.remaining());g.pose().pushPose();g.pose().translate(sw-10,sh-8*TEXT.rows().size()-14,300);g.pose().scale(.5f,.5f,1);g.drawString(mc.font,more,-mc.font.width(more),0,0xffcccccc,true);g.pose().popPose();}
        int entry=0;
        for(var row:TEXT.rows()){
            drawRow(g,mc,row.value(),now,sw,rowY(row,entry,now,sh),TEXT.alpha(entry,now),row.entered(),false);entry++;
        }
        LEAVING.removeIf(row->now-row.started>=250);
        for(var row:LEAVING){float progress=1-remaining(row.started,now),distance=mc.font.width(row.entry.text)*.5f+30;
            drawRow(g,mc,row.entry,now,sw+distance*progress,row.y,row.alpha*(1-progress),-1,mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
        }
        MOVING.entrySet().removeIf(e->now-e.getValue().started>=250);
    }
    private static void drawRow(net.minecraft.client.gui.GuiGraphics g,Minecraft mc,Entry li,long now,float right,float y,float opacity,long entered,boolean cross){
        int width=mc.font.width(li.text),alpha=Math.round(opacity*255);if(alpha<4)return;
        int color=(alpha<<24)|(li.aspect!=null?0xffffff:li.color&0xffffff);
        g.pose().pushPose();g.pose().translate(right-width/2f-10,y,300);g.pose().scale(.5f,.5f,1);g.drawString(mc.font,li.text,-4,-8,color,true);g.pose().popPose();
        if(li.aspect!=null&&li.aspect.texture()!=null){g.pose().pushPose();g.pose().translate(right-9,y-6,300);com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();int c=li.aspect.getColor();g.setColor((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,Math.min(1,alpha/255f*.5f));g.pose().scale(.5f,.5f,1);ResearchGraphics.blit(g,net.minecraft.resources.ResourceLocation.parse(li.aspect.texture()),0,0,16,16,0,0,32,32,32,32);g.setColor(1,1,1,1);g.pose().popPose();}
        if(entered>=0&&now-entered<1500){float p=(now-entered)/1500f;g.pose().pushPose();g.pose().translate(right-9-width*1.5f*p*p*p,y-8,310);ResearchGraphics.discoveryFlame(g,0,0,(1-p)*.6f);g.flush();g.pose().popPose();}
        if(cross){g.pose().pushPose();g.pose().translate(0,0,500);small(g,mc,net.minecraft.network.chat.Component.literal("\u00d7"),right-width*.5f-20,y-5,(alpha<<24)|0xcccccc);g.pose().popPose();}
    }
    private static void dismiss(int index,long now,int sh,boolean all){
        var rows=TEXT.rows();var positions=new IdentityHashMap<ResearchNotificationQueue.Row<Entry>,Float>();
        for(int i=0;i<rows.size();i++){var row=rows.get(i);float y=rowY(row,i,now,sh);positions.put(row,y);if(all||i==index)LEAVING.add(new Leaving(row.value(),y,TEXT.alpha(i,now),now));}
        if(all){TEXT.clear();MOVING.clear();FLYING.clear();return;}
        TEXT.dismiss(index,now);int i=0;for(var row:TEXT.rows()){var old=positions.get(row);MOVING.remove(row);if(old!=null)MOVING.put(row,new Move(old-rowY(row,i,now,sh),now));i++;}
        MOVING.keySet().removeIf(row->!positions.containsKey(row)&&!TEXT.rows().contains(row));
    }
    private static void flying(net.minecraft.client.gui.GuiGraphics g,Minecraft mc,long now,int sw,int sh){
        if(FLYING.isEmpty())return;
        FLYING.removeIf(li->li.expire<now);float mainAlpha=0;
        for(var li:FLYING){if(li.created>now)continue;
            int startX=(int)(sw*li.startX),startY=(int)(sh*li.startY),endX=sw,endY=-8,bezierX=(int)(sw*(.25f+li.startX)),bezierY=(int)(sh*li.startY);
            double t=(double)(now-li.created)/(li.expire-li.created);if(t>1)continue;
            double x=(1-t)*(1-t)*startX+2*(1-t)*t*bezierX+t*t*endX,y=(1-t)*(1-t)*startY+2*(1-t)*t*bezierY+t*t*endY;
            float alpha=t<.3f?(float)(t/.3f):t>.66f?(float)(1-(t-.66f)/.34f):1;if(alpha>mainAlpha)mainAlpha=alpha;
            g.pose().pushPose();g.pose().translate(x,y,300);float size=16*alpha;
            if(li.aspect.texture()!=null){int c=li.aspect.getColor();com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();g.setColor((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,Math.min(1,alpha));ResearchGraphics.blit(g,net.minecraft.resources.ResourceLocation.parse(li.aspect.texture()),(int)(-size/2),(int)(-size/2),(int)size,(int)size,0,0,32,32,32,32);g.setColor(1,1,1,1);}
            g.pose().popPose();}
        if(mainAlpha>0){g.pose().pushPose();com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();g.setColor(1,1,1,Math.min(1,mainAlpha));g.pose().translate(sw-16,0,300);ResearchGraphics.blit(g,net.minecraft.resources.ResourceLocation.parse("thaumcraft:textures/items/thaumonomicon.png"),0,0,0,0,16,16,16,16);g.setColor(1,1,1,1);g.pose().popPose();}
    }
    /** Chat owns the clickable overlay; ordinary gameplay retains a passive HUD. */
    @SubscribeEvent public static void chatControls(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){
        if(!(event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen))return;
        var mc=Minecraft.getInstance();if(mc.player==null||TEXT.rows().isEmpty())return;var g=event.getGuiGraphics();long now=System.nanoTime()/1000000;int sw=g.guiWidth(),sh=g.guiHeight()-14;
        var clean=net.minecraft.network.chat.Component.translatable("thaumcraft.notifications.clean_all").withStyle(net.minecraft.ChatFormatting.UNDERLINE);
        float cleanWidth=mc.font.width(clean)*.5f;int headerY=sh-8*TEXT.rows().size()-14;float cleanX=sw-2-cleanWidth;
        g.pose().pushPose();g.pose().translate(0,0,500);
        small(g,mc,clean,cleanX,headerY,ResearchGraphics.inside(event.getMouseX(),event.getMouseY(),(int)cleanX,headerY,(int)Math.ceil(cleanWidth),7)?0xffaaffaa:0xff66ff66);
        if(TEXT.remaining()>0){var more=net.minecraft.network.chat.Component.translatable("thaumcraft.notifications.remaining",TEXT.remaining());small(g,mc,more,cleanX-4-mc.font.width(more)*.5f,headerY,0xffcccccc);}
        int index=0;for(var row:TEXT.rows()){int y=Math.round(rowY(row,index++,now,sh)-6),x=dismissX(mc,row.value(),sw);boolean hover=ResearchGraphics.inside(event.getMouseX(),event.getMouseY(),x,y,9,8);small(g,mc,net.minecraft.network.chat.Component.literal("\u00d7"),x+2,y+1,hover?0xffff6666:0xffcccccc);}
        g.flush();g.pose().popPose();
    }
    private static int dismissX(Minecraft mc,Entry entry,int width){return (int)Math.floor(width-mc.font.width(entry.text)*.5f-22);}
    private static void small(net.minecraft.client.gui.GuiGraphics g,Minecraft mc,net.minecraft.network.chat.Component text,float x,float y,int color){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.5f,.5f,1);g.drawString(mc.font,text,0,0,color,true);g.pose().popPose();}
    @SubscribeEvent public static void click(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event){
        if(!(event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen)||event.getButton()!=0||TEXT.rows().isEmpty())return;
        var mc=Minecraft.getInstance();int sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight()-14;long now=System.nanoTime()/1000000;
        int width=(int)Math.ceil(mc.font.width(net.minecraft.network.chat.Component.translatable("thaumcraft.notifications.clean_all"))*.5);int y=sh-8*TEXT.rows().size()-14;
        if(ResearchGraphics.inside(event.getMouseX(),event.getMouseY(),sw-2-width,y,width,7)){dismiss(-1,now,sh,true);event.setCanceled(true);return;}
        for(int index=0;index<TEXT.rows().size();index++){y=Math.round(rowY(TEXT.rows().get(index),index,now,sh)-6);if(ResearchGraphics.inside(event.getMouseX(),event.getMouseY(),dismissX(mc,TEXT.rows().get(index).value(),sw),y,9,8)){dismiss(index,now,sh,false);event.setCanceled(true);return;}}
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){TEXT.clear();FLYING.clear();LEAVING.clear();MOVING.clear();ResearchHighlights.clear();}
    private ResearchNotifications(){}
}
