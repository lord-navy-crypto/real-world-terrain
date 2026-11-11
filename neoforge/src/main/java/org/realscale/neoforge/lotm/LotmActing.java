package org.realscale.neoforge.lotm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.level.BlockEvent;
/** NPC-free acting loop: unique biome exploration, skill use, research and fieldwork. */
public final class LotmActing {
    private static final String DAY_KEY="realscale_lotm_acting_day", PREFIX="realscale_lotm_acting_daily_";
    private LotmActing() {}
    public static void onPlayerTick(ServerPlayer player) {
        LotmPath path=LotmPath.of(player); if(path==null) return;
        String biome=player.level().getBiome(player.blockPosition()).unwrapKey().map(k->k.location().toString()).orElse("");
        if(biome.isEmpty()) return;
        String key="realscale_lotm_seen_"+path.id()+"_"+LotmState.sequence(player);
        CompoundTag tag=player.getPersistentData(); ListTag seen=tag.getList(key,Tag.TAG_STRING);
        StringTag id=StringTag.valueOf(biome);
        if(!seen.contains(id)){seen.add(id);tag.put(key,seen);award(player,"exploration",4,12);}
    }
    public static void recordSkillUse(Player p,int points){award(p,"skill",points,8);}
    public static void recordResearch(Player p,int points){award(p,"research",points,9);}
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player p=event.getPlayer(); if(p.level().isClientSide()||LotmPath.of(p)==null)return;
        Block b=event.getState().getBlock();
        if(b==LotmContent.STAR_ORE.get()||b==LotmContent.CURSED_ORE.get()||b==LotmContent.GRAVE_ORE.get()
          ||b==LotmContent.MIDNIGHT_BLOOM.get()||b==LotmContent.FACE_ROSE.get()||b==LotmContent.SOUL_BELLFLOWER.get()
          ||b==LotmContent.BLOOD_ROSE.get()||b==LotmContent.SPIRIT_QUARTZ_ORE.get()||b==LotmContent.RELIC_CACHE.get())
            award(p,"fieldwork",2,6);
    }
    private static void award(Player p,String category,int points,int cap) {
        if(points<=0||LotmPath.of(p)==null)return;
        CompoundTag tag=p.getPersistentData(); long day=p.level().getDayTime()/24000L;
        if(!tag.contains(DAY_KEY)||tag.getLong(DAY_KEY)!=day){
            tag.putLong(DAY_KEY,day); tag.putInt(PREFIX+"exploration",0); tag.putInt(PREFIX+"skill",0);
            tag.putInt(PREFIX+"research",0); tag.putInt(PREFIX+"fieldwork",0);
        }
        String key=PREFIX+category; int used=tag.getInt(key);
        int scaled=Math.max(0,points*LotmConfig.DIGESTION_REWARD_PERCENT.get()/100);
        int reward=Math.min(scaled,Math.max(0,cap-used));
        if(reward>0){tag.putInt(key,used+reward);LotmState.recordPractice(p,reward);}
    }
}
