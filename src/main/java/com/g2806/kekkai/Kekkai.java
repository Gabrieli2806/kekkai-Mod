package com.g2806.kekkai;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.ShapeContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Kekkai implements ModInitializer {
	public static final String MOD_ID = "kekkai";
	public static final Block SHIMENAWA = new ShimenawaBlock(Block.Settings.create().strength(0.5f).nonOpaque());
	public static final Block OFUDA = new OfudaBlock(Block.Settings.create().strength(0.5f).nonOpaque());
	public static final Item SHIMENAWA_ITEM = register(new BlockItem(SHIMENAWA, new Item.Settings()), "shimenawa");
	public static final Item OFUDA_ITEM = register(new BlockItem(OFUDA, new Item.Settings()), "ofuda");
	public static final BlockEntityType<KekkaiBlockEntity> KEKKAI_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MOD_ID, "kekkai_block_entity"),
			BlockEntityType.Builder.create(KekkaiBlockEntity::new, SHIMENAWA, OFUDA).build(null)
	);

	// Thread-safe list for storing positions of Shimenawa and Ofuda blocks
	private static final List<BlockPos> KEKKAI_POSITIONS = new CopyOnWriteArrayList<>();

	public static Item register(Item item, String id) {
		Identifier itemID = Identifier.of(MOD_ID, id);
		return Registry.register(Registries.ITEM, itemID, item);
	}

	@Override
	public void onInitialize() {
		Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "shimenawa"), SHIMENAWA);
		Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "ofuda"), OFUDA);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(group -> {
			group.add(SHIMENAWA_ITEM);
			group.add(OFUDA_ITEM);
		});

		// Register spawn prevention event
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof HostileEntity && !world.isClient) {
				Vec3d entityPos = entity.getPos();
				for (BlockPos kekkaiPos : KEKKAI_POSITIONS) {
					if (entityPos.distanceTo(new Vec3d(
							kekkaiPos.getX() + 0.5, kekkaiPos.getY(), kekkaiPos.getZ() + 0.5)) <= 100.0) {
						// Prevent spawn by removing the entity
						entity.discard();
						break;
					}
				}
			}
		});
	}

	public static class ShimenawaBlock extends Block implements BlockEntityProvider {
		private static final double REPEL_RADIUS = 100.0;
		public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
		private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 2.0);
		private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 14.0, 16.0, 16.0, 16.0);
		private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(14.0, 0.0, 0.0, 16.0, 16.0, 16.0);
		private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 2.0, 16.0, 16.0);

		public ShimenawaBlock(Settings settings) {
			super(settings);
			this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
		}

		@Override
		protected void appendProperties(net.minecraft.state.StateManager.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getPlacementState(ItemPlacementContext ctx) {
			Direction side = ctx.getSide();
			Direction facing;
			// If the block is placed on the top or bottom, use the player's horizontal facing
			if (side == Direction.UP || side == Direction.DOWN) {
				facing = ctx.getHorizontalPlayerFacing(); // Player's horizontal facing direction
			} else {
				facing = side.getOpposite(); // Use the opposite of the clicked side for horizontal placement
			}
			return this.getDefaultState().with(FACING, facing);
		}

		@Override
		public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return switch (state.get(FACING)) {
				case SOUTH -> SOUTH_SHAPE;
				case EAST -> EAST_SHAPE;
				case WEST -> WEST_SHAPE;
				default -> NORTH_SHAPE;
			};
		}

		@Override
		public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return getOutlineShape(state, world, pos, context);
		}

		@Override
		public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
			return true;
		}

		@Override
		public BlockRenderType getRenderType(BlockState state) {
			return BlockRenderType.MODEL;
		}

		@Override
		public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new KekkaiBlockEntity(pos, state);
		}

		@Override
		public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
			if (!world.isClient) {
				KEKKAI_POSITIONS.add(pos.toImmutable());
				world.scheduleBlockTick(pos, this, 20);
			}
		}

		@Override
		public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
			if (!world.isClient && state.getBlock() != newState.getBlock()) {
				KEKKAI_POSITIONS.remove(pos);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}

		@Override
		public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos,
								  net.minecraft.util.math.random.Random random) {
			super.scheduledTick(state, world, pos, random);
			Box area = new Box(pos).expand(REPEL_RADIUS);
			for (HostileEntity entity : world.getEntitiesByClass(HostileEntity.class, area,
					e -> e instanceof MobEntity)) {
				MobEntity mob = (MobEntity) entity;
				Vec3d blockPos = new Vec3d(
						pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				Vec3d entityPos = mob.getPos();
				double distance = entityPos.distanceTo(blockPos);

				if (distance < REPEL_RADIUS && distance > 0) {
					Vec3d direction = entityPos.subtract(blockPos).normalize();
					Vec3d targetPos = blockPos.add(direction.multiply(REPEL_RADIUS));
					EntityNavigation navigation = mob.getNavigation();
					navigation.startMovingTo(targetPos.x, targetPos.y, targetPos.z, 1.4);
				}
			}
			world.scheduleBlockTick(pos, this, 20);
		}
	}

	public static class OfudaBlock extends Block implements BlockEntityProvider {
		public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
		private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 2.0);
		private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 14.0, 16.0, 16.0, 16.0);
		private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(14.0, 0.0, 0.0, 16.0, 16.0, 16.0);
		private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 2.0, 16.0, 16.0);

		public OfudaBlock(Settings settings) {
			super(settings);
			this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
		}

		@Override
		protected void appendProperties(net.minecraft.state.StateManager.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getPlacementState(ItemPlacementContext ctx) {
			Direction side = ctx.getSide();
			Direction facing;
			// If the block is placed on the top or bottom, use the player's horizontal facing
			if (side == Direction.UP || side == Direction.DOWN) {
				facing = ctx.getHorizontalPlayerFacing(); // Player's horizontal facing direction
			} else {
				facing = side.getOpposite(); // Use the opposite of the clicked side for horizontal placement
			}
			return this.getDefaultState().with(FACING, facing);
		}

		@Override
		public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return switch (state.get(FACING)) {
				case SOUTH -> SOUTH_SHAPE;
				case EAST -> EAST_SHAPE;
				case WEST -> WEST_SHAPE;
				default -> NORTH_SHAPE;
			};
		}

		@Override
		public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return getOutlineShape(state, world, pos, context);
		}

		@Override
		public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
			return true;
		}

		@Override
		public BlockRenderType getRenderType(BlockState state) {
			return BlockRenderType.MODEL;
		}

		@Override
		public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new KekkaiBlockEntity(pos, state);
		}

		@Override
		public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
			if (!world.isClient) {
				KEKKAI_POSITIONS.add(pos.toImmutable());
			}
		}

		@Override
		public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
			if (!world.isClient && state.getBlock() != newState.getBlock()) {
				KEKKAI_POSITIONS.remove(pos);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	public static class KekkaiBlockEntity extends BlockEntity {
		public KekkaiBlockEntity(BlockPos pos, BlockState state) {
			super(KEKKAI_BLOCK_ENTITY, pos, state);
		}
	}
}