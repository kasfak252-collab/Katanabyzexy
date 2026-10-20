package com.smp.katana;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Level 1-3: small basic particles
 * Level 4-7: glowing orbits + custom sounds
 * Level 8+ : heavy aura waves + (visual only) lightning flashes
 * Katana in the LEFT hand (Lv 4+): pressure on nearby players.
 */
public final class AuraTask extends BukkitRunnable {

    private static final Color PRESSURE_COLOR = Color.fromRGB(25, 0, 35);

    private final KatanaPlugin plugin;
    private final KatanaManager mgr;
    private final int interval;
    private long tick = 0;

    public AuraTask(KatanaPlugin plugin, int interval) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
        this.interval = interval;
    }

    @Override
    public void run() {
        tick++;
        FileConfiguration c = plugin.getConfig();
        boolean auraOn = c.getBoolean("aura.enabled", true);
        boolean pressureOn = c.getBoolean("pressure.enabled", true);
        int pressureEvery = Math.max(1, 20 / interval);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            KatanaData main = mgr.mainKatana(p);
            KatanaData off = mgr.offKatana(p);
            if (main == null && off == null) continue;

            if (main != null) mgr.touchOwner(main, p);
            if (off != null) mgr.touchOwner(off, p);

            if (auraOn && !mgr.isAuraOff(p)) {
                if (main != null) aura(p, main);
                if (off != null) aura(p, off);
            }
            if (pressureOn && off != null && tick % pressureEvery == 0) {
                pressure(p, off);
            }
        }
    }

    // ------------------------------------------------------------------ aura

    private void aura(Player p, KatanaData d) {
        FileConfiguration c = plugin.getConfig();
        int tier2 = c.getInt("aura.tier2-level", 4);
        int tier3 = c.getInt("aura.tier3-level", 8);
        int tier = d.level >= tier3 ? 3 : (d.level >= tier2 ? 2 : 1);

        Location base = p.getLocation();
        Color col = d.type.rgb;
        Particle sig = d.type.particle();

        if (tier == 1) {
            Fx.dust(base.clone().add(0, 1.0, 0), 2, 0.35, col, 0.8f);
            if (tick % 3 == 0) Fx.particle(base.clone().add(0, 1.2, 0), sig, 1, 0.3, 0.4, 0.3, 0.01);
            return;
        }

        // tier 2+: glowing orbits
        int orbs = tier == 2 ? 3 : 5;
        double radius = tier == 2 ? 0.9 : 1.25;
        float size = tier == 2 ? 1.2f : 1.7f;
        for (int i = 0; i < orbs; i++) {
            double a = tick * 0.35 + i * (2 * Math.PI / orbs);
            double y = 1.0 + Math.sin(tick * 0.2 + i) * 0.7;
            Location l = base.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
            Fx.dust(l, 1, 0.02, col, size);
            Fx.particle(l, sig, 1, 0, 0, 0, 0);
        }
        // custom sound roughly every 2 seconds
        if (tick % Math.max(1, 40 / interval) == 0) {
            Fx.sound(base, d.type.sound, 0.45f, 0.7f + Math.min(d.level, 12) * 0.04f);
        }

        if (tier < 3) return;

        // tier 3: expanding aura wave
        double rr = 0.6 + ((tick * 0.5) % 4.5);
        int pts = 24;
        for (int j = 0; j < pts; j++) {
            double a = 2 * Math.PI * j / pts;
            Location l = base.clone().add(Math.cos(a) * rr, 0.15, Math.sin(a) * rr);
            Fx.dust(l, 1, 0.0, col, 2.0f);
            if (j % 4 == 0) Fx.particle(l, sig, 1, 0.05, 0.1, 0.05, 0.01);
        }
        // rising pillar
        for (double y = 0; y <= 2.4; y += 0.6) {
            Fx.dust(base.clone().add(0, y, 0), 2, 0.25, col, 1.5f);
        }
        // visual-only lightning flashes
        if (c.getBoolean("aura.lightning", true)) {
            int every = Math.max(1, c.getInt("aura.lightning-interval-ticks", 200) / interval);
            if (tick % every == 0 && ThreadLocalRandom.current().nextDouble() < 0.6) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double ang = r.nextDouble() * Math.PI * 2;
                double dist = 3 + r.nextDouble() * 3;
                Location strike = base.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                World w = strike.getWorld();
                if (w != null) w.strikeLightningEffect(strike); // effect only: no damage, no fire
            }
        }
    }

    // ------------------------------------------------------------------ left-hand pressure

    private void pressure(Player holder, KatanaData d) {
        FileConfiguration c = plugin.getConfig();
        int min = c.getInt("pressure.min-level", 4);
        if (d.level < min) return;

        double radius = c.getDouble("pressure.radius", 8.0)
                + (d.level - min) * c.getDouble("pressure.radius-per-level", 0.4);
        Location center = holder.getLocation();
        Particle smoke = Fx.find("SMOKE", "SMOKE_NORMAL");

        // holder visuals: dark cloud + faint ring marking the pressure field
        Fx.dust(center.clone().add(0, 1.0, 0), 10, 0.8, PRESSURE_COLOR, 1.6f);
        Fx.particle(center.clone().add(0, 1.0, 0), smoke, 6, 0.6, 0.6, 0.6, 0.01);
        int pts = 28;
        for (int j = 0; j < pts; j++) {
            double a = 2 * Math.PI * j / pts;
            Fx.dust(center.clone().add(Math.cos(a) * radius, 0.2, Math.sin(a) * radius), 1, 0, PRESSURE_COLOR, 1.2f);
        }

        boolean slow = c.getBoolean("pressure.slowness", true);
        int slowAmp = c.getInt("pressure.slowness-amplifier", 0);
        boolean nausea = c.getBoolean("pressure.nausea", true)
                && d.level >= c.getInt("pressure.nausea-min-level", 6);
        boolean heartbeat = c.getBoolean("pressure.heartbeat", true);
        PotionEffectType slowType = Fx.effect("slowness");
        PotionEffectType nauseaType = Fx.effect("nausea");
        float pitch = 0.9f + Math.min(d.level, 12) * 0.03f;

        for (Entity e : holder.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player target)) continue;
            if (target.equals(holder) || target.getGameMode() == GameMode.SPECTATOR) continue;
            if (target.hasPermission("katana.immune")) continue;
            if (target.getLocation().distanceSquared(center) > radius * radius) continue;

            if (slow && slowType != null) {
                target.addPotionEffect(new PotionEffect(slowType, 60, slowAmp, true, false, false));
            }
            if (nausea && nauseaType != null) {
                target.addPotionEffect(new PotionEffect(nauseaType, 100, 0, true, false, false));
            }
            if (heartbeat) {
                target.playSound(target.getLocation(), "entity.warden.heartbeat", 0.9f, pitch);
            }
            Fx.particle(target.getEyeLocation(), smoke, 3, 0.3, 0.3, 0.3, 0.01);
        }
    }
}
