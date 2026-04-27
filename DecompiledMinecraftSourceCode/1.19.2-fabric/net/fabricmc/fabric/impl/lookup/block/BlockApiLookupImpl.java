/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.lookup.block;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.custom.ApiLookupMap;
import net.fabricmc.fabric.api.lookup.v1.custom.ApiProviderMap;
import net.fabricmc.fabric.mixin.lookup.BlockEntityTypeAccessor;
import net.minecraft.class_1937;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2586;
import net.minecraft.class_2591;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class BlockApiLookupImpl<A, C> implements BlockApiLookup<A, C> {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabric-api-lookup-api-v1/block");
	private static final ApiLookupMap<BlockApiLookup<?, ?>> LOOKUPS = ApiLookupMap.create(BlockApiLookupImpl::new);

	@SuppressWarnings("unchecked")
	public static <A, C> BlockApiLookup<A, C> get(class_2960 lookupId, Class<A> apiClass, Class<C> contextClass) {
		return (BlockApiLookup<A, C>) LOOKUPS.getLookup(lookupId, apiClass, contextClass);
	}

	private final class_2960 identifier;
	private final Class<A> apiClass;
	private final Class<C> contextClass;
	private final ApiProviderMap<class_2248, BlockApiProvider<A, C>> providerMap = ApiProviderMap.create();
	private final List<BlockApiProvider<A, C>> fallbackProviders = new CopyOnWriteArrayList<>();

	@SuppressWarnings("unchecked")
	private BlockApiLookupImpl(class_2960 identifier, Class<?> apiClass, Class<?> contextClass) {
		this.identifier = identifier;
		this.apiClass = (Class<A>) apiClass;
		this.contextClass = (Class<C>) contextClass;
	}

	@Nullable
	@Override
	public A find(class_1937 world, class_2338 pos, @Nullable class_2680 state, @Nullable class_2586 blockEntity, C context) {
		Objects.requireNonNull(world, "World may not be null.");
		Objects.requireNonNull(pos, "BlockPos may not be null.");
		// Providers have the final say whether a null context is allowed.

		// Get the block state and the block entity
		if (blockEntity == null) {
			if (state == null) {
				state = world.method_8320(pos);
			}

			if (state.method_31709()) {
				blockEntity = world.method_8321(pos);
			}
		} else {
			if (state == null) {
				state = blockEntity.method_11010();
			}
		}

		@Nullable
		BlockApiProvider<A, C> provider = getProvider(state.method_26204());
		A instance = null;

		if (provider != null) {
			instance = provider.find(world, pos, state, blockEntity, context);
		}

		if (instance != null) {
			return instance;
		}

		// Query the fallback providers
		for (BlockApiProvider<A, C> fallbackProvider : fallbackProviders) {
			instance = fallbackProvider.find(world, pos, state, blockEntity, context);

			if (instance != null) {
				return instance;
			}
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void registerSelf(class_2591<?>... blockEntityTypes) {
		for (class_2591<?> blockEntityType : blockEntityTypes) {
			class_2248 supportBlock = ((BlockEntityTypeAccessor) blockEntityType).getBlocks().iterator().next();
			Objects.requireNonNull(supportBlock, "Could not get a support block for block entity type.");
			class_2586 blockEntity = blockEntityType.method_11032(class_2338.field_10980, supportBlock.method_9564());
			Objects.requireNonNull(blockEntity, "Instantiated block entity may not be null.");

			if (!apiClass.isAssignableFrom(blockEntity.getClass())) {
				String errorMessage = String.format(
						"Failed to register self-implementing block entities. API class %s is not assignable from block entity class %s.",
						apiClass.getCanonicalName(),
						blockEntity.getClass().getCanonicalName()
				);
				throw new IllegalArgumentException(errorMessage);
			}
		}

		registerForBlockEntities((blockEntity, context) -> (A) blockEntity, blockEntityTypes);
	}

	@Override
	public void registerForBlocks(BlockApiProvider<A, C> provider, class_2248... blocks) {
		Objects.requireNonNull(provider, "BlockApiProvider may not be null.");

		if (blocks.length == 0) {
			throw new IllegalArgumentException("Must register at least one Block instance with a BlockApiProvider.");
		}

		for (class_2248 block : blocks) {
			Objects.requireNonNull(block, "Encountered null block while registering a block API provider mapping.");

			if (providerMap.putIfAbsent(block, provider) != null) {
				LOGGER.warn("Encountered duplicate API provider registration for block: " + class_7923.field_41175.method_10221(block));
			}
		}
	}

	@Override
	public void registerForBlockEntities(BlockEntityApiProvider<A, C> provider, class_2591<?>... blockEntityTypes) {
		Objects.requireNonNull(provider, "BlockEntityApiProvider may not be null.");

		if (blockEntityTypes.length == 0) {
			throw new IllegalArgumentException("Must register at least one BlockEntityType instance with a BlockEntityApiProvider.");
		}

		BlockApiProvider<A, C> nullCheckedProvider = (world, pos, state, blockEntity, context) -> {
			if (blockEntity == null) {
				return null;
			} else {
				return provider.find(blockEntity, context);
			}
		};

		for (class_2591<?> blockEntityType : blockEntityTypes) {
			Objects.requireNonNull(blockEntityType, "Encountered null block entity type while registering a block entity API provider mapping.");

			class_2248[] blocks = ((BlockEntityTypeAccessor) blockEntityType).getBlocks().toArray(new class_2248[0]);
			registerForBlocks(nullCheckedProvider, blocks);
		}
	}

	@Override
	public void registerFallback(BlockApiProvider<A, C> fallbackProvider) {
		Objects.requireNonNull(fallbackProvider, "BlockApiProvider may not be null.");

		fallbackProviders.add(fallbackProvider);
	}

	@Override
	public class_2960 getId() {
		return identifier;
	}

	@Override
	public Class<A> apiClass() {
		return apiClass;
	}

	@Override
	public Class<C> contextClass() {
		return contextClass;
	}

	@Override
	@Nullable
	public BlockApiProvider<A, C> getProvider(class_2248 block) {
		return providerMap.get(block);
	}

	public List<BlockApiProvider<A, C>> getFallbackProviders() {
		return fallbackProviders;
	}
}
