package thaumcraft.items;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.wands.*;
import thaumcraft.api.aspects.*;
import thaumcraft.registry.*;
public final class WandMaterials {
    public static void initialize(){
        new WandRod("wood",25,()->new ItemStack(Items.STICK),1,null);
        String[] tags={"greatwood","obsidian","silverwood","ice","quartz","reed","blaze","bone"};
        int[] capacity={50,75,100,75,75,75,75,75},cost={3,6,9,6,6,6,6,6};
        int[] staffCapacity={125,175,250,175,175,175,175,175},staffCost={8,14,24,14,14,14,14,14};
        String[] renewal={null,"terra",null,"aqua","ordo","aer","ignis","perditio"};
        for(int i=0;i<tags.length;i++){
            final String tag=tags[i],aspect=renewal[i];
            java.util.function.BiConsumer<ItemStack,Player> update=aspect==null?null:(stack,player)->{
                var a=Aspect.getAspect(aspect);if(player.tickCount%200==0&&WandVis.get(stack,a)<WandVis.capacity(stack)/10)WandVis.add(stack,a,1);
            };
            new WandRod(tag,capacity[i],()->new ItemStack(R3Equipment.RODS.get(tag).get()),cost[i],update);
            new StaffRod(tag,staffCapacity[i],()->new ItemStack(R3Equipment.RODS.get(tag+"_staff").get()),staffCost[i],update);
        }
        var primal=new StaffRod("primal",250,()->new ItemStack(R3Equipment.RODS.get("primal_staff").get()),32,(stack,player)->{
            if(player.tickCount%50!=0)return;var candidates=Aspects.PRIMALS.stream().filter(a->WandVis.get(stack,a)<WandVis.capacity(stack)/10).toList();
            if(!candidates.isEmpty())WandVis.add(stack,candidates.get(player.getRandom().nextInt(candidates.size())),1);
        });
        primal.setRunes(true);
        cap("iron",1.1f,1,Set.of(),1.1f);cap("gold",1,3,Set.of(),1);cap("thaumium",.9f,6,Set.of(),.9f);cap("void",.8f,9,Set.of(),.8f);
        cap("copper",1.1f,2,Set.of(Aspect.getAspect("ordo"),Aspect.getAspect("perditio")),1);
        cap("silver",1,4,Set.of(Aspect.getAspect("aer"),Aspect.getAspect("terra"),Aspect.getAspect("ignis"),Aspect.getAspect("aqua")),.95f);
    }
    private static void cap(String tag,float base,int cost,Set<Aspect> special,float modifier){new WandCap(tag,base,special,modifier,()->new ItemStack(tag.equals("iron")?R2Content.IRON_CAP.get():R3Equipment.CAPS.get(tag).get()),cost);}
    public static WandParts parts(ItemStack stack){return stack.getOrDefault(WandComponents.PARTS.get(),WandParts.DEFAULT);}
    public static WandRod rod(ItemStack stack){return WandRod.rods.getOrDefault(parts(stack).rod(),WandRod.rods.get("wood"));}
    public static WandCap cap(ItemStack stack){return WandCap.caps.getOrDefault(parts(stack).cap(),WandCap.caps.get("iron"));}
    public static boolean staff(ItemStack stack){return stack.getItem() instanceof CastingWandItem wand&&wand.kind()==CastingWandItem.Kind.STAFF;}
    public static boolean sceptre(ItemStack stack){return stack.getItem() instanceof CastingWandItem wand&&wand.kind()==CastingWandItem.Kind.SCEPTRE;}
    public static float modifier(ItemStack stack,Aspect aspect,float discount){var cap=cap(stack);if(cap!=null&&cap.modifier(aspect)<=0.0f)return 0.0f;return Math.max(.1f,(cap==null?1.1f:cap.modifier(aspect))-discount-(sceptre(stack)?.1f:0));}
    private WandMaterials(){}
}
