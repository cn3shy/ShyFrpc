# ShyFrpc

一个纯客户端 Minecraft Mod：在游戏内启动本机的 [frpc](https://github.com/fatedier/frp)
进程，把局域网房间通过 FRP 隧道暴露到公网——不需要端口映射，也不用改路由器。

支持 **Fabric**、**Forge**、**NeoForge** 三个加载器，适配 Minecraft **26.3**。

[English](README.md)

## 前置要求

- Minecraft 26.3
- Java 25
- 本机已有 `frpc` 可执行文件
- （使用 SakuraFrp 模式时）一个 [SakuraFrp](https://www.natfrp.com/) 账号和已创建的隧道

## 安装

1. 把对应加载器的 jar 放进 `mods` 文件夹。
2. 启动游戏，进入一个单人世界。
3. 用下面的命令配置并启动 frpc，然后把世界开放到局域网。

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

### 示例：使用本地 frpc 配置

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set toml "C:\frpc\minecraft.toml"
/shyfrpc mode default
/shyfrpc on
```

实际执行的是 `frpc.exe -c C:\frpc\minecraft.toml`。

### 示例：使用 SakuraFrp

```
/shyfrpc set app "C:\frpc\frpc.exe"
/shyfrpc set sakura 55qt9v9oofm19s1gfjzpkcz9o5ry4qmv 27811507
/shyfrpc mode sakura
/shyfrpc on
```

实际执行的是 `frpc.exe -f 55qt9v9oofm19s1gfjzpkcz9o5ry4qmv:27811507`。

## 配置文件

配置存放在 `config/shyFrpc/config.yaml`，由上述命令自动维护。
你也可以手工编辑，每次执行 `/shyfrpc` 命令都会重新读取。

```yaml
app: "C:\\frpc\\frpc.exe"
toml: "C:\\frpc\\minecraft.toml"
mode: default
sakura-token: ""
sakura-tunnel: ""
auto-start: false
```

## 生命周期

- frpc 作为 Minecraft 的子进程运行。
- 关闭局域网房间时，frpc 自动停止。
- 退出世界或退出游戏时，frpc 自动停止。
- frpc 的所有输出都会转发到游戏聊天栏，并写入 `logs/latest.log`。

## 构建

```bash
./gradlew build
```

产物在 `fabric/build/libs`、`forge/build/libs`、`neoforge/build/libs`。

> **国内用户注意**：`maven.neoforged.net` 会被 DNS 污染（解析到无关 IP），
> `maven.minecraftforge.net` 也常不可达。Gradle 主进程会读
> `~/.gradle/gradle.properties` 里的代理配置，但 ForgeGradle 的 mavenizer 跑在
> **fork 出来的独立 JVM** 里，读不到，需要额外设置 `JAVA_TOOL_OPTIONS`：
>
> ```bash
> export JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
> ```
>
> 另：构建被中断会留下截断的 jar，导致 `zip END header not found`，
> 需删除 `~/.gradle/caches/minecraftforge` 后重试。

## 许可证

Apache License 2.0
