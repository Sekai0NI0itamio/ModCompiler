package net.mcreator.ashenremains.init;

import net.mcreator.ashenremains.item.AcaciaFrondItem;
import net.mcreator.ashenremains.item.AshBallItem;
import net.mcreator.ashenremains.item.BirchFrondItem;
import net.mcreator.ashenremains.item.BucketOfAshItem;
import net.mcreator.ashenremains.item.CherrySeedItem;
import net.mcreator.ashenremains.item.DarkAcornItem;
import net.mcreator.ashenremains.item.FlintAndTinderItem;
import net.mcreator.ashenremains.item.JunglePodItem;
import net.mcreator.ashenremains.item.OakAcornItem;
import net.mcreator.ashenremains.item.PineConeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AshenremainsModItems {
   public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, "ashenremains");
   public static final RegistryObject<Item> FLOWERING_GRASS = block(AshenremainsModBlocks.FLOWERING_GRASS);
   public static final RegistryObject<Item> OAK_ACORN = REGISTRY.register("oak_acorn", () -> new OakAcornItem());
   public static final RegistryObject<Item> BIRCH_FROND = REGISTRY.register("birch_frond", () -> new BirchFrondItem());
   public static final RegistryObject<Item> ACACIA_FROND = REGISTRY.register("acacia_frond", () -> new AcaciaFrondItem());
   public static final RegistryObject<Item> JUNGLE_POD = REGISTRY.register("jungle_pod", () -> new JunglePodItem());
   public static final RegistryObject<Item> DARK_ACORN = REGISTRY.register("dark_acorn", () -> new DarkAcornItem());
   public static final RegistryObject<Item> PINE_CONE = REGISTRY.register("pine_cone", () -> new PineConeItem());
   public static final RegistryObject<Item> OAK_SPROUT = block(AshenremainsModBlocks.OAK_SPROUT);
   public static final RegistryObject<Item> DARK_OAK_SPROUT = block(AshenremainsModBlocks.DARK_OAK_SPROUT);
   public static final RegistryObject<Item> BIRCH_SPROUT = block(AshenremainsModBlocks.BIRCH_SPROUT);
   public static final RegistryObject<Item> ACACIA_SPROUT = block(AshenremainsModBlocks.ACACIA_SPROUT);
   public static final RegistryObject<Item> JUNGLE_SPROUT = block(AshenremainsModBlocks.JUNGLE_SPROUT);
   public static final RegistryObject<Item> SPRUCE_SPROUT = block(AshenremainsModBlocks.SPRUCE_SPROUT);
   public static final RegistryObject<Item> ASH = block(AshenremainsModBlocks.ASH);
   public static final RegistryObject<Item> ASH_LAYER_14 = block(AshenremainsModBlocks.ASH_LAYER_14);
   public static final RegistryObject<Item> ASH_LAYER_12 = block(AshenremainsModBlocks.ASH_LAYER_12);
   public static final RegistryObject<Item> ASH_LAYER_10 = block(AshenremainsModBlocks.ASH_LAYER_10);
   public static final RegistryObject<Item> ASH_LAYER_8 = block(AshenremainsModBlocks.ASH_LAYER_8);
   public static final RegistryObject<Item> ASH_LAYER_6 = block(AshenremainsModBlocks.ASH_LAYER_6);
   public static final RegistryObject<Item> ASH_LAYER_4 = block(AshenremainsModBlocks.ASH_LAYER_4);
   public static final RegistryObject<Item> ASH_LAYER_2 = block(AshenremainsModBlocks.ASH_LAYER_2);
   public static final RegistryObject<Item> BUCKET_OF_ASH = REGISTRY.register("bucket_of_ash", () -> new BucketOfAshItem());
   public static final RegistryObject<Item> WEIRD_FIRE = block(AshenremainsModBlocks.WEIRD_FIRE);
   public static final RegistryObject<Item> EAST_FIRE = block(AshenremainsModBlocks.EAST_FIRE);
   public static final RegistryObject<Item> WEST_FIRE = block(AshenremainsModBlocks.WEST_FIRE);
   public static final RegistryObject<Item> NORTH_FIRE = block(AshenremainsModBlocks.NORTH_FIRE);
   public static final RegistryObject<Item> SOUTH_FIRE = block(AshenremainsModBlocks.SOUTH_FIRE);
   public static final RegistryObject<Item> FLINT_AND_TINDER = REGISTRY.register("flint_and_tinder", () -> new FlintAndTinderItem());
   public static final RegistryObject<Item> CHARRED_WOOD_WOOD = block(AshenremainsModBlocks.CHARRED_WOOD_WOOD);
   public static final RegistryObject<Item> CHARRED_WOOD_LOG = block(AshenremainsModBlocks.CHARRED_WOOD_LOG);
   public static final RegistryObject<Item> CHARRED_WOOD_PLANKS = block(AshenremainsModBlocks.CHARRED_WOOD_PLANKS);
   public static final RegistryObject<Item> CHARRED_WOOD_STAIRS = block(AshenremainsModBlocks.CHARRED_WOOD_STAIRS);
   public static final RegistryObject<Item> CHARRED_WOOD_SLAB = block(AshenremainsModBlocks.CHARRED_WOOD_SLAB);
   public static final RegistryObject<Item> CHARRED_WOOD_FENCE = block(AshenremainsModBlocks.CHARRED_WOOD_FENCE);
   public static final RegistryObject<Item> CHARRED_WOOD_FENCE_GATE = block(AshenremainsModBlocks.CHARRED_WOOD_FENCE_GATE);
   public static final RegistryObject<Item> CHARRED_TRAP_DOOR = block(AshenremainsModBlocks.CHARRED_TRAP_DOOR);
   public static final RegistryObject<Item> CHARRED_DOOR = doubleBlock(AshenremainsModBlocks.CHARRED_DOOR);
   public static final RegistryObject<Item> CHARRED_STRIPPED_WOOD = block(AshenremainsModBlocks.CHARRED_STRIPPED_WOOD);
   public static final RegistryObject<Item> CHARRED_STRIPPED_LOG = block(AshenremainsModBlocks.CHARRED_STRIPPED_LOG);
   public static final RegistryObject<Item> FLAMING_ASH = block(AshenremainsModBlocks.FLAMING_ASH);
   public static final RegistryObject<Item> FLAMING_ASH_LAYER_14 = block(AshenremainsModBlocks.FLAMING_ASH_LAYER_14);
   public static final RegistryObject<Item> FLAMING_ASH_LAYER_12 = block(AshenremainsModBlocks.FLAMING_ASH_LAYER_12);
   public static final RegistryObject<Item> FLAMING_ASH_LAYER_10 = block(AshenremainsModBlocks.FLAMING_ASH_LAYER_10);
   public static final RegistryObject<Item> FLAMING_ASH_LAYER_8 = block(AshenremainsModBlocks.FLAMING_ASH_LAYER_8);
   public static final RegistryObject<Item> FLAMING_ASH_LAYER_6 = block(AshenremainsModBlocks.FLAMING_ASH_LAYER_6);
   public static final RegistryObject<Item> FLAMING_ASH_LAYER_4 = block(AshenremainsModBlocks.FLAMING_ASH_LAYER_4);
   public static final RegistryObject<Item> FLAMING_ASH_LAYER_2 = block(AshenremainsModBlocks.FLAMING_ASH_LAYER_2);
   public static final RegistryObject<Item> STRANGE_FIRE = block(AshenremainsModBlocks.STRANGE_FIRE);
   public static final RegistryObject<Item> GRIEFER_SPAWN_EGG = REGISTRY.register(
      "griefer_spawn_egg", () -> new ForgeSpawnEggItem(AshenremainsModEntities.GRIEFER, -10066330, -26368, new Properties())
   );
   public static final RegistryObject<Item> ACTIVATED_SOUL_SAND = block(AshenremainsModBlocks.ACTIVATED_SOUL_SAND);
   public static final RegistryObject<Item> ACTIVATED_SOUL_SOIL = block(AshenremainsModBlocks.ACTIVATED_SOUL_SOIL);
   public static final RegistryObject<Item> ASHY_GRASS = block(AshenremainsModBlocks.ASHY_GRASS);
   public static final RegistryObject<Item> CHARRED_ROOTS = block(AshenremainsModBlocks.CHARRED_ROOTS);
   public static final RegistryObject<Item> FAKE_STONE_BRICKS = block(AshenremainsModBlocks.FAKE_STONE_BRICKS);
   public static final RegistryObject<Item> FAKE_COBBLESTONE = block(AshenremainsModBlocks.FAKE_COBBLESTONE);
   public static final RegistryObject<Item> BLOSSOMING_STONE_BRICKS = block(AshenremainsModBlocks.BLOSSOMING_STONE_BRICKS);
   public static final RegistryObject<Item> BLOSSOMING_COBBLESTONE = block(AshenremainsModBlocks.BLOSSOMING_COBBLESTONE);
   public static final RegistryObject<Item> CHERRY_SPROUT = block(AshenremainsModBlocks.CHERRY_SPROUT);
   public static final RegistryObject<Item> CHERRY_SEED = REGISTRY.register("cherry_seed", () -> new CherrySeedItem());
   public static final RegistryObject<Item> ASH_BALL = REGISTRY.register("ash_ball", () -> new AshBallItem());

   private static RegistryObject<Item> block(RegistryObject<Block> block) {
      return REGISTRY.register(block.getId().m_135815_(), () -> new BlockItem((Block)block.get(), new Properties()));
   }

   private static RegistryObject<Item> doubleBlock(RegistryObject<Block> block) {
      return REGISTRY.register(block.getId().m_135815_(), () -> new DoubleHighBlockItem((Block)block.get(), new Properties()));
   }
}
