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
