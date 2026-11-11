# AdminAbusePrevention

Paper 1.21.x plugin that watches players (including ops) who are not excused:

- **Gamemode changes** are announced to the whole server.
- **Items taken from the creative menu** are announced (what item, how many).
- **Flagged items** (diamond/netherite armor and tools, golden apples, enchanted golden apples, any potions, villager spawn eggs) trigger the punishment:
  the item is not given, the player is put in survival, `lp user <name> parent set default` and `deop <name>` are run by the console, and everyone is told.

## Excusing players
Give them the LuckPerms permission `admin.abuse.prevention.excuse`:

    /lp user <name> permission set admin.abuse.prevention.excuse true

Being OP does **not** excuse anyone (the permission defaults to false). Anyone with the `*`
wildcard in LuckPerms would be excused too; negate it for people you want watched:

    /lp user <name> permission set admin.abuse.prevention.excuse false

## Commands (`abuseguard.admin`, op by default)
- `/abuseguard check`: shows whether YOU are excused and whether the item in your hand is flagged
- `/abuseguard reload`

## Test (use a test account, it will deop you!)
1. `/abuseguard check` should say you are watched (not excused).
2. Switch to creative: the server announces it.
3. Take a stick from the creative menu: announced as "took Stick".
4. Take a Diamond Helmet: you are put in survival, deopped, demoted, and it is announced.

## Config
`plugins/AdminAbusePrevention/config.yml`: messages, the flagged item lists, and the punishment commands.

## Build
`mvn package` -> `target/AdminAbusePrevention.jar`. LuckPerms is a soft dependency.
