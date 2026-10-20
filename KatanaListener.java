package com.smp.katana;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class KatanaListener implements Listener {

    private static final Set<String> UNDEAD = Set.of("ZOMBIE", "SKELETON", "HUSK", "DROWNED", "ZOMBIE_VILLAGER",
            "STRAY", "WITHER_SKELETON", "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE", "PHANTOM", "WITHER", "SKELETON_HORSE",
            "ZOMBIE_HORSE", "ZOGLIN", "BOGGED");
    private static final Set<String> NETHER = Set.of("BLAZE", "GHAST", "WITHER_SKELETON", "PIGLIN_BRUTE", "PIGLIN",
            "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE", "MAGMA_CUBE", "HOGLIN", "ZOGLIN");
    private static final Set<String> BOSSES = Set.of("WITHER", "ENDER_DRAGON", "WARDEN", "ELDER_GUARDIAN");

    private final KatanaPlugin plugin;
    private final KatanaManager mgr;
    private final Ultimates ult;

    public KatanaListener(KatanaPlugin plugin) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
        this.ult = plugin.ultimates();
    }

    // ------------------------------------------------------------------ F key = ultimate, Shift+F = normal swap

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        // the item that is about to move INTO the off hand is the one currently in the main hand
        KatanaData d = mgr.read(e.getOffHandItem());
        if (d == null) return;
        if (p.isSneaking()) return; // Shift+F -> normal hand swap, no ability

        if (!plugin.getConfig().getBoolean("ultimate.enabled", true)) return;
        e.setCancelled(true);
        if (!mgr.isActive(d)) {
            plugin.actionBar(p, "§cThis katana is sealed (outdated copy).");
            return;
        }
        ult.cast(p, d, false);
    }

    // ------------------------------------------------------------------ bonus damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        Player p = (Player) e.getDamager();
        if (ult.isUltimateDamage(p.getUniqueId())) return; // ultimate damage is handled by Ultimates
        KatanaData d = mgr.mainKatana(p);
        if (d == null) return;

        e.setDamage(e.getDamage() + mgr.bonusDamage(d));
        mgr.progress(p, d, KatanaTask.Type.DAMAGE, (int) Math.max(1, Math.round(e.getDamage())));
    }

    // ------------------------------------------------------------------ kills -> XP + tasks

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || victim instanceof ArmorStand || victim.equals(killer)) return;
        KatanaData d = mgr.mainKatana(killer);
        if (d == null) return;

        FileConfiguration c = plugin.getConfig();
        int xp;
        if (victim instanceof Player) {
            xp = c.getInt("leveling.xp-player", 120);
            d.playerKills++;
            mgr.progress(killer, d, KatanaTask.Type.PLAYER_KILLS, 1);
        } else {
            String name = victim.getType().name();
            boolean boss = BOSSES.contains(name) || victim instanceof Boss;
            boolean hostile = victim instanceof Enemy;
            xp = boss ? c.getInt("leveling.xp-boss", 400)
                    : hostile ? c.getInt("leveling.xp-hostile", 10)
                    : c.getInt("leveling.xp-mob", 3);
            if (hostile || boss) {
                d.mobKills++;
                mgr.progress(killer, d, KatanaTask.Type.MOB_KILLS, 1);
            }
            if (UNDEAD.contains(name)) mgr.progress(killer, d, KatanaTask.Type.UNDEAD_KILLS, 1);
            if (NETHER.contains(name)) mgr.progress(killer, d, KatanaTask.Type.NETHER_KILLS, 1);
            if (boss) mgr.progress(killer, d, KatanaTask.Type.BOSS_KILLS, 1);
        }
        d.dirty = true;
        mgr.grantXp(killer, d, xp);
        plugin.actionBar(killer, d.type.color + "+" + xp + " XP §7(" + d.type.display + ")");
    }

    // ------------------------------------------------------------------ keep item data in sync

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mgr.flush(e.getPlayer());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player) mgr.flush((Player) e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack st = e.getItemDrop().getItemStack();
        KatanaData d = mgr.read(st);
        if (d == null) return;
        mgr.write(st, d);
        e.getItemDrop().setItemStack(st);
        d.dirty = false;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        mgr.flush(e.getEntity());
        for (ItemStack it : e.getDrops()) {
            KatanaData d = mgr.read(it);
            if (d != null) mgr.write(it, d);
        }
    }

    // ------------------------------------------------------------------ protection

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent e) {
        if (!plugin.getConfig().getBoolean("protect-dropped-items", true)) return;
        if (e.getEntity() instanceof Item && mgr.read(((Item) e.getEntity()).getItemStack()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent e) {
        if (!plugin.getConfig().getBoolean("protect-dropped-items", true)) return;
        if (mgr.read(e.getEntity().getItemStack()) != null) e.setCancelled(true);
    }

    /** Katanas can never be crafted with / turned into anything else. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (it != null && mgr.read(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
