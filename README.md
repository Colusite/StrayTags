<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/0a809e27-ac19-4d35-903e-7a7bcdea32b4" />
<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/049db5f1-4b4c-4fda-be1b-9debbce3b83e" />

# Stray Tags
## Tested on Fabric 1.21, 1.21.4, 1.21.8, 1.21.11
Tired of clan leaders changing their clan tag colour every 5 minutes?\
With this mod, you can freely customise the nametags of players so that your colour recognition isn't messed up by clan leaders.
## Dependencies
- Fabric API
- YACL + Mod Menu (optional but recommended, for in-game config screen)
## Rundown
- Intercepts TextDisplay entities riding players (used by servers like stray for nametags) and reformats them based on configurable categories using MiniMessage formatting.
- Mixin into `DisplayRenderer.TextDisplayRenderer` to intercept nametag rendering.
- Strips formatting codes and Unicode icons (SVC, TierTagger) for clean pattern matching and preserves them for reapplication.
- Per-server configuration with regex-based name parsing.
- Partial server address matching (`eu.stray.gg` matches the `stray.gg` config).
## User Defined Categories
The Own/Allied/Enemy/Neutral split is no longer hardcoded. You can create as many categories as you want, name them whatever you want (with MiniMessage formatting on the name itself), and reorder them. The first category whose lists match a player's nametag wins.

Each category has:
- **Name** - display label, supports MiniMessage (`<gold>Own</gold>`)
- **Format** - MiniMessage format applied to the player's nametag
- **One list per regex group** - e.g. with the default regex you get a `Usernames` list and a `Clans` list. If you change the regex to add a `Rank` group, a `Rank` list automatically appears.
- **Match Priority** *(advanced)* - comma-separated regex group names; lookups check in this order, first match wins.
- **Server Filters** *(advanced)* - comma-separated server addresses where this category applies. With dot = exact match (`stray.gg`), without dot = substring (`eu` matches `eu.stray.gg`). Empty = all servers.
- **Regex Override** *(advanced)* - per-category custom regex.

Categories not matched fall through to the **Neutral** format (player has a clan tag) or **No Clan** format (no clan tag).
## Generic Regex Groups
The screen builder discovers regex groups dynamically from your `Default Matching Regex` and creates one `ListOption` per named group. Format strings use `%groupName%` placeholders for any group.

Example regex with three groups:
```
^(?:<(?<Rank>[^>]+)>\s+)?(?<Usernames>\S+)(?:\s+\[(?<Clans>[^\]]+)\])?$
```
Each category now exposes three lists (`Rank`, `Usernames`, `Clans`) and the format can use `%Rank% %Usernames% [%Clans%]`.

`%username%` and `%clan%` are kept as legacy aliases (mapped to the first / second named group) so old configs keep rendering correctly.
## Format Preview
The config screen shows a static preview below each format field. Previews update on save - close and reopen Mod Menu (or click Save Changes) to refresh.\
Blank formats show `(unchanged - blank format)`.
## Server Whitelist & Per-Server Configs
Configure different categories per server. The `__default__` fallback applies to any whitelisted server that doesn't have its own config. The default `stray.gg` config ships with Own / Allied / Elysium / Enemy categories tuned for Latte.

The "Elysium" category demonstrates server filtering - Elysium clans are tagged as enemy on `eu.stray.gg` only; on NA/AS they fall through to the neutral format.
## Import / Export
Share your config with other players using Base64 share codes:
- **Export Code** - copy this and send to a friend
- **Import Code** - paste a code and click Save Changes

Codes use the `ST2:` prefix. Legacy `ST1:`, `ST1C:`, and `ST1F:` codes are still accepted and migrated automatically.
## Commands
- `/straytags reload` - reload config from disk (always available)

The following commands require "Enable Debug Commands" to be turned on in the General tab:
- `/straytags test <string>` - test a raw string against the active regex
- `/straytags testuser [player]` - inspect a player's nametag (must be within render distance)
- `/straytags verbose` - toggle logging of all processed nametags to chat
- `/straytags debug` - toggle raw/cleaned string comparison logging
## Config
JSON config at `config/straytags.json`. YACL + Mod Menu integration for in-game editing.\
Legacy configs (Cloth Config era + hardcoded Own/Allied/Enemy) auto-migrate on load.

<img width="2560" height="1441" alt="image" src="https://github.com/user-attachments/assets/43590f09-a896-4eac-932b-68d662a5ac48" />