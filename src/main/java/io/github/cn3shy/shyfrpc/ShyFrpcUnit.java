package io.github.cn3shy.shyfrpc;

import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

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
	public static Path gameDir() {
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

	/** 当前是否已把单人世界开放到局域网。 */
	public static boolean isLanPublished() {
		return isPublished(Minecraft.getInstance());
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
