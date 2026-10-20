package com.smp.katana;

public enum KatanaPath {
    SLASH("Slash", "§b", "Phantom Slash", "Dash forward in a blinding line and cut everything in the way."),
    STORM("Storm", "§e", "Thunder Dominion", "Summon a temporary thunderstorm that rains lightning around you."),
    BLAST("Blast", "§c", "Cataclysm", "Unleash waves of non-griefing explosions that crush the area.");

    private final String display;
    private final String color;
    private final String ultimateName;
    private final String description;

    KatanaPath(String display, String color, String ultimateName, String description) {
        this.display = display;
        this.color = color;
        this.ultimateName = ultimateName;
        this.description = description;
    }

    public String display() { return display; }
    public String color() { return color; }
    public String colored() { return color + display; }
    public String ultimateName() { return ultimateName; }
    public String description() { return description; }
    public String id() { return name().toLowerCase(); }

    public static KatanaPath parse(String s) {
        if (s == null) return null;
        for (KatanaPath p : values()) {
            if (p.name().equalsIgnoreCase(s)) return p;
        }
        return null;
    }
}
