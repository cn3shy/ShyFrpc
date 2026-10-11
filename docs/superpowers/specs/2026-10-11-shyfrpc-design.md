# ShyFrpc 设计文档

日期：2026-10-11
状态：待评审

## 1. 目标

为 Minecraft **26.3 正式版**提供一个纯客户端 Mod，让房主在游戏内通过命令启动本机的
frpc 进程，把「对局域网开放」的房间通过 FRP 隧道暴露到公网，实现免端口映射的联机。

支持三种 Mod 加载器：**Fabric**、**Forge**、**NeoForge**。主分支为 `26.3`。

两种运行模式：

- **default** — 使用用户本地已有的 frpc 配置文件（`frpc.exe -c <toml>`）
- **sakura** — 使用 [SakuraFrp](https://www.natfrp.com/) 的用户 token 与隧道 ID
  （`frpc.exe -f <token>:<隧道ID>`）

## 2. 范围

### 做

- `/shyfrpc` 命令树（设置、模式切换、启停、自动启动、状态查询）
- `config/shyFrpc/config.yaml` 配置持久化
- frpc 子进程的启动、停止、输出实时转发
- 局域网开房状态检测，联动 `auto-start` 自动启停
- 退出游戏时清理残留进程
- en_us / zh_cn 双语
- 三个 GitHub 工作流：构建、GitHub Release、Modrinth/CurseForge 发布

### 不做

- 图形界面（只做命令）
- 服务端专用服支持（纯客户端 Mod）
- 自动下载 / 打包 frpc 可执行文件
- 端口映射管理、隧道创建（用户需自行在 SakuraFrp 后台建好隧道）
- 除 en_us / zh_cn 外的语言

## 3. 约束与前置

| 项 | 值 |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25（构建与运行） |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.17+ |
| Forge | 66.0.2 |
| NeoForge | 26.3.0.8-beta |
| 第三方依赖 | **无** |
| 开发机 JDK | 本机仅 JDK 17，由 Gradle foojay-resolver 自动拉取 JDK 25 工具链 |

**零第三方依赖**是本设计的硬约束。所有多加载器工程若引入外部库，需要走 jarJar/shadow
把库塞进产物，在每个加载器上配置方式都不同且易出错。本 Mod 的功能足够简单，完全不需要。

## 4. 架构

### 4.1 工程布局

沿用 `mcwifipnp` 的多加载器布局：一份公共代码 + 三个薄适配层。

```
shy-frpc/
├── gradle.properties                    # 三平台共享的版本号
├── settings.gradle                      # include fabric / forge / neoforge
├── gradlew, gradlew.bat, gradle/wrapper/
├── LICENSE
├── README.md, README.zh-CN.md
├── docs/superpowers/specs/
├── .github/workflows/{build,release,publish}.yml
│
├── src/main/java/io/github/cn3shy/shyfrpc/       # 公共代码（三平台共用）
│   ├── ShyFrpcUnit.java                 # 公共入口：常量、LOGGER、命令注册、tick 逻辑
│   ├── FrpcConfig.java                  # 配置读写
│   ├── FrpcYaml.java                    # 极简 YAML 编解码
│   ├── FrpcProcess.java                 # 子进程生命周期 + 输出转发
│   ├── FrpcState.java                   # 运行状态与模式枚举
│   └── commands/ShyFrpcCommand.java     # 命令树
│
├── src/main/resources/
│   ├── assets/shyfrpc/lang/en_us.json
│   ├── assets/shyfrpc/lang/zh_cn.json
│   └── pack.mcmeta
│
├── fabric/build.gradle
├── fabric/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcFabric.java
├── fabric/src/main/resources/fabric.mod.json
├── fabric/src/test/java/io/github/cn3shy/shyfrpc/          # 单元测试
│
├── forge/build.gradle
├── forge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcForge.java
├── forge/src/main/resources/META-INF/mods.toml
│
├── neoforge/build.gradle
├── neoforge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcNeoForge.java
└── neoforge/src/main/resources/META-INF/neoforge.mods.toml
```

三个平台模块通过 `sourceSets.main.java.srcDir "../src/main/java"` 与
`resources.srcDir "../src/main/resources"` 把公共代码挂进各自的源集。

### 4.2 平台差异边界

整个工程里**只有三处**需要平台特定实现：

| 关注点 | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 入口类 | `ModInitializer` | `@Mod` + `MinecraftForge.EVENT_BUS` | `@Mod` + `NeoForge.EVENT_BUS` |
| 注册命令 | `CommandRegistrationCallback` | `RegisterCommandsEvent` | `RegisterCommandsEvent` |
| 客户端 tick | `ClientTickEvents.END_CLIENT_TICK` | `ClientTickEvent.Post` | `ClientTickEvent.Post` |

其余全部在公共代码中。

**为什么公共代码可以直接引用客户端类**：本 Mod 声明为纯客户端
（Fabric `"environment": "client"`，Forge/NeoForge `clientSideOnly=true`），
公共代码永远不会在专用服务端被加载，因此可以放心 import
`net.minecraft.client.Minecraft`。这带来一个重要简化——**游戏目录**直接取自
`Minecraft.getInstance().gameDirectory`，无需各平台的文件系统 API。

### 4.3 为什么不用 Mixin

`mcwifipnp` 用了 Mixin（例如注入 `IntegratedServer.getMaxPlayers`）。本 Mod 不采用，原因：

1. 检测「是否已开局域网」只需读 `IntegratedServer.isPublished()`，是个纯查询，
   用客户端 tick 轮询即可，无需改写任何方法体。
2. Mixin 要在三个加载器上分别维护注册（Fabric 的 `mixins` 数组、Forge 的
   `MixinConfigs` manifest 属性、NeoForge 的 `[[mixins]]` 段），配置面广。
3. Mixin 注入点绑定方法签名，MC 版本升级时容易失效；`isPublished()` 是稳定的公开 API。

代价是最高 50ms（一 tick）的检测延迟，对「开房后启动隧道」这个场景完全无感知。

## 5. 配置

### 5.1 位置

`<游戏目录>/config/shyFrpc/config.yaml`

### 5.2 格式

```yaml
# ShyFrpc 配置文件，由 /shyfrpc 命令自动维护
app: ""
toml: ""
mode: default
sakura-token: ""
sakura-tunnel: ""
auto-start: false
```

| 键 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `app` | string | `""` | frpc 可执行文件路径。绝对路径原样使用；相对路径按游戏目录解析 |
| `toml` | string | `""` | frpc 配置文件路径。解析规则同 `app` |
| `mode` | enum | `default` | `default` 或 `sakura` |
| `sakura-token` | string | `""` | SakuraFrp 用户 token |
| `sakura-tunnel` | string | `""` | SakuraFrp 隧道 ID |
| `auto-start` | bool | `false` | 开启局域网房间时是否自动启动 frpc |

### 5.3 YAML 解析策略

不引入 SnakeYAML 等库。配置结构是**扁平的、固定的键值对**，足以用自写的极简解析器覆盖：

- **写**：逐行输出 `key: <gson 序列化的 JSON 字符串>`。YAML 的双引号标量语法与
  JSON 字符串语法完全一致，因此可以直接复用 MC 自带的 Gson 做转义，
  正确处理引号、反斜杠、换行、非 ASCII 字符。
- **读**：逐行匹配 `^\s*([\w-]+)\s*:\s*(.*)$`。值分两种情况：
  1. 以 `"` 开头 → 按 JSON 字符串解析（自己写的文件走这条路径）
  2. 否则 → 去除首尾空格，去除可选的一对首尾引号，作为裸标量
     （用户手工编辑的文件走这条路径）

  以 `#` 开头的行和空行跳过。未知键忽略。缺失键取默认值。

- **容错**：文件不存在 → 用默认值；解析异常 → 记录警告日志，用默认值，不删除用户文件
  （与 `mcwifipnp` 的「解析失败即删文件」策略不同，避免误删用户手写的配置）。
- **首次运行**：若文件不存在，在第一次写操作时创建父目录并落盘。

## 6. 命令

### 6.1 命令树

```
/shyfrpc
├── set
│   ├── app <路径>
│   ├── toml <路径>
│   └── sakura <token> <隧道ID>
├── mode <default|sakura>
├── on
├── off
├── auto-start <true|false>
└── status
```

所有命令要求 `Commands.hasPermission(Commands.LEVEL_OWNERS)`（单人游戏房主天然满足）。

### 6.2 行为细则

| 命令 | 行为 |
| --- | --- |
| `set app <路径>` | 写入 `app` 并保存。不校验文件是否存在（允许先设路径后放文件） |
| `set toml <路径>` | 写入 `toml` 并保存 |
| `set sakura <token> <隧道ID>` | 写入两个凭据字段并保存。**不自动切换 mode** |
| `mode default\|sakura` | 切换并保存 `mode`。`mode default` 即等价于「取消 Sakura 模式」 |
| `on` | 执行前置校验后启动 frpc；已在运行则提示 |
| `off` | 停止 frpc；未运行则提示 |
| `auto-start <bool>` | 写入并保存。**如果此刻已在局域网房间中且改为 true，立即启动一次** |
| `status` | 显示：各配置项（路径类显示解析后的绝对路径 + 是否存在）、当前 mode、frpc 运行状态与 PID |

`set sakura` 与 `mode` 刻意保持正交：用户显式要了 `mode` 命令，说明需要显式控制，
不应让设置凭据隐式改变运行模式。

### 6.3 `on` 的前置校验

按顺序检查，任一失败即报错返回、不启动进程：

1. `app` 非空
2. `app` 解析后的文件存在
3. 若 `mode == default` → `toml` 非空，且解析后的文件存在
4. 若 `mode == sakura` → `sakura-token` 与 `sakura-tunnel` 均非空

### 6.4 路径解析规则

`app` 与 `toml` 使用同一套规则：

- 绝对路径 → 原样使用
- 相对路径 → 相对**游戏目录**解析为绝对路径
- 进程的工作目录（`ProcessBuilder.directory`）显式设为游戏目录，
  因此把 `-c` 的实参写成绝对路径后，相对引用（如 toml 内部引用的证书文件）仍按游戏目录解析

### 6.5 命令执行的线程

Brigadier 命令在服务端线程执行。启动子进程和写文件都是阻塞操作，但耗时极短
（`ProcessBuilder.start()` 是 fork/exec 级别），直接在命令线程执行即可，无需异步化。
输出转发在独立线程中进行，不阻塞命令线程。

## 7. 进程管理

### 7.1 命令构造

| mode | 实参列表 |
| --- | --- |
| `default` | `[appAbs, "-c", tomlAbs]` |
| `sakura` | `[appAbs, "-f", token + ":" + tunnel]` |

直接用 `ProcessBuilder(List<String>)` 启动，**不经过 shell**。好处：

- 跨平台行为一致（避免 cmd.exe / sh 的引号与转义差异）
- 无 shell 注入面
- 更容易干净地终止进程

由于参数形式完全固定、没有自由拼接的用户参数，**不需要命令行分词器**。

### 7.2 输出转发

- `getInputStream()` 与 `getErrorStream()` 各起一个守护线程，逐行读取
- 每一行实时转发到聊天栏，同时通过 LOGGER 写入 `logs/latest.log`
- 聊天栏发送必须回主线程：通过 `Minecraft.getInstance().execute(...)` 投递
- 消息加统一前缀以便与普通聊天区分
- 线程为守护线程且随进程结束而退出；进程重启时确保旧线程已终止

### 7.3 停止

```
destroy() → 等待最多 3 秒 → 仍存活则 destroyForcibly()
```

同时注册 JVM `ShutdownHook`，兜底处理正常退出与崩溃，避免残留僵尸进程。
ShutdownHook 中的停止操作不等待（进程即将退出），直接 `destroyForcibly()`。

### 7.4 状态

`FrpcProcess` 持有以下状态，供 `status` 命令与自动启停逻辑读取：

- `running`（`Process.isAlive()`）
- `pid`
- 启动时间
- 该进程的启动来源（`MANUAL` / `AUTO`）

进程意外退出（frpc 自己崩了）时，下一次状态查询/tick 应能反映出来并清理内部状态、
在聊天栏提示。

## 8. 生命周期与自动启停

### 8.1 局域网检测

在客户端 tick 事件中**每秒轮询一次**（而非每 tick，降低开销）：

```java
IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
boolean published = server != null && server.isPublished();
```

维护上一次的 `published` 值，只在**状态翻转**时触发动作。

### 8.2 状态机

| 翻转 | 条件 | 动作 |
| --- | --- | --- |
| `false → true` | `auto-start == true` 且 frpc 未运行 | 启动 frpc（来源标记 `AUTO`） |
| `true → false` | frpc 正在运行（**不论来源**） | 停止 frpc |

「不论来源」是用户的明确选择：关闭局域网房间时，手动 `/shyfrpc on` 启动的进程也会被停掉。

只在翻转时触发，意味着：玩家手动 `on` 但从未开局域网的情况不会被误杀。

### 8.3 其他停止时机

| 事件 | 动作 |
| --- | --- |
| `/shyfrpc off` | 停止 |
| 退出世界回到主菜单（`getSingleplayerServer()` 变 `null`） | 停止 |
| 退出游戏 | `ShutdownHook` 停止 |

## 9. 本地化

`assets/shyfrpc/lang/en_us.json` 与 `zh_cn.json`，所有面向玩家的文本一律用
`Component.translatable("shyfrpc.xxx")`，不硬编码字符串。

键的组织：

```
shyfrpc.command.set.app.success
shyfrpc.command.set.toml.success
shyfrpc.command.set.sakura.success
shyfrpc.command.mode.success
shyfrpc.command.on.success
shyfrpc.command.on.already-running
shyfrpc.command.on.error.no-app
shyfrpc.command.on.error.app-not-found
shyfrpc.command.on.error.no-toml
shyfrpc.command.on.error.toml-not-found
shyfrpc.command.on.error.no-sakura
shyfrpc.command.off.success
shyfrpc.command.off.not-running
shyfrpc.command.autoStart.success
shyfrpc.command.status.*
shyfrpc.frpc.output
shyfrpc.frpc.started
shyfrpc.frpc.stopped
shyfrpc.frpc.exited-unexpectedly
```

## 10. 构建产物

`gradle.properties` 关键项：

```properties
mod_id = shyfrpc
mod_version = 2610.1.0
mod_group_id = io.github.cn3shy.shyfrpc
minecraft_version = 26.3

# 版本范围占位符，供各平台元数据文件注入
minecraft_version_min = 26.3-alpha.1
minecraft_version_max = 27
fabric_version_range = *
fabric_loader_version_range = >=0.18.4
forge_version_range = [66,)
forge_loader_version_range = [66,)
neo_version_range = [26.3.0.0-beta,)
neo_loader_version_range = [2,)
pack_format_number = 121.0
```

各平台模块：

```groovy
base.archivesName = "${mod_id}-fabric"          // 或 -forge / -neoforge
version = "${minecraft_version}-${mod_version}"
```

得到：

```
shyFrpc-fabric-26.3-2610.1.0.jar
shyFrpc-forge-26.3-2610.1.0.jar
shyFrpc-neoforge-26.3-2610.1.0.jar
```

注意 `base.archivesName` 用驼峰 `shyFrpc`（用户指定的文件名形态），
而 `mod_id` 用小写 `shyfrpc`（Fabric/Forge 对 mod id 有大小写约束）。

### 10.1 版本号命名规则

`mod_version` 采用 `<年月>.<本月发布次数>.<mod类型>` 三段式：

| 段 | 含义 | 示例 |
| --- | --- | --- |
| 年月 | `YYMM` 两位年 + 两位月 | `2610` = 2026 年 10 月 |
| 本月发布次数 | 该月内的第几次发布，从 `1` 起 | `1` = 本月第 1 次 |
| mod类型 | `0` = beta，`1` = release | `0` = beta 版 |

因此 `2610.1.0` 读作「2026 年 10 月的第 1 次发布，beta 版」。

**发版时的人工步骤**：修改 `gradle.properties` 的 `mod_version`——同月内再发一次就把
中段加一（`2610.2.0`），转正式版就把末段改成 `1`（`2610.1.1`）。

这个末位类型标记与 `publish.yml` 的 `channel` 输入相互独立：前者写进版本号、
对用户可见，后者只决定 Modrinth/CurseForge 上的版本分类。发布时应保持两者一致。

## 11. GitHub 工作流

### 11.1 `build.yml`

- 触发：`push`、`pull_request`
- JDK 25（temurin）+ `gradle/actions/setup-gradle`
- `./gradlew build`，上传三个 jar 为 artifact

### 11.2 `release.yml`

- 触发：推送 `v*` tag，或 `workflow_dispatch`
- 构建三个 jar
- 创建 GitHub Release，三个 jar 作为附件，文件名即上述三个

### 11.3 `publish.yml`

- 触发：`workflow_dispatch`，输入 `channel`（`alpha` / `beta` / `release`），
  **默认 `beta`**（与当前 `mod_version` 末位 `0` 一致，见 §10.1）
- 用 `Kir-Antipov/mc-publish@v3.3` 分别以 `fabric`、`forge`、`neoforge` 加载器
  发布到 Modrinth 与 CurseForge
- 项目 ID 与 token **留占位符**，待用户创建项目后替换：
  - `modrinth-id: TODO_CHANGE_ME`
  - `curseforge-id: TODO_CHANGE_ME`
  - secrets `MODRINTH_TOKEN`、`CURSEFORGE_TOKEN`

## 12. 元数据文件

- `fabric.mod.json`：`"environment": "client"`，入口点同时注册 `main` 与 `client`
- `mods.toml` / `neoforge.mods.toml`：`clientSideOnly=true`
- 三者都声明对 `minecraft` 的版本依赖，版本范围由 `gradle.properties` 的占位符注入
- 三者的 `license`、`authors`、`displayName`（`ShyFrpc`）、描述保持一致

**发布 ID 占位符不会阻塞构建与本地测试**，只影响 `publish.yml`。

## 13. 测试

### 13.1 策略

只有**纯逻辑**值得写自动化测试，MC 相关的集成测试（真起客户端、真开房、真跑隧道）
成本远高于收益，不做。

测试放在 `fabric/src/test/java`（common 代码通过 `srcDir` 挂在该模块的 main 源集上，
测试可直接访问），使用 JUnit 5。

### 13.2 覆盖点

**`FrpcYaml` 往返**（写→读得到等值对象）：

- 全部默认值的首次生成
- 含空格、引号、反斜杠的路径（Windows 典型）
- 含中文的值
- 空字符串
- 布尔值的两种取值

**`FrpcYaml` 解析容错**：

- 空文件
- 只有注释的文件
- 键顺序被打乱
- 用户手写的裸标量（不带引号）
- 未知键被忽略
- 缺失键取默认值
- 畸形内容不抛异常、降级为默认值

**`FrpcConfig.resolvePath`**：

- 绝对路径原样返回
- 相对路径按游戏目录拼接
- Windows 与 Unix 两种分隔符

**`FrpcProcess.buildCommand`**：

- `mode=default` → `[app, "-c", tomlAbs]`
- `mode=sakura` → `[app, "-f", "token:tunnel"]`
- 相对路径的 app/toml 被正确解析为绝对路径

### 13.3 不做测试的部分

- Minecraft API 交互（`isPublished()` 轮询、聊天栏投递）
- 真实进程的启动与终止
- GitHub 工作流

## 14. 已知风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| frpc 路径含空格 | Windows 上常见 | 用 `ProcessBuilder(List)` 而非拼接字符串，天然安全 |
| frpc 输出瞬间刷屏 | 连接失败重试时可能一次吐几十行 | 用户已明确选择「全部实时转发」；如实际体验糟，后续可加限流 |
| 26.3 的 API 与参考项目产出的版本可能微调 | 编译期即暴露 | 首次构建时按报错调整 |
| 本机无 JDK 25 | 26.3 要求 Java 25 | Gradle foojay-resolver 自动下载工具链 |
| 命令权限即本机任意程序执行 | OP 可通过本 Mod 启动任意可执行文件 | 设计上即是如此（Mod 的核心功能），已限制为 OP 可用 |
| 发布 ID 未填 | `publish.yml` 无法真正发布 | 占位符明确标注 `TODO_CHANGE_ME` |

## 15. 验收标准

1. 三个平台模块都能 `./gradlew build` 通过，产出三个正确命名的 jar
2. 单人游戏内能注册出完整 `/shyfrpc` 命令树，非 OP 环境下被拒绝
3. `set app` / `set toml` / `set sakura` / `mode` / `auto-start` 能正确写入
   `config/shyFrpc/config.yaml`，重启游戏后配置保留
4. `default` 模式下 `on` 能拉起 `frpc -c <toml>`，输出实时出现在聊天栏
5. `sakura` 模式下 `on` 能拉起 `frpc -f <token>:<隧道ID>`
6. `off` 能干净终止进程；退出游戏后无残留 frpc 进程
7. `auto-start=true` 时，点「对局域网开放」自动启动；关闭局域网自动停止
8. 前置校验失败时给出明确的中文/英文报错，不启动进程
9. en_us 与 zh_cn 两种客户端语言下所有文本都正确显示
