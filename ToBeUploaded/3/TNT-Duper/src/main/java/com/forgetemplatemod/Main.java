package com.forgetemplatemod;

import com.forgetemplatemod.proxy.CommonProxy;

import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid= Main.MOD_ID, version = Main.VERSION, name = Main.NAME)
public class Main {
	public static final String MOD_ID = "tnt_duper";
	public static final String VERSION = "1.0";
	public static final String NAME = "TNT Duper";
	
	@Instance
	public static Main main;	
	
	/**
	 * must include package paths
	 */
	@SidedProxy(serverSide = "com.forgetemplatemod.proxy.CommonProxy", clientSide = "com.forgetemplatemod.proxy.ClientProxy")
	public static CommonProxy proxy;  
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		
	}
	
	@EventHandler
	public void init(FMLInitializationEvent event) {
		net.minecraft.block.BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(
			Item.getItemFromBlock(net.minecraft.init.Blocks.TNT), 
			new BehaviorDispenseTNTDuper()
		);
	}
	
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		
	}
	
	@EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		
	}
}
