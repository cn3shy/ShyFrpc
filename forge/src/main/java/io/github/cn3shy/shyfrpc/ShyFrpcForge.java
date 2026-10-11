package io.github.cn3shy.shyfrpc;

import io.github.cn3shy.shyfrpc.commands.ShyFrpcCommand;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(ShyFrpcUnit.MODID)
public class ShyFrpcForge {
	public ShyFrpcForge() {
		MinecraftForge.EVENT_BUS.register(this);
		ShyFrpcUnit.LOGGER.info("ShyFrpc loaded on Forge");
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		ShyFrpcCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent.Post event) {
		ShyFrpcUnit.onClientTick(Minecraft.getInstance());
	}
}
