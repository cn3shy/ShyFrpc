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
				client.player.sendSystemMessage(message);
			}
		});
	}
}
