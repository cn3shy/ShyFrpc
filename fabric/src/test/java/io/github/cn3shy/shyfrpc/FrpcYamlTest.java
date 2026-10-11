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
