package com.smp.katana;

import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.Locale;

/** The 15 unique katanas. They can never be crafted - only /katana give or task rewards. */
public enum KatanaType {
    BLACK_SILENT_KILLER("Black Silent Killer", "§8", "☠", "Silent shadow that strikes without a sound.",
            15, 15, 15, KatanaPath.SLASH, "entity.phantom.ambient", "SMOKE", "SMOKE_NORMAL"),
    RED_DESTROYER("Red Destroyer", "§c", "☄", "Raw, unstoppable destructive fury.",
            220, 20, 20, KatanaPath.BLAST, "entity.blaze.ambient", "FLAME"),
    BLUE_TEMPEST("Blue Tempest", "§9", "☂", "The roar of an ocean storm.",
            30, 90, 255, KatanaPath.STORM, "item.trident.thunder", "CLOUD"),
    EMERALD_SERPENT("Emerald Serpent", "§2", "✿", "Venomous guardian of the deep jungle.",
            20, 200, 90, KatanaPath.SLASH, "entity.witch.ambient", "HAPPY_VILLAGER", "VILLAGER_HAPPY"),
    GOLDEN_DAWN("Golden Dawn", "§6", "☀", "The first light of a new age.",
            255, 200, 40, KatanaPath.BLAST, "block.beacon.ambient", "END_ROD"),
    VIOLET_PHANTOM("Violet Phantom", "§5", "☯", "A ghost that walks between worlds.",
            150, 40, 220, KatanaPath.SLASH, "entity.vex.ambient", "WITCH", "SPELL_WITCH"),
    FROST_FANG("Frost Fang", "§b", "❄", "The frozen heart of the endless tundra.",
            130, 230, 255, KatanaPath.STORM, "entity.snow_golem.ambient", "SNOWFLAKE", "CLOUD"),
    CRIMSON_MOON("Crimson Moon", "§4", "☽", "A blood-soaked lunar blade.",
            140, 0, 30, KatanaPath.SLASH, "entity.wither.ambient", "CRIMSON_SPORE"),
    THUNDER_EMPEROR("Thunder Emperor", "§e", "⚡", "Ruler of the lightning.",
            255, 240, 60, KatanaPath.STORM, "block.conduit.ambient", "ELECTRIC_SPARK", "CRIT"),
    VOID_REAPER("Void Reaper", "§1", "☢", "It harvests what the void forgot.",
            40, 20, 140, KatanaPath.BLAST, "block.portal.ambient", "REVERSE_PORTAL", "PORTAL"),
    INFERNO_WRATH("Inferno Wrath", "§6", "♨", "Molten anger of the nether depths.",
            255, 100, 0, KatanaPath.BLAST, "block.fire.ambient", "LAVA"),
    JADE_DRAGON("Jade Dragon", "§a", "❖", "An ancient dragon sleeping in green steel.",
            60, 230, 140, KatanaPath.SLASH, "entity.ender_dragon.flap", "END_ROD"),
    SHADOW_DANCER("Shadow Dancer", "§7", "⚔", "Moves where the light never reaches.",
            100, 100, 120, KatanaPath.SLASH, "entity.bat.takeoff", "SQUID_INK"),
    CELESTIAL_BLADE("Celestial Blade", "§f", "✧", "Forged from fallen starlight.",
            255, 255, 255, KatanaPath.STORM, "block.amethyst_block.chime", "END_ROD"),
    SOUL_EATER("Soul Eater", "§3", "☣", "Hungry. It is always hungry.",
            40, 200, 210, KatanaPath.BLAST, "particle.soul_escape", "SOUL_FIRE_FLAME");

    public final String display;
    public final String color;
    public final String symbol;
    public final String theme;
    public final Color rgb;
    public final KatanaPath defaultPath;
    public final String sound;
    private final String[] particles;

    KatanaType(String display, String color, String symbol, String theme, int r, int g, int b,
               KatanaPath defaultPath, String sound, String... particles) {
        this.display = display;
        this.color = color;
        this.symbol = symbol;
        this.theme = theme;
        this.rgb = Color.fromRGB(r, g, b);
        this.defaultPath = defaultPath;
        this.sound = sound;
        this.particles = particles;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String coloredName() {
        return color + symbol + " §l" + display;
    }

    /** Signature particle of this katana (resolved for the running server version). */
    public Particle particle() {
        return Fx.find(particles);
    }

    public int number() {
        return ordinal() + 1;
    }

    public static KatanaType parse(String s) {
        if (s == null) return null;
        String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (KatanaType t : values()) {
            if (t.name().equals(n)) return t;
        }
        return null;
    }
}
