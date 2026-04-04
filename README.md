# Initial Release
## Only tested on 1.21.11 Fabric 0.141.3 Loader 0.18.4
Tired of clan leaders changing their tag clan tag colour every 5 minutes?\
With this mod, you can freely customise the nametags of players so that your colour recognition isn't messed up by clan leaders.\
## Dependencies
- Fabric API
- Cloth Config + Mod Menu (optional, for in game config screen)
## Rundown
- Intercepts TextDisplay entities riding players (used by servers like stray.gg for nametags) and reformats them based on configurable clan/player categories using MiniMessage formatting.
- Mixin into DisplayRenderer.TextDisplayRenderer to intercept nametag rendering
- Strips § color codes and Unicode icons (SVC, TierTagger) for clean pattern matching and preserves them for reapplication
- Per server configuration with regex based name parsing
- Player and clan categorization: Own, Allied, Enemy, Neutral
- MiniMessage format strings with %username%, %clan%, %rank% placeholders
- Partial server address matching (eu.stray.gg matches stray.gg config)
## Commands
- /straytags test <string>     - test a raw string against the config pattern
- /straytags testuser [player] - inspect a player's nametag (has to be within render)
- /straytags verbose           - toggle logging of all processed nametags to chat
- /straytags debug             - toggle raw/cleaned string comparison logging
- /straytags reload            - reload config from disk
## Config
JSON config at config/straytags.json\
Cloth Config + Mod Menu integration for in game editing\
Server whitelist with per server clan lists and format overrides\
Default stray.gg config with pre-populated clan/player lists for Latte
