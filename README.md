# SMPKatana

15 unique, uncraftable, leveling katanas for Paper / Spigot **1.20 → 1.21+** (ViaVersion / ViaBackwards friendly).

## Build
Requires Java 17+ and Maven.

```
mvn clean package
```
Jar: `target/SMPKatana-1.0.0.jar` → drop into `plugins/`, restart.

## Katanas (15)
Black Silent Killer, Red Destroyer, Blue Tempest, Emerald Serpent, Golden Dawn, Violet Phantom, Frost Fang,
Crimson Moon, Thunder Emperor, Void Reaper, Inferno Wrath, Jade Dragon, Shadow Dancer, Celestial Blade, Soul Eater.

Not craftable. Obtained only via `/katana give` or task rewards (console commands in `config.yml → tasks.rewards`).
With `unique-mode: true` each katana exists once; re-issuing it seals the old copy.

## Features
- **Levels / XP** from mob + player kills (config: `leveling`).
- **Aura**: Lv1-3 small particles · Lv4-7 orbits + sounds · Lv8+ aura waves + visual-only lightning.
- **Left-hand pressure** (Lv4+): particles, heartbeat, slowness, nausea (Lv6+) for nearby players. `katana.immune` skips it.
- **F = ultimate**, **Shift+F = normal swap**.
  - SLASH → Phantom Slash (dash + line damage)
  - STORM → Thunder Dominion (temporary thunderstorm, lightning strikes, area damage)
  - BLAST → Cataclysm (3 explosion waves, never breaks blocks)
- **Paths** (`/katana path`) and **tasks** (easy → legendary). Every 3 tasks the katana evolves a stage
  (Dormant → Awakened → Ascended → Mythic → Immortal): more damage, shorter cooldown.

## Commands (all in tab-complete, clickable in `/katana help`)
```
/katana help [page]
/katana list
/katana info
/katana tasks [all|todo|done|easy|medium|hard|extreme|legendary]
/katana path [slash|storm|blast]
/katana ultimate
/katana aura <on|off>
/katana give <player> <katana|random> [force]     (katana.admin)
/katana revoke <katana>                           (katana.admin)
/katana setlevel <level>                          (katana.admin)
/katana addxp <amount>                            (katana.admin)
/katana complete <task>                           (katana.admin)
/katana reload                                    (katana.admin)
```
Aliases: `/kt`, `/katanas`.

## Permissions
`katana.use` (default true) · `katana.admin` (op) · `katana.immune` (false)

## Compatibility notes
- Compiled against Spigot API 1.20.1 and uses no version-specific enums: particles are looked up by name with
  fallbacks (names changed in 1.20.5), sounds are played by key string, potion effects by namespaced key.
- Item progress is cached and written to the item every `flush-ticks` (default 3 s) so the held sword does not flicker.
- Damage uses `LivingEntity#damage(amount, source)`, so WorldGuard / protection plugins can still cancel it.
- `compat.via-safe-particles: true` skips 1.17+ only particles for old clients behind ViaBackwards.
- If you lower `leveling.max-level` below 12, edit the "Legend of the SMP" task target in `KatanaTask.java`.
