package com.smp.katana;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KatanaManager {

    public static final String[] STAGE_NAMES = {"Dormant", "Awakened", "Ascended", "Mythic", "Immortal"};
    public static final String[] STAGE_COLORS = {"§7", "§a", "§b", "§d", "§6"};

    public enum GiveResult { OK, ALREADY_ISSUED }

    public static final class Issued {
        public final UUID serial;
        public String owner;
        public final long since;

        Issued(UUID serial, String owner, long since) {
            this.serial = serial;
            this.owner = owner;
            this.since = since;
        }
    }

    private final KatanaPlugin plugin;
    private final NamespacedKey kId, kSerial, kLevel, kXp, kMobs, kPlayers, kPath, kProgress, kDone;

    /** Authoritative live data, keyed by katana serial. Flushed to items every few seconds. */
    private final Map<UUID, KatanaData> cache = new HashMap<>();
    private final Map<KatanaType, Issued> issued = new EnumMap<>(KatanaType.class);
    private final Set<UUID> auraOff = new HashSet<>();
    private boolean registryDirty = false;
    private File dataFile;
    private Material baseMaterial = Material.NETHERITE_SWORD;

    public KatanaManager(KatanaPlugin plugin) {
        this.plugin = plugin;
        kId = new NamespacedKey(plugin, "katana_id");
        kSerial = new NamespacedKey(plugin, "serial");
        kLevel = new NamespacedKey(plugin, "level");
        kXp = new NamespacedKey(plugin, "xp");
        kMobs = new NamespacedKey(plugin, "mob_kills");
        kPlayers = new NamespacedKey(plugin, "player_kills");
        kPath = new NamespacedKey(plugin, "path");
        kProgress = new NamespacedKey(plugin, "progress");
        kDone = new NamespacedKey(plugin, "done");
        reloadSettings();
    }

    public void reloadSettings() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("katana-material", "NETHERITE_SWORD"));
        baseMaterial = m != null ? m : Material.NETHERITE_SWORD;
    }

    // ------------------------------------------------------------------ settings helpers

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("leveling.max-level", 12));
    }

    public int xpNeeded(int level) {
        return plugin.getConfig().getInt("leveling.xp-base", 100)
                + (level - 1) * plugin.getConfig().getInt("leveling.xp-step", 75);
    }

    public boolean uniqueMode() {
        return plugin.getConfig().getBoolean("unique-mode", true);
    }

    public int stageFor(int doneCount) {
        int s = 0;
        for (int t : plugin.getConfig().getIntegerList("evolution.stage-thresholds")) {
            if (doneCount >= t) s++;
        }
        return Math.min(s, STAGE_NAMES.length - 1);
    }

    public int stage(KatanaData d) {
        return stageFor(d.done.size());
    }

    public double bonusDamage(KatanaData d) {
        return d.level * plugin.getConfig().getDouble("leveling.bonus-damage-per-level", 0.35)
                + stage(d) * plugin.getConfig().getDouble("leveling.bonus-damage-per-stage", 1.0);
    }

    // ------------------------------------------------------------------ persistence (registry)

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = y.getConfigurationSection("issued");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                KatanaType t = KatanaType.parse(key);
                String serial = sec.getString(key + ".serial");
                if (t == null || serial == null) continue;
                try {
                    issued.put(t, new Issued(UUID.fromString(serial), sec.getString(key + ".owner", "?"),
                            sec.getLong(key + ".since", System.currentTimeMillis())));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (String s : y.getStringList("aura-off")) {
            try {
                auraOff.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<KatanaType, Issued> e : issued.entrySet()) {
            String base = "issued." + e.getKey().name();
            y.set(base + ".serial", e.getValue().serial.toString());
            y.set(base + ".owner", e.getValue().owner);
            y.set(base + ".since", e.getValue().since);
        }
        List<String> off = new ArrayList<>();
        for (UUID u : auraOff) off.add(u.toString());
        y.set("aura-off", off);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(dataFile);
            registryDirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ registry / uniqueness

    public Issued issuedOf(KatanaType t) {
        return issued.get(t);
    }

    public boolean isIssued(KatanaType t) {
        return issued.containsKey(t);
    }

    /** A copy is active when unique-mode is off, or its serial is the registered one. */
    public boolean isActive(KatanaData d) {
        if (!uniqueMode()) return true;
        Issued i = issued.get(d.type);
        return i != null && i.serial.equals(d.serial);
    }

    public void touchOwner(KatanaData d, Player p) {
        Issued i = issued.get(d.type);
        if (i != null && i.serial.equals(d.serial) && !p.getName().equals(i.owner)) {
            i.owner = p.getName();
            registryDirty = true;
        }
    }

    public void revoke(KatanaType t) {
        issued.remove(t);
        registryDirty = true;
        for (KatanaData d : cache.values()) {
            if (d.type == t) d.dirty = true; // rewrite lore -> "Sealed"
        }
        save();
    }

    public KatanaType randomAvailable() {
        List<KatanaType> pool = new ArrayList<>();
        for (KatanaType t : KatanaType.values()) {
            if (!uniqueMode() || !isIssued(t)) pool.add(t);
        }
        if (pool.isEmpty()) return null;
        return pool.get((int) (Math.random() * pool.size()));
    }

    public GiveResult give(Player target, KatanaType type, boolean force) {
        if (uniqueMode() && isIssued(type) && !force) return GiveResult.ALREADY_ISSUED;
        UUID serial = UUID.randomUUID();
        ItemStack item = create(type, serial);
        issued.put(type, new Issued(serial, target.getName(), System.currentTimeMillis()));
        registryDirty = true;
        save();
        Map<Integer, ItemStack> left = target.getInventory().addItem(item);
        for (ItemStack it : left.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), it);
        }
        return GiveResult.OK;
    }

    public boolean isAuraOff(Player p) {
        return auraOff.contains(p.getUniqueId());
    }

    public void setAura(Player p, boolean on) {
        if (on) auraOff.remove(p.getUniqueId());
        else auraOff.add(p.getUniqueId());
        registryDirty = true;
    }

    // ------------------------------------------------------------------ item <-> data

    public ItemStack create(KatanaType type, UUID serial) {
        ItemStack item = new ItemStack(baseMaterial);
        KatanaData d = new KatanaData(type, serial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
            Enchantment glow = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (glow != null) meta.addEnchant(glow, 1, true);
            meta.setCustomModelData(plugin.getConfig().getInt("custom-model-data-base", 7000) + type.number());
            item.setItemMeta(meta);
        }
        cache.put(serial, d);
        write(item, d);
        return item;
    }

    /** Returns the live data for a katana item, or null if the item is not a katana. */
    public KatanaData read(ItemStack item) {
        if (item == null || item.getType() != baseMaterial || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(kId, PersistentDataType.STRING);
        String serialStr = pdc.get(kSerial, PersistentDataType.STRING);
        if (id == null || serialStr == null) return null;
        KatanaType type = KatanaType.parse(id);
        if (type == null) return null;
        UUID serial;
        try {
            serial = UUID.fromString(serialStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        KatanaData cached = cache.get(serial);
        if (cached != null) return cached;

        KatanaData d = new KatanaData(type, serial);
        d.level = pdc.getOrDefault(kLevel, PersistentDataType.INTEGER, 1);
        d.xp = pdc.getOrDefault(kXp, PersistentDataType.INTEGER, 0);
        d.mobKills = pdc.getOrDefault(kMobs, PersistentDataType.INTEGER, 0);
        d.playerKills = pdc.getOrDefault(kPlayers, PersistentDataType.INTEGER, 0);
        KatanaPath path = KatanaPath.parse(pdc.get(kPath, PersistentDataType.STRING));
        if (path != null) d.path = path;
        d.decodeProgress(pdc.get(kProgress, PersistentDataType.STRING));
        d.decodeDone(pdc.get(kDone, PersistentDataType.STRING));
        cache.put(serial, d);
        return d;
    }

    /** Writes data + name + lore into the item. */
    public void write(ItemStack item, KatanaData d) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kId, PersistentDataType.STRING, d.type.id());
        pdc.set(kSerial, PersistentDataType.STRING, d.serial.toString());
        pdc.set(kLevel, PersistentDataType.INTEGER, d.level);
        pdc.set(kXp, PersistentDataType.INTEGER, d.xp);
        pdc.set(kMobs, PersistentDataType.INTEGER, d.mobKills);
        pdc.set(kPlayers, PersistentDataType.INTEGER, d.playerKills);
        pdc.set(kPath, PersistentDataType.STRING, d.path.name());
        pdc.set(kProgress, PersistentDataType.STRING, d.encodeProgress());
        pdc.set(kDone, PersistentDataType.STRING, d.encodeDone());
        meta.setDisplayName(d.type.coloredName() + " §7[Lv." + d.level + "]");
        meta.setLore(lore(d));
        item.setItemMeta(meta);
    }

    private List<String> lore(KatanaData d) {
        List<String> l = new ArrayList<>();
        int stage = stage(d);
        l.add("§8§o" + d.type.theme);
        l.add("");
        l.add("§8» §7Level: §f" + d.level + "§7/" + maxLevel());
        if (d.level >= maxLevel()) {
            l.add("§8» §7XP: §6MAX LEVEL");
        } else {
            int need = xpNeeded(d.level);
            l.add("§8» §7XP: §f" + d.xp + "§7/" + need + " " + bar(d.xp, need, 10));
        }
        l.add("§8» §7Stage: " + STAGE_COLORS[stage] + STAGE_NAMES[stage]);
        l.add("§8» §7Path: " + d.path.colored());
        l.add("§8» §7Kills: §f" + d.mobKills + " §7mobs §8| §f" + d.playerKills + " §7players");
        l.add("§8» §7Tasks: §f" + d.doneIn(d.path) + "§7/" + KatanaTask.forPath(d.path).size());
        l.add("");
        l.add("§6Ultimate: " + d.path.color() + d.path.ultimateName());
        l.add("§e[F] §7Ultimate  §8|  §e[Shift+F] §7Normal swap");
        l.add("§e[Left hand] §7Pressure aura (Lv." + plugin.getConfig().getInt("pressure.min-level", 4) + "+)");
        if (!isActive(d)) {
            l.add("");
            l.add("§c✖ Sealed - this copy is no longer valid");
        }
        return l;
    }

    public static String bar(int cur, int max, int len) {
        int filled = max <= 0 ? len : (int) Math.min(len, (long) cur * len / max);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < len; i++) {
            if (i == filled) sb.append("§8");
            sb.append('|');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ held katanas

    public KatanaData mainKatana(Player p) {
        KatanaData d = read(p.getInventory().getItemInMainHand());
        return d != null && isActive(d) ? d : null;
    }

    public KatanaData offKatana(Player p) {
        KatanaData d = read(p.getInventory().getItemInOffHand());
        return d != null && isActive(d) ? d : null;
    }

    // ------------------------------------------------------------------ flushing (no hand flicker)

    /** Writes dirty data into every matching katana in the player's inventory. */
    public void flush(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            KatanaData d = read(it);
            if (d != null && d.dirty) {
                write(it, d);
                inv.setItem(i, it);
                d.dirty = false;
            }
        }
        ItemStack off = inv.getItemInOffHand();
        KatanaData od = read(off);
        if (od != null && od.dirty) {
            write(off, od);
            inv.setItemInOffHand(off);
            od.dirty = false;
        }
    }

    public void flushDirty() {
        boolean any = false;
        for (KatanaData d : cache.values()) {
            if (d.dirty) {
                any = true;
                break;
            }
        }
        if (any) {
            for (Player p : Bukkit.getOnlinePlayers()) flush(p);
        }
        if (registryDirty) save();
    }

    public void flushAll() {
        for (Player p : Bukkit.getOnlinePlayers()) flush(p);
        save();
    }

    // ------------------------------------------------------------------ XP / levels

    public void grantXp(Player p, KatanaData d, int amount) {
        if (amount <= 0) return;
        d.xp += amount;
        d.dirty = true;
        boolean leveled = false;
        while (d.level < maxLevel() && d.xp >= xpNeeded(d.level)) {
            d.xp -= xpNeeded(d.level);
            d.level++;
            leveled = true;
            onLevelUp(p, d);
        }
        if (d.level >= maxLevel()) d.xp = 0;
        if (leveled) flush(p);
    }

    private void onLevelUp(Player p, KatanaData d) {
        p.sendTitle(d.type.color + "LEVEL UP!", "§7" + d.type.display + " §8» §fLevel " + d.level, 5, 40, 10);
        Fx.sound(p.getLocation(), "entity.player.levelup", 1f, 0.8f + Math.min(d.level, 12) * 0.05f);
        int announce = plugin.getConfig().getInt("leveling.announce-level", 8);
        if (announce > 0 && d.level == announce) {
            Bukkit.broadcastMessage(plugin.prefix() + "§f" + p.getName() + "§7's " + d.type.coloredName()
                    + " §7reached §6Level " + d.level + "§7! Its aura grows violent...");
        }
        progress(p, d, KatanaTask.Type.REACH_LEVEL, d.level, true);
    }

    public void setLevel(Player p, KatanaData d, int level) {
        d.level = Math.max(1, Math.min(level, maxLevel()));
        d.xp = 0;
        d.dirty = true;
        progress(p, d, KatanaTask.Type.REACH_LEVEL, d.level, true);
        flush(p);
    }

    // ------------------------------------------------------------------ tasks

    public void progress(Player p, KatanaData d, KatanaTask.Type type, int amount) {
        progress(p, d, type, amount, false);
    }

    public void progress(Player p, KatanaData d, KatanaTask.Type type, int amount, boolean absolute) {
        for (KatanaTask t : new ArrayList<>(KatanaTask.forPath(d.path))) {
            if (t.type != type || d.done.contains(t.name())) continue;
            int cur = d.progressOf(t);
            int next = absolute ? Math.max(cur, amount) : cur + amount;
            d.progress.put(t.name(), Math.min(next, t.target));
            d.dirty = true;
            if (next >= t.target) complete(p, d, t);
        }
    }

    public boolean complete(Player p, KatanaData d, KatanaTask t) {
        if (!d.done.add(t.name())) return false;
        int stageBefore = stageFor(d.done.size() - 1);
        d.progress.put(t.name(), t.target);
        d.dirty = true;

        p.sendMessage(plugin.prefix() + "§a✔ Task complete: " + t.tier.color + t.title
                + " §8(" + t.tier.label + ") §6+" + t.xp + " XP");
        Fx.sound(p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        grantXp(p, d, t.xp);

        for (String cmd : plugin.getConfig().getStringList("tasks.rewards." + t.name())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", p.getName()).replace("%katana%", d.type.id()));
        }

        int stageAfter = stage(d);
        if (stageAfter > stageBefore) {
            p.sendTitle(STAGE_COLORS[stageAfter] + "EVOLUTION!", d.type.color + d.type.display
                    + " §8» " + STAGE_COLORS[stageAfter] + STAGE_NAMES[stageAfter], 5, 50, 15);
            Fx.sound(p.getLocation(), "entity.wither.spawn", 0.6f, 1.4f);
            p.sendMessage(plugin.prefix() + "§dYour katana evolved to " + STAGE_COLORS[stageAfter]
                    + STAGE_NAMES[stageAfter] + "§d! Bonus damage and shorter ultimate cooldown.");
        }
        flush(p);
        return true;
    }

    public List<KatanaTask> tasksFor(KatanaData d) {
        return Collections.unmodifiableList(KatanaTask.forPath(d.path));
    }
}
