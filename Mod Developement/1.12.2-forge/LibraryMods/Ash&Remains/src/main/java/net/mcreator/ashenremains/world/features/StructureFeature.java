package net.mcreator.ashenremains.world.features;

import com.mojang.serialization.Codec;
import net.mcreator.ashenremains.world.features.configurations.StructureFeatureConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@EventBusSubscriber
public class StructureFeature extends Feature<StructureFeatureConfiguration> {
   public static final DeferredRegister<Feature<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.FEATURES, "ashenremains");
   public static final RegistryObject<Feature<?>> STRUCTURE_FEATURE = REGISTRY.register(
      "structure_feature", () -> new StructureFeature(StructureFeatureConfiguration.CODEC)
   );

   public StructureFeature(Codec<StructureFeatureConfiguration> codec) {
      super(codec);
   }

   public boolean m_142674_(FeaturePlaceContext<StructureFeatureConfiguration> context) {
      RandomSource random = context.m_225041_();
      WorldGenLevel worldGenLevel = context.m_159774_();
      StructureFeatureConfiguration config = (StructureFeatureConfiguration)context.m_159778_();
      Rotation rotation = config.randomRotation() ? Rotation.m_221990_(random) : Rotation.NONE;
      Mirror mirror = config.randomMirror() ? Mirror.values()[random.m_188503_(2)] : Mirror.NONE;
      BlockPos placePos = context.m_159777_().m_121955_(config.offset());
      StructureTemplateManager structureManager = worldGenLevel.m_6018_().m_7654_().m_236738_();
      StructureTemplate template = structureManager.m_230359_(config.structure());
      StructurePlaceSettings placeSettings = new StructurePlaceSettings()
         .m_74379_(rotation)
         .m_74377_(mirror)
         .m_230324_(random)
         .m_74392_(false)
         .m_74383_(new BlockIgnoreProcessor(config.ignoredBlocks().m_203614_().map(Holder::get).toList()));
      template.m_230328_(worldGenLevel, placePos, placePos, placeSettings, random, 4);
      return true;
   }
}
