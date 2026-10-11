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
