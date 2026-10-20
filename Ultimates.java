package com.smp.katana;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * F-key ultimates. Nothing here ever changes blocks: lightning is effect-only and
 * "explosions" are particles + sound + damage. Damage goes through LivingEntity#damage(amount, source),
 * so WorldGuard & other protection plugins can still cancel it.
 */
public final class Ultimates {

    private final KatanaPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** Players whose damage is ultimate damage right now (so the normal-hit bonus is skipped). */
    private final Set<UUID> ultimateDamage = new HashSet<>();

    // temporary weather bookkeeping
    private final Map<UUID, boolean[]> stormPrev = new HashMap<>();
    private final Map<UUID, Long> stormEnd = new HashMap<>();

    public Ultimates(KatanaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isUltimateDamage(UUID id) {
        return ultimateDamage.contains(id);
    }

    public long cooldownLeftMs(Player p) {
        Long end = cooldowns.get(p.getUniqueId());
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ cast

    public void cast(Player p, KatanaData d, boolean fromCommand) {
        FileConfiguration c = plugin.getConfig();
        KatanaManager mgr = plugin.manager();

        if (!c.getBoolean("ultimate.enabled", true)) return;
        if (!p.hasPermission("katana.use")) return;
        if (c.getStringList("ultimate.disabled-worlds").contains(p.getWorld().getName())) {
            plugin.send(p, "§cUltimates are disabled in this world.");
            return;
        }
        int minLevel = c.getInt("ultimate.min-level", 3);
        if (d.level < minLevel) {
            plugin.actionBar(p, "§cUltimate unlocks at katana level " + minLevel + " §7(now " + d.level + ")");
            return;
        }
        long left = cooldownLeftMs(p);
        if (left > 0) {
            plugin.actionBar(p, "§cUltimate ready in §f" + (left / 1000 + 1) + "s");
            return;
        }

        int stage = mgr.stage(d);
        int cd = Math.max(c.getInt("ultimate.min-cooldown-seconds", 8),
                c.getInt("ultimate.cooldown-seconds", 30) - stage * c.getInt("ultimate.cooldown-reduction-per-stage", 3));
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + cd * 1000L);

        double dmg = c.getDouble("ultimate.base-damage", 10.0)
                + d.level * c.getDouble("ultimate.damage-per-level", 0.75)
                + stage * c.getDouble("ultimate.damage-per-stage", 2.0);

        plugin.actionBar(p, d.path.color() + "§l" + d.path.ultimateName() + "§7!");
        switch (d.path) {
            case SLASH:
                slash(p, d, dmg);
                break;
            case STORM:
                storm(p, d, dmg);
                break;
            case BLAST:
                blast(p, d, dmg);
                break;
            default:
                break;
        }

        mgr.progress(p, d, KatanaTask.Type.ULTIMATES, 1);
        mgr.grantXp(p, d, c.getInt("ultimate.xp-reward", 25));
    }

    // ------------------------------------------------------------------ helpers

    private List<LivingEntity> near(Player caster, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        if (c.getWorld() == null) return out;
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.equals(caster) || e instanceof ArmorStand || e.isDead()) continue;
            if (e.getLocation().distanceSquared(c) > r * r) continue;
            out.add((LivingEntity) e);
        }
        return out;
    }

    private boolean hit(Player caster, LivingEntity target, double dmg) {
        if (target instanceof Player) {
            Player tp = (Player) target;
            if (!plugin.getConfig().getBoolean("ultimate.damage-players", true)) return false;
            if (!tp.getWorld().getPVP()) return false;
            if (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR) return false;
        }
        if (target instanceof Tameable) {
            Tameable t = (Tameable) target;
            if (t.isTamed() && caster.equals(t.getOwner())) return false;
        }
        ultimateDamage.add(caster.getUniqueId());
        try {
            target.damage(dmg, caster);
        } finally {
            ultimateDamage.remove(caster.getUniqueId());
        }
        return true;
    }

    private void creditDamage(Player p, KatanaData d, int dealt) {
        if (dealt > 0 && p.isOnline()) {
            plugin.manager().progress(p, d, KatanaTask.Type.DAMAGE, dealt);
        }
    }

    // ------------------------------------------------------------------ SLASH: Phantom Slash

    private void slash(Player p, KatanaData d, double dmg) {
        FileConfiguration c = plugin.getConfig();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1e-4) side = new Vector(1, 0, 0);
        side.normalize();
        final Vector fSide = side;
        final int length = Math.max(3, c.getInt("ultimate.slash.length", 12));

        Fx.sound(p.getLocation(), "entity.player.attack.sweep", 1.2f, 0.6f);
        Fx.sound(p.getLocation(), "item.trident.riptide_1", 1f, 1.2f);

        if (c.getBoolean("ultimate.slash.dash", true)) {
            Vector v = dir.clone().setY(0);
            if (v.lengthSquared() > 1e-4) {
                v.normalize().multiply(1.1);
                v.setY(0.12);
                p.setVelocity(v);
            }
        }

        final Particle sweep = Fx.find("SWEEP_ATTACK");
        final Particle sig = d.type.particle();
        final Set<UUID> hitIds = new HashSet<>();
        final int[] dealt = {0};

        new BukkitRunnable() {
            double dist = 0;

            @Override
            public void run() {
                for (int i = 0; i < 3; i++) {
                    dist += 1.0;
                    if (dist > length) {
                        finish();
                        return;
                    }
                    Location pt = eye.clone().add(dir.clone().multiply(dist));
                    if (pt.getBlock().getType().isSolid()) {
                        finish();
                        return;
                    }
                    for (int j = -3; j <= 3; j++) {
                        Location a = pt.clone().add(fSide.clone().multiply(j * 0.45));
                        Fx.dust(a, 1, 0.0, d.type.rgb, 1.6f);
                    }
                    Fx.particle(pt, sweep, 1, 0.8, 0.3, 0.8, 0);
                    Fx.particle(pt, sig, 3, 0.5, 0.3, 0.5, 0.02);
                    for (LivingEntity le : near(p, pt, 2.2)) {
                        if (hitIds.add(le.getUniqueId()) && hit(p, le, dmg)) {
                            dealt[0] += (int) dmg;
                            Fx.particle(le.getLocation().add(0, 1, 0), sweep, 1, 0.2, 0.2, 0.2, 0);
                        }
                    }
                }
            }

            private void finish() {
                cancel();
                creditDamage(p, d, dealt[0]);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ STORM: Thunder Dominion

    private void storm(Player p, KatanaData d, double dmg) {
        FileConfiguration c = plugin.getConfig();
        final World w = p.getWorld();
        final int dur = Math.max(20, c.getInt("ultimate.storm.duration-seconds", 8) * 20);
        final double radius = Math.max(4, c.getDouble("ultimate.storm.radius", 10));
        if (c.getBoolean("ultimate.storm.change-weather", true)) startStorm(w, dur);

        Fx.sound(p.getLocation(), "entity.lightning_bolt.thunder", 1.5f, 0.9f);
        final Particle spark = Fx.find("ELECTRIC_SPARK", "CRIT");
        final int[] dealt = {0};
        final double strikeDmg = dmg * 0.7;

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= dur) {
                    cancel();
                    creditDamage(p, d, dealt[0]);
                    return;
                }
                Location c0 = p.getLocation();
                Fx.particle(c0.clone().add(0, 1, 0), spark, 8, 1.2, 1.0, 1.2, 0.1);

                if (t % 8 == 0) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    double ang = r.nextDouble() * Math.PI * 2;
                    double dist = 3 + r.nextDouble() * (radius - 3);
                    Location s = c0.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);

                    Location visual = s.clone();
                    if (w.getEnvironment() == World.Environment.NORMAL) {
                        int top = w.getHighestBlockYAt(s) + 1;
                        if (Math.abs(top - s.getY()) <= 6) visual.setY(top);
                    }
                    w.strikeLightningEffect(visual); // effect only: no damage, no fire
                    Fx.dust(s.clone().add(0, 1, 0), 20, 1.2, d.type.rgb, 2f);

                    for (LivingEntity le : near(p, s, 3.5)) {
                        if (hit(p, le, strikeDmg)) dealt[0] += (int) strikeDmg;
                    }
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void startStorm(World w, int ticks) {
        UUID id = w.getUID();
        if (!stormEnd.containsKey(id)) {
            stormPrev.put(id, new boolean[]{w.hasStorm(), w.isThundering()});
        }
        long end = System.currentTimeMillis() + ticks * 50L;
        Long old = stormEnd.get(id);
        stormEnd.put(id, old == null ? end : Math.max(old, end));

        w.setStorm(true);
        w.setThundering(true);
        w.setWeatherDuration(ticks + 200);
        w.setThunderDuration(ticks + 200);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long e = stormEnd.get(id);
            if (e != null && System.currentTimeMillis() >= e - 100) {
                restoreWeather(id);
            }
        }, ticks + 2L);
    }

    private void restoreWeather(UUID worldId) {
        stormEnd.remove(worldId);
        boolean[] prev = stormPrev.remove(worldId);
        World w = Bukkit.getWorld(worldId);
        if (w != null && prev != null) {
            w.setStorm(prev[0]);
            w.setThundering(prev[1]);
        }
    }

    // ------------------------------------------------------------------ BLAST: Cataclysm

    private void blast(Player p, KatanaData d, double dmg) {
        final Location origin = p.getLocation().clone();
        final double radius = Math.max(3, plugin.getConfig().getDouble("ultimate.blast.radius", 8));
        final Particle boom = Fx.find("EXPLOSION", "EXPLOSION_LARGE");
        final Particle huge = Fx.find("EXPLOSION_EMITTER", "EXPLOSION_HUGE");
        final Particle sig = d.type.particle();
        final int[] dealt = {0};

        for (int wave = 0; wave < 3; wave++) {
            final int wv = wave;
            final double r = radius * (wv + 1) / 3.0;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Fx.sound(origin, "entity.generic.explode", 1.4f, 0.8f + 0.15f * wv);
                int pts = 16 + wv * 8;
                for (int i = 0; i < pts; i++) {
                    double a = 2 * Math.PI * i / pts;
                    Location l = origin.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
                    Fx.particle(l, boom, 1, 0, 0, 0, 0);
                    Fx.dust(l.clone().add(0, 0.5, 0), 4, 0.4, d.type.rgb, 2f);
                    Fx.particle(l, sig, 2, 0.3, 0.3, 0.3, 0.02);
                }
                if (wv == 2) Fx.particle(origin.clone().add(0, 1, 0), huge, 1, 0, 0, 0, 0);

                double waveDmg = wv == 2 ? dmg : dmg * 0.5;
                for (LivingEntity le : near(p, origin, r)) {
                    if (!hit(p, le, waveDmg)) continue;
                    dealt[0] += (int) waveDmg;
                    Vector away = le.getLocation().toVector().subtract(origin.toVector());
                    away.setY(0);
                    if (away.lengthSquared() < 1e-4) {
                        away = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
                    }
                    away.normalize().multiply(0.8).setY(0.4);
                    le.setVelocity(away);
                }
                if (wv == 2) creditDamage(p, d, dealt[0]);
            }, wave * 10L);
        }
    }

    // ------------------------------------------------------------------ shutdown

    public void shutdown() {
        for (UUID id : new ArrayList<>(stormEnd.keySet())) restoreWeather(id);
    }
}
