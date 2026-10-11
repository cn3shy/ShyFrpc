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
