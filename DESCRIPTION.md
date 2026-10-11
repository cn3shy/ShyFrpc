<!--
  Modrinth / CurseForge 发布用简介。
  下面是英文版，中文版从 "---" 分隔线之后开始。
  Modrinth 支持按语言分别填写描述；CurseForge 用单栏，建议填英文版。
  文件我没有加进 git 仓库，需要的话你自行 git add。
-->

# ShyFrpc

**Open your LAN world to the internet without touching your router.**

ShyFrpc is a client-side Minecraft mod that launches a local [frpc](https://github.com/fatedier/frp)
process from inside the game, so you can forward your singleplayer LAN world through an
FRP tunnel. No port forwarding, no NAT configuration, no external launcher — just a slash
command.

Built for the exact problem every host hits: you want to play with a friend, you open your
world to LAN, and then you realize your ISP put you behind CGNAT and port forwarding is
simply not an option.

## Features

- **No port forwarding.** Works behind CGNAT and carrier-grade NAT.
- **Pure client-side.** Nothing to install on the other player's side. They just need your
  tunnel address.
- **Two modes.** Point it at your own frpc config file, or use SakuraFrp credentials with
  a single command.
- **Live output.** Everything frpc prints goes straight to your in-game chat and to
  `logs/latest.log`, so you can see the tunnel address and spot auth failures immediately.
- **Auto-start.** Turn it on and frpc starts the moment you open your world to LAN.
- **Automatic cleanup.** Close the LAN room, leave the world, or quit the game — frpc is
  stopped for you. No zombie processes.
- **English and Chinese** localization.
- **No third-party runtime dependencies.**

## Requirements

| | |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Mod loader | Fabric, Forge or NeoForge |
| frpc | A local `frpc` executable — **not bundled**, you supply your own |

For SakuraFrp mode you also need a [SakuraFrp](https://www.natfrp.com/) account with a
tunnel already created in their panel.

## Installation

1. Download the jar matching your mod loader and drop it in your `mods` folder.
2. Install [frp](https://github.com/fatedier/frp/releases) and note the full path to
   `frpc.exe` / `frpc`.
3. Launch the game, load a singleplayer world, and configure ShyFrpc with the commands
   below.
4. Share the tunnel address that appears in chat with your friends.

## Commands

All commands require operator permission. In singleplayer you have it by default.

| Command | Description |
| --- | --- |
| `/shyfrpc set app <path>` | Path to the frpc executable. Absolute, or relative to the game directory. |
| `/shyfrpc set toml <path>` | Path to the frpc config file. Same path rules as `app`. |
| `/shyfrpc set sakura <token> <tunnel>` | Store SakuraFrp credentials. Does **not** switch modes. |
| `/shyfrpc mode <default\|sakura>` | Choose which mode `on` uses. |
| `/shyfrpc on` | Start frpc. |
| `/shyfrpc off` | Stop frpc. |
| `/shyfrpc auto-start <true\|false>` | Start frpc automatically when you open your world to LAN. |
| `/shyfrpc status` | Show the current configuration and whether frpc is running. |

## Usage

### Using your own frpc config

If you already have a working frpc setup, point ShyFrpc at it:

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set toml "C:\frpc\minecraft.toml"
/shyfrpc mode default
/shyfrpc on
```

ShyFrpc runs:

```
frpc.exe -c C:\frpc\minecraft.toml
```

The `-c` path is resolved to an absolute path before launch, and frpc's working directory
is set to your game directory — so relative references inside your `.toml` still resolve
the way you expect.

### Using SakuraFrp

Create a tunnel in the SakuraFrp panel, then copy the user token from your account page
and the tunnel ID from the tunnel list:

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set sakura token 123
/shyfrpc mode sakura
/shyfrpc on
```

ShyFrpc runs:

```
frpc.exe -f token:123
```

### Fully automatic

Once configured, this is all you need to do each session:

```
/shyfrpc auto-start true
```

Then just press Escape → **Open to LAN** → **Start LAN World**. frpc starts on its own,
and stops when you close the room or leave the world.

## Configuration file

Settings are stored in `config/shyFrpc/config.yaml` and written by the commands above.
You can edit the file by hand — it is re-read every time you run a `/shyfrpc` command, so
no restart is needed.

```yaml
app: "C:\\frpc\\frpc.exe"
toml: "C:\\frpc\\minecraft.toml"
mode: default
sakura-token: ""
sakura-tunnel: ""
auto-start: false
```

If the file is ever corrupted, ShyFrpc falls back to defaults and logs a warning — it will
never delete your configuration for you.

## Lifecycle

ShyFrpc treats frpc as a child process of Minecraft:

| Event | What happens |
| --- | --- |
| `/shyfrpc off` | frpc stops |
| You close the LAN room | frpc stops |
| You leave the world | frpc stops |
| You quit the game | frpc stops |

Stopping is graceful: frpc gets a normal termination signal, and is force-killed only if it
has not exited within three seconds.

## Troubleshooting

**"frpc executable not found"**
The path is resolved relative to your game directory if it is not absolute. Run
`/shyfrpc status` — it prints the fully resolved path and marks it `[missing]` if it does
not exist.

**The tunnel connects but my friend cannot join**
Start the LAN world *before* or right after running `/shyfrpc on` — frpc has nothing to
forward until the world is published to LAN. Also make sure the tunnel's remote port
matches the port shown in the "Local game hosted on port …" chat message.

**frpc output shows an authentication error**
Double-check the token and tunnel ID with `/shyfrpc status` — the token is masked there, so
set it again if you are unsure. Make sure the tunnel exists and is not disabled in the
SakuraFrp panel.

**Nothing appears in chat**
frpc output is forwarded to the player only while you are in a world. If you started frpc
and then returned to the main menu, check `logs/latest.log` instead.

## Building from source

```bash
./gradlew build
```

The jars land in `fabric/build/libs`, `forge/build/libs` and `neoforge/build/libs`.

## License

Apache License 2.0

---

# ShyFrpc（中文）

**不改路由器，就能把局域网房间开到公网。**

ShyFrpc 是一个纯客户端的 Minecraft Mod：在游戏内启动本机的
[frpc](https://github.com/fatedier/frp) 进程，把单人世界的局域网房间通过 FRP 隧道转发出去。
不需要端口映射，不需要改 NAT，也不需要额外的启动器——一条斜杠命令就够了。

它解决的是每个房主都会撞上的那个问题：想和朋友联机，把世界开放到局域网，结果发现运营商
给你套了层 CGNAT，端口映射根本没法做。

## 功能

- **不需要端口映射**，在 CGNAT / 运营商级 NAT 后面也能用。
- **纯客户端**，朋友那边什么都不用装，只要拿到你的隧道地址。
- **两种模式**，可以直接用你自己的 frpc 配置文件，也可以用 SakuraFrp 凭据一条命令搞定。
- **输出实时可见**，frpc 打印的所有内容都会转发到游戏聊天栏并写入 `logs/latest.log`，
  隧道地址一眼就能看到，认证失败也能立刻发现。
- **自动启动**，开启后你一点「对局域网开放」frpc 就自己起来了。
- **自动清理**，关闭房间、退出世界、退出游戏都会自动停止 frpc，不留僵尸进程。
- **中英双语**。
- **零第三方运行时依赖**。

## 前置要求

| 项 | 说明 |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| 加载器 | Fabric / Forge / NeoForge |
| frpc | 本机的 `frpc` 可执行文件——**Mod 不附带**，需要你自己准备 |

使用 SakuraFrp 模式还需要一个 [SakuraFrp](https://www.natfrp.com/) 账号，
并在后台建好隧道。

## 安装

1. 下载对应加载器的 jar，放进 `mods` 文件夹。
2. 安装 [frp](https://github.com/fatedier/frp/releases)，记下 `frpc.exe` / `frpc` 的完整路径。
3. 启动游戏，进入单人世界，用下面的命令配置 ShyFrpc。
4. 把聊天栏里出现的隧道地址发给朋友。

## 命令

所有命令都需要管理员（OP）权限，单人游戏默认满足。

| 命令 | 说明 |
| --- | --- |
| `/shyfrpc set app <路径>` | frpc 可执行文件路径。绝对路径，或相对游戏目录的路径。 |
| `/shyfrpc set toml <路径>` | frpc 配置文件路径。路径规则同 `app`。 |
| `/shyfrpc set sakura <token> <隧道ID>` | 保存 SakuraFrp 凭据。**不会**自动切换模式。 |
| `/shyfrpc mode <default\|sakura>` | 选择 `on` 使用哪种模式。 |
| `/shyfrpc on` | 启动 frpc。 |
| `/shyfrpc off` | 停止 frpc。 |
| `/shyfrpc auto-start <true\|false>` | 开启局域网房间时是否自动启动 frpc。 |
| `/shyfrpc status` | 查看当前配置和 frpc 运行状态。 |

## 用法

### 使用你自己的 frpc 配置

如果你已经有能用的 frpc 配置，直接指过来就行：

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set toml "C:\frpc\minecraft.toml"
/shyfrpc mode default
/shyfrpc on
```

实际执行：

```
frpc.exe -c C:\frpc\minecraft.toml
```

`-c` 的路径会先解析成绝对路径再启动，同时进程的工作目录被设为游戏目录——
所以你的 `.toml` 内部的相对引用仍然按预期解析。

### 使用 SakuraFrp

先在 SakuraFrp 后台建好隧道，然后从账户页复制用户 token，从隧道列表复制隧道 ID：

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set sakura token 123
/shyfrpc mode sakura
/shyfrpc on
```

实际执行：

```
frpc.exe -f token:123
```

### 全自动

配置好之后，以后每次开局只需要：

```
/shyfrpc auto-start true
```

然后按 Esc → **对局域网开放** → **开始 LAN 世界**。frpc 会自动启动，
关闭房间或退出世界时自动停止。

## 配置文件

配置存放在 `config/shyFrpc/config.yaml`，由上述命令自动维护。
你也可以手工编辑——每次执行 `/shyfrpc` 命令都会重新读取，无需重启游戏。

```yaml
app: "C:\\frpc\\frpc.exe"
toml: "C:\\frpc\\minecraft.toml"
mode: default
sakura-token: ""
sakura-tunnel: ""
auto-start: false
```

配置文件万一损坏，ShyFrpc 会退回默认值并记录警告——**它绝不会替你删掉配置**。

## 生命周期

ShyFrpc 把 frpc 当作 Minecraft 的子进程管理：

| 事件 | 行为 |
| --- | --- |
| `/shyfrpc off` | frpc 停止 |
| 关闭局域网房间 | frpc 停止 |
| 退出世界 | frpc 停止 |
| 退出游戏 | frpc 停止 |

停止是优雅的：先发正常终止信号，3 秒内没退出才强杀。

## 常见问题

**「找不到 frpc 可执行文件」**
非绝对路径会按游戏目录解析。执行 `/shyfrpc status`，它会打印解析后的完整路径，
不存在时会标注 `[缺失]`。

**隧道连上了但朋友进不来**
请先开局域网房间再执行 `/shyfrpc on`（或反过来，紧接着执行）——世界没有开放到局域网之前，
frpc 没有东西可以转发。另外确认隧道映射的远程端口，与聊天栏里
「本地游戏已在端口 xxx 上开启」显示的端口一致。

**frpc 输出显示认证失败**
用 `/shyfrpc status` 核对 token 和隧道 ID——token 在里面是打码的，
不确定就重新 set 一次。同时确认 SakuraFrp 后台那个隧道确实存在且没被禁用。

**聊天栏里什么都没有**
frpc 的输出只在你处于世界内时才转发给玩家。如果你是先启动 frpc 再退回主菜单，
请改看 `logs/latest.log`。

## 从源码构建

```bash
./gradlew build
```

产物在 `fabric/build/libs`、`forge/build/libs`、`neoforge/build/libs`。

## 许可证

Apache License 2.0
