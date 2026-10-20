package com.smp.katana;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class KatanaCommand implements CommandExecutor, TabCompleter {

    private static final int HELP_PER_PAGE = 7;

    private static final class Sub {
        final String name;
        final String usage;
        final String desc;
        final String perm;

        Sub(String name, String usage, String desc, String perm) {
            this.name = name;
            this.usage = usage;
            this.desc = desc;
            this.perm = perm;
        }
    }

    private final KatanaPlugin plugin;
    private final KatanaManager mgr;

    private final List<Sub> subs = Arrays.asList(
            new Sub("help", "/katana help [page]", "Show this help menu", null),
            new Sub("list", "/katana list", "All 15 katanas and who owns them", null),
            new Sub("info", "/katana info", "Stats of the katana in your hand", null),
            new Sub("tasks", "/katana tasks [all|todo|done|easy|medium|hard|extreme|legendary]",
                    "Tasks of your katana, easiest to hardest", null),
            new Sub("path", "/katana path [slash|storm|blast]", "View or change your katana's path", null),
            new Sub("ultimate", "/katana ultimate", "Use the ultimate without the F key", "katana.use"),
            new Sub("aura", "/katana aura <on|off>", "Toggle your own aura particles", null),
            new Sub("give", "/katana give <player> <katana|random> [force]", "Give a katana (admin)", "katana.admin"),
            new Sub("revoke", "/katana revoke <katana>", "Unregister a katana; old copies get sealed (admin)", "katana.admin"),
            new Sub("setlevel", "/katana setlevel <level>", "Set the level of your held katana (admin)", "katana.admin"),
            new Sub("addxp", "/katana addxp <amount>", "Add XP to your held katana (admin)", "katana.admin"),
            new Sub("complete", "/katana complete <task>", "Force-complete a task on your held katana (admin)", "katana.admin"),
            new Sub("reload", "/katana reload", "Reload config.yml (admin)", "katana.admin")
    );

    public KatanaCommand(KatanaPlugin plugin) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
    }

    // ------------------------------------------------------------------ execute

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            help(sender, 1);
            return true;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        Sub sub = find(name);
        if (sub == null) {
            plugin.send(sender, "§cUnknown subcommand. Try §e/katana help");
            return true;
        }
        if (sub.perm != null && !sender.hasPermission(sub.perm)) {
            plugin.send(sender, "§cYou don't have permission for that.");
            return true;
        }

        switch (name) {
            case "help":
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                help(sender, page);
                break;
            case "list":
                list(sender);
                break;
            case "give":
                give(sender, args);
                break;
            case "revoke":
                revoke(sender, args);
                break;
            case "reload":
                plugin.reload();
                plugin.send(sender, "§aConfig reloaded.");
                break;
            default:
                if (!(sender instanceof Player)) {
                    plugin.send(sender, "§cThis subcommand is for players only.");
                    break;
                }
                playerCommand((Player) sender, name, args);
        }
        return true;
    }

    private void playerCommand(Player p, String name, String[] args) {
        switch (name) {
            case "aura":
                if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
                    plugin.send(p, "§cUsage: §e/katana aura <on|off>");
                    return;
                }
                boolean on = args[1].equalsIgnoreCase("on");
                mgr.setAura(p, on);
                plugin.send(p, "Your aura particles are now " + (on ? "§aON" : "§cOFF") + "§7.");
                return;
            default:
                break;
        }

        KatanaData d = held(p);
        if (d == null) {
            plugin.send(p, "§cHold a katana in your hand first.");
            return;
        }
        if (!mgr.isActive(d)) {
            plugin.send(p, "§cThis katana is sealed (outdated copy).");
            return;
        }

        switch (name) {
            case "info":
                info(p, d);
                break;
            case "tasks":
                tasks(p, d, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all");
                break;
            case "path":
                path(p, d, args);
                break;
            case "ultimate":
                if (!plugin.getConfig().getBoolean("ultimate.allow-command", true)) {
                    plugin.send(p, "§cUse the F key to trigger the ultimate.");
                } else {
                    plugin.ultimates().cast(p, d, true);
                }
                break;
            case "setlevel":
                if (args.length < 2 || parseInt(args[1]) < 1) {
                    plugin.send(p, "§cUsage: §e/katana setlevel <1-" + mgr.maxLevel() + ">");
                    return;
                }
                mgr.setLevel(p, d, parseInt(args[1]));
                plugin.send(p, "Katana level set to §f" + d.level + "§7.");
                break;
            case "addxp":
                if (args.length < 2 || parseInt(args[1]) < 1) {
                    plugin.send(p, "§cUsage: §e/katana addxp <amount>");
                    return;
                }
                mgr.grantXp(p, d, parseInt(args[1]));
                mgr.flush(p);
                plugin.send(p, "Added §f" + parseInt(args[1]) + " §7XP.");
                break;
            case "complete":
                KatanaTask t = args.length > 1 ? KatanaTask.parse(args[1]) : null;
                if (t == null) {
                    plugin.send(p, "§cUnknown task. Use tab-complete to see all tasks.");
                    return;
                }
                if (!mgr.complete(p, d, t)) plugin.send(p, "§cThat task is already completed.");
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ subcommands

    private void help(CommandSender s, int page) {
        List<Sub> visible = new ArrayList<>();
        for (Sub sub : subs) {
            if (sub.perm == null || s.hasPermission(sub.perm)) visible.add(sub);
        }
        int pages = Math.max(1, (visible.size() + HELP_PER_PAGE - 1) / HELP_PER_PAGE);
        page = Math.max(1, Math.min(page, pages));

        s.sendMessage("§8§m      §r §6§lKatana Help §7(" + page + "/" + pages + ") §8§m      ");
        int from = (page - 1) * HELP_PER_PAGE;
        for (int i = from; i < Math.min(visible.size(), from + HELP_PER_PAGE); i++) {
            line(s, visible.get(i).usage, visible.get(i).desc);
        }
        s.sendMessage("§7Hold a katana: §eF §7= ultimate, §eShift+F §7= normal swap, §eLeft hand §7= pressure aura.");
        if (page < pages) s.sendMessage("§7Next page: §e/katana help " + (page + 1));
    }

    private void line(CommandSender s, String usage, String desc) {
        String text = "§e" + usage + " §8- §7" + desc;
        if (s instanceof Player) {
            int idx = firstArg(usage);
            String suggest = idx < 0 ? usage : usage.substring(0, idx);
            BaseComponent[] parts = TextComponent.fromLegacyText(text);
            for (BaseComponent bc : parts) {
                bc.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest));
            }
            ((Player) s).spigot().sendMessage(parts);
        } else {
            s.sendMessage(text);
        }
    }

    private int firstArg(String usage) {
        int a = usage.indexOf('<');
        int b = usage.indexOf('[');
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private void list(CommandSender s) {
        s.sendMessage("§8§m      §r §6§l15 Katanas §8§m      ");
        for (KatanaType t : KatanaType.values()) {
            String status;
            if (!mgr.uniqueMode()) {
                status = "§7(any amount)";
            } else if (mgr.isIssued(t)) {
                status = "§c● " + mgr.issuedOf(t).owner;
            } else {
                status = "§a● Available";
            }
            s.sendMessage("§8" + String.format("%2d", t.number()) + ". " + t.coloredName()
                    + " §8[" + t.defaultPath.colored() + "§8] " + status);
        }
    }

    private void info(Player p, KatanaData d) {
        int stage = mgr.stage(d);
        p.sendMessage("§8§m      §r " + d.type.coloredName() + " §8§m      ");
        p.sendMessage("§7" + d.type.theme);
        p.sendMessage("§6Level: §f" + d.level + "§7/" + mgr.maxLevel()
                + (d.level < mgr.maxLevel()
                ? "  §8" + KatanaManager.bar(d.xp, mgr.xpNeeded(d.level), 10) + " §7" + d.xp + "/" + mgr.xpNeeded(d.level)
                : "  §6MAX"));
        p.sendMessage("§6Stage: " + KatanaManager.STAGE_COLORS[stage] + KatanaManager.STAGE_NAMES[stage]
                + " §8(" + d.done.size() + " tasks done)");
        p.sendMessage("§6Path: " + d.path.colored() + " §8- §7" + d.path.ultimateName());
        p.sendMessage("§6Bonus damage: §f+" + String.format("%.1f", mgr.bonusDamage(d))
                + " §8| §6Kills: §f" + d.mobKills + " mobs, " + d.playerKills + " players");
        long cd = plugin.ultimates().cooldownLeftMs(p);
        p.sendMessage("§6Ultimate: " + (cd > 0 ? "§cready in " + (cd / 1000 + 1) + "s" : "§aREADY (press F)"));
    }

    private void tasks(Player p, KatanaData d, String filter) {
        KatanaTask.Tier only = KatanaTask.Tier.parse(filter);
        boolean todo = filter.equals("todo");
        boolean doneOnly = filter.equals("done");
        p.sendMessage("§8§m      §r §6§lTasks §7(easy → hard) §8- " + d.path.colored() + " path §8§m      ");

        KatanaTask.Tier last = null;
        for (KatanaTask t : mgr.tasksFor(d)) {
            boolean done = d.done.contains(t.name());
            if (only != null && t.tier != only) continue;
            if (todo && done) continue;
            if (doneOnly && !done) continue;
            if (t.tier != last) {
                last = t.tier;
                p.sendMessage(t.tier.color + "§l" + t.tier.label.toUpperCase());
            }
            if (done) {
                p.sendMessage(" §a✔ §7" + t.title + " §8- §7" + t.description);
            } else {
                p.sendMessage(" §8☐ §f" + t.title + " §8- §7" + t.description
                        + " §e(" + d.progressOf(t) + "/" + t.target + ") §6+" + t.xp + "xp");
            }
        }
        p.sendMessage("§7Done: §f" + d.doneIn(d.path) + "§7/" + mgr.tasksFor(d).size()
                + " §8| §7Filters: §etodo, done, easy, medium, hard, extreme, legendary");
    }

    private void path(Player p, KatanaData d, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§8§m      §r §6§lPaths §8§m      ");
            for (KatanaPath kp : KatanaPath.values()) {
                p.sendMessage((kp == d.path ? "§a▶ " : "§8  ") + kp.colored() + " §8- §f" + kp.ultimateName()
                        + " §8- §7" + kp.description());
            }
            p.sendMessage("§7Change with §e/katana path <slash|storm|blast>");
            return;
        }
        KatanaPath np = KatanaPath.parse(args[1]);
        if (np == null) {
            plugin.send(p, "§cUnknown path. Choose §bslash§c, §estorm§c or §cblast§c.");
            return;
        }
        if (np == d.path) {
            plugin.send(p, "Your katana is already on the " + np.colored() + " §7path.");
            return;
        }
        d.path = np;
        d.dirty = true;
        mgr.flush(p);
        plugin.send(p, "Path changed to " + np.colored() + "§7. New ultimate: §f" + np.ultimateName()
                + "§7. Path tasks were unlocked - see §e/katana tasks");
    }

    private void give(CommandSender s, String[] args) {
        if (args.length < 3) {
            plugin.send(s, "§cUsage: §e/katana give <player> <katana|random> [force]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.send(s, "§cPlayer not found.");
            return;
        }
        KatanaType type = args[2].equalsIgnoreCase("random") ? mgr.randomAvailable() : KatanaType.parse(args[2]);
        if (type == null) {
            plugin.send(s, args[2].equalsIgnoreCase("random")
                    ? "§cNo katana is available anymore (all 15 are issued)."
                    : "§cUnknown katana. Use tab-complete to see all 15.");
            return;
        }
        boolean force = args.length > 3 && args[3].equalsIgnoreCase("force");
        if (mgr.give(target, type, force) == KatanaManager.GiveResult.ALREADY_ISSUED) {
            plugin.send(s, type.coloredName() + " §cis already issued to §f" + mgr.issuedOf(type).owner
                    + "§c. Use §e/katana give " + target.getName() + " " + type.id()
                    + " force §cto re-issue (old copy gets sealed).");
            return;
        }
        plugin.send(s, "Gave " + type.coloredName() + " §7to §f" + target.getName() + "§7.");
        plugin.send(target, "You received " + type.coloredName() + "§7! Press §eF §7for its ultimate.");
    }

    private void revoke(CommandSender s, String[] args) {
        KatanaType type = args.length > 1 ? KatanaType.parse(args[1]) : null;
        if (type == null) {
            plugin.send(s, "§cUsage: §e/katana revoke <katana>");
            return;
        }
        mgr.revoke(type);
        plugin.send(s, type.coloredName() + " §7unregistered. Existing copies are now sealed; it can be given again.");
    }

    // ------------------------------------------------------------------ helpers

    private KatanaData held(Player p) {
        KatanaData d = mgr.read(p.getInventory().getItemInMainHand());
        return d != null ? d : mgr.read(p.getInventory().getItemInOffHand());
    }

    private Sub find(String name) {
        for (Sub s : subs) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Sub s : subs) {
                if (s.perm == null || sender.hasPermission(s.perm)) out.add(s.name);
            }
            return filter(out, args[0]);
        }
        Sub sub = find(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || (sub.perm != null && !sender.hasPermission(sub.perm))) return out;

        String name = sub.name;
        if (args.length == 2) {
            switch (name) {
                case "give":
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                    break;
                case "revoke":
                    for (KatanaType t : KatanaType.values()) out.add(t.id());
                    break;
                case "path":
                    for (KatanaPath p : KatanaPath.values()) out.add(p.id());
                    break;
                case "aura":
                    out.addAll(Arrays.asList("on", "off"));
                    break;
                case "tasks":
                    out.addAll(Arrays.asList("all", "todo", "done", "easy", "medium", "hard", "extreme", "legendary"));
                    break;
                case "setlevel":
                    out.addAll(Arrays.asList("1", "3", "4", "8", String.valueOf(mgr.maxLevel())));
                    break;
                case "addxp":
                    out.addAll(Arrays.asList("100", "500", "1000", "5000"));
                    break;
                case "complete":
                    for (KatanaTask t : KatanaTask.values()) out.add(t.name().toLowerCase(Locale.ROOT));
                    break;
                case "help":
                    out.addAll(Arrays.asList("1", "2"));
                    break;
                default:
                    break;
            }
        } else if (args.length == 3 && name.equals("give")) {
            out.add("random");
            for (KatanaType t : KatanaType.values()) out.add(t.id());
        } else if (args.length == 4 && name.equals("give")) {
            out.add("force");
        }
        return filter(out, args[args.length - 1]);
    }

    private List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> res = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) res.add(o);
        }
        return res;
    }
}
