package com.smp.katana;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class KatanaPlugin extends JavaPlugin {

    private KatanaManager manager;
    private Ultimates ultimates;
    private BukkitTask auraTask;
    private BukkitTask flushTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Fx.setViaSafe(getConfig().getBoolean("compat.via-safe-particles", false));

        manager = new KatanaManager(this);
        manager.load();
        ultimates = new Ultimates(this);

        Bukkit.getPluginManager().registerEvents(new KatanaListener(this), this);

        KatanaCommand command = new KatanaCommand(this);
        PluginCommand pc = getCommand("katana");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        startTasks();

        boolean via = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");
        boolean viaBack = Bukkit.getPluginManager().isPluginEnabled("ViaBackwards");
        getLogger().info("SMPKatana enabled on " + Bukkit.getBukkitVersion()
                + " | ViaVersion: " + (via ? "yes" : "no")
                + " | ViaBackwards: " + (viaBack ? "yes" : "no"));
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.flushAll();
        if (ultimates != null) ultimates.shutdown();
        stopTasks();
    }

    public void reload() {
        reloadConfig();
        Fx.setViaSafe(getConfig().getBoolean("compat.via-safe-particles", false));
        manager.reloadSettings();
        startTasks();
    }

    private void startTasks() {
        stopTasks();
        int interval = Math.max(1, getConfig().getInt("aura.interval-ticks", 4));
        auraTask = new AuraTask(this, interval).runTaskTimer(this, 20L, interval);
        int flush = Math.max(20, getConfig().getInt("leveling.flush-ticks", 60));
        flushTask = Bukkit.getScheduler().runTaskTimer(this, () -> manager.flushDirty(), flush, flush);
    }

    private void stopTasks() {
        if (auraTask != null) auraTask.cancel();
        if (flushTask != null) flushTask.cancel();
        auraTask = null;
        flushTask = null;
    }

    public KatanaManager manager() {
        return manager;
    }

    public Ultimates ultimates() {
        return ultimates;
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String prefix() {
        return color(getConfig().getString("prefix", "&8[&6Katana&8] &7"));
    }

    public void send(CommandSender to, String message) {
        to.sendMessage(prefix() + message);
    }

    public void actionBar(Player p, String message) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}
