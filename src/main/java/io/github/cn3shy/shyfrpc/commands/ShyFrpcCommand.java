package io.github.cn3shy.shyfrpc.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
		String normalized = rawMode.strip().toLowerCase(Locale.ROOT);
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
			source.sendFailure(Component.translatable("shyfrpc.command.on.error.spawn-failed",
					String.valueOf(e.getMessage())));
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
		boolean exists = Files.isRegularFile(resolved);
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
