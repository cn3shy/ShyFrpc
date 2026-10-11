package io.github.cn3shy.shyfrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
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
	void resolvesNestedRelativePathWithPlatformSeparator(@TempDir Path gameDir) {
		String nested = "a" + File.separator + "b.toml";

		assertEquals(gameDir.resolve("a").resolve("b.toml"), FrpcConfig.resolvePath(gameDir, nested));
	}

	/**
	 * 反斜杠只在 Windows 上是分隔符，在 Linux 上会被当成普通文件名字符，
	 * 因此这条断言只能在 Windows 上跑，否则 CI（Linux）会误报失败。
	 */
	@Test
	@EnabledOnOs(OS.WINDOWS)
	void resolvesNestedRelativePathWithBackslashesOnWindows(@TempDir Path gameDir) {
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
		Files.writeString(file, "!!! this is not yaml at all\n@@@ <<< broken\n", StandardCharsets.UTF_8);

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
