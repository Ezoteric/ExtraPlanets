package com.mjr.extraplanets.handlers;

import java.util.List;
import java.util.Random;

import micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase;
import micdoodle8.mods.galacticraft.api.world.IGalacticraftWorldProvider;
import micdoodle8.mods.galacticraft.core.dimension.WorldProviderMoon;
import micdoodle8.mods.galacticraft.core.dimension.WorldProviderSpaceStation;
import micdoodle8.mods.galacticraft.core.entities.EntityLanderBase;
import micdoodle8.mods.galacticraft.core.entities.player.GCPlayerHandler.ThermalArmorEvent;
import micdoodle8.mods.galacticraft.core.util.OxygenUtil;
import micdoodle8.mods.galacticraft.planets.asteroids.dimension.WorldProviderAsteroids;
import micdoodle8.mods.galacticraft.planets.asteroids.items.AsteroidsItems;
import micdoodle8.mods.galacticraft.planets.mars.dimension.WorldProviderMars;
import micdoodle8.mods.galacticraft.planets.venus.dimension.WorldProviderVenus;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.google.common.collect.Lists;
import com.mjr.extraplanets.Config;
import com.mjr.extraplanets.Constants;
import com.mjr.extraplanets.ExtraPlanets;
import com.mjr.extraplanets.api.item.IModularArmor;
import com.mjr.extraplanets.api.item.IPressureSuit;
import com.mjr.extraplanets.api.item.IRadiationSuit;
import com.mjr.extraplanets.api.prefabs.entity.EntityElectricRocketBase;
import com.mjr.extraplanets.api.prefabs.world.WorldProviderRealisticSpace;
import com.mjr.extraplanets.blocks.fluid.ExtraPlanets_Fluids;
import com.mjr.extraplanets.blocks.fluid.FluidBlockEP;
import com.mjr.extraplanets.client.handlers.capabilities.CapabilityProviderStatsClient;
import com.mjr.extraplanets.client.handlers.capabilities.CapabilityStatsClientHandler;
import com.mjr.extraplanets.handlers.capabilities.CapabilityProviderStats;
import com.mjr.extraplanets.handlers.capabilities.CapabilityStatsHandler;
import com.mjr.extraplanets.handlers.capabilities.IStatsCapability;
import com.mjr.extraplanets.items.ExtraPlanets_Items;
import com.mjr.extraplanets.items.armor.modules.Module;
import com.mjr.extraplanets.items.armor.modules.ModuleHelper;
import com.mjr.extraplanets.network.ExtraPlanetsPacketHandler;
import com.mjr.extraplanets.network.PacketSimpleEP;
import com.mjr.extraplanets.network.PacketSimpleEP.EnumSimplePacket;
import com.mjr.extraplanets.util.DamageSourceEP;
import com.mjr.mjrlegendslib.util.MessageUtilities;
import com.mjr.mjrlegendslib.util.PlayerUtilties;
import com.mjr.mjrlegendslib.util.TranslateUtilities;

public class MainHandlerServer {

	private static List<ExtraPlanetsPacketHandler> packetHandlers = Lists.newCopyOnWriteArrayList();

	public static void addPacketHandler(ExtraPlanetsPacketHandler handler) {
		MainHandlerServer.packetHandlers.add(handler);
	}

	@SubscribeEvent
	public void worldUnloadEvent(WorldEvent.Unload event) {
		for (ExtraPlanetsPacketHandler packetHandler : packetHandlers) {
			packetHandler.unload(event.world);
		}
	}

	@SubscribeEvent
	public void onWorldTick(WorldTickEvent event) {
		if (event.phase == Phase.END) {
			final WorldServer world = (WorldServer) event.world;

			for (ExtraPlanetsPacketHandler handler : packetHandlers) {
				handler.tick(world);
			}
		}
	}

	@SubscribeEvent
	public void onPlayerCloned(PlayerEvent.Clone event) {
		IStatsCapability oldStats = event.original.getCapability(CapabilityStatsHandler.EP_STATS_CAPABILITY, null);
		IStatsCapability newStats = event.entityPlayer.getCapability(CapabilityStatsHandler.EP_STATS_CAPABILITY, null);
		newStats.copyFrom(oldStats, !event.wasDeath || event.original.worldObj.getGameRules().getBoolean("keepInventory"));
	}

	@SubscribeEvent
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if (event.player instanceof EntityPlayerMP) {
			EntityPlayerMP player = (EntityPlayerMP) event.player;
			World world = event.player.worldObj;

			IBlockState blockTest = world.getBlockState(player.getPosition());
			if (blockTest.getBlock() instanceof FluidBlockEP) {
				BlockPos block = world.getTopSolidOrLiquidBlock(player.getPosition().add(1, 1, 0));
				world.setBlockState(block, world.getBiomeGenForCoords(block).topBlock);
				ExtraPlanets.packetPipeline.sendTo(new PacketSimpleEP(EnumSimplePacket.C_MOVE_PLAYER, world.provider.getDimensionId(), new Object[] { block }), player);
			}
		}
	}

	@SubscribeEvent
	public void onEntityDealth(LivingDeathEvent event) {
		if (event.entity instanceof EntityPlayerMP) {
			final EntityLivingBase entityLiving = event.entityLiving;
			IStatsCapability stats = null;

			if (entityLiving != null) {
				stats = entityLiving.getCapability(CapabilityStatsHandler.EP_STATS_CAPABILITY, null);
			}
			if (stats.getRadiationLevel() >= 85)
				stats.setRadiationLevel(80);
			else if (stats.getRadiationLevel() >= 65 && stats.getRadiationLevel() < 85)
				stats.setRadiationLevel(60);
			else if (stats.getRadiationLevel() >= 50 && stats.getRadiationLevel() < 65)
				stats.setRadiationLevel(50);
		}
	}

	@SubscribeEvent
	public void onPlayer(PlayerTickEvent event) {
		if (Config.JUITPER_LIGHTING && event.player.worldObj.provider.getDimensionId() == Config.JUPITER_ID) {
			Random rand = new Random();
			int addX = rand.nextInt(64);
			int addZ = rand.nextInt(64);
			if (rand.nextInt(2) == 1)
				addX = -addX;
			if (rand.nextInt(2) == 1)
				addZ = -addZ;
			if (addX <= 10)
				addX = 10;
			if (addZ <= 10)
				addZ = 10;
			int lightingSpawnChance = rand.nextInt(100);
			if (lightingSpawnChance == 10) {
				event.player.worldObj.addWeatherEffect(new EntityLightningBolt(event.player.worldObj, event.player.posX + addX, event.player.worldObj.getHeight(new BlockPos(event.player.posX + addX, 0, (int) event.player.posZ + addZ)).getY(),
						event.player.posZ + addZ));
			}
		}
	}

	@SubscribeEvent
	public void onThermalArmorEvent(ThermalArmorEvent event) {
		if (event.armorStack == null) {
			event.setArmorAddResult(ThermalArmorEvent.ArmorAddResult.REMOVE);
			return;
		}
		if (event.armorStack.getItem() == AsteroidsItems.thermalPadding && event.armorStack.getItemDamage() == event.armorIndex) {
			event.setArmorAddResult(ThermalArmorEvent.ArmorAddResult.ADD);
			return;
		}
		if (event.armorStack.getItem() == ExtraPlanets_Items.TIER_3_THERMAL_PADDING && event.armorStack.getItemDamage() == event.armorIndex) {
			event.setArmorAddResult(ThermalArmorEvent.ArmorAddResult.ADD);
			return;
		}
		if (event.armorStack.getItem() == ExtraPlanets_Items.TIER_4_THERMAL_PADDING && event.armorStack.getItemDamage() == event.armorIndex) {
			event.setArmorAddResult(ThermalArmorEvent.ArmorAddResult.ADD);
			return;
		}
		if (event.armorStack.getItem() == ExtraPlanets_Items.TIER_5_THERMAL_PADDING && event.armorStack.getItemDamage() == event.armorIndex) {
			event.setArmorAddResult(ThermalArmorEvent.ArmorAddResult.ADD);
			return;
		}
		event.setArmorAddResult(ThermalArmorEvent.ArmorAddResult.NOTHING);
	}

	@SubscribeEvent
	public void onAttachCapability(AttachCapabilitiesEvent.Entity event) {
		if (event.getEntity() instanceof EntityPlayerMP) {
			event.addCapability(CapabilityStatsHandler.EP_PLAYER_PROP, new CapabilityProviderStats((EntityPlayerMP) event.getEntity()));

		} else if (event.getEntity() instanceof EntityPlayer && ((EntityPlayer) event.getEntity()).worldObj.isRemote) {
			this.onAttachCapabilityClient(event);
		}
	}

	@SideOnly(Side.CLIENT)
	private void onAttachCapabilityClient(AttachCapabilitiesEvent.Entity event) {
		if (event.getEntity() instanceof EntityPlayerSP)
			event.addCapability(CapabilityStatsClientHandler.EP_PLAYER_CLIENT_PROP, new CapabilityProviderStatsClient((EntityPlayerSP) event.getEntity()));
	}

	@SubscribeEvent
	public void onEntityUpdate(LivingEvent.LivingUpdateEvent event) {
		final EntityLivingBase entityLiving = event.entityLiving;
		if (entityLiving instanceof EntityPlayerMP) {
			tickModules(event, entityLiving);
			if (isInGlowstone((EntityPlayerMP) entityLiving))
				entityLiving.addPotionEffect(new PotionEffect(Potion.nightVision.id, 500, 0));
			onPlayerUpdate((EntityPlayerMP) entityLiving);
			if (OxygenUtil.isAABBInBreathableAirBlock(entityLiving.worldObj, entityLiving.getEntityBoundingBox(), true) == false)
				runChecks(event, entityLiving);
		}
	}

	private void tickModules(LivingUpdateEvent event, EntityLivingBase entityLiving) {
		EntityPlayerMP player = (EntityPlayerMP) entityLiving;

		ItemStack helmet = player.inventory.armorInventory[0];
		ItemStack chest = player.inventory.armorInventory[1];
		ItemStack leggins = player.inventory.armorInventory[2];
		ItemStack boots = player.inventory.armorInventory[3];

		if (helmet != null && helmet.getItem() instanceof IModularArmor)
			for (Module hemletModules : ModuleHelper.getModules(helmet)) {
				if (hemletModules.isActive()) {
					int passivePower = ModuleHelper.getModulePassiveCost(hemletModules);
					if ((player.ticksExisted - 1) % 20 == 0 && ModuleHelper.hasPower(helmet, passivePower))
						ModuleHelper.takeArmourPower(helmet, passivePower);
					if (ModuleHelper.hasPower(helmet, ModuleHelper.getModuleUseCost(hemletModules)))
						hemletModules.tickServer(player);
				}
			}
		if (chest != null && chest.getItem() instanceof IModularArmor)
			for (Module chestModules : ModuleHelper.getModules(chest)) {
				if (chestModules.isActive()) {
					int passivePower = ModuleHelper.getModulePassiveCost(chestModules);
					if ((player.ticksExisted - 1) % 20 == 0 && ModuleHelper.hasPower(chest, passivePower))
						ModuleHelper.takeArmourPower(chest, passivePower);
					if (ModuleHelper.hasPower(helmet, ModuleHelper.getModuleUseCost(chestModules)))
						chestModules.tickServer(player);
				}
			}
		if (leggins != null && leggins.getItem() instanceof IModularArmor)
			for (Module legginsModules : ModuleHelper.getModules(leggins)) {
				if (legginsModules.isActive()) {
					int passivePower = ModuleHelper.getModulePassiveCost(legginsModules);
					if ((player.ticksExisted - 1) % 20 == 0 && ModuleHelper.hasPower(leggins, passivePower))
						ModuleHelper.takeArmourPower(leggins, passivePower);
					if (ModuleHelper.hasPower(helmet, ModuleHelper.getModuleUseCost(legginsModules)))
						legginsModules.tickServer(player);
				}
			}
		if (boots != null && boots.getItem() instanceof IModularArmor)
			for (Module bootsModules : ModuleHelper.getModules(boots)) {
				if (bootsModules.isActive()) {
					int passivePower = ModuleHelper.getModulePassiveCost(bootsModules);
					if ((player.ticksExisted - 1) % 20 == 0 && ModuleHelper.hasPower(boots, passivePower))
						ModuleHelper.takeArmourPower(boots, passivePower);
					if (ModuleHelper.hasPower(helmet, ModuleHelper.getModuleUseCost(bootsModules)))
						bootsModules.tickServer(player);
				}
			}
	}

	public boolean isInGlowstone(EntityPlayerMP player) {
		return player.worldObj.isMaterialInBB(player.getEntityBoundingBox().expand(-0.10000000149011612D, -0.4000000059604645D, -0.10000000149011612D), ExtraPlanets_Fluids.GLOWSTONE_MATERIAL);
	}

	private void runChecks(LivingEvent.LivingUpdateEvent event, EntityLivingBase entityLiving) {
		EntityPlayerMP player = (EntityPlayerMP) entityLiving;
		if (player.capabilities.isCreativeMode)
			return;
		if ((entityLiving.ridingEntity instanceof EntityLanderBase))
			return;
		if ((entityLiving.ridingEntity instanceof EntityElectricRocketBase))
			return;
		if ((entityLiving.ridingEntity instanceof EntitySpaceshipBase))
			return;
		if (entityLiving.worldObj.provider instanceof IGalacticraftWorldProvider) {
			if (((EntityPlayerMP) entityLiving).worldObj.provider instanceof WorldProviderRealisticSpace) {
				if (Config.PRESSURE)
					checkPressure(event, player, ((WorldProviderRealisticSpace) player.worldObj.provider).getPressureLevel());
				if (Config.RADIATION)
					checkRadiation(event, player, ((WorldProviderRealisticSpace) player.worldObj.provider).getSolarRadiationLevel());
			} else if (player.worldObj.provider instanceof WorldProviderMoon) {
				if (Config.GC_PRESSURE)
					checkPressure(event, player, 80);
				if (Config.GC_RADIATION)
					checkRadiation(event, player, Config.MOON_RADIATION_AMOUNT);
			} else if (player.worldObj.provider instanceof WorldProviderMars) {
				if (Config.GC_PRESSURE)
					checkPressure(event, player, 90);
				if (Config.GC_RADIATION)
					checkRadiation(event, player, Config.MARS_RADIATION_AMOUNT);
			} else if (player.worldObj.provider instanceof WorldProviderVenus) {
				if (Config.GC_PRESSURE)
					checkPressure(event, player, 100);
				if (Config.GC_RADIATION)
					checkRadiation(event, player, Config.VENUS_RADIATION_AMOUNT);
			} else if (player.worldObj.provider instanceof WorldProviderAsteroids) {
				if (Config.GC_PRESSURE)
					checkPressure(event, player, 100);
				if (Config.GC_RADIATION)
					checkRadiation(event, player, Config.ASTEROIDS_RADIATION_AMOUNT);
			} else if (player.worldObj.provider instanceof WorldProviderSpaceStation) {
				if (Config.GC_PRESSURE || Config.PRESSURE)
					checkPressure(event, player, 100);
				if (Config.GC_RADIATION || Config.RADIATION)
					checkRadiation(event, player, Config.SPACE_STATION_RADIATION_AMOUNT);
			}
		}
	}

	private void checkPressure(LivingEvent.LivingUpdateEvent event, EntityPlayerMP playerMP, int amount) {
		if ((playerMP.ticksExisted - 1) % 50 == 0) {
			if (amount == 0)
				return;

			if ((playerMP.ticksExisted - 1) % 300 == 0 && Config.DEBUG_MODE)
				MessageUtilities.debugMessageToLog(Constants.modID, "Environment Pressure Amount: " + amount);

			ItemStack helmet = playerMP.inventory.armorInventory[0];
			ItemStack chest = playerMP.inventory.armorInventory[1];
			ItemStack leggins = playerMP.inventory.armorInventory[2];
			ItemStack boots = playerMP.inventory.armorInventory[3];

			boolean doDamage = false;

			if (helmet == null || !(helmet.getItem() instanceof IPressureSuit))
				doDamage = true;
			else if (chest == null || !(chest.getItem() instanceof IPressureSuit))
				doDamage = true;
			else if (leggins == null || !(leggins.getItem() instanceof IPressureSuit))
				doDamage = true;
			else if (boots == null || !(boots.getItem() instanceof IPressureSuit))
				doDamage = true;

			if (doDamage) {
				float tempLevel = amount;
				tempLevel = (tempLevel / 100) * 8;
				if ((playerMP.ticksExisted - 1) % 100 == 0 && Config.DEBUG_MODE)
					MessageUtilities.debugMessageToLog(Constants.modID, "Damage Amount for Pressure: " + tempLevel);
				playerMP.attackEntityFrom(DamageSourceEP.pressure, tempLevel);
			}
		}
	}

	private void checkRadiation(LivingEvent.LivingUpdateEvent event, EntityPlayerMP playerMP, int amount) {
		// Tier 1 Space Suit
		// 25 Level = 36 mins
		// 50 Level = 14 mins
		// Tier 2 Space Suit
		// 25 Level = 38 mins
		// 50 Level = 15 mins

		if (amount == 0)
			return;
		if ((playerMP.ticksExisted - 1) % 300 == 0 && Config.DEBUG_MODE)
			MessageUtilities.debugMessageToLog(Constants.modID, "Environment Radiation Amount: " + amount);
		boolean doDamage = false;
		boolean doArmorCheck = false;
		double damageModifer = 0;
		if (playerMP.inventory.armorInventory[0] == null || playerMP.inventory.armorInventory[1] == null || playerMP.inventory.armorInventory[2] == null || playerMP.inventory.armorInventory[3] == null) {
			damageModifer = 0.1;
			doDamage = true;
		} else if (!(playerMP.inventory.armorInventory[0].getItem() instanceof IRadiationSuit) && !(playerMP.inventory.armorInventory[1].getItem() instanceof IRadiationSuit)
				&& !(playerMP.inventory.armorInventory[2].getItem() instanceof IRadiationSuit) && !(playerMP.inventory.armorInventory[3].getItem() instanceof IRadiationSuit)) {
			damageModifer = 0.1;
			doDamage = true;
		} else if (playerMP.inventory.armorInventory[0].getItem() instanceof IRadiationSuit && playerMP.inventory.armorInventory[1].getItem() instanceof IRadiationSuit && playerMP.inventory.armorInventory[2].getItem() instanceof IRadiationSuit
				&& playerMP.inventory.armorInventory[3].getItem() instanceof IRadiationSuit) {
			doArmorCheck = true;
			doDamage = false;
		} else {
			damageModifer = 0.1;
			doDamage = true;
		}
		if (doArmorCheck) {
			int helmetTier = ((IRadiationSuit) playerMP.inventory.armorInventory[0].getItem()).getArmorTier();
			int chestTier = ((IRadiationSuit) playerMP.inventory.armorInventory[1].getItem()).getArmorTier();
			int legginsTier = ((IRadiationSuit) playerMP.inventory.armorInventory[2].getItem()).getArmorTier();
			int bootsTier = ((IRadiationSuit) playerMP.inventory.armorInventory[3].getItem()).getArmorTier();

			int tierValue = (helmetTier + chestTier + legginsTier + bootsTier) / 2;
			double damageToTake = 0.005 * tierValue;
			damageModifer = 0.0075 - (damageToTake / 2) / 10;
			doDamage = true;
		}
		if (doDamage) {
			IStatsCapability stats = null;
			if (playerMP != null) {
				stats = playerMP.getCapability(CapabilityStatsHandler.EP_STATS_CAPABILITY, null);
			}
			if ((playerMP.ticksExisted - 1) % 100 == 0 && Config.DEBUG_MODE)
				MessageUtilities.debugMessageToLog(Constants.modID, "Player Current Radiation Amount: " + stats.getRadiationLevel());
			if (stats.getRadiationLevel() >= 100) {
				if ((playerMP.ticksExisted - 1) % 50 == 0)
					playerMP.attackEntityFrom(DamageSourceEP.radiation, 3F);
			} else if (stats.getRadiationLevel() >= 0) {
				double tempLevel = 0.0;
				if (amount < 10)
					tempLevel = (damageModifer * amount) / 100;
				else
					tempLevel = damageModifer * (amount / 10) / 6;
				if ((playerMP.ticksExisted - 1) % 100 == 0 && Config.DEBUG_MODE)
					MessageUtilities.debugMessageToLog(Constants.modID, "Gained amount of Radiation: " + tempLevel);
				stats.setRadiationLevel(stats.getRadiationLevel() + tempLevel);
			} else
				stats.setRadiationLevel(0);
		}
	}

	public void onPlayerUpdate(EntityPlayerMP player) {
		int tick = player.ticksExisted - 1;
		final boolean isInGCDimension = player.worldObj.provider instanceof IGalacticraftWorldProvider;
		IStatsCapability stats = player.getCapability(CapabilityStatsHandler.EP_STATS_CAPABILITY, null);

		if ((isInGCDimension || player.worldObj.provider instanceof WorldProviderSpaceStation) && Config.RADIATION) {
			if (tick % 30 == 0) {
				this.sendSolarRadiationPacket(player, stats);
			}
		}
	}

	protected void sendSolarRadiationPacket(EntityPlayerMP player, IStatsCapability stats) {
		ExtraPlanets.packetPipeline.sendTo(new PacketSimpleEP(EnumSimplePacket.C_UPDATE_SOLAR_RADIATION_LEVEL, player.worldObj.provider.getDimensionId(), new Object[] { stats.getRadiationLevel() }), player);
	}

	@SubscribeEvent
	public void onSleepInBedEvent(PlayerWakeUpEvent event) {
		EntityPlayer player = event.entityPlayer;
		if (player.worldObj.isRemote == false && (!event.wakeImmediatly && !event.updateWorld)) {
			EntityPlayerMP playerMP = (EntityPlayerMP) player;
			IStatsCapability stats = null;
			if (playerMP != null) {
				stats = playerMP.getCapability(CapabilityStatsHandler.EP_STATS_CAPABILITY, null);
			}
			double temp = stats.getRadiationLevel();
			double level = (temp * Config.RADIATION_SLEEPING_REDUCE_AMOUNT) / 100;
			if (level <= 0)
				stats.setRadiationLevel(0);
			else {
				stats.setRadiationLevel(stats.getRadiationLevel() - level);
				PlayerUtilties.sendMessage(player, "" + EnumChatFormatting.AQUA + EnumChatFormatting.BOLD + playerMP.getName() + EnumChatFormatting.GOLD + ", " + TranslateUtilities.translate("gui.radiation.reduced.message") + " "
						+ Config.RADIATION_SLEEPING_REDUCE_AMOUNT + "%");
				PlayerUtilties.sendMessage(player,
						"" + EnumChatFormatting.AQUA + EnumChatFormatting.BOLD + playerMP.getName() + EnumChatFormatting.DARK_AQUA + ", " + TranslateUtilities.translate("gui.radiation.current.message") + ": " + (int) stats.getRadiationLevel() + "%");
			}
		}
	}

	@SubscribeEvent
	public void onWorldChange(PlayerChangedDimensionEvent event) {
		if (event.player.worldObj.isRemote == false) {
			if (event.player.worldObj.provider instanceof WorldProviderRealisticSpace || event.player.worldObj.provider instanceof WorldProviderMoon || event.player.worldObj.provider instanceof WorldProviderMars
					|| event.player.worldObj.provider instanceof WorldProviderAsteroids || event.player.worldObj.provider instanceof WorldProviderVenus || event.player.worldObj.provider instanceof WorldProviderSpaceStation) {
				EntityPlayer player = event.player;
				int amount = 0;
				if (event.player.worldObj.provider instanceof WorldProviderRealisticSpace)
					amount = ((WorldProviderRealisticSpace) event.player.worldObj.provider).getSolarRadiationLevel();
				if (event.player.worldObj.provider instanceof WorldProviderMoon)
					amount = Config.MOON_RADIATION_AMOUNT;
				if (event.player.worldObj.provider instanceof WorldProviderMars)
					amount = Config.MARS_RADIATION_AMOUNT;
				if (event.player.worldObj.provider instanceof WorldProviderAsteroids)
					amount = Config.ASTEROIDS_RADIATION_AMOUNT;
				if (event.player.worldObj.provider instanceof WorldProviderVenus)
					amount = Config.VENUS_RADIATION_AMOUNT;
				if (player.worldObj.provider instanceof WorldProviderSpaceStation)
					amount = Config.SPACE_STATION_RADIATION_AMOUNT;
				PlayerUtilties.sendMessage(player, "" + EnumChatFormatting.AQUA + EnumChatFormatting.BOLD + player.getName() + EnumChatFormatting.DARK_RED + ", " + TranslateUtilities.translate("gui.radiation.subject.message") + " " + amount + "% "
						+ TranslateUtilities.translate("gui.radiation.type.message") + "");
				PlayerUtilties.sendMessage(player, "" + EnumChatFormatting.AQUA + EnumChatFormatting.BOLD + player.getName() + EnumChatFormatting.DARK_GREEN + ", " + TranslateUtilities.translate("gui.radiation.reverse.message") + "!");
				PlayerUtilties.sendMessage(player, "" + EnumChatFormatting.AQUA + EnumChatFormatting.BOLD + player.getName() + EnumChatFormatting.GOLD + ", " + TranslateUtilities.translate("gui.radiation.cancel.message") + "!");
			}
		}
	}
}
