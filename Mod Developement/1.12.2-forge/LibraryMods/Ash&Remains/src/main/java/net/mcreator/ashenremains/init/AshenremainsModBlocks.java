package net.mcreator.ashenremains.init;

import net.mcreator.ashenremains.block.AcaciaSproutBlock;
import net.mcreator.ashenremains.block.ActivatedSoulSandBlock;
import net.mcreator.ashenremains.block.ActivatedSoulSoilBlock;
import net.mcreator.ashenremains.block.AshBlock;
import net.mcreator.ashenremains.block.AshLayer10Block;
import net.mcreator.ashenremains.block.AshLayer12Block;
import net.mcreator.ashenremains.block.AshLayer14Block;
import net.mcreator.ashenremains.block.AshLayer2Block;
import net.mcreator.ashenremains.block.AshLayer4Block;
import net.mcreator.ashenremains.block.AshLayer6Block;
import net.mcreator.ashenremains.block.AshLayer8Block;
import net.mcreator.ashenremains.block.AshyGrassBlock;
import net.mcreator.ashenremains.block.BirchSproutBlock;
import net.mcreator.ashenremains.block.BlossomingCobblestoneBlock;
import net.mcreator.ashenremains.block.BlossomingStoneBricksBlock;
import net.mcreator.ashenremains.block.CharredDoorBlock;
import net.mcreator.ashenremains.block.CharredRootsBlock;
import net.mcreator.ashenremains.block.CharredStrippedLogBlock;
import net.mcreator.ashenremains.block.CharredStrippedWoodBlock;
import net.mcreator.ashenremains.block.CharredTrapDoorBlock;
import net.mcreator.ashenremains.block.CharredWoodFenceBlock;
import net.mcreator.ashenremains.block.CharredWoodFenceGateBlock;
import net.mcreator.ashenremains.block.CharredWoodLogBlock;
import net.mcreator.ashenremains.block.CharredWoodPlanksBlock;
import net.mcreator.ashenremains.block.CharredWoodSlabBlock;
import net.mcreator.ashenremains.block.CharredWoodStairsBlock;
import net.mcreator.ashenremains.block.CharredWoodWoodBlock;
import net.mcreator.ashenremains.block.CherrySproutBlock;
import net.mcreator.ashenremains.block.DarkOakSproutBlock;
import net.mcreator.ashenremains.block.EastFireBlock;
import net.mcreator.ashenremains.block.FakeCobblestoneBlock;
import net.mcreator.ashenremains.block.FakeStoneBricksBlock;
import net.mcreator.ashenremains.block.FlamingAshBlock;
import net.mcreator.ashenremains.block.FlamingAshLayer10Block;
import net.mcreator.ashenremains.block.FlamingAshLayer12Block;
import net.mcreator.ashenremains.block.FlamingAshLayer14Block;
import net.mcreator.ashenremains.block.FlamingAshLayer2Block;
import net.mcreator.ashenremains.block.FlamingAshLayer4Block;
import net.mcreator.ashenremains.block.FlamingAshLayer6Block;
import net.mcreator.ashenremains.block.FlamingAshLayer8Block;
import net.mcreator.ashenremains.block.FloweringGrassBlock;
import net.mcreator.ashenremains.block.JungleSproutBlock;
import net.mcreator.ashenremains.block.NorthFireBlock;
import net.mcreator.ashenremains.block.OakSproutBlock;
import net.mcreator.ashenremains.block.SouthFireBlock;
import net.mcreator.ashenremains.block.SpruceSproutBlock;
import net.mcreator.ashenremains.block.StrangeFireBlock;
import net.mcreator.ashenremains.block.WeirdFireBlock;
import net.mcreator.ashenremains.block.WestFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AshenremainsModBlocks {
   public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, "ashenremains");
   public static final RegistryObject<Block> FLOWERING_GRASS = REGISTRY.register("flowering_grass", () -> new FloweringGrassBlock());
   public static final RegistryObject<Block> OAK_SPROUT = REGISTRY.register("oak_sprout", () -> new OakSproutBlock());
   public static final RegistryObject<Block> DARK_OAK_SPROUT = REGISTRY.register("dark_oak_sprout", () -> new DarkOakSproutBlock());
   public static final RegistryObject<Block> BIRCH_SPROUT = REGISTRY.register("birch_sprout", () -> new BirchSproutBlock());
   public static final RegistryObject<Block> ACACIA_SPROUT = REGISTRY.register("acacia_sprout", () -> new AcaciaSproutBlock());
   public static final RegistryObject<Block> JUNGLE_SPROUT = REGISTRY.register("jungle_sprout", () -> new JungleSproutBlock());
   public static final RegistryObject<Block> SPRUCE_SPROUT = REGISTRY.register("spruce_sprout", () -> new SpruceSproutBlock());
   public static final RegistryObject<Block> ASH = REGISTRY.register("ash", () -> new AshBlock());
   public static final RegistryObject<Block> ASH_LAYER_14 = REGISTRY.register("ash_layer_14", () -> new AshLayer14Block());
   public static final RegistryObject<Block> ASH_LAYER_12 = REGISTRY.register("ash_layer_12", () -> new AshLayer12Block());
   public static final RegistryObject<Block> ASH_LAYER_10 = REGISTRY.register("ash_layer_10", () -> new AshLayer10Block());
   public static final RegistryObject<Block> ASH_LAYER_8 = REGISTRY.register("ash_layer_8", () -> new AshLayer8Block());
   public static final RegistryObject<Block> ASH_LAYER_6 = REGISTRY.register("ash_layer_6", () -> new AshLayer6Block());
   public static final RegistryObject<Block> ASH_LAYER_4 = REGISTRY.register("ash_layer_4", () -> new AshLayer4Block());
   public static final RegistryObject<Block> ASH_LAYER_2 = REGISTRY.register("ash_layer_2", () -> new AshLayer2Block());
   public static final RegistryObject<Block> WEIRD_FIRE = REGISTRY.register("weird_fire", () -> new WeirdFireBlock());
   public static final RegistryObject<Block> EAST_FIRE = REGISTRY.register("east_fire", () -> new EastFireBlock());
   public static final RegistryObject<Block> WEST_FIRE = REGISTRY.register("west_fire", () -> new WestFireBlock());
   public static final RegistryObject<Block> NORTH_FIRE = REGISTRY.register("north_fire", () -> new NorthFireBlock());
   public static final RegistryObject<Block> SOUTH_FIRE = REGISTRY.register("south_fire", () -> new SouthFireBlock());
   public static final RegistryObject<Block> CHARRED_WOOD_WOOD = REGISTRY.register("charred_wood_wood", () -> new CharredWoodWoodBlock());
   public static final RegistryObject<Block> CHARRED_WOOD_LOG = REGISTRY.register("charred_wood_log", () -> new CharredWoodLogBlock());
   public static final RegistryObject<Block> CHARRED_WOOD_PLANKS = REGISTRY.register("charred_wood_planks", () -> new CharredWoodPlanksBlock());
   public static final RegistryObject<Block> CHARRED_WOOD_STAIRS = REGISTRY.register("charred_wood_stairs", () -> new CharredWoodStairsBlock());
   public static final RegistryObject<Block> CHARRED_WOOD_SLAB = REGISTRY.register("charred_wood_slab", () -> new CharredWoodSlabBlock());
   public static final RegistryObject<Block> CHARRED_WOOD_FENCE = REGISTRY.register("charred_wood_fence", () -> new CharredWoodFenceBlock());
   public static final RegistryObject<Block> CHARRED_WOOD_FENCE_GATE = REGISTRY.register("charred_wood_fence_gate", () -> new CharredWoodFenceGateBlock());
   public static final RegistryObject<Block> CHARRED_TRAP_DOOR = REGISTRY.register("charred_trap_door", () -> new CharredTrapDoorBlock());
   public static final RegistryObject<Block> CHARRED_DOOR = REGISTRY.register("charred_door", () -> new CharredDoorBlock());
   public static final RegistryObject<Block> CHARRED_STRIPPED_WOOD = REGISTRY.register("charred_stripped_wood", () -> new CharredStrippedWoodBlock());
   public static final RegistryObject<Block> CHARRED_STRIPPED_LOG = REGISTRY.register("charred_stripped_log", () -> new CharredStrippedLogBlock());
   public static final RegistryObject<Block> FLAMING_ASH = REGISTRY.register("flaming_ash", () -> new FlamingAshBlock());
   public static final RegistryObject<Block> FLAMING_ASH_LAYER_14 = REGISTRY.register("flaming_ash_layer_14", () -> new FlamingAshLayer14Block());
   public static final RegistryObject<Block> FLAMING_ASH_LAYER_12 = REGISTRY.register("flaming_ash_layer_12", () -> new FlamingAshLayer12Block());
   public static final RegistryObject<Block> FLAMING_ASH_LAYER_10 = REGISTRY.register("flaming_ash_layer_10", () -> new FlamingAshLayer10Block());
   public static final RegistryObject<Block> FLAMING_ASH_LAYER_8 = REGISTRY.register("flaming_ash_layer_8", () -> new FlamingAshLayer8Block());
   public static final RegistryObject<Block> FLAMING_ASH_LAYER_6 = REGISTRY.register("flaming_ash_layer_6", () -> new FlamingAshLayer6Block());
   public static final RegistryObject<Block> FLAMING_ASH_LAYER_4 = REGISTRY.register("flaming_ash_layer_4", () -> new FlamingAshLayer4Block());
   public static final RegistryObject<Block> FLAMING_ASH_LAYER_2 = REGISTRY.register("flaming_ash_layer_2", () -> new FlamingAshLayer2Block());
   public static final RegistryObject<Block> STRANGE_FIRE = REGISTRY.register("strange_fire", () -> new StrangeFireBlock());
   public static final RegistryObject<Block> ACTIVATED_SOUL_SAND = REGISTRY.register("activated_soul_sand", () -> new ActivatedSoulSandBlock());
   public static final RegistryObject<Block> ACTIVATED_SOUL_SOIL = REGISTRY.register("activated_soul_soil", () -> new ActivatedSoulSoilBlock());
   public static final RegistryObject<Block> ASHY_GRASS = REGISTRY.register("ashy_grass", () -> new AshyGrassBlock());
   public static final RegistryObject<Block> CHARRED_ROOTS = REGISTRY.register("charred_roots", () -> new CharredRootsBlock());
   public static final RegistryObject<Block> FAKE_STONE_BRICKS = REGISTRY.register("fake_stone_bricks", () -> new FakeStoneBricksBlock());
   public static final RegistryObject<Block> FAKE_COBBLESTONE = REGISTRY.register("fake_cobblestone", () -> new FakeCobblestoneBlock());
   public static final RegistryObject<Block> BLOSSOMING_STONE_BRICKS = REGISTRY.register("blossoming_stone_bricks", () -> new BlossomingStoneBricksBlock());
   public static final RegistryObject<Block> BLOSSOMING_COBBLESTONE = REGISTRY.register("blossoming_cobblestone", () -> new BlossomingCobblestoneBlock());
   public static final RegistryObject<Block> CHERRY_SPROUT = REGISTRY.register("cherry_sprout", () -> new CherrySproutBlock());

   @EventBusSubscriber(
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static class ClientSideHandler {
      @SubscribeEvent
      public static void blockColorLoad(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
         FloweringGrassBlock.blockColorLoad(event);
      }

      @SubscribeEvent
      public static void itemColorLoad(Item event) {
         FloweringGrassBlock.itemColorLoad(event);
      }
   }
}
