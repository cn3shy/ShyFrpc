package io.github.cn3shy.shyfrpc;

import io.github.cn3shy.shyfrpc.commands.ShyFrpcCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ShyFrpcFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				ShyFrpcCommand.register(dispatcher));

		ClientTickEvents.END_CLIENT_TICK.register(ShyFrpcUnit::onClientTick);

		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on Fabric");
	}
}
