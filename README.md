# Races: Paper plugin for Minecraft 26.3

## Install
1. Put `Races-1.0.0.jar` in your server's `plugins/` folder (Paper 26.3, Java 25).
2. Start the server. `plugins/Races/config.yml` is created. You can change hearts, cooldowns, damage and the Piglin loot table there. After editing, run `/race reload`.

## How it works
- On a player's first join they get a random race. All 12 join races have the same chance.
- **Ability:** sneak + press whatever key you have bound to "Swap Item With Offhand" (works with any rebinding). `/ability` does the same thing.
- Crafted races are never given on join. Players craft the race's **Essence** and right-click it to become that race.

## Commands
| Command | Who | What |
|---|---|---|
| `/race` | everyone | Shows your race and ability |
| `/race list`, `/race info <race>` | everyone | Lists races, shows a race's details |
| `/trusted add/remove/list <player>` | everyone | Your abilities won't hit players you trust |
| `/recipes` | everyone | Menu showing the crafted-race recipes |
| `/recipe` | operators | Menu to turn each crafted race on or off. `/recipe give/take ...` still runs the vanilla command |
| `/race set <player> <race>` | operators | Sets someone's race |
| `/race reroll <player>` | operators | Gives someone a new random join race |
| `/race reload` | operators | Reloads config.yml |

## 9 Lives texture (optional)
The Cat's 9 Lives totem has a cat-themed texture in a small resource pack (`plugins/Races/races-pack.zip`).
- **Self-hosted server or VPS:** set `resource-pack.public-address` to the IP or domain players join with, and open port `8164`. The plugin hosts the pack itself.
- **Hosting provider that doesn't allow extra ports (Aternos, etc.):** upload `races-pack.zip` somewhere that gives a direct download link. Set `resource-pack.mode: url` and paste the link into `resource-pack.url`.
- Without the pack, 9 Lives still works. It just looks like a normal totem.

## Build from source
`gradle build` (needs JDK 25). The jar ends up in `build/libs/`.
