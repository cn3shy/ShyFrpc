# ShyFrpc

A client-side Minecraft mod that starts a local [frpc](https://github.com/fatedier/frp)
process from inside the game, exposing your LAN world to the internet through an FRP
tunnel — no port forwarding, no router configuration.

Supports **Fabric**, **Forge** and **NeoForge** on Minecraft **26.3**.

[中文说明](README.zh-CN.md)

## Requirements

- Minecraft 26.3
- Java 25
- A local `frpc` executable
- (For SakuraFrp mode) a [SakuraFrp](https://www.natfrp.com/) account with a tunnel

## Installation

1. Install the jar matching your mod loader into your `mods` folder.
2. Launch the game and open a singleplayer world.
3. Configure and start frpc with the commands below, then open your world to LAN.

## Commands

All commands require operator permission. In singleplayer you have it by default.

| Command | Description |
| --- | --- |
| `/shyfrpc set app <path>` | Path to the frpc executable. Absolute, or relative to the game directory. |
| `/shyfrpc set toml <path>` | Path to the frpc config file. Same path rules as `app`. |
| `/shyfrpc set sakura <token> <tunnel>` | Store SakuraFrp credentials. Does not switch modes. |
| `/shyfrpc mode <default\|sakura>` | Choose which mode `on` uses. |
| `/shyfrpc on` | Start frpc. |
| `/shyfrpc off` | Stop frpc. |
| `/shyfrpc auto-start <true\|false>` | Start frpc automatically when you open your world to LAN. |
| `/shyfrpc status` | Show the current configuration and whether frpc is running. |

### Example: local frpc config

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set toml "C:\frpc\minecraft.toml"
/shyfrpc mode default
/shyfrpc on
```

This runs `frpc.exe -c C:\frpc\minecraft.toml`.

### Example: SakuraFrp

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set sakura 55qt9v9oofm19s1gfjzpkcz9o5ry4qmv 27811507
/shyfrpc mode sakura
/shyfrpc on
```

This runs `frpc.exe -f 55qt9v9oofm19s1gfjzpkcz9o5ry4qmv:27811507`.

## Configuration file

Settings live in `config/shyFrpc/config.yaml` and are written by the commands above.
You can edit the file by hand; it is re-read on every `/shyfrpc` command.

```yaml
app: "C:\\frpc\\frpc.exe"
toml: "C:\\frpc\\minecraft.toml"
mode: default
sakura-token: ""
sakura-tunnel: ""
auto-start: false
```

## Lifecycle

- frpc runs as a child process of Minecraft.
- When you close your LAN world, frpc is stopped automatically.
- When you leave the world or quit the game, frpc is stopped automatically.
- All frpc output is forwarded to the in-game chat and to `logs/latest.log`.

## Building

```bash
./gradlew build
```

The jars land in `fabric/build/libs`, `forge/build/libs` and `neoforge/build/libs`.

> **Note for users in mainland China:** `maven.neoforged.net` and
> `maven.minecraftforge.net` are often unreachable there. Gradle's main process reads
> proxy settings from `~/.gradle/gradle.properties`, but ForgeGradle's mavenizer runs in
> a **forked JVM** that does not — set `JAVA_TOOL_OPTIONS` as well:
>
> ```bash
> export JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
> ```

## License

Apache License 2.0
