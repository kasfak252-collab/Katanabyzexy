package com.smp.katana;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tasks are listed from EASY to LEGENDARY. Common tasks (path == null) apply to every katana,
 * path tasks only count while the katana is on that path.
 */
public enum KatanaTask {

    // ---------- EASY ----------
    FIRST_BLOOD("First Blood", "Kill 5 hostile mobs", Tier.EASY, Type.MOB_KILLS, 5, 50, null),
    UNDEAD_HUNTER("Undead Hunter", "Kill 15 undead mobs", Tier.EASY, Type.UNDEAD_KILLS, 15, 80, null),
    AWAKENING("Awakening", "Use the ultimate (F) once", Tier.EASY, Type.ULTIMATES, 1, 100, null),

    // ---------- MEDIUM ----------
    NIGHT_STALKER("Night Stalker", "Kill 50 hostile mobs", Tier.MEDIUM, Type.MOB_KILLS, 50, 200, null),
    RISING_BLADE("Rising Blade", "Reach katana level 5", Tier.MEDIUM, Type.REACH_LEVEL, 5, 250, null),
    FIRST_DUEL("First Duel", "Kill 3 players", Tier.MEDIUM, Type.PLAYER_KILLS, 3, 300, null),
    SWIFT_EDGE("Swift Edge", "Kill 100 hostile mobs (Slash path)", Tier.MEDIUM, Type.MOB_KILLS, 100, 350, KatanaPath.SLASH),
    GATHERING_CLOUDS("Gathering Clouds", "Use the ultimate 10 times (Storm path)", Tier.MEDIUM, Type.ULTIMATES, 10, 350, KatanaPath.STORM),
    SPARK_OF_RUIN("Spark of Ruin", "Kill 40 nether mobs (Blast path)", Tier.MEDIUM, Type.NETHER_KILLS, 40, 350, KatanaPath.BLAST),

    // ---------- HARD ----------
    HUNDRED_CUTS("Hundred Cuts", "Deal 1,000 damage", Tier.HARD, Type.DAMAGE, 1000, 500, null),
    NETHER_PURGE("Nether Purge", "Kill 25 nether mobs", Tier.HARD, Type.NETHER_KILLS, 25, 600, null),
    STORM_CALLER("Storm Caller", "Use the ultimate 15 times", Tier.HARD, Type.ULTIMATES, 15, 600, null),
    DEATH_LINE("Death Line", "Use the ultimate 25 times (Slash path)", Tier.HARD, Type.ULTIMATES, 25, 800, KatanaPath.SLASH),
    TEMPEST_LORD("Tempest Lord", "Deal 5,000 damage (Storm path)", Tier.HARD, Type.DAMAGE, 5000, 800, KatanaPath.STORM),
    DEMOLISHER("Demolisher", "Kill 300 hostile mobs (Blast path)", Tier.HARD, Type.MOB_KILLS, 300, 800, KatanaPath.BLAST),

    // ---------- EXTREME ----------
    WARLORD("Warlord", "Kill 10 players", Tier.EXTREME, Type.PLAYER_KILLS, 10, 900, null),
    BOSS_SLAYER("Boss Slayer", "Kill 1 boss (Wither, Dragon, Warden, Elder Guardian)", Tier.EXTREME, Type.BOSS_KILLS, 1, 1500, null),
    BLOOD_SOAKED("Blood Soaked", "Deal 10,000 damage", Tier.EXTREME, Type.DAMAGE, 10000, 1500, null),
    SOVEREIGN_OF_BLADES("Sovereign of Blades", "Kill 25 players (Slash path)", Tier.EXTREME, Type.PLAYER_KILLS, 25, 2000, KatanaPath.SLASH),
    EYE_OF_THE_STORM("Eye of the Storm", "Kill 3 bosses (Storm path)", Tier.EXTREME, Type.BOSS_KILLS, 3, 2000, KatanaPath.STORM),
    WORLD_BREAKER("World Breaker", "Deal 25,000 damage (Blast path)", Tier.EXTREME, Type.DAMAGE, 25000, 2000, KatanaPath.BLAST),

    // ---------- LEGENDARY ----------
    LEGEND_OF_THE_SMP("Legend of the SMP", "Reach katana level 12", Tier.LEGENDARY, Type.REACH_LEVEL, 12, 3000, null),
    MASSACRE("Massacre", "Kill 1,000 hostile mobs", Tier.LEGENDARY, Type.MOB_KILLS, 1000, 3000, null);

    public enum Tier {
        EASY("§a", "Easy"),
        MEDIUM("§e", "Medium"),
        HARD("§6", "Hard"),
        EXTREME("§c", "Extreme"),
        LEGENDARY("§d", "Legendary");

        public final String color;
        public final String label;

        Tier(String color, String label) {
            this.color = color;
            this.label = label;
        }

        public static Tier parse(String s) {
            if (s == null) return null;
            for (Tier t : values()) {
                if (t.name().equalsIgnoreCase(s)) return t;
            }
            return null;
        }
    }

    public enum Type {
        MOB_KILLS, UNDEAD_KILLS, NETHER_KILLS, BOSS_KILLS, PLAYER_KILLS, DAMAGE, ULTIMATES, REACH_LEVEL
    }

    public final String title;
    public final String description;
    public final Tier tier;
    public final Type type;
    public final int target;
    public final int xp;
    public final KatanaPath path;

    KatanaTask(String title, String description, Tier tier, Type type, int target, int xp, KatanaPath path) {
        this.title = title;
        this.description = description;
        this.tier = tier;
        this.type = type;
        this.target = target;
        this.xp = xp;
        this.path = path;
    }

    private static final Map<KatanaPath, List<KatanaTask>> BY_PATH = new EnumMap<>(KatanaPath.class);

    /** Common tasks + this path's tasks, sorted easy -> hard. */
    public static List<KatanaTask> forPath(KatanaPath p) {
        return BY_PATH.computeIfAbsent(p, k -> {
            List<KatanaTask> out = new ArrayList<>();
            for (KatanaTask t : values()) {
                if (t.path == null || t.path == k) out.add(t);
            }
            out.sort(Comparator.<KatanaTask>comparingInt(t -> t.tier.ordinal()).thenComparingInt(KatanaTask::ordinal));
            return Collections.unmodifiableList(out);
        });
    }

    public static KatanaTask parse(String s) {
        if (s == null) return null;
        String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (KatanaTask t : values()) {
            if (t.name().equals(n)) return t;
        }
        return null;
    }
}
