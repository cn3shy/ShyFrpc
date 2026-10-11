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
		cfg.sakuraToken = "fake-token-for-testing";
		cfg.sakuraTunnel = "12345678";
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
				"fake-token-for-testing:12345678"), command);
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
