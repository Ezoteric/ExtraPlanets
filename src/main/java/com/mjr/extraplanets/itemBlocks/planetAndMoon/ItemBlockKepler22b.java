package com.mjr.extraplanets.itemBlocks.planetAndMoon;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemBlockKepler22b extends ItemBlock
{
	public ItemBlockKepler22b(Block block)
	{
		super(block);
		this.setMaxDamage(0);
		this.setHasSubtypes(true);
	}

	@Override
	public int getMetadata(int meta)
	{
		return meta;
	}

	@Override
	public String getUnlocalizedName(ItemStack itemstack)
	{
		String name = "";

		switch (itemstack.getItemDamage())
		{
		case 0:
		{
			name = "Dirt";
			break;
		}
		case 1:
		{
			name = "Stone";
			break;
		}
		case 2:
		{
			name = "ore_iron";
			break;
		}
		case 3:
		{
			name = "ore_tin";
			break;
		}
		case 4:
		{
			name = "ore_copper";
			break;
		}
		case 5:
		{
			name = "OreDenseCoal";
			break;
		}
		case 6:
		{
			name = "OreBlueDiamonds";
			break;
		}
		case 7:
		{
			name = "OreRedDiamonds";
			break;
		}
		case 8:
		{
			name = "OrePurpleDiamonds";
			break;
		}
		case 9:
		{
			name = "OreYellowDiamonds";
			break;
		}
		case 10:
		{
			name = "OreGreenDiamonds";
			break;
		}
		case 11:
		{
			name = "stone_bricks";
			break;
		}
		case 12:
		{
			name = "Cobblestone";
			break;
		}
		default:
			name = "null";
		}
		return this.block.getUnlocalizedName() + "." + name;
	}

	@Override
	public String getUnlocalizedName()
	{
		return this.block.getUnlocalizedName() + ".0";
	}
}