package io.github.cn3shy.shyfrpc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 构造 frpc 的启动命令并做前置校验。
 *
 * <p>本类不依赖任何 Minecraft 类，也自己不启动进程——真正的进程管理在
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
