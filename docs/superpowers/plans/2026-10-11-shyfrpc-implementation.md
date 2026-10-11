# ShyFrpc 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Minecraft 26.3 实现一个纯客户端 Mod，让玩家用 `/shyfrpc` 命令在游戏内启动本机 frpc 进程，把局域网房间通过 FRP 隧道暴露到公网。

**Architecture:** 一份公共代码（`src/main`，三加载器共享）+ 三个薄适配层（`fabric` / `forge` / `neoforge`），只有入口类、命令注册回调、客户端 tick 三处需要平台特定实现。公共代码按职责拆成 YAML 编解码、配置模型、启动参数构造、进程管理、状态机、命令树六块，其中不碰 Minecraft API 的部分全部可单元测试。

**Tech Stack:** Java 25、Gradle（多模块）、Fabric Loom 1.17+ / ForgeGradle 7 / NeoForge ModDev 2.x、Brigadier 命令、Gson（MC 自带）、log4j2（MC 自带）、JUnit 5。**零第三方运行时依赖。**

**规格文档：** `docs/superpowers/specs/2026-10-11-shyfrpc-design.md`

**重要提示：** 本计划中的 Minecraft/Forge/NeoForge API 调用基于参考项目 `D:\Project\MinecraftPro\mcwifipnp`（同为 MC 26.3 + Forge 66.0.2 + NeoForge 26.3.0.8-beta）的实际用法。但**客户端 tick 事件的类名未经验证**（参考项目没用）。Task 1 与 Task 9 里的编译步骤就是要尽早暴露这类问题。

---

## 文件结构

| 文件 | 职责 | 是否碰 MC API |
| --- | --- | --- |
| `src/main/java/io/github/cn3shy/shyfrpc/FrpcYaml.java` | 扁平键值对的极简 YAML 编解码 | 否 |
| `src/main/java/io/github/cn3shy/shyfrpc/FrpcMode.java` | `DEFAULT` / `SAKURA` 枚举 | 否 |
| `src/main/java/io/github/cn3shy/shyfrpc/FrpcConfig.java` | 配置模型、读写、路径解析 | 否 |
| `src/main/java/io/github/cn3shy/shyfrpc/FrpcLauncher.java` | 启动命令构造 + 前置校验 | 否 |
| `src/main/java/io/github/cn3shy/shyfrpc/FrpcProcess.java` | 子进程启停 + 输出转发 | 是 |
| `src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java` | 公共入口、局域网检测状态机 | 是 |
| `src/main/java/io/github/cn3shy/shyfrpc/commands/ShyFrpcCommand.java` | `/shyfrpc` 命令树 | 是 |
| `fabric/src/main/java/.../ShyFrpcFabric.java` | Fabric 入口 | 是 |
| `forge/src/main/java/.../ShyFrpcForge.java` | Forge 入口 | 是 |
| `neoforge/src/main/java/.../ShyFrpcNeoForge.java` | NeoForge 入口 | 是 |
| `fabric/src/test/java/.../FrpcYamlTest.java` | YAML 编解码测试 | 否 |
| `fabric/src/test/java/.../FrpcConfigTest.java` | 配置模型与路径解析测试 | 否 |
| `fabric/src/test/java/.../FrpcLauncherTest.java` | 命令构造与校验测试 | 否 |

**与规格文档 §4.1 的一处偏差**：规格里写的 `FrpcState.java` 被拆成了 `FrpcMode.java`（模式枚举）与 `FrpcProcess.java` 内的 `Source` 枚举（启动来源）；规格里的 `FrpcProcess.buildCommand` 被提到独立的 `FrpcLauncher.java`，为的是让「命令构造 + 校验」这两块纯逻辑不依赖 Minecraft，从而能被单元测试覆盖。功能划分与规格一致。

---

## Task 1: 工程骨架与三平台构建验证

这一步的目标是让 `./gradlew build` 在三个加载器上跑通，**在写任何功能代码之前**确认构建链、版本号、依赖坐标都对。后面每个任务都依赖它。

**Files:**
- Create: `gradle.properties`
- Create: `settings.gradle`
- Create: `fabric/build.gradle`
- Create: `forge/build.gradle`
- Create: `neoforge/build.gradle`
- Create: `src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java`
- Create: `src/main/resources/pack.mcmeta`
- Create: `fabric/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcFabric.java`
- Create: `fabric/src/main/resources/fabric.mod.json`
- Create: `forge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcForge.java`
- Create: `forge/src/main/resources/META-INF/mods.toml`
- Create: `neoforge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcNeoForge.java`
- Create: `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- Copy: `gradle/`、`gradlew`、`gradlew.bat` 从参考项目

- [ ] **Step 1: 复制 Gradle Wrapper**

Wrapper 的 jar 是二进制文件，不能手写，从参考项目复制：

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
cp -r "D:/Project/MinecraftPro/mcwifipnp/gradle" .
cp "D:/Project/MinecraftPro/mcwifipnp/gradlew" .
cp "D:/Project/MinecraftPro/mcwifipnp/gradlew.bat" .
chmod +x gradlew
ls -la gradle/wrapper/ gradlew
```

Expected: 看到 `gradle/wrapper/gradle-wrapper.jar` 与 `gradle-wrapper.properties`。

- [ ] **Step 2: 写 `gradle.properties`**

```properties
org.gradle.jvmargs = -Xmx4G
org.gradle.daemon = false
org.gradle.debug = false
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configureondemand=true

org.gradle.configuration-cache=true
org.gradle.configuration-cache.parallel=true
org.gradle.configuration-cache.problems=warn

net.minecraftforge.gradle.merge-source-sets=true

# Mod Properties
mod_id = shyfrpc
mod_archive_name = shyFrpc
mod_version = 2610.1.0
mod_group_id = io.github.cn3shy.shyfrpc
mod_name = ShyFrpc
mod_authors = cn3shy

# The Minecraft version in the development environment
minecraft_version = 26.3

# Fabric Properties on https://fabricmc.net/develop/
fabric_loader_version = 0.19.5
fabric_api_version = 0.161.0+26.3
fabric_loom_version = 1.17+

# Forge Properties on https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json
forge_version = 66.0.2

# NeoForge Properties on https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge
neo_version = 26.3.0.8-beta

# Mod Config
# The minimum minecraft version (inclusive) to run this Mod
minecraft_version_min=26.3-alpha.1
# The maximum minecraft version (exclusive) to run this Mod
minecraft_version_max=27

# Fabric
fabric_version_range = *
fabric_loader_version_range = >=0.18.4

# Forge
forge_version_range = [66,)
forge_loader_version_range = [66,)

# NeoForge
neo_version_range = [26.3.0.0-beta,)
neo_loader_version_range = [2,)

# Resource Pack
pack_format_number = 121.0
```

- [ ] **Step 3: 写 `settings.gradle`**

```groovy
pluginManagement {
  repositories {
    mavenLocal()
    maven {
      name = 'Fabric'
      url = 'https://maven.fabricmc.net/'
    }
    maven {
      name = 'MinecraftForge'
      url = 'https://maven.minecraftforge.net/'
    }
    maven {
      name = 'NeoForge'
      url = 'https://maven.neoforged.net/#/releases'
    }
    maven {
      name = 'Quilt'
      url = 'https://maven.quiltmc.org/repository/release'
    }
    maven {
        name = 'Minecraft libraries'
        url = 'https://libraries.minecraft.net'
    }
    exclusiveContent {
        forRepository {
            maven {
                name = 'Sponge'
                url = 'https://repo.spongepowered.org/repository/maven-public'
            }
        }
        filter {
            includeGroupAndSubgroups('org.spongepowered')
        }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = "shy-frpc"

include("fabric")
include("forge")
include("neoforge")
```

- [ ] **Step 4: 写 `fabric/build.gradle`**

```groovy
plugins {
  id 'net.fabricmc.fabric-loom' version "${fabric_loom_version}"
}

println "Java: ${System.getProperty 'java.version'}, JVM: ${System.getProperty 'java.vm.version'} (${System.getProperty 'java.vendor'}), Arch: ${System.getProperty 'os.arch'}"

version = project.minecraft_version + "-" + project.mod_version
group = project.mod_group_id
base.archivesName = "${mod_archive_name}-fabric"

dependencies {
  minecraft "com.mojang:minecraft:${project.minecraft_version}"
  implementation "net.fabricmc:fabric-loader:${project.fabric_loader_version}"
  implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

  testImplementation platform('org.junit:junit-bom:5.11.4')
  testImplementation 'org.junit.jupiter:junit-jupiter'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

sourceSets {
  main {
    java {
      srcDir "../src/main/java"
    }
    resources {
      srcDir "../src/main/resources"
      srcDir "src/generated/resources"
    }
  }
}

tasks.withType(ProcessResources).configureEach {
  var replaceProperties = [
    mod_name: project.mod_name,
    mod_authors: project.mod_authors,
    version: project.mod_version,
    minecraft_version_min : project.minecraft_version_min,
    minecraft_version_max : project.minecraft_version_max,
    fabric_version_range : project.fabric_version_range,
    fabric_loader_version_range : project.fabric_loader_version_range,
    pack_format_number: project.pack_format_number,
  ]
  inputs.properties replaceProperties
  filesMatching(['fabric.mod.json', 'pack.mcmeta']) {
    expand replaceProperties
  }
}

tasks.withType(JavaCompile).configureEach {
  it.options.encoding = "UTF-8"
  it.options.release = 25
}

test {
  useJUnitPlatform()
}

java {
  withSourcesJar()
  sourceCompatibility = targetCompatibility = JavaVersion.VERSION_25
}

jar {
  from("../LICENSE") {
  }
}
```

- [ ] **Step 5: 写 `forge/build.gradle`**

```groovy
plugins {
  id 'java'
  id 'idea'
  id 'eclipse'
  id 'net.minecraftforge.gradle' version '[7.0.17,8)'
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

println "Java: ${System.getProperty 'java.version'}, JVM: ${System.getProperty 'java.vm.version'} (${System.getProperty 'java.vendor'}), Arch: ${System.getProperty 'os.arch'}"

version = project.minecraft_version + "-" + project.mod_version
group = project.mod_group_id
base.archivesName = "${mod_archive_name}-forge"

minecraft {
  runs {
    configureEach {
      workingDir = layout.projectDirectory.dir('run')
      systemProperty 'eventbus.api.strictRuntimeChecks', 'true'
    }

    register('client')

    register('server') {
      args '--nogui'
    }
  }
}

repositories {
  minecraft.mavenizer(it)
  maven fg.forgeMaven
  maven fg.minecraftLibsMaven
  mavenCentral()
}

dependencies {
  implementation minecraft.dependency("net.minecraftforge:forge:${project.minecraft_version}-${project.forge_version}")
  annotationProcessor 'net.minecraftforge:eventbus-validator:7.0.5'
}

sourceSets.main {
  java {
    srcDir "../src/main/java"
  }
  resources {
    srcDir "../src/main/resources"
    srcDir "src/generated/resources"
  }
}

tasks.withType(ProcessResources).configureEach {
  var replaceProperties = [
    mod_name: project.mod_name,
    mod_authors: project.mod_authors,
    mod_version : project.mod_version,
    forge_version_range : project.forge_version_range,
    minecraft_version_min : project.minecraft_version_min,
    minecraft_version_max : project.minecraft_version_max,
    forge_loader_version_range : project.forge_loader_version_range,
    pack_format_number: project.pack_format_number,
  ]
  inputs.properties replaceProperties
  filesMatching(['META-INF/mods.toml', 'pack.mcmeta']) {
    expand replaceProperties
  }
}

tasks.withType(JavaCompile).configureEach {
  options.encoding = 'UTF-8'
}

java.withSourcesJar()

jar {
  from("../LICENSE") {
  }
}

idea {
  module {
    downloadSources = true
    downloadJavadoc = true
  }
}
```

- [ ] **Step 6: 写 `neoforge/build.gradle`**

```groovy
plugins {
  id 'java'
  id 'idea'
  id 'eclipse'
  id 'net.neoforged.moddev' version '2.+'
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

println "Java: ${System.getProperty 'java.version'}, JVM: ${System.getProperty 'java.vm.version'} (${System.getProperty 'java.vendor'}), Arch: ${System.getProperty 'os.arch'}"

version = project.minecraft_version + "-" + project.mod_version
group = project.mod_group_id
base.archivesName = "${mod_archive_name}-neoforge"

repositories {
  mavenLocal()
}

neoForge {
  version = project.neo_version

  runs {
    client {
      client()
    }

    server {
      server()
      programArgument '--nogui'
    }
  }

  mods {
    "${mod_id}" {
      sourceSet(sourceSets.main)
    }
  }
}

sourceSets.main {
  java {
    srcDir "../src/main/java"
  }
  resources {
    srcDir "../src/main/resources"
    srcDir "src/generated/resources"
  }
}

tasks.withType(ProcessResources).configureEach {
  var replaceProperties = [
    mod_name: project.mod_name,
    mod_authors: project.mod_authors,
    mod_version: project.mod_version,
    neo_version_range : project.neo_version_range,
    minecraft_version_min : project.minecraft_version_min,
    minecraft_version_max : project.minecraft_version_max,
    neo_loader_version_range : project.neo_loader_version_range,
    pack_format_number: project.pack_format_number,
  ]
  inputs.properties replaceProperties
  filesMatching(['META-INF/neoforge.mods.toml', 'pack.mcmeta']) {
    expand replaceProperties
  }
}

tasks.withType(JavaCompile).configureEach {
  options.encoding = 'UTF-8'
}

java.withSourcesJar()

jar {
  from("../LICENSE") {
  }
}

idea {
  module {
    downloadSources = true
    downloadJavadoc = true
  }
}
```

- [ ] **Step 7: 写公共常量类 `ShyFrpcUnit.java`**

这一步只放常量和 logger，功能后面再加。

`src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java`：

```java
package io.github.cn3shy.shyfrpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 公共入口。三个平台加载器的入口类都调用这里，本类不导入任何平台特定类。
 */
public final class ShyFrpcUnit {
	public static final String MODID = "shyfrpc";

	/** 全 Mod 共用的 logger，输出到 logs/latest.log。 */
	public static final Logger LOGGER = LogManager.getLogger("ShyFrpc");

	private ShyFrpcUnit() {
	}
}
```

- [ ] **Step 8: 写 `pack.mcmeta`**

`src/main/resources/pack.mcmeta`：

```json
{
  "pack": {
    "description": "ShyFrpc Mod Resources",
    "pack_format": ${pack_format_number},
    "min_format": 1,
    "max_format": 999,
    "supported_formats": [1, 999]
  }
}
```

- [ ] **Step 9: 写 Fabric 入口与元数据**

`fabric/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcFabric.java`：

```java
package io.github.cn3shy.shyfrpc;

import net.fabricmc.api.ClientModInitializer;

public class ShyFrpcFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on Fabric");
	}
}
```

`fabric/src/main/resources/fabric.mod.json`：

```json
{
  "schemaVersion": 1,
  "id": "shyfrpc",
  "version": "${version}",
  "name": "${mod_name}",
  "description": "启动本机 frpc 进程，把局域网房间通过 FRP 隧道暴露到公网。",
  "authors": [
    "${mod_authors}"
  ],
  "contact": {
    "homepage": "https://github.com/cn3shy/shy-frpc",
    "issues": "https://github.com/cn3shy/shy-frpc/issues",
    "sources": "https://github.com/cn3shy/shy-frpc"
  },
  "license": "Apache-2.0",
  "environment": "client",
  "entrypoints": {
    "client": [ "io.github.cn3shy.shyfrpc.ShyFrpcFabric" ]
  },
  "depends": {
    "fabric-api": "${fabric_version_range}",
    "minecraft": ">=${minecraft_version_min} <${minecraft_version_max}",
    "fabricloader": "${fabric_loader_version_range}",
    "java": ">=25"
  }
}
```

- [ ] **Step 10: 写 Forge 入口与元数据**

`forge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcForge.java`：

```java
package io.github.cn3shy.shyfrpc;

import net.minecraftforge.fml.common.Mod;

@Mod(ShyFrpcUnit.MODID)
public class ShyFrpcForge {
	public ShyFrpcForge() {
		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on Forge");
	}
}
```

`forge/src/main/resources/META-INF/mods.toml`：

```toml
modLoader="javafml"
loaderVersion="${forge_loader_version_range}"
license="Apache-2.0"
issueTrackerURL="https://github.com/cn3shy/shy-frpc/issues"
clientSideOnly=true

[[mods]]
modId="shyfrpc"
version="${mod_version}"
displayName="${mod_name}"
displayURL="https://github.com/cn3shy/shy-frpc"
authors="${mod_authors}"
description='''
启动本机 frpc 进程，把局域网房间通过 FRP 隧道暴露到公网。
'''

[[dependencies.shyfrpc]]
  modId="forge"
  mandatory=true
  versionRange="${forge_version_range}"
  ordering="NONE"
  side="CLIENT"

[[dependencies.shyfrpc]]
  modId="minecraft"
  mandatory=true
  versionRange="[${minecraft_version_min}, ${minecraft_version_max})"
  ordering="NONE"
  side="CLIENT"
```

- [ ] **Step 11: 写 NeoForge 入口与元数据**

`neoforge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcNeoForge.java`：

```java
package io.github.cn3shy.shyfrpc;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(ShyFrpcUnit.MODID)
public class ShyFrpcNeoForge {
	public ShyFrpcNeoForge(IEventBus modEventBus) {
		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on NeoForge");
	}
}
```

`neoforge/src/main/resources/META-INF/neoforge.mods.toml`：

```toml
modLoader="javafml"
loaderVersion="${neo_loader_version_range}"
license="Apache-2.0"
issueTrackerURL="https://github.com/cn3shy/shy-frpc/issues"

[[mods]]
modId="shyfrpc"
version="${mod_version}"
displayName="${mod_name}"
displayURL="https://github.com/cn3shy/shy-frpc"
authors="${mod_authors}"
description='''
启动本机 frpc 进程，把局域网房间通过 FRP 隧道暴露到公网。
'''

[[dependencies.shyfrpc]]
  modId="neoforge"
  type="required"
  versionRange="${neo_version_range}"
  ordering="NONE"
  side="CLIENT"

[[dependencies.shyfrpc]]
  modId="minecraft"
  type="required"
  versionRange="[${minecraft_version_min}, ${minecraft_version_max})"
  ordering="NONE"
  side="CLIENT"
```

- [ ] **Step 12: 造一个 LICENSE 占位**

`forge/build.gradle` 等里的 `jar { from("../LICENSE") }` 需要这个文件存在，否则构建报错。

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
cp "D:/Project/MinecraftPro/mcwifipnp/LICENSE" ./LICENSE
head -3 LICENSE
```

Expected: 显示 Apache License 2.0 的开头。Task 11 会确认是否需要换成别的许可证。

- [ ] **Step 13: 构建验证**

这是整个 Task 1 的核心产出。首次运行会下载 MC 26.3、Forge 66.0.2、NeoForge 26.3.0.8-beta 以及 Gradle 自动拉取的 JDK 25 工具链，可能耗时 10 分钟以上。

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew build --no-daemon 2>&1 | tail -40
```

Expected: `BUILD SUCCESSFUL`。

然后确认产物名字对：

```bash
ls fabric/build/libs/ forge/build/libs/ neoforge/build/libs/
```

Expected:
```
fabric/build/libs/shyFrpc-fabric-26.3-2610.1.0.jar
forge/build/libs/shyFrpc-forge-26.3-2610.1.0.jar
neoforge/build/libs/shyFrpc-neoforge-26.3-2610.1.0.jar
```

（各自还会有一个 `-sources.jar`，忽略。）

**如果构建失败**，按错误类型处理：

- 依赖坐标 404 → 去对应 maven 仓库确认 MC 26.3 的 Forge/NeoForge/Loader 版本号，改 `gradle.properties`
- `expand` 报 `Missing property` → `ProcessResources` 的 `replaceProperties` 里漏了某个占位符，对照元数据文件补齐
- Java 工具链下载失败 → 手动装 JDK 25 并设 `JAVA_HOME`
- `net.minecraftforge.gradle` 插件版本区间不匹配 → 放宽为 `'[7.0,8)'`

- [ ] **Step 14: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add -A
git commit -m "build: 搭建三平台工程骨架，构建通过

gradle 多模块结构（common + fabric/forge/neoforge），MC 26.3 +
Forge 66.0.2 + NeoForge 26.3.0.8-beta + Fabric 0.19.5。
产物命名 shyFrpc-{loader}-26.3-2610.1.0.jar。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 2: FrpcYaml —— 极简 YAML 编解码

**Files:**
- Create: `src/main/java/io/github/cn3shy/shyfrpc/FrpcYaml.java`
- Test: `fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcYamlTest.java`

- [ ] **Step 1: 写失败的测试**

`fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcYamlTest.java`：

```java
package io.github.cn3shy.shyfrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FrpcYamlTest {

	@Test
	void writesAndReadsBackDefaultValues() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("app", "");
		values.put("toml", "");
		values.put("mode", "default");
		values.put("auto-start", "false");

		String text = FrpcYaml.write(values);
		assertEquals(values, FrpcYaml.read(text));
	}

	@Test
	void roundTripsWindowsPathWithBackslashes() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("app", "C:\\Program Files\\frpc\\frpc.exe");

		String text = FrpcYaml.write(values);
		assertEquals(values, FrpcYaml.read(text));
	}

	@Test
	void roundTripsValueWithDoubleQuotes() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("toml", "C:\\a \"b\" c\\frpc.toml");

		String text = FrpcYaml.write(values);
		assertEquals(values, FrpcYaml.read(text));
	}

	@Test
	void roundTripsChineseValue() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("toml", "D:\\我的服务器\\frpc.toml");

		String text = FrpcYaml.write(values);
		assertEquals(values, FrpcYaml.read(text));
	}

	@Test
	void readsBareScalarsWrittenByHand() {
		String text = "app: C:\\frpc\\frpc.exe\nmode: sakura\nauto-start: true\n";
		Map<String, String> read = FrpcYaml.read(text);

		assertEquals("C:\\frpc\\frpc.exe", read.get("app"));
		assertEquals("sakura", read.get("mode"));
		assertEquals("true", read.get("auto-start"));
	}

	@Test
	void readsBareScalarsWrappedInQuotes() {
		String text = "app: 'C:\\frpc\\frpc.exe'\ntoml: \"a b.toml\"\n";
		Map<String, String> read = FrpcYaml.read(text);

		assertEquals("C:\\frpc\\frpc.exe", read.get("app"));
		assertEquals("a b.toml", read.get("toml"));
	}

	@Test
	void skipsCommentsAndBlankLinesButKeepsUnknownKeys() {
		String text = "# 这是注释\n\napp: \"\"\n   \nunknown-key: whatever\n";
		Map<String, String> read = FrpcYaml.read(text);

		// 未知键由 FrpcConfig.apply 负责忽略，FrpcYaml 这一层原样返回
		assertEquals(2, read.size());
		assertEquals("", read.get("app"));
		assertEquals("whatever", read.get("unknown-key"));
	}

	@Test
	void returnsEmptyMapForEmptyOrCommentOnlyInput() {
		assertTrue(FrpcYaml.read("").isEmpty());
		assertTrue(FrpcYaml.read("# 只有注释\n").isEmpty());
	}

	@Test
	void toleratesMalformedLinesWithoutThrowing() {
		String text = "app\nnot a kv line\n: novalue\napp: \"unterminated\ntoml: ok\n";
		Map<String, String> read = FrpcYaml.read(text);

		// "not a kv line" 与 "app" 不匹配键值模式，被跳过；": novalue" 同理
		assertEquals("ok", read.get("toml"));
	}

	@Test
	void emptyValueParsesAsEmptyString() {
		Map<String, String> read = FrpcYaml.read("app: \n");

		assertTrue(read.containsKey("app"));
		assertEquals("", read.get("app"));
	}
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:test --tests '*FrpcYamlTest*' --no-daemon 2>&1 | tail -30
```

Expected: 编译失败，`cannot find symbol: class FrpcYaml`。

**如果此时报的是找不到 Minecraft 类或 JUnit 类**，说明 Loom 没把依赖放进 test 源集。解决办法是在 `fabric/build.gradle` 的 `dependencies` 块里补一行：

```groovy
  testImplementation sourceSets.main.output
```

然后重跑。

- [ ] **Step 3: 写实现**

`src/main/java/io/github/cn3shy/shyfrpc/FrpcYaml.java`：

```java
package io.github.cn3shy.shyfrpc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * 极简 YAML 编解码器，只处理扁平的 {@code key: value} 结构。
 *
 * <p>写出时值一律用 JSON 字符串语法——YAML 的双引号标量与 JSON 字符串语法完全一致，
 * 因此可以直接复用 Gson 处理引号、反斜杠、换行、非 ASCII 的转义，无需自己实现。
 *
 * <p>读取时兼容两种写法：本类写出的带引号形式，以及用户手工编辑的裸标量。
 */
public final class FrpcYaml {
	private static final Gson GSON = new Gson();

	/** 键允许字母、数字、下划线、连字符；值取整行剩余部分。 */
	private static final Pattern LINE = Pattern.compile("^\\s*([A-Za-z0-9_-]+)\\s*:\\s*(.*)$");

	private FrpcYaml() {
	}

	/**
	 * 把键值对序列化为 YAML 文本，每行一个键，顺序与 Map 迭代顺序一致。
	 */
	public static String write(Map<String, String> values) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(GSON.toJson(entry.getValue())).append('\n');
		}
		return sb.toString();
	}

	/**
	 * 解析 YAML 文本。无法识别的行被静默跳过，未知键照常返回（由调用方决定是否使用）。
	 */
	public static Map<String, String> read(String text) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String raw : text.split("\n", -1)) {
			String trimmed = raw.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			Matcher matcher = LINE.matcher(raw);
			if (!matcher.matches()) {
				continue;
			}
			out.put(matcher.group(1), parseValue(matcher.group(2)));
		}
		return out;
	}

	private static String parseValue(String raw) {
		String value = raw.strip();
		if (value.isEmpty()) {
			return "";
		}
		if (value.startsWith("\"")) {
			try {
				String parsed = GSON.fromJson(value, String.class);
				return parsed == null ? "" : parsed;
			} catch (JsonSyntaxException e) {
				// 引号未闭合等畸形内容：退回原样返回，交由调用方判定为非法值
				return value;
			}
		}
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:test --tests '*FrpcYamlTest*' --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`，10 个测试全过。

- [ ] **Step 5: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add src/main/java/io/github/cn3shy/shyfrpc/FrpcYaml.java fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcYamlTest.java
git commit -m "feat: 添加扁平键值对的极简 YAML 编解码

零第三方依赖实现：写出用 JSON 字符串语法转义（YAML 双引号标量与
JSON 字符串语法一致，直接复用 Gson），读取兼容手写的裸标量。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 3: FrpcMode 与 FrpcConfig —— 配置模型与路径解析

**Files:**
- Create: `src/main/java/io/github/cn3shy/shyfrpc/FrpcMode.java`
- Create: `src/main/java/io/github/cn3shy/shyfrpc/FrpcConfig.java`
- Test: `fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcConfigTest.java`

- [ ] **Step 1: 写失败的测试**

`fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcConfigTest.java`：

```java
package io.github.cn3shy.shyfrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrpcConfigTest {

	@Test
	void defaultsAreEmptyAndBetaSafe() {
		FrpcConfig cfg = new FrpcConfig();

		assertEquals("", cfg.app);
		assertEquals("", cfg.toml);
		assertEquals(FrpcMode.DEFAULT, cfg.mode);
		assertEquals("", cfg.sakuraToken);
		assertEquals("", cfg.sakuraTunnel);
		assertFalse(cfg.autoStart);
	}

	@Test
	void modeParsesFromIdCaseInsensitively() {
		assertEquals(FrpcMode.DEFAULT, FrpcMode.fromId("default"));
		assertEquals(FrpcMode.SAKURA, FrpcMode.fromId("SAKURA"));
		assertEquals(FrpcMode.DEFAULT, FrpcMode.fromId("nonsense"));
		assertEquals(FrpcMode.DEFAULT, FrpcMode.fromId(""));
		assertEquals(FrpcMode.DEFAULT, FrpcMode.fromId(null));
	}

	@Test
	void modeIdIsLowercase() {
		assertEquals("default", FrpcMode.DEFAULT.id());
		assertEquals("sakura", FrpcMode.SAKURA.id());
	}

	@Test
	void resolvesAbsolutePathUnchanged(@TempDir Path gameDir) {
		Path absolute = gameDir.resolve("elsewhere").resolve("frpc.exe");

		assertEquals(absolute, FrpcConfig.resolvePath(gameDir, absolute.toString()));
	}

	@Test
	void resolvesRelativePathAgainstGameDir(@TempDir Path gameDir) {
		assertEquals(gameDir.resolve("frpc.exe"), FrpcConfig.resolvePath(gameDir, "frpc.exe"));
		assertEquals(gameDir.resolve("sub").resolve("frpc.toml"), FrpcConfig.resolvePath(gameDir, "sub/frpc.toml"));
	}

	@Test
	void resolvesNestedRelativePathWithBackslashes(@TempDir Path gameDir) {
		assertEquals(gameDir.resolve("a").resolve("b.toml"), FrpcConfig.resolvePath(gameDir, "a\\b.toml"));
	}

	@Test
	void saveThenLoadRoundTripsAllFields(@TempDir Path gameDir) {
		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "C:\\Program Files\\frpc\\frpc.exe";
		cfg.toml = "config/minecraft.toml";
		cfg.mode = FrpcMode.SAKURA;
		cfg.sakuraToken = "55qt9v9oofm19s1gfjzpkcz9o5ry4qmv";
		cfg.sakuraTunnel = "27811507";
		cfg.autoStart = true;
		cfg.save(gameDir);

		FrpcConfig loaded = FrpcConfig.load(gameDir);

		assertEquals(cfg.app, loaded.app);
		assertEquals(cfg.toml, loaded.toml);
		assertEquals(FrpcMode.SAKURA, loaded.mode);
		assertEquals(cfg.sakuraToken, loaded.sakuraToken);
		assertEquals(cfg.sakuraTunnel, loaded.sakuraTunnel);
		assertTrue(loaded.autoStart);
	}

	@Test
	void loadReturnsDefaultsWhenFileMissing(@TempDir Path gameDir) {
		FrpcConfig loaded = FrpcConfig.load(gameDir);

		assertEquals("", loaded.app);
		assertEquals(FrpcMode.DEFAULT, loaded.mode);
		assertFalse(loaded.autoStart);
	}

	@Test
	void loadReadsHandEditedBareScalars(@TempDir Path gameDir) throws IOException {
		Path file = FrpcConfig.configFile(gameDir);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "app: C:\\frpc\\frpc.exe\nmode: sakura\nauto-start: true\n",
				StandardCharsets.UTF_8);

		FrpcConfig loaded = FrpcConfig.load(gameDir);

		assertEquals("C:\\frpc\\frpc.exe", loaded.app);
		assertEquals(FrpcMode.SAKURA, loaded.mode);
		assertTrue(loaded.autoStart);
	}

	@Test
	void loadFallsBackToDefaultsOnGarbageWithoutDeletingFile(@TempDir Path gameDir) throws IOException {
		Path file = FrpcConfig.configFile(gameDir);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "!!! this is not yaml at all\n\x00\x01", StandardCharsets.UTF_8);

		FrpcConfig loaded = FrpcConfig.load(gameDir);

		assertEquals("", loaded.app);
		assertTrue(Files.exists(file), "损坏的配置文件不应被删除");
	}

	@Test
	void loadIgnoresUnknownKeys(@TempDir Path gameDir) throws IOException {
		Path file = FrpcConfig.configFile(gameDir);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "app: frpc.exe\nfuture-key: something\n", StandardCharsets.UTF_8);

		FrpcConfig loaded = FrpcConfig.load(gameDir);

		assertEquals("frpc.exe", loaded.app);
	}

	@Test
	void resolveAppAndTomlUseGameDir(@TempDir Path gameDir) {
		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "frpc.exe";
		cfg.toml = "minecraft.toml";

		assertEquals(gameDir.resolve("frpc.exe"), cfg.resolveApp(gameDir));
		assertEquals(gameDir.resolve("minecraft.toml"), cfg.resolveToml(gameDir));
	}

	@Test
	void blankPathsResolveToNull(@TempDir Path gameDir) {
		FrpcConfig cfg = new FrpcConfig();

		assertNull(cfg.resolveApp(gameDir));
		assertNull(cfg.resolveToml(gameDir));
	}
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:test --tests '*FrpcConfigTest*' --no-daemon 2>&1 | tail -30
```

Expected: 编译失败，`cannot find symbol: class FrpcConfig`。

- [ ] **Step 3: 写 `FrpcMode.java`**

`src/main/java/io/github/cn3shy/shyfrpc/FrpcMode.java`：

```java
package io.github.cn3shy.shyfrpc;

import java.util.Locale;

/** frpc 的启动模式。 */
public enum FrpcMode {
	/** 用本地的 frpc 配置文件启动：{@code frpc -c <toml>}。 */
	DEFAULT,
	/** 用 SakuraFrp 的用户 token 与隧道 ID 启动：{@code frpc -f <token>:<tunnel>}。 */
	SAKURA;

	/** 写入配置文件时用的标识符（小写）。 */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** 从配置文件读到的字符串还原枚举，无法识别时回退到 {@link #DEFAULT}。 */
	public static FrpcMode fromId(String id) {
		if (id != null) {
			for (FrpcMode mode : values()) {
				if (mode.id().equalsIgnoreCase(id.strip())) {
					return mode;
				}
			}
		}
		return DEFAULT;
	}
}
```

- [ ] **Step 4: 写 `FrpcConfig.java`**

`src/main/java/io/github/cn3shy/shyfrpc/FrpcConfig.java`：

```java
package io.github.cn3shy.shyfrpc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ShyFrpc 的配置，持久化在 {@code <游戏目录>/config/shyFrpc/config.yaml}。
 *
 * <p>本类刻意不依赖任何 Minecraft 类，以便单元测试。
 */
public class FrpcConfig {
	private static final Logger LOGGER = LogManager.getLogger("ShyFrpc");

	public static final String KEY_APP = "app";
	public static final String KEY_TOML = "toml";
	public static final String KEY_MODE = "mode";
	public static final String KEY_SAKURA_TOKEN = "sakura-token";
	public static final String KEY_SAKURA_TUNNEL = "sakura-tunnel";
	public static final String KEY_AUTO_START = "auto-start";

	/** frpc 可执行文件路径。绝对路径原样使用，相对路径按游戏目录解析。 */
	public String app = "";

	/** frpc 配置文件（toml）路径。解析规则同 {@link #app}。 */
	public String toml = "";

	/** 启动模式。 */
	public FrpcMode mode = FrpcMode.DEFAULT;

	/** SakuraFrp 用户 token。 */
	public String sakuraToken = "";

	/** SakuraFrp 隧道 ID。 */
	public String sakuraTunnel = "";

	/** 开启局域网房间时是否自动启动 frpc。 */
	public boolean autoStart = false;

	/** 配置文件路径。 */
	public static Path configFile(Path gameDir) {
		return gameDir.resolve("config").resolve("shyFrpc").resolve("config.yaml");
	}

	/**
	 * 解析一个可能是相对路径的路径字符串。空字符串返回 {@code null}。
	 */
	public static Path resolvePath(Path gameDir, String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String trimmed = raw.strip();
		Path path = gameDir.getFileSystem().getPath(trimmed);
		return path.isAbsolute() ? path.normalize() : gameDir.resolve(path).normalize();
	}

	/** {@link #app} 解析为绝对路径，未设置时返回 {@code null}。 */
	public Path resolveApp(Path gameDir) {
		return resolvePath(gameDir, this.app);
	}

	/** {@link #toml} 解析为绝对路径，未设置时返回 {@code null}。 */
	public Path resolveToml(Path gameDir) {
		return resolvePath(gameDir, this.toml);
	}

	/** 从磁盘读取配置；文件不存在或无法解析时返回全默认值。 */
	public static FrpcConfig load(Path gameDir) {
		Path file = configFile(gameDir);
		FrpcConfig cfg = new FrpcConfig();
		if (!Files.isRegularFile(file)) {
			return cfg;
		}
		try {
			cfg.apply(FrpcYaml.read(Files.readString(file, StandardCharsets.UTF_8)));
		} catch (IOException | RuntimeException e) {
			// 刻意不删除用户的配置文件：手写的配置被静默删掉比读失败更糟
			LOGGER.warn("读取配置失败，本次使用默认值: {}", file, e);
		}
		return cfg;
	}

	/** 写回磁盘，父目录不存在时自动创建。 */
	public void save(Path gameDir) {
		Path file = configFile(gameDir);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, FrpcYaml.write(toMap()), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("保存配置失败: {}", file, e);
		}
	}

	/** 序列化为待写入的键值对。 */
	public Map<String, String> toMap() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put(KEY_APP, this.app);
		map.put(KEY_TOML, this.toml);
		map.put(KEY_MODE, this.mode.id());
		map.put(KEY_SAKURA_TOKEN, this.sakuraToken);
		map.put(KEY_SAKURA_TUNNEL, this.sakuraTunnel);
		map.put(KEY_AUTO_START, Boolean.toString(this.autoStart));
		return map;
	}

	/** 用解析出的键值对覆盖本实例的字段，未知键忽略，缺失键保持原值。 */
	public void apply(Map<String, String> map) {
		this.app = map.getOrDefault(KEY_APP, this.app);
		this.toml = map.getOrDefault(KEY_TOML, this.toml);
		this.mode = FrpcMode.fromId(map.getOrDefault(KEY_MODE, this.mode.id()));
		this.sakuraToken = map.getOrDefault(KEY_SAKURA_TOKEN, this.sakuraToken);
		this.sakuraTunnel = map.getOrDefault(KEY_SAKURA_TUNNEL, this.sakuraTunnel);
		this.autoStart = Boolean.parseBoolean(map.getOrDefault(KEY_AUTO_START, Boolean.toString(this.autoStart)));
	}
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:test --tests '*FrpcConfigTest*' --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`，13 个测试全过。

- [ ] **Step 6: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add src/main/java/io/github/cn3shy/shyfrpc/FrpcMode.java src/main/java/io/github/cn3shy/shyfrpc/FrpcConfig.java fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcConfigTest.java
git commit -m "feat: 添加配置模型与路径解析

FrpcMode 枚举（default/sakura）、FrpcConfig 的读写与路径解析。
路径规则统一：绝对路径原样使用，相对路径按游戏目录解析。
解析失败降级为默认值但不删除用户文件。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 4: FrpcLauncher —— 启动命令构造与前置校验

**Files:**
- Create: `src/main/java/io/github/cn3shy/shyfrpc/FrpcLauncher.java`
- Test: `fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcLauncherTest.java`

- [ ] **Step 1: 写失败的测试**

`fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcLauncherTest.java`：

```java
package io.github.cn3shy.shyfrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrpcLauncherTest {

	private static FrpcConfig validDefaultConfig(Path gameDir) throws IOException {
		Files.writeString(gameDir.resolve("frpc.exe"), "stub");
		Files.writeString(gameDir.resolve("minecraft.toml"), "stub");

		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "frpc.exe";
		cfg.toml = "minecraft.toml";
		return cfg;
	}

	private static FrpcConfig validSakuraConfig(Path gameDir) throws IOException {
		Files.writeString(gameDir.resolve("frpc.exe"), "stub");

		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "frpc.exe";
		cfg.mode = FrpcMode.SAKURA;
		cfg.sakuraToken = "55qt9v9oofm19s1gfjzpkcz9o5ry4qmv";
		cfg.sakuraTunnel = "27811507";
		return cfg;
	}

	@Test
	void defaultModeBuildsDashCCommand(@TempDir Path gameDir) throws IOException {
		FrpcConfig cfg = validDefaultConfig(gameDir);

		List<String> command = FrpcLauncher.buildCommand(cfg, gameDir);

		assertEquals(List.of(
				gameDir.resolve("frpc.exe").toString(),
				"-c",
				gameDir.resolve("minecraft.toml").toString()), command);
	}

	@Test
	void sakuraModeBuildsDashFCommand(@TempDir Path gameDir) throws IOException {
		FrpcConfig cfg = validSakuraConfig(gameDir);

		List<String> command = FrpcLauncher.buildCommand(cfg, gameDir);

		assertEquals(List.of(
				gameDir.resolve("frpc.exe").toString(),
				"-f",
				"55qt9v9oofm19s1gfjzpkcz9o5ry4qmv:27811507"), command);
	}

	@Test
	void defaultModeIgnoresSakuraCredentials(@TempDir Path gameDir) throws IOException {
		FrpcConfig cfg = validDefaultConfig(gameDir);
		cfg.sakuraToken = "leftover-token";
		cfg.sakuraTunnel = "12345";

		List<String> command = FrpcLauncher.buildCommand(cfg, gameDir);

		assertEquals("-c", command.get(1));
	}

	@Test
	void sakuraModeIgnoresToml(@TempDir Path gameDir) throws IOException {
		FrpcConfig cfg = validSakuraConfig(gameDir);
		cfg.toml = "minecraft.toml";

		List<String> command = FrpcLauncher.buildCommand(cfg, gameDir);

		assertEquals("-f", command.get(1));
	}

	@Test
	void absoluteAppPathIsUsedAsIs(@TempDir Path gameDir) throws IOException {
		Path exe = gameDir.resolve("frpc.exe");
		Files.writeString(exe, "stub");
		Files.writeString(gameDir.resolve("minecraft.toml"), "stub");

		FrpcConfig cfg = new FrpcConfig();
		cfg.app = exe.toString();
		cfg.toml = "minecraft.toml";

		assertEquals(exe.toString(), FrpcLauncher.buildCommand(cfg, gameDir).get(0));
	}

	@Test
	void validatePassesForCompleteDefaultConfig(@TempDir Path gameDir) throws IOException {
		assertNull(FrpcLauncher.validate(validDefaultConfig(gameDir), gameDir));
	}

	@Test
	void validatePassesForCompleteSakuraConfig(@TempDir Path gameDir) throws IOException {
		assertNull(FrpcLauncher.validate(validSakuraConfig(gameDir), gameDir));
	}

	@Test
	void validateRejectsMissingApp(@TempDir Path gameDir) {
		ValidationError error = FrpcLauncher.validate(new FrpcConfig(), gameDir);

		assertNotNull(error);
		assertEquals("shyfrpc.command.on.error.no-app", error.translationKey());
		assertNull(error.detail());
	}

	@Test
	void validateRejectsAppThatDoesNotExist(@TempDir Path gameDir) {
		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "nowhere/frpc.exe";
		cfg.toml = "minecraft.toml";

		ValidationError error = FrpcLauncher.validate(cfg, gameDir);

		assertNotNull(error);
		assertEquals("shyfrpc.command.on.error.app-not-found", error.translationKey());
		assertEquals(gameDir.resolve("nowhere").resolve("frpc.exe").toString(), error.detail());
	}

	@Test
	void validateRejectsMissingTomlInDefaultMode(@TempDir Path gameDir) throws IOException {
		Files.writeString(gameDir.resolve("frpc.exe"), "stub");

		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "frpc.exe";

		ValidationError error = FrpcLauncher.validate(cfg, gameDir);

		assertNotNull(error);
		assertEquals("shyfrpc.command.on.error.no-toml", error.translationKey());
	}

	@Test
	void validateRejectsTomlThatDoesNotExist(@TempDir Path gameDir) throws IOException {
		Files.writeString(gameDir.resolve("frpc.exe"), "stub");

		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "frpc.exe";
		cfg.toml = "missing.toml";

		ValidationError error = FrpcLauncher.validate(cfg, gameDir);

		assertNotNull(error);
		assertEquals("shyfrpc.command.on.error.toml-not-found", error.translationKey());
		assertEquals(gameDir.resolve("missing.toml").toString(), error.detail());
	}

	@Test
	void validateRejectsMissingSakuraCredentials(@TempDir Path gameDir) throws IOException {
		Files.writeString(gameDir.resolve("frpc.exe"), "stub");

		FrpcConfig cfg = new FrpcConfig();
		cfg.app = "frpc.exe";
		cfg.mode = FrpcMode.SAKURA;
		cfg.sakuraToken = "token-only";

		ValidationError error = FrpcLauncher.validate(cfg, gameDir);

		assertNotNull(error);
		assertEquals("shyfrpc.command.on.error.no-sakura", error.translationKey());
	}

	@Test
	void validateDoesNotRequireTomlInSakuraMode(@TempDir Path gameDir) throws IOException {
		assertNull(FrpcLauncher.validate(validSakuraConfig(gameDir), gameDir));
	}
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:test --tests '*FrpcLauncherTest*' --no-daemon 2>&1 | tail -30
```

Expected: 编译失败，`cannot find symbol: class FrpcLauncher`。

- [ ] **Step 3: 写 `ValidationError.java`**

`src/main/java/io/github/cn3shy/shyfrpc/ValidationError.java`：

```java
package io.github.cn3shy.shyfrpc;

/**
 * 启动前置校验的失败结果。
 *
 * @param translationKey 本地化键，直接交给 {@code Component.translatable}
 * @param detail 可选参数（通常是解析后的路径），无参数时为 {@code null}
 */
public record ValidationError(String translationKey, String detail) {
}
```

- [ ] **Step 4: 写 `FrpcLauncher.java`**

`src/main/java/io/github/cn3shy/shyfrpc/FrpcLauncher.java`：

```java
package io.github.cn3shy.shyfrpc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 构造 frpc 的启动命令并做前置校验。
 *
 * <p>本类不依赖任何 Minecraft 类，也不启动进程——真正的进程管理在
 * {@link FrpcProcess}。这样「命令长什么样」和「校验什么」是纯函数，可单元测试。
 */
public final class FrpcLauncher {
	public static final String ERROR_NO_APP = "shyfrpc.command.on.error.no-app";
	public static final String ERROR_APP_NOT_FOUND = "shyfrpc.command.on.error.app-not-found";
	public static final String ERROR_NO_TOML = "shyfrpc.command.on.error.no-toml";
	public static final String ERROR_TOML_NOT_FOUND = "shyfrpc.command.on.error.toml-not-found";
	public static final String ERROR_NO_SAKURA = "shyfrpc.command.on.error.no-sakura";

	private FrpcLauncher() {
	}

	/**
	 * 构造启动命令。参数形式完全固定，因此不需要命令行分词器。
	 *
	 * <ul>
	 *   <li>{@code mode=default} → {@code [<app>, "-c", <toml>]}</li>
	 *   <li>{@code mode=sakura} → {@code [<app>, "-f", "<token>:<tunnel>"]}</li>
	 * </ul>
	 *
	 * <p>路径均已解析为绝对路径。调用前应先通过 {@link #validate} 校验。
	 */
	public static List<String> buildCommand(FrpcConfig cfg, Path gameDir) {
		List<String> command = new ArrayList<>(3);
		Path app = cfg.resolveApp(gameDir);
		command.add(app == null ? "" : app.toString());

		if (cfg.mode == FrpcMode.SAKURA) {
			command.add("-f");
			command.add(cfg.sakuraToken + ":" + cfg.sakuraTunnel);
		} else {
			command.add("-c");
			Path toml = cfg.resolveToml(gameDir);
			command.add(toml == null ? "" : toml.toString());
		}
		return command;
	}

	/**
	 * 校验当前配置能否启动。返回 {@code null} 表示可以启动，否则返回失败原因。
	 */
	public static ValidationError validate(FrpcConfig cfg, Path gameDir) {
		if (cfg.app == null || cfg.app.isBlank()) {
			return new ValidationError(ERROR_NO_APP, null);
		}
		Path app = cfg.resolveApp(gameDir);
		if (app == null || !Files.isRegularFile(app)) {
			return new ValidationError(ERROR_APP_NOT_FOUND, app == null ? cfg.app : app.toString());
		}

		if (cfg.mode == FrpcMode.SAKURA) {
			if (cfg.sakuraToken == null || cfg.sakuraToken.isBlank()
					|| cfg.sakuraTunnel == null || cfg.sakuraTunnel.isBlank()) {
				return new ValidationError(ERROR_NO_SAKURA, null);
			}
			return null;
		}

		if (cfg.toml == null || cfg.toml.isBlank()) {
			return new ValidationError(ERROR_NO_TOML, null);
		}
		Path toml = cfg.resolveToml(gameDir);
		if (toml == null || !Files.isRegularFile(toml)) {
			return new ValidationError(ERROR_TOML_NOT_FOUND, toml == null ? cfg.toml : toml.toString());
		}
		return null;
	}
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:test --tests '*FrpcLauncherTest*' --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`，13 个测试全过。

- [ ] **Step 6: 跑全部测试**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:test --no-daemon 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`，共 36 个测试全过。

- [ ] **Step 7: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add src/main/java/io/github/cn3shy/shyfrpc/FrpcLauncher.java src/main/java/io/github/cn3shy/shyfrpc/ValidationError.java fabric/src/test/java/io/github/cn3shy/shyfrpc/FrpcLauncherTest.java
git commit -m "feat: 添加启动命令构造与前置校验

default 模式拼 -c <toml>，sakura 模式拼 -f <token>:<隧道ID>。
校验顺序：app 非空 → app 文件存在 → 按模式校验 toml 或 sakura 凭据。
纯函数，不碰 Minecraft，可单测。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 5: FrpcProcess —— 子进程生命周期与输出转发

本任务没有单元测试：它全程跟真实进程和 Minecraft 客户端交互，无法在 JVM 单测里有意义地覆盖。验证方式是 Task 9 接线后跑真客户端，以及一个独立的手工冒烟脚本。

**Files:**
- Create: `src/main/java/io/github/cn3shy/shyfrpc/FrpcProcess.java`

- [ ] **Step 1: 写 `FrpcProcess.java`**

`src/main/java/io/github/cn3shy/shyfrpc/FrpcProcess.java`：

```java
package io.github.cn3shy.shyfrpc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * frpc 子进程的生命周期管理与输出转发。
 *
 * <p>同一时刻最多只有一个 frpc 进程，因此状态用静态字段保存。
 */
public final class FrpcProcess {
	/** 进程的启动来源，决定「关闭局域网房间」时是否要停掉它。 */
	public enum Source {
		/** 玩家手动执行 /shyfrpc on。 */
		MANUAL,
		/** auto-start 在局域网房间开启时自动拉起。 */
		AUTO
	}

	/** 优雅终止的等待时长。超时后强杀。 */
	private static final long GRACEFUL_KILL_TIMEOUT_SECONDS = 3L;

	private static Process process;
	private static Source source;
	private static long startedAtMillis;
	private static boolean shutdownHookInstalled;

	private FrpcProcess() {
	}

	/** frpc 是否正在运行。 */
	public static synchronized boolean isRunning() {
		return process != null && process.isAlive();
	}

	/** 当前进程的启动来源；未运行时返回 {@code null}。 */
	public static synchronized Source currentSource() {
		return process == null ? null : source;
	}

	/** 当前进程的 PID；未运行时返回 {@code -1}。 */
	public static synchronized long currentPid() {
		return process == null ? -1L : process.pid();
	}

	/** 当前进程的启动时刻（{@code System.currentTimeMillis()}）；未运行时返回 {@code 0}。 */
	public static synchronized long startedAtMillis() {
		return process == null ? 0L : startedAtMillis;
	}

	/**
	 * 启动 frpc。调用前必须先通过 {@link FrpcLauncher#validate}。
	 *
	 * @param cfg     配置
	 * @param gameDir 游戏目录，同时用作进程的工作目录
	 * @param origin  启动来源
	 * @throws IOException 进程创建失败（可执行文件缺失、无执行权限等）
	 */
	public static synchronized void start(FrpcConfig cfg, Path gameDir, Source origin) throws IOException {
		if (isRunning()) {
			return;
		}

		List<String> command = FrpcLauncher.buildCommand(cfg, gameDir);
		ShyFrpcUnit.LOGGER.info("启动 frpc: {}", String.join(" ", command));

		ProcessBuilder builder = new ProcessBuilder(command);
		// 工作目录设为游戏目录，让 toml 内部的相对引用也能正确解析
		builder.directory(gameDir.toFile());

		Process started = builder.start();
		process = started;
		source = origin;
		startedAtMillis = System.currentTimeMillis();

		startPump(started.getInputStream(), "out");
		startPump(started.getErrorStream(), "err");
		startWaiter(started);

		installShutdownHook();
		toChat(Component.translatable("shyfrpc.frpc.started"));
	}

	/**
	 * 停止 frpc。立即发出终止信号，但不阻塞调用线程——等待与强杀在后台线程完成，
	 * 避免卡住命令执行的服务器线程。
	 */
	public static synchronized void stop() {
		Process doomed = process;
		if (doomed == null) {
			return;
		}
		process = null;
		source = null;

		doomed.destroy();

		Thread reaper = new Thread(() -> {
			try {
				if (!doomed.waitFor(GRACEFUL_KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					doomed.destroyForcibly();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				doomed.destroyForcibly();
			}
		}, "shyfrpc-reaper");
		reaper.setDaemon(true);
		reaper.start();

		toChat(Component.translatable("shyfrpc.frpc.stopped"));
	}

	/**
	 * 读取子进程的一路输出流，逐行转发到聊天栏与日志。
	 */
	private static void startPump(InputStream stream, String streamName) {
		Thread pump = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					ShyFrpcUnit.LOGGER.info("[frpc] {}", line);
					toChat(Component.translatable("shyfrpc.frpc.output", line));
				}
			} catch (IOException e) {
				// 进程被终止时流会中断，属正常情况
				ShyFrpcUnit.LOGGER.debug("frpc {} 流已关闭", streamName, e);
			}
		}, "shyfrpc-frpc-" + streamName);
		pump.setDaemon(true);
		pump.start();
	}

	/**
	 * 等待子进程结束，处理「frpc 自己退出」的情况（崩溃、认证失败等）。
	 */
	private static void startWaiter(Process watched) {
		Thread waiter = new Thread(() -> {
			int exitCode;
			try {
				exitCode = watched.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			boolean unexpected;
			synchronized (FrpcProcess.class) {
				// 若已被 stop() 主动清理，process 已经不是这个进程了，属正常退出
				unexpected = process == watched;
				if (unexpected) {
					process = null;
					source = null;
				}
			}

			ShyFrpcUnit.LOGGER.info("frpc 退出，退出码 {}", exitCode);
			if (unexpected) {
				toChat(Component.translatable("shyfrpc.frpc.exited-unexpectedly", exitCode));
			}
		}, "shyfrpc-waiter");
		waiter.setDaemon(true);
		waiter.start();
	}

	/**
	 * 注册 JVM 关闭钩子，兜底清理进程，避免游戏退出后残留僵尸 frpc。
	 */
	private static void installShutdownHook() {
		if (shutdownHookInstalled) {
			return;
		}
		shutdownHookInstalled = true;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			Process lingering = process;
			if (lingering != null && lingering.isAlive()) {
				lingering.destroyForcibly();
			}
		}, "shyfrpc-shutdown"));
	}

	/**
	 * 把消息发到玩家聊天栏。聊天栏只能在主线程更新，因此这里投递回主线程；
	 * 玩家不在世界中（主菜单）时静默丢弃。
	 */
	private static void toChat(Component message) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.displayClientMessage(message, false);
			}
		});
	}
}
```

- [ ] **Step 2: 编译验证**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`。

编译失败时的排查方向：

- `Minecraft.execute` / `Minecraft.player` / `displayClientMessage` 签名不符 → 打开
  `~/.gradle/caches/` 下 MC 26.3 的 jar，或直接查 `net.minecraft.client.Minecraft` 与
  `net.minecraft.world.entity.player.Player`（`displayClientMessage` 实际声明在
  `net.minecraft.client.player.LocalPlayer` 上）
- `Process.pid()` 在 Java 9+ 才有，工具链是 25，没问题

- [ ] **Step 3: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add src/main/java/io/github/cn3shy/shyfrpc/FrpcProcess.java
git commit -m "feat: 添加 frpc 子进程管理与输出转发

stdout/stderr 各起一个守护线程逐行转发到聊天栏与日志。
停止时非阻塞：立即 destroy，后台线程等 3 秒后强杀。
注册 ShutdownHook 兜底清理，避免游戏退出后残留进程。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 6: ShyFrpcUnit —— 局域网检测与自动启停状态机

**Files:**
- Modify: `src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java`

- [ ] **Step 1: 用完整实现替换 `ShyFrpcUnit.java`**

`src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java`：

```java
package io.github.cn3shy.shyfrpc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 公共入口。三个平台加载器的入口类都调用这里，本类不导入任何平台特定类。
 *
 * <p>本 Mod 声明为纯客户端（Fabric {@code environment=client}，Forge/NeoForge
 * {@code clientSideOnly=true}），因此这里可以直接引用客户端类。
 */
public final class ShyFrpcUnit {
	public static final String MODID = "shyfrpc";

	/** 全 Mod 共用的 logger，输出到 logs/latest.log。 */
	public static final Logger LOGGER = LogManager.getLogger("ShyFrpc");

	/** 每多少 tick 检查一次局域网状态。20 tick = 1 秒。 */
	private static final int TICK_CHECK_INTERVAL = 20;

	private static int tickCounter;
	private static boolean lastPublished;

	private ShyFrpcUnit() {
	}

	/** 游戏目录，配置文件与相对路径都基于它解析。 */
	public static java.nio.file.Path gameDir() {
		return Minecraft.getInstance().gameDirectory.toPath();
	}

	/** 从磁盘读取当前配置。每次调用都重新读，因此用户手改配置文件后无需重启。 */
	public static FrpcConfig currentConfig() {
		return FrpcConfig.load(gameDir());
	}

	/**
	 * 由各平台的客户端 tick 事件调用。每秒检查一次局域网房间状态，
	 * 只在状态翻转时触发动作。
	 */
	public static void onClientTick(Minecraft client) {
		if (++tickCounter < TICK_CHECK_INTERVAL) {
			return;
		}
		tickCounter = 0;

		boolean published = isPublished(client);

		if (published != lastPublished) {
			lastPublished = published;
			if (published) {
				onLanOpened();
			} else {
				onLanClosed();
			}
			return;
		}

		// 不在任何世界时清理残留进程（覆盖「手动 on 之后退回主菜单」的情况）
		if (!published && client.getSingleplayerServer() == null && FrpcProcess.isRunning()) {
			FrpcProcess.stop();
		}
	}

	private static boolean isPublished(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		return server != null && server.isPublished();
	}

	private static void onLanOpened() {
		FrpcConfig cfg = currentConfig();
		if (!cfg.autoStart) {
			return;
		}
		if (FrpcProcess.isRunning()) {
			return;
		}

		ValidationError error = FrpcLauncher.validate(cfg, gameDir());
		if (error != null) {
			// 自动启动失败不打扰玩家，只记日志——否则每次开房都弹一次错
			LOGGER.warn("auto-start 已开启但配置不完整，跳过自动启动: {}", error.translationKey());
			return;
		}

		try {
			FrpcProcess.start(cfg, gameDir(), FrpcProcess.Source.AUTO);
		} catch (Exception e) {
			LOGGER.error("auto-start 启动 frpc 失败", e);
		}
	}

	private static void onLanClosed() {
		if (FrpcProcess.isRunning()) {
			FrpcProcess.stop();
		}
	}
}
```

- [ ] **Step 2: 编译验证**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`。

若 `getSingleplayerServer()` 或 `isPublished()` 不存在，去 MC jar 里核对方法名
（参考项目 `mcwifipnp` 的 `Config.java` 与 `MixinIntegratedServer.java` 里用的是
`Minecraft.getInstance().getSingleplayerServer()` 和 `server.isPublished()`，
在同样的 26.3 上可用）。

- [ ] **Step 3: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java
git commit -m "feat: 添加局域网检测与自动启停状态机

每秒轮询一次 IntegratedServer.isPublished()，只在状态翻转时动作：
false→true 且有 auto-start 时启动 frpc，true→false 时无条件停止。
退回主菜单时清理残留进程。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 7: ShyFrpcCommand —— 命令树

**Files:**
- Create: `src/main/java/io/github/cn3shy/shyfrpc/commands/ShyFrpcCommand.java`

- [ ] **Step 1: 写 `ShyFrpcCommand.java`**

`src/main/java/io/github/cn3shy/shyfrpc/commands/ShyFrpcCommand.java`：

```java
package io.github.cn3shy.shyfrpc.commands;

import java.nio.file.Path;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.github.cn3shy.shyfrpc.FrpcConfig;
import io.github.cn3shy.shyfrpc.FrpcLauncher;
import io.github.cn3shy.shyfrpc.FrpcMode;
import io.github.cn3shy.shyfrpc.FrpcProcess;
import io.github.cn3shy.shyfrpc.ShyFrpcUnit;
import io.github.cn3shy.shyfrpc.ValidationError;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /shyfrpc} 命令树。
 */
public final class ShyFrpcCommand {
	private static final String MODE_DEFAULT = "default";
	private static final String MODE_SAKURA = "sakura";

	private ShyFrpcCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("shyfrpc")
				.requires(Commands.hasPermission(Commands.LEVEL_OWNERS));

		root.then(Commands.literal("set")
				.then(Commands.literal("app")
						.then(Commands.argument("path", StringArgumentType.greedyString())
								.executes(ctx -> setApp(ctx, StringArgumentType.getString(ctx, "path")))))
				.then(Commands.literal("toml")
						.then(Commands.argument("path", StringArgumentType.greedyString())
								.executes(ctx -> setToml(ctx, StringArgumentType.getString(ctx, "path")))))
				.then(Commands.literal("sakura")
						.then(Commands.argument("token", StringArgumentType.string())
								.then(Commands.argument("tunnel", StringArgumentType.string())
										.executes(ctx -> setSakura(ctx,
												StringArgumentType.getString(ctx, "token"),
												StringArgumentType.getString(ctx, "tunnel")))))));

		root.then(Commands.literal("mode")
				.then(Commands.argument("mode", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							builder.suggest(MODE_DEFAULT);
							builder.suggest(MODE_SAKURA);
							return builder.buildFuture();
						})
						.executes(ctx -> setMode(ctx, StringArgumentType.getString(ctx, "mode")))));

		root.then(Commands.literal("on").executes(ShyFrpcCommand::on));
		root.then(Commands.literal("off").executes(ShyFrpcCommand::off));

		root.then(Commands.literal("auto-start")
				.then(Commands.argument("value", BoolArgumentType.bool())
						.executes(ctx -> setAutoStart(ctx, BoolArgumentType.getBool(ctx, "value")))));

		root.then(Commands.literal("status").executes(ShyFrpcCommand::status));

		dispatcher.register(root);
	}

	// ---------- set ----------

	private static int setApp(CommandContext<CommandSourceStack> ctx, String rawPath) {
		FrpcConfig cfg = ShyFrpcUnit.currentConfig();
		cfg.app = cleanPath(rawPath);
		return saveAndReport(ctx, cfg, "shyfrpc.command.set.app.success", cfg.app);
	}

	private static int setToml(CommandContext<CommandSourceStack> ctx, String rawPath) {
		FrpcConfig cfg = ShyFrpcUnit.currentConfig();
		cfg.toml = cleanPath(rawPath);
		return saveAndReport(ctx, cfg, "shyfrpc.command.set.toml.success", cfg.toml);
	}

	private static int setSakura(CommandContext<CommandSourceStack> ctx, String token, String tunnel) {
		FrpcConfig cfg = ShyFrpcUnit.currentConfig();
		cfg.sakuraToken = token.strip();
		cfg.sakuraTunnel = tunnel.strip();
		return saveAndReport(ctx, cfg, "shyfrpc.command.set.sakura.success", cfg.sakuraTunnel);
	}

	private static int setMode(CommandContext<CommandSourceStack> ctx, String rawMode) {
		String normalized = rawMode.strip().toLowerCase(java.util.Locale.ROOT);
		if (!MODE_DEFAULT.equals(normalized) && !MODE_SAKURA.equals(normalized)) {
			ctx.getSource().sendFailure(Component.translatable("shyfrpc.command.mode.invalid", rawMode));
			return 0;
		}

		FrpcConfig cfg = ShyFrpcUnit.currentConfig();
		cfg.mode = FrpcMode.fromId(normalized);
		return saveAndReport(ctx, cfg, "shyfrpc.command.mode.success", cfg.mode.id());
	}

	private static int setAutoStart(CommandContext<CommandSourceStack> ctx, boolean value) {
		FrpcConfig cfg = ShyFrpcUnit.currentConfig();
		cfg.autoStart = value;
		cfg.save(ShyFrpcUnit.gameDir());

		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("shyfrpc.command.autoStart.success",
				Component.translatable(value ? "options.on" : "options.off")), false);

		// 已经在局域网房间里再打开 auto-start：立刻补启动一次
		if (value && !FrpcProcess.isRunning() && ShyFrpcUnit.isLanPublished()) {
			startFrpc(source, cfg, FrpcProcess.Source.AUTO, true);
		}
		return 1;
	}

	// ---------- on / off ----------

	private static int on(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		FrpcConfig cfg = ShyFrpcUnit.currentConfig();

		if (FrpcProcess.isRunning()) {
			source.sendFailure(Component.translatable("shyfrpc.command.on.already-running"));
			return 0;
		}
		return startFrpc(source, cfg, FrpcProcess.Source.MANUAL, false);
	}

	private static int startFrpc(CommandSourceStack source, FrpcConfig cfg, FrpcProcess.Source origin,
			boolean quiet) {
		ValidationError error = FrpcLauncher.validate(cfg, ShyFrpcUnit.gameDir());
		if (error != null) {
			if (!quiet) {
				source.sendFailure(error.detail() == null
						? Component.translatable(error.translationKey())
						: Component.translatable(error.translationKey(), error.detail()));
			}
			return 0;
		}

		try {
			FrpcProcess.start(cfg, ShyFrpcUnit.gameDir(), origin);
		} catch (Exception e) {
			ShyFrpcUnit.LOGGER.error("启动 frpc 失败", e);
			Component message = Component.translatable("shyfrpc.command.on.error.spawn-failed",
					String.valueOf(e.getMessage()));
			source.sendFailure(message);
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("shyfrpc.command.on.success"), false);
		return 1;
	}

	private static int off(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (!FrpcProcess.isRunning()) {
			source.sendFailure(Component.translatable("shyfrpc.command.off.not-running"));
			return 0;
		}
		FrpcProcess.stop();
		source.sendSuccess(() -> Component.translatable("shyfrpc.command.off.success"), false);
		return 1;
	}

	// ---------- status ----------

	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Path gameDir = ShyFrpcUnit.gameDir();
		FrpcConfig cfg = ShyFrpcUnit.currentConfig();

		source.sendSuccess(() -> Component.translatable("shyfrpc.command.status.header"), false);
		source.sendSuccess(() -> line("app", describePath(cfg.resolveApp(gameDir))), false);
		source.sendSuccess(() -> line("toml", describePath(cfg.resolveToml(gameDir))), false);
		source.sendSuccess(() -> Component.translatable("shyfrpc.command.status.mode", cfg.mode.id()), false);
		source.sendSuccess(() -> Component.translatable("shyfrpc.command.status.auto-start",
				Component.translatable(cfg.autoStart ? "options.on" : "options.off")), false);
		source.sendSuccess(() -> Component.translatable("shyfrpc.command.status.sakura",
				cfg.sakuraToken.isBlank() ? "-" : mask(cfg.sakuraToken),
				cfg.sakuraTunnel.isBlank() ? "-" : cfg.sakuraTunnel), false);

		if (FrpcProcess.isRunning()) {
			long seconds = (System.currentTimeMillis() - FrpcProcess.startedAtMillis()) / 1000L;
			FrpcProcess.Source origin = FrpcProcess.currentSource();
			source.sendSuccess(() -> Component.translatable("shyfrpc.command.status.running",
					FrpcProcess.currentPid(),
					seconds,
					Component.translatable(origin == FrpcProcess.Source.AUTO
							? "shyfrpc.command.status.source.auto"
							: "shyfrpc.command.status.source.manual")), false);
		} else {
			source.sendSuccess(() -> Component.translatable("shyfrpc.command.status.stopped"), false);
		}
		return 1;
	}

	// ---------- 辅助 ----------

	private static int saveAndReport(CommandContext<CommandSourceStack> ctx, FrpcConfig cfg,
			String successKey, String detail) {
		cfg.save(ShyFrpcUnit.gameDir());
		ctx.getSource().sendSuccess(() -> Component.translatable(successKey, detail), false);
		return 1;
	}

	private static Component line(String label, String value) {
		return Component.translatable("shyfrpc.command.status.line", label, value);
	}

	/** 路径为空时显示未设置，否则显示解析后的绝对路径并标注是否存在。 */
	private static String describePath(Path resolved) {
		if (resolved == null) {
			return "-";
		}
		boolean exists = java.nio.file.Files.isRegularFile(resolved);
		return resolved + (exists ? "" : "  [缺失]");
	}

	/** token 只显示前 4 位，避免截图泄露。 */
	private static String mask(String token) {
		if (token.length() <= 4) {
			return "****";
		}
		return token.substring(0, 4) + "****";
	}

	/**
	 * greedyString 会原样吞掉整行，因此带空格的路径不需要引号；
	 * 但用户习惯性加引号时也不该把引号存进配置，这里剥掉一对首尾引号。
	 */
	private static String cleanPath(String raw) {
		String path = raw.strip();
		if (path.length() >= 2) {
			char first = path.charAt(0);
			char last = path.charAt(path.length() - 1);
			if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
				path = path.substring(1, path.length() - 1).strip();
			}
		}
		return path;
	}

	/** 供命令补全使用。 */
	public static List<String> modeIds() {
		return List.of(MODE_DEFAULT, MODE_SAKURA);
	}
}
```

- [ ] **Step 2: 给 `ShyFrpcUnit` 补一个查询方法**

`status` 与 `auto-start` 需要知道当前是否已开局域网房间。在
`src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java` 里，把已有的私有方法
`isPublished` 改成公开的查询：

找到：

```java
	private static boolean isPublished(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		return server != null && server.isPublished();
	}
```

替换为：

```java
	/** 当前是否已把单人世界开放到局域网。 */
	public static boolean isLanPublished() {
		return isPublished(Minecraft.getInstance());
	}

	private static boolean isPublished(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		return server != null && server.isPublished();
	}
```

- [ ] **Step 3: 编译验证**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`。

编译失败时的排查方向：

- `sendSuccess` 的签名：参考项目用的是 `source.sendSuccess(() -> component, false)`，
  第一个参数是 `Supplier<Component>`，第二个是 `boolean broadcastToOps`
- `options.on` / `options.off` 是原版已有的本地化键，若不存在就换成
  `shyfrpc.command.status.yes` / `no` 并在 Task 8 里补上对应条目

- [ ] **Step 4: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add src/main/java/io/github/cn3shy/shyfrpc/commands/ShyFrpcCommand.java src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcUnit.java
git commit -m "feat: 添加 /shyfrpc 命令树

set app|toml|sakura、mode、on/off、auto-start、status，
统一要求 LEVEL_OWNERS 权限。status 只显示 token 前 4 位避免截图泄露。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 8: 语言文件 en_us / zh_cn

**Files:**
- Create: `src/main/resources/assets/shyfrpc/lang/en_us.json`
- Create: `src/main/resources/assets/shyfrpc/lang/zh_cn.json`

- [ ] **Step 1: 写 `en_us.json`**

`src/main/resources/assets/shyfrpc/lang/en_us.json`：

```json
{
  "shyfrpc.command.set.app.success": "frpc path set to: %s",
  "shyfrpc.command.set.toml.success": "frpc config path set to: %s",
  "shyfrpc.command.set.sakura.success": "SakuraFrp credentials saved (tunnel %s)",

  "shyfrpc.command.mode.success": "Mode set to: %s",
  "shyfrpc.command.mode.invalid": "Unknown mode \"%s\". Use default or sakura.",

  "shyfrpc.command.autoStart.success": "Auto-start: %s",

  "shyfrpc.command.on.success": "frpc starting...",
  "shyfrpc.command.on.already-running": "frpc is already running.",
  "shyfrpc.command.on.error.no-app": "frpc path is not set. Use /shyfrpc set app <path> first.",
  "shyfrpc.command.on.error.app-not-found": "frpc executable not found: %s",
  "shyfrpc.command.on.error.no-toml": "Config path is not set. Use /shyfrpc set toml <path>, or switch to sakura mode with /shyfrpc mode sakura.",
  "shyfrpc.command.on.error.toml-not-found": "frpc config file not found: %s",
  "shyfrpc.command.on.error.no-sakura": "SakuraFrp token or tunnel ID is not set. Use /shyfrpc set sakura <token> <tunnel>.",
  "shyfrpc.command.on.error.spawn-failed": "Failed to start frpc: %s",

  "shyfrpc.command.off.success": "frpc stopped.",
  "shyfrpc.command.off.not-running": "frpc is not running.",

  "shyfrpc.command.status.header": "=== ShyFrpc status ===",
  "shyfrpc.command.status.line": "%s: %s",
  "shyfrpc.command.status.mode": "mode: %s",
  "shyfrpc.command.status.auto-start": "auto-start: %s",
  "shyfrpc.command.status.sakura": "sakura: token=%s tunnel=%s",
  "shyfrpc.command.status.running": "frpc running (pid %s, up %ss, started by %s)",
  "shyfrpc.command.status.source.auto": "auto-start",
  "shyfrpc.command.status.source.manual": "command",
  "shyfrpc.command.status.stopped": "frpc not running",

  "shyfrpc.frpc.output": "[frpc] %s",
  "shyfrpc.frpc.started": "[ShyFrpc] frpc started",
  "shyfrpc.frpc.stopped": "[ShyFrpc] frpc stopped",
  "shyfrpc.frpc.exited-unexpectedly": "[ShyFrpc] frpc exited unexpectedly (exit code %s)"
}
```

- [ ] **Step 2: 写 `zh_cn.json`**

`src/main/resources/assets/shyfrpc/lang/zh_cn.json`：

```json
{
  "shyfrpc.command.set.app.success": "frpc 路径已设置为：%s",
  "shyfrpc.command.set.toml.success": "frpc 配置文件路径已设置为：%s",
  "shyfrpc.command.set.sakura.success": "SakuraFrp 凭据已保存（隧道 %s）",

  "shyfrpc.command.mode.success": "模式已切换为：%s",
  "shyfrpc.command.mode.invalid": "未知模式「%s」。请使用 default 或 sakura。",

  "shyfrpc.command.autoStart.success": "自动启动：%s",

  "shyfrpc.command.on.success": "frpc 正在启动...",
  "shyfrpc.command.on.already-running": "frpc 已在运行中。",
  "shyfrpc.command.on.error.no-app": "尚未设置 frpc 路径。请先执行 /shyfrpc set app <路径>。",
  "shyfrpc.command.on.error.app-not-found": "找不到 frpc 可执行文件：%s",
  "shyfrpc.command.on.error.no-toml": "尚未设置配置文件路径。请执行 /shyfrpc set toml <路径>，或用 /shyfrpc mode sakura 切换到 SakuraFrp 模式。",
  "shyfrpc.command.on.error.toml-not-found": "找不到 frpc 配置文件：%s",
  "shyfrpc.command.on.error.no-sakura": "尚未设置 SakuraFrp 的 token 或隧道 ID。请执行 /shyfrpc set sakura <token> <隧道ID>。",
  "shyfrpc.command.on.error.spawn-failed": "启动 frpc 失败：%s",

  "shyfrpc.command.off.success": "frpc 已停止。",
  "shyfrpc.command.off.not-running": "frpc 当前未运行。",

  "shyfrpc.command.status.header": "=== ShyFrpc 状态 ===",
  "shyfrpc.command.status.line": "%s：%s",
  "shyfrpc.command.status.mode": "模式：%s",
  "shyfrpc.command.status.auto-start": "自动启动：%s",
  "shyfrpc.command.status.sakura": "SakuraFrp：token=%s 隧道=%s",
  "shyfrpc.command.status.running": "frpc 运行中（PID %s，已运行 %s 秒，由%s启动）",
  "shyfrpc.command.status.source.auto": "自动启动",
  "shyfrpc.command.status.source.manual": "命令",
  "shyfrpc.command.status.stopped": "frpc 未运行",

  "shyfrpc.frpc.output": "[frpc] %s",
  "shyfrpc.frpc.started": "[ShyFrpc] frpc 已启动",
  "shyfrpc.frpc.stopped": "[ShyFrpc] frpc 已停止",
  "shyfrpc.frpc.exited-unexpectedly": "[ShyFrpc] frpc 意外退出（退出码 %s）"
}
```

- [ ] **Step 3: 校验两个语言的键集合完全一致**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
python -c "
import json
en = set(json.load(open('src/main/resources/assets/shyfrpc/lang/en_us.json', encoding='utf-8')))
zh = set(json.load(open('src/main/resources/assets/shyfrpc/lang/zh_cn.json', encoding='utf-8')))
print('en 有 zh 没有:', sorted(en - zh))
print('zh 有 en 没有:', sorted(zh - en))
assert en == zh, '两种语言的键不一致'
print('OK:', len(en), '个键')
"
```

Expected: `OK: 30 个键`，没有差异行。

- [ ] **Step 4: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add src/main/resources/assets/shyfrpc/lang/
git commit -m "feat: 添加 en_us 与 zh_cn 语言文件

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 9: 三平台入口接线

三个入口类的结构完全相同：注册命令 + 注册客户端 tick。差异只在各平台的 API。

**Files:**
- Modify: `fabric/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcFabric.java`
- Modify: `forge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcForge.java`
- Modify: `neoforge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcNeoForge.java`
- Modify: `fabric/src/main/resources/fabric.mod.json`

- [ ] **Step 1: 写 Fabric 入口**

`fabric/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcFabric.java`：

```java
package io.github.cn3shy.shyfrpc;

import io.github.cn3shy.shyfrpc.commands.ShyFrpcCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ShyFrpcFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				ShyFrpcCommand.register(dispatcher));

		ClientTickEvents.END_CLIENT_TICK.register(ShyFrpcUnit::onClientTick);

		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on Fabric");
	}
}
```

`fabric.mod.json` 的 `depends` 里已经有 `fabric-api`，无需改动。确认它仍在：

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
grep -n "fabric-api\|environment" fabric/src/main/resources/fabric.mod.json
```

Expected: 看到 `"environment": "client"` 和 `"fabric-api": "${fabric_version_range}"`。

- [ ] **Step 2: 写 Forge 入口**

`forge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcForge.java`：

```java
package io.github.cn3shy.shyfrpc;

import io.github.cn3shy.shyfrpc.commands.ShyFrpcCommand;
import net.minecraftforge.client.event.ClientTickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(ShyFrpcUnit.MODID)
public class ShyFrpcForge {
	public ShyFrpcForge() {
		MinecraftForge.EVENT_BUS.register(this);
		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on Forge");
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		ShyFrpcCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void onClientTick(ClientTickEvent.Post event) {
		ShyFrpcUnit.onClientTick(net.minecraft.client.Minecraft.getInstance());
	}
}
```

- [ ] **Step 3: 编译 Forge 模块，确认 tick 事件类名**

这一步是整份计划里不确定性最高的一处——参考项目没有用客户端 tick。

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :forge:compileJava --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`。

**如果报 `cannot find symbol: class ClientTickEvent` 或 `class Post`**，去 Forge 的 jar 里
列出候选类名：

```bash
find "C:/Users/asus/.gradle/caches" -name "forge-26.3-66*.jar" 2>/dev/null | head -5
```

拿到 jar 路径后：

```bash
unzip -l <上一步的jar路径> | grep -i "client/event/.*Tick"
```

Expected: 看到形如 `net/minecraftforge/client/event/ClientTickEvent$Post.class` 的条目。
按实际条目调整 import 与 `@SubscribeEvent` 方法签名。若该版本的 Forge 把客户端 tick
放在了别处（例如 `net.minecraftforge.event.TickEvent$ClientTickEvent`），用实际类名替换。

- [ ] **Step 4: 写 NeoForge 入口**

`neoforge/src/main/java/io/github/cn3shy/shyfrpc/ShyFrpcNeoForge.java`：

```java
package io.github.cn3shy.shyfrpc;

import io.github.cn3shy.shyfrpc.commands.ShyFrpcCommand;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(ShyFrpcUnit.MODID)
public class ShyFrpcNeoForge {
	public ShyFrpcNeoForge(IEventBus modEventBus) {
		NeoForge.EVENT_BUS.register(this);
		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on NeoForge");
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		ShyFrpcCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void onClientTick(ClientTickEvent.Post event) {
		ShyFrpcUnit.onClientTick(Minecraft.getInstance());
	}
}
```

- [ ] **Step 5: 编译 NeoForge 模块**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :neoforge:compileJava --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`。

**若 tick 事件类名不对**，用同样的办法核对：

```bash
find "C:/Users/asus/.gradle/caches" -name "neoforge-26.3*.jar" 2>/dev/null | head -5
unzip -l <jar路径> | grep -i "client/event/.*Tick"
```

- [ ] **Step 6: 三平台全量构建**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew build --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`，三个 jar 均已产出。

- [ ] **Step 7: 确认没有加载器把服务端专用类打进产物**

三个产物都应该是纯客户端 Mod。检查元数据注入是否生效：

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
unzip -p fabric/build/libs/shyFrpc-fabric-26.3-2610.1.0.jar fabric.mod.json | head -20
unzip -p forge/build/libs/shyFrpc-forge-26.3-2610.1.0.jar META-INF/mods.toml | grep -i "clientSideOnly"
unzip -p neoforge/build/libs/shyFrpc-neoforge-26.3-2610.1.0.jar META-INF/neoforge.mods.toml | grep -A1 side
```

Expected:
- fabric：`"environment": "client"`，版本号已被替换成 `2610.1.0`（不是 `${version}`）
- forge：`clientSideOnly=true`
- neoforge：`side="CLIENT"`

- [ ] **Step 8: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add fabric/src forge/src neoforge/src
git commit -m "feat: 三平台入口接线完成

各平台注册 /shyfrpc 命令与客户端 tick 事件。
产物均为纯客户端：fabric environment=client，
forge clientSideOnly=true，neoforge side=CLIENT。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 10: GitHub 工作流

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `.github/workflows/release.yml`
- Create: `.github/workflows/publish.yml`

- [ ] **Step 1: 写 `build.yml`**

`.github/workflows/build.yml`：

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read

    steps:
    - uses: actions/checkout@v6

    - name: Set up JDK 25
      uses: actions/setup-java@v5
      with:
        java-version: '25'
        distribution: 'temurin'

    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v6
      with:
          cache-read-only: false
          cache-cleanup: always

    - name: Make Gradle Wrapper Executable
      if: ${{ runner.os != 'Windows' }}
      run: chmod +x ./gradlew

    - name: Build with Gradle Wrapper
      run: ./gradlew build

    - name: Upload Artifacts
      uses: actions/upload-artifact@v7
      with:
        name: shyfrpc-artifacts
        path: '**/build/libs/*.jar'
```

- [ ] **Step 2: 写 `release.yml`**

`.github/workflows/release.yml`：

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:

jobs:
  release:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v6

      - name: Set up JDK 25
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6
        with:
          cache-read-only: false
          cache-cleanup: always

      - name: Make Gradle Wrapper Executable
        if: ${{ runner.os != 'Windows' }}
        run: chmod +x ./gradlew

      - name: Build with Gradle Wrapper
        run: ./gradlew build

      - name: Collect Jars
        run: |
          mkdir -p release-jars
          for loader in fabric forge neoforge; do
            cp "$loader"/build/libs/*.jar release-jars/
            rm -f release-jars/*-sources.jar release-jars/*-javadoc.jar
          done
          ls -la release-jars/

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: release-jars/*.jar
          generate_release_notes: true
          draft: false
          prerelease: ${{ !contains(github.ref_name, 'release') }}
```

- [ ] **Step 3: 写 `publish.yml`**

`.github/workflows/publish.yml`：

```yaml
name: Publish to Modrinth & CurseForge

on:
  workflow_dispatch:
    inputs:
      channel:
        type: choice
        description: Release Channel
        default: beta
        options:
          - alpha
          - beta
          - release

env:
  java-version: "25"
  # TODO: 创建 Modrinth / CurseForge 项目后替换这两个占位符
  modrinth-id: TODO_CHANGE_ME
  curseforge-id: TODO_CHANGE_ME

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v6

      - name: Set up JDK ${{ env.java-version }}
        uses: actions/setup-java@v5
        with:
          java-version: ${{ env.java-version }}
          distribution: "temurin"

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6
        with:
          cache-read-only: false
          cache-cleanup: always

      - name: Make Gradle Wrapper Executable
        if: ${{ runner.os != 'Windows' }}
        run: chmod +x ./gradlew

      - name: Build with Gradle Wrapper
        run: ./gradlew build

      - name: Publish (Fabric)
        uses: Kir-Antipov/mc-publish@v3.3
        with:
          fail-mode: warn
          modrinth-id: ${{ env.modrinth-id }}
          modrinth-token: "${{ secrets.MODRINTH_TOKEN }}"
          curseforge-id: ${{ env.curseforge-id }}
          curseforge-token: "${{ secrets.CURSEFORGE_TOKEN }}"

          name: "ShyFrpc Fabric"
          version-type: ${{ inputs.channel }}
          loaders: fabric
          files: fabric/build/libs/!(*-@(dev|sources|javadoc)).jar
          java: Java ${{ env.java-version }}

      - name: Publish (Forge)
        uses: Kir-Antipov/mc-publish@v3.3
        with:
          fail-mode: warn
          modrinth-id: ${{ env.modrinth-id }}
          modrinth-token: "${{ secrets.MODRINTH_TOKEN }}"
          curseforge-id: ${{ env.curseforge-id }}
          curseforge-token: "${{ secrets.CURSEFORGE_TOKEN }}"

          name: "ShyFrpc Forge"
          version-type: ${{ inputs.channel }}
          loaders: forge
          files: forge/build/libs/!(*-@(dev|sources|javadoc)).jar
          java: Java ${{ env.java-version }}

      - name: Publish (NeoForge)
        uses: Kir-Antipov/mc-publish@v3.3
        with:
          fail-mode: warn
          modrinth-id: ${{ env.modrinth-id }}
          modrinth-token: "${{ secrets.MODRINTH_TOKEN }}"
          curseforge-id: ${{ env.curseforge-id }}
          curseforge-token: "${{ secrets.CURSEFORGE_TOKEN }}"

          name: "ShyFrpc NeoForge"
          version-type: ${{ inputs.channel }}
          loaders: neoforge
          files: neoforge/build/libs/!(*-@(dev|sources|javadoc)).jar
          java: Java ${{ env.java-version }}
```

- [ ] **Step 4: 校验 YAML 语法**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
python -c "
import yaml, glob
for f in sorted(glob.glob('.github/workflows/*.yml')):
    yaml.safe_load(open(f, encoding='utf-8'))
    print('OK', f)
"
```

Expected:
```
OK .github/workflows/build.yml
OK .github/workflows/publish.yml
OK .github/workflows/release.yml
```

若本机没有 PyYAML，跳过这步，改在推送后看 GitHub Actions 的报错。

- [ ] **Step 5: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add .github/workflows
git commit -m "ci: 添加构建、发布、发布到 Modrinth/CurseForge 三个工作流

publish.yml 的 modrinth-id 与 curseforge-id 是 TODO_CHANGE_ME 占位符，
创建项目后需替换。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 11: README 与许可证确认

**Files:**
- Create: `README.md`
- Create: `README.zh-CN.md`
- Modify: `LICENSE`（如需要）

- [ ] **Step 1: 确认许可证**

`forge/build.gradle` 等三个模块都会把 `LICENSE` 打进 jar，元数据里也写了
`license="Apache-2.0"`（Fabric 是 `"license": "Apache-2.0"`）。当前 `LICENSE` 是从参考项目
`mcwifipnp` 复制的 Apache License 2.0。

**这一条需要用户确认**：如果要用 MIT 或别的许可证，现在改——替换 `LICENSE` 文件内容，
并把 `fabric.mod.json` 的 `license`、`mods.toml` 与 `neoforge.mods.toml` 的 `license` 字段
同步改掉。没有异议就保持 Apache-2.0。

- [ ] **Step 2: 写 `README.md`**

`README.md`：

```markdown
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

## License

Apache License 2.0
```

- [ ] **Step 3: 写 `README.zh-CN.md`**

`README.zh-CN.md`：

```markdown
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

## 许可证

Apache License 2.0
```

- [ ] **Step 4: 提交**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
git add README.md README.zh-CN.md LICENSE
git commit -m "docs: 添加中英文 README

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 12: 端到端手工验证

单元测试覆盖不到「真启动一个 frpc 进程」「真在游戏里敲命令」这部分。这个任务用真客户端跑一遍验收标准。

**Files:** 无（纯验证）

- [ ] **Step 1: 准备一个可用的假 frpc**

为了不依赖真实的 SakuraFrp 账号，先造一个能打印输出并常驻的假可执行文件，用来验证进程管理与输出转发。

Windows 下用一个批处理脚本最省事。创建 `D:\frpc-test\frpc.bat`：

```bat
@echo off
echo [fake-frpc] args: %*
echo [fake-frpc] starting...
:loop
timeout /t 2 /nobreak >nul
echo [fake-frpc] heartbeat
goto loop
```

创建 `D:\frpc-test\minecraft.toml`：

```toml
serverAddr = "127.0.0.1"
```

- [ ] **Step 2: 启动 Fabric 客户端**

```bash
cd "D:/Project/MinecraftPro/shy-frpc"
./gradlew :fabric:runClient --no-daemon
```

Expected: 客户端启动，主菜单出现。

- [ ] **Step 3: 验证命令树与权限提示**

进入一个单人世界（创造模式，允许作弊），然后执行：

```
/shyfrpc status
```

Expected: 显示 `=== ShyFrpc 状态 ===` 和各项配置，全部为未设置或默认值，最后一行是「frpc 未运行」。

- [ ] **Step 4: 验证配置写入**

```
/shyfrpc set app D:\frpc-test\frpc.bat
/shyfrpc set toml D:\frpc-test\minecraft.toml
/shyfrpc mode default
/shyfrpc status
```

Expected: 三条 `... 已设置/已切换` 提示；`status` 显示两个路径且**没有** `[缺失]` 标记。

然后确认文件真的落盘了：

```bash
cat "D:/Project/MinecraftPro/shy-frpc/fabric/run/config/shyFrpc/config.yaml"
```

Expected:
```
app: "D:\\frpc-test\\frpc.bat"
toml: "D:\\frpc-test\\minecraft.toml"
mode: "default"
sakura-token: ""
sakura-tunnel: ""
auto-start: "false"
```

- [ ] **Step 5: 验证启动与输出转发**

```
/shyfrpc on
```

Expected:
- 聊天栏出现 `[ShyFrpc] frpc 已启动`
- 紧接着出现 `[frpc] [fake-frpc] args: -c D:\frpc-test\minecraft.toml`
- 每 2 秒出现一条 `[frpc] [fake-frpc] heartbeat`

```
/shyfrpc status
```

Expected: 显示运行中，含 PID、已运行秒数、由「命令」启动。

- [ ] **Step 6: 验证校验失败路径**

```
/shyfrpc set app D:\frpc-test\does-not-exist.exe
/shyfrpc on
```

Expected: 红色报错「找不到 frpc 可执行文件：D:\frpc-test\does-not-exist.exe」，**不**启动进程。

改回来。

- [ ] **Step 7: 验证 off 与进程真的没了**

```
/shyfrpc off
```

Expected: `[ShyFrpc] frpc 已停止`，之后不再有 heartbeat。

用任务管理器或命令确认没有残留：

```bash
tasklist 2>/dev/null | grep -i -E "frpc|cmd.exe" | head
```

- [ ] **Step 8: 验证 auto-start 联动**

```
/shyfrpc auto-start true
```

然后按 Esc → 「对局域网开放」→ 开始。

Expected: 自动出现 `[ShyFrpc] frpc 已启动` 和后续的 frpc 输出，无需手敲 `on`。

再按 Esc → 「关闭局域网」（或回到主菜单），约 1 秒后：

Expected: 出现 `[ShyFrpc] frpc 已停止`。

- [ ] **Step 9: 验证退出世界清理**

重新开局域网让 frpc 跑起来，然后直接「保存并退出到标题画面」。

Expected: frpc 停止，`tasklist` 里没有残留的 `frpc.bat` / `cmd.exe`。

- [ ] **Step 10: 验证语言切换**

选项 → 语言 → 切换 English，重跑一次 `/shyfrpc status`。

Expected: 所有文本为英文，没有未翻译的键名（形如 `shyfrpc.command.status.header` 的裸字符串）。

- [ ] **Step 11: 重复 Steps 2-10 于 Forge 与 NeoForge**

```bash
./gradlew :forge:runClient --no-daemon
./gradlew :neoforge:runClient --no-daemon
```

Expected: 行为与 Fabric 完全一致。

- [ ] **Step 12: 提交验证记录**

如果验证过程中改了代码，提交它们。若一切顺利无需改动，在计划里勾掉本任务即可。

---

## 自查结果

**规格覆盖检查**（对照 `docs/superpowers/specs/2026-10-11-shyfrpc-design.md`）：

| 规格章节 | 对应任务 |
| --- | --- |
| §4.1 工程布局 | Task 1 |
| §4.2 平台差异边界 | Task 9 |
| §5 配置（位置/格式/YAML 策略） | Task 2、Task 3 |
| §6 命令（树/行为/校验/路径/线程） | Task 4、Task 7 |
| §7 进程管理（命令构造/转发/停止/状态） | Task 4、Task 5 |
| §8 生命周期与自动启停 | Task 6 |
| §9 本地化 | Task 8 |
| §10 构建产物与版本命名 | Task 1（Step 2、13） |
| §11 GitHub 工作流 | Task 10 |
| §12 元数据文件 | Task 1、Task 9（Step 7） |
| §13 测试 | Task 2、Task 3、Task 4（单测）；Task 12（集成） |
| §14 已知风险 | Task 1 Step 13、Task 5 Step 2、Task 9 Step 3/5 |
| §15 验收标准 | Task 12 |

**占位符扫描**：计划中唯一的 `TODO` 是 `publish.yml` 里故意留下的 `TODO_CHANGE_ME`——它是规格 §11.3 明确要求交付的占位符，不是计划缺陷。已在 Task 10 Step 3 的注释与 Task 11 Step 1 附近标明替换点。

**类型一致性检查**：跨任务引用的符号已核对——`FrpcConfig.resolveApp/resolveToml/save/load`、`FrpcMode.fromId/id`、`FrpcLauncher.buildCommand/validate`、`ValidationError.translationKey/detail`、`FrpcProcess.isRunning/start/stop/currentPid/startedAtMillis/currentSource/Source`、`ShyFrpcUnit.gameDir/currentConfig/isLanPublished/onClientTick` 在各任务中的用法一致。

**已知未决项**（需要用户输入，不影响 Task 1-10 的推进）：
1. Task 11 Step 1 的许可证确认（默认沿用 Apache-2.0）
2. Task 10 的 Modrinth / CurseForge 项目 ID
