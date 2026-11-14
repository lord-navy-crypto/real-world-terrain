package org.realscale.neoforge.lotm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** NPC-free acting loop: unique biome exploration, skill use, research and fieldwork. */
public final class LotmActing {
    private static final String DAY_KEY = "realscale_lotm_acting_day";
    private static final String PREFIX = "realscale_lotm_acting_daily_";
    private static final String RESEARCH_PREFIX = "realscale_lotm_research_seen_";
    private static final String JOURNAL_PREFIX = "realscale_lotm_research_journal_";
    private static final String JOURNAL_MIGRATED_PREFIX = "realscale_lotm_research_journal_migrated_";
    private static final String EVENT_JOURNAL_PREFIX = "realscale_lotm_event_journal_";
    private static final int MAX_EVENT_JOURNAL_ENTRIES = 128;
    private static final int MAX_RESEARCH_JOURNAL_ENTRIES = 128;
    private static final int MAX_RESEARCH_SOURCE_LENGTH = 256;
    private LotmActing() {}

    public static void onPlayerTick(ServerPlayer player) {
        LotmPath path = LotmPath.of(player);
        if (!isActive(player) || path == null) return;
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        if (biome.isEmpty()) return;
        String key = "realscale_lotm_seen_" + path.id() + "_" + LotmState.sequence(player);
        CompoundTag tag = player.getPersistentData();
        ListTag seen = tag.getList(key, Tag.TAG_STRING);
        StringTag id = StringTag.valueOf(biome);
        if (!seen.contains(id)) {
            int reward = award(player, "exploration", 4);
            // Do not permanently mark a biome while the daily cap is full.
            if (reward > 0) {
                seen.add(id);
                tag.put(key, seen);
            }
        }
    }

    public static void recordSkillUse(Player player, int points) { award(player, "skill", points); }

    public static void recordSkillUse(Player player, int points, int slot) {
        award(player, "skill", points);
        LotmPath path = LotmPath.of(player);
        if (path != null && slot >= 1 && slot <= 2) {
            recordJournalEvent(player, "skill", "verified",
                    path.id() + ":" + LotmState.sequence(player) + ":" + slot);
        }
    }
    /**
     * Record a clue independently of digestion rewards. A full daily cap must never
     * erase a discovery from the journal; reward eligibility is tracked per sequence.
     */
    public static int recordResearchDiscovery(Player player, String sourceId, int points) {
        LotmPath path = LotmPath.of(player);
        if (!isActive(player) || path == null || sourceId == null || sourceId.isBlank()) return 0;

        String boundedId = sourceId.length() > MAX_RESEARCH_SOURCE_LENGTH
                ? sourceId.substring(0, MAX_RESEARCH_SOURCE_LENGTH) : sourceId;
        StringTag id = StringTag.valueOf(boundedId);
        CompoundTag tag = player.getPersistentData();

        // Keep the research journal pathway-wide so Sequence 9 discoveries remain
        // visible after advancement. The journal is bounded for long-running saves.
        String journalKey = researchJournalKey(path);
        ListTag journal = loadResearchJournal(player, path);
        boolean newlyRecorded = !journal.contains(id);
        if (newlyRecorded) {
            while (journal.size() >= MAX_RESEARCH_JOURNAL_ENTRIES) journal.remove(0);
            journal.add(id);
            tag.put(journalKey, journal);
        }

        // Digestion rewards remain unique per pathway + sequence. A clue recorded
        // at the daily cap is still kept in the journal without granting extra XP.
        ListTag rewarded = tag.getList(researchKey(player), Tag.TAG_STRING);
        int reward = 0;
        if (!rewarded.contains(id)) {
            reward = award(player, "research", points);
            if (reward > 0) {
                rewarded.add(id);
                tag.put(researchKey(player), rewarded);
            }
        }

        if (newlyRecorded) {
            recordJournalEvent(player, researchCategory(boundedId), "discovered", boundedId);
        }
        return reward;
    }

    /** Existing API for other scripted research events; scans use unique discoveries. */
    public static void recordResearch(Player player, int points) { award(player, "research", points); }

    private static String researchKey(Player player) {
        LotmPath path = LotmPath.of(player);
        return path == null ? RESEARCH_PREFIX + "none"
                : RESEARCH_PREFIX + path.id() + "_" + LotmState.sequence(player);
    }

    private static String researchJournalKey(LotmPath path) {
        return JOURNAL_PREFIX + path.id();
    }

    private static String researchMigrationKey(LotmPath path) {
        return JOURNAL_MIGRATED_PREFIX + path.id();
    }

    /**
     * Merge discoveries from older per-sequence saves into the pathway-wide journal.
     * The migration marker avoids rescanning old lists on every menu data tick.
     */
    private static ListTag loadResearchJournal(Player player, LotmPath path) {
        CompoundTag tag = player.getPersistentData();
        String journalKey = researchJournalKey(path);
        ListTag journal = tag.getList(journalKey, Tag.TAG_STRING);
        boolean changed = false;
        while (journal.size() > MAX_RESEARCH_JOURNAL_ENTRIES) {
            journal.remove(0);
            changed = true;
        }
        String migrationKey = researchMigrationKey(path);
        if (!tag.getBoolean(migrationKey)) {
            for (int sequence : new int[] {9, 8}) {
                ListTag legacy = tag.getList(RESEARCH_PREFIX + path.id() + "_" + sequence, Tag.TAG_STRING);
                for (int index = 0; index < legacy.size(); index++) {
                    String source = legacy.getString(index);
                    if (source.isBlank()) continue;
                    String bounded = source.length() > MAX_RESEARCH_SOURCE_LENGTH
                            ? source.substring(0, MAX_RESEARCH_SOURCE_LENGTH) : source;
                    StringTag id = StringTag.valueOf(bounded);
                    if (journal.contains(id)) continue;
                    while (journal.size() >= MAX_RESEARCH_JOURNAL_ENTRIES) journal.remove(0);
                    journal.add(id);
                    changed = true;
                }
            }
            tag.putBoolean(migrationKey, true);
            changed = true;
        }
        if (changed) tag.put(journalKey, journal);
        return journal;
    }

    private static String eventJournalKey(LotmPath path) {
        return EVENT_JOURNAL_PREFIX + path.id();
    }

    private static String researchCategory(String source) {
        int separator = source.indexOf(':');
        if (separator <= 0) return "other";
        String kind = source.substring(0, separator);
        return switch (kind) {
            case "entity", "block", "item", "biome" -> kind;
            default -> "other";
        };
    }

    private static String[] parseJournalEvent(String value) {
        if (value == null) return null;
        String[] parts = value.split("\\|", 5);
        return parts.length == 5 && "event".equals(parts[0]) ? parts : null;
    }

    private static int statusRank(String status) {
        return switch (status) {
            case "discovered" -> 1;
            case "incomplete" -> 2;
            case "verified" -> 3;
            default -> 0;
        };
    }

    private static long journalGameTime(Player player) {
        return player instanceof ServerPlayer server && server.getServer() != null
                ? server.getServer().overworld().getGameTime() : player.level().getGameTime();
    }

    /**
     * Upsert one typed journal entry. A verified result can advance an earlier
     * discovery/incomplete record, but a later duplicate cannot downgrade it.
     */
    public static void recordJournalEvent(Player player, String category, String status, String sourceId) {
        LotmPath path = LotmPath.of(player);
        if (!isActive(player) || path == null || sourceId == null || sourceId.isBlank()) return;
        if (!List.of("entity", "block", "item", "biome", "material", "potion",
                "alchemy", "skill", "pathway", "other").contains(category)
                || statusRank(status) == 0) return;

        String id = sourceId.replace("|", "/");
        if (id.length() > 190) id = id.substring(0, 190);
        CompoundTag tag = player.getPersistentData();
        String key = eventJournalKey(path);
        ListTag journal = tag.getList(key, Tag.TAG_STRING);
        int existingRank = 0;
        for (int index = 0; index < journal.size(); index++) {
            String[] old = parseJournalEvent(journal.getString(index));
            if (old != null && category.equals(old[2]) && id.equals(old[4])) {
                existingRank = Math.max(existingRank, statusRank(old[3]));
            }
        }
        if (statusRank(status) <= existingRank) return;
        for (int index = journal.size() - 1; index >= 0; index--) {
            String[] old = parseJournalEvent(journal.getString(index));
            if (old != null && category.equals(old[2]) && id.equals(old[4])) journal.remove(index);
        }

        long time = Math.max(0L, journalGameTime(player));
        journal.add(StringTag.valueOf("event|" + time + "|" + category + "|" + status + "|" + id));
        while (journal.size() > MAX_EVENT_JOURNAL_ENTRIES) journal.remove(0);
        tag.put(key, journal);

        if (player instanceof ServerPlayer serverPlayer) {
            if (LotmPreferences.journalNotificationsEnabled(player)
                    && List.of("material", "potion", "alchemy", "skill", "pathway").contains(category)) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.realscale.lotm.journal.updated",
                        Component.translatable("message.realscale.lotm.journal.category." + category),
                        Component.translatable("message.realscale.lotm.journal.status." + status)), true);
            }
            LotmPanelNetwork.syncResearchLog(serverPlayer);
        }
    }

    /** Typed journal snapshot plus migrated legacy clues; each item carries game time, category and status. */
    public static List<String> journalEntries(Player player) {
        LotmPath path = LotmPath.of(player);
        if (path == null) return List.of();
        List<String> entries = new ArrayList<>();
        ListTag journal = player.getPersistentData().getList(eventJournalKey(path), Tag.TAG_STRING);
        for (int index = 0; index < journal.size(); index++) {
            String value = journal.getString(index);
            if (parseJournalEvent(value) != null) entries.add(value);
        }
        ListTag legacy = loadResearchJournal(player, path);
        for (int index = 0; index < legacy.size(); index++) {
            String source = legacy.getString(index);
            String category = researchCategory(source);
            String bounded = source.replace("|", "/");
            if (bounded.length() > 190) bounded = bounded.substring(0, 190);
            boolean exists = false;
            for (String value : entries) {
                String[] parts = parseJournalEvent(value);
                if (parts != null && category.equals(parts[2]) && bounded.equals(parts[4])) {
                    exists = true;
                    break;
                }
            }
            if (!exists) entries.add("event|0|" + category + "|discovered|" + bounded);
        }
        return List.copyOf(entries);
    }

    /** Pathway-wide journal history survives sequence changes and death/respawn. */
    public static List<String> researchDiscoveries(Player player) {
        LotmPath path = LotmPath.of(player);
        if (path == null) return List.of();
        ListTag journal = loadResearchJournal(player, path);
        List<String> ids = new ArrayList<>(journal.size());
        for (int i = 0; i < journal.size(); i++) ids.add(journal.getString(i));
        return List.copyOf(ids);
    }

    /** Track placement in world data so trading or relogging does not reset provenance. */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Player)
                || !isFieldworkBlock(event.getPlacedBlock().getBlock())) return;
        LotmResourceProvenance.get(level).markPlaced(event.getPos());
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player.level() instanceof ServerLevel level)
                || event.isCanceled() || !isFieldworkBlock(event.getState().getBlock())) return;
        // Always clear the mark on breaking, even for a non-Beyonder or a creative player.
        if (LotmResourceProvenance.get(level).consumePlacement(event.getPos())) return;
        if (!player.isCreative() && isActive(player)) {
            award(player, "fieldwork", 2);
            String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(event.getState().getBlock()).toString();
            recordJournalEvent(player, "material", "verified", blockId);
        }
    }

    /**
     * ExplosionEvent.Start is the cancellable phase. Detonate is non-cancellable
     * and only supplies the accepted explosion's affected positions.
     */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.getAffectedBlocks().isEmpty()) return;
        LotmResourceProvenance.get(level).consumePlacements(event.getAffectedBlocks());
    }

    static boolean isFieldworkBlock(Block block) {
        return block == LotmContent.STAR_ORE.get() || block == LotmContent.CURSED_ORE.get()
                || block == LotmContent.GRAVE_ORE.get() || block == LotmContent.LUNAR_MICA_ORE.get()
                || block == LotmContent.ECHO_QUARTZ_ORE.get()
                || block == LotmContent.ABYSSAL_SALT_ORE.get()
                || block == LotmContent.MIDNIGHT_BLOOM.get()
                || block == LotmContent.FACE_ROSE.get()
                || block == LotmContent.SOUL_BELLFLOWER.get()
                || block == LotmContent.BLOOD_ROSE.get()
                || block == LotmContent.RELIC_CACHE.get();
    }

    public static int dailyUsed(Player player, String category) {
        int cap = dailyCap(category);
        if (!isActive(player) || cap == 0) return 0;
        resetDailyIfNeeded(player);
        CompoundTag tag = player.getPersistentData();
        String key = PREFIX + category;
        int stored = tag.getInt(key);
        int used = clampDailyUsed(stored, cap);
        if (stored != used) tag.putInt(key, used);
        return used;
    }

    static int clampDailyUsed(int stored, int cap) {
        return Math.min(Math.max(0, stored), Math.max(0, cap));
    }

    static int scaledReward(int points, int percent, int remaining) {
        if (points <= 0 || percent <= 0 || remaining <= 0) return 0;
        long scaled = (long) points * percent / 100L;
        return (int) Math.min(scaled, (long) remaining);
    }

    public static int dailyCap(String category) {
        return switch (category) {
            case "exploration" -> 12;
            case "skill" -> 8;
            case "research" -> 9;
            case "fieldwork" -> 6;
            default -> 0;
        };
    }

    static long activityDay(long gameTime) {
        return Math.floorDiv(gameTime, 24000L);
    }

    private static void resetDailyIfNeeded(Player player) {
        CompoundTag tag = player.getPersistentData();
        // Server game time cannot be accelerated by sleeping or /time set.
        // Use the Overworld clock across every dimension.
        long gameTime = player instanceof ServerPlayer server && server.getServer() != null
                ? server.getServer().overworld().getGameTime() : player.level().getGameTime();
        long day = activityDay(gameTime);
        if (!tag.contains(DAY_KEY) || tag.getLong(DAY_KEY) != day) {
            tag.putLong(DAY_KEY, day);
            tag.putInt(PREFIX + "exploration", 0);
            tag.putInt(PREFIX + "skill", 0);
            tag.putInt(PREFIX + "research", 0);
            tag.putInt(PREFIX + "fieldwork", 0);
        }
    }

    private static int award(Player player, String category, int points) {
        if (points <= 0 || !isActive(player)) return 0;
        int cap = dailyCap(category);
        if (cap == 0) return 0;
        resetDailyIfNeeded(player);
        CompoundTag tag = player.getPersistentData();
        String key = PREFIX + category;
        int stored = tag.getInt(key);
        int used = clampDailyUsed(stored, cap);
        if (stored != used) tag.putInt(key, used);
        int reward = scaledReward(points, LotmConfig.DIGESTION_REWARD_PERCENT.get(), cap - used);
        if (reward > 0) {
            tag.putInt(key, used + reward);
            LotmState.recordPractice(player, reward);
        }
        return reward;
    }

    private static boolean isActive(Player player) {
        int sequence = LotmState.sequence(player);
        return LotmPath.of(player) != null && (sequence == 9 || sequence == 8);
    }

    /** Preserve daily limits and unique-biome discovery across death and respawn. */
    public static void copy(Player original, Player clone) {
        CompoundTag source = original.getPersistentData();
        CompoundTag target = clone.getPersistentData();
        for (String key : new String[] {DAY_KEY, PREFIX + "exploration", PREFIX + "skill",
                PREFIX + "research", PREFIX + "fieldwork"}) {
            if (source.contains(key)) target.put(key, source.get(key).copy());
        }
        for (LotmPath path : LotmPath.values()) {
            String journalKey = researchJournalKey(path);
            String migrationKey = researchMigrationKey(path);
            String eventJournalKey = eventJournalKey(path);
            if (source.contains(eventJournalKey)) target.put(eventJournalKey, source.get(eventJournalKey).copy());
            if (source.contains(journalKey)) target.put(journalKey, source.get(journalKey).copy());
            if (source.contains(migrationKey)) target.put(migrationKey, source.get(migrationKey).copy());
            for (int sequence : new int[] {9, 8}) {
                String suffix = path.id() + "_" + sequence;
                for (String key : new String[] {"realscale_lotm_seen_" + suffix,
                        RESEARCH_PREFIX + suffix}) {
                    if (source.contains(key)) target.put(key, source.get(key).copy());
                }
            }
        }
    }
}
