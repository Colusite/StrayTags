<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/97fb038b-e154-4d1d-833f-44dbc6555def" />
<img width="1193" height="671" alt="image" src="https://github.com/user-attachments/assets/628c5796-d7c5-412b-bc5f-ca20bc48e2f0" />

# Stray Tags
## Only tested on 1.21.11 Fabric 0.141.3 Loader 0.18.4
Tired of clan leaders changing their clan tag colour every 5 minutes?\
With this mod, you can freely customise the nametags of players so that your colour recognition isn't messed up by clan leaders.
## Dependencies
- Fabric API
- Cloth Config + Mod Menu (optional but recommended, for in game config screen)
## Rundown
- Intercepts TextDisplay entities riding players (used by servers like stray for nametags) and reformats them based on configurable clan and player categories using MiniMessage formatting.
- Mixin into DisplayRenderer.TextDisplayRenderer to intercept nametag rendering
- Strips formatting codes and Unicode icons (SVC, TierTagger) for clean pattern matching and preserves them for reapplication
- Per server configuration with regex based name parsing
- Partial server address matching (eu.stray.gg matches stray.gg config)
## Player and Clan Categorization
Players are sorted into categories that determine how their nametag is displayed:
- **Own** - your own clan members
- **Allied** - clans/players you're allied with
- **Enemy** - clans/players you're fighting against
- **Neutral** - everyone else with a clan tag
- **No Clan** - players without a clan tag

Each category has its own MiniMessage format string with `%username%`, `%clan%`, and `%rank%` placeholders.\
Leave a format blank to keep the original nametag for that category.
## Individual Player Overrides
You can add specific players to any category list regardless of what clan they're in.\
Player list takes priority over clan list - if a player is in your enemy players list but their clan is in your allied clans list, they will show as enemy.\
This lets you mark individual players as allies or enemies even if their clan isn't.
## Format Preview
The config screen shows a live preview below each format field so you can see exactly what the nametag will look like before saving.\
Uses your actual Minecraft username and renders the full MiniMessage formatting inline.\
Blank formats show `(unchanged - blank format)`.
## Import / Export
Share your config with other players using Base64 share codes:
- **Export Clans** - generates a `ST1C:` code containing all your clan and player lists
- **Export Formats** - generates a `ST1F:` code containing all your format strings and name pattern
- **Export Full** - generates a `ST1:` code containing everything for that server

Copy the code from the export field, send it to a friend, they paste it into the matching import field and hit Save & Quit.
## Commands
- `/straytags reload` - reload config from disk (always available)

The following commands require "Enable Debug Commands" to be turned on in the config:
- `/straytags test <string>` - test a raw string against the config pattern
- `/straytags testuser [player]` - inspect a player's nametag (has to be within render distance)
- `/straytags verbose` - toggle logging of all processed nametags to chat
- `/straytags debug` - toggle raw/cleaned string comparison logging
## Config
JSON config at `config/straytags.json`\
Cloth Config + Mod Menu integration for in game editing\
Server whitelist with per server clan lists and format overrides\
Default stray.gg config with pre-populated clan/player lists for Latte\
`__default__` fallback config for any whitelisted server without its own config
<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/f59f2a3a-9380-49f6-b924-eab85c7f80ee" />
