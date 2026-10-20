package com.smp.katana;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Version-safe effects. Particle names changed in 1.20.5 and Sound/Particle became registry types,
 * so everything is looked up by name at runtime (reflection) and sounds are played by key string.
 * That is what lets one jar run on 1.20 -> 1.21+ (and keeps ViaVersion players error free).
 */
public final class Fx {

    private static final Map<String, Particle> FOUND = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    private static final Set<String> MODERN = Set.of("SNOWFLAKE", "ELECTRIC_SPARK", "SCULK_SOUL", "SONIC_BOOM");
    private static boolean viaSafe = false;

    private Fx() {}

    public static void setViaSafe(boolean value) {
        viaSafe = value;
    }

    /** Returns the first particle that exists on this server version, or null. */
    public static Particle find(String... names) {
        for (String n : names) {
            if (viaSafe && MODERN.contains(n)) continue;
            Particle cached = FOUND.get(n);
            if (cached != null) return cached;
            if (MISSING.contains(n)) continue;
            try {
                Object o = Particle.class.getField(n).get(null);
                if (o instanceof Particle) {
                    FOUND.put(n, (Particle) o);
                    return (Particle) o;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // not on this version
            }
            MISSING.add(n);
        }
        return null;
    }

    public static void particle(Location l, Particle p, int count, double dx, double dy, double dz, double speed) {
        if (p == null || l == null || l.getWorld() == null) return;
        try {
            l.getWorld().spawnParticle(p, l, count, dx, dy, dz, speed);
        } catch (RuntimeException ignored) {
            // particle needs extra data on this version - skip silently
        }
    }

    public static void dust(Location l, int count, double spread, Color color, float size) {
        Particle d = find("DUST", "REDSTONE");
        if (d == null || l == null || l.getWorld() == null) return;
        try {
            l.getWorld().spawnParticle(d, l, count, spread, spread, spread, 0, new Particle.DustOptions(color, size));
        } catch (RuntimeException ignored) {
        }
    }

    /** Plays a sound by namespaced key string, e.g. "entity.warden.heartbeat". Unknown keys are ignored by the client. */
    public static void sound(Location l, String key, float volume, float pitch) {
        if (l == null || l.getWorld() == null || key == null) return;
        try {
            l.getWorld().playSound(l, key, volume, pitch);
        } catch (RuntimeException ignored) {
        }
    }

    /** "slowness", "nausea", ... (minecraft keys never changed, unlike the enum names). */
    public static PotionEffectType effect(String key) {
        try {
            return PotionEffectType.getByKey(NamespacedKey.minecraft(key));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
