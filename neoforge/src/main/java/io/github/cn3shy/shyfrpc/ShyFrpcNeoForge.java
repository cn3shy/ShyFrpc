package io.github.cn3shy.shyfrpc;

import io.github.cn3shy.shyfrpc.commands.ShyFrpcCommand;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(ShyFrpcUnit.MODID)
public class ShyFrpcNeoForge {
	public ShyFrpcNeoForge(IEventBus modEventBus) {
		NeoForge.EVENT_BUS.register(this);
		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on NeoForge");
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		ShyFrpcCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void onClientTick(ClientTickEvent.Post event) {
		ShyFrpcUnit.onClientTick(Minecraft.getInstance());
	}
}
