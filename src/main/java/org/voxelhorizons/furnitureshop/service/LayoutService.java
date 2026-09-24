package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.FurnitureManager;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.RecordedBlock;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LayoutService {
    private final FurnitureManager furniture;

    public LayoutService(FurnitureManager furniture) { this.furniture = furniture; }

    @SuppressWarnings("deprecation")
    public LayoutSnapshot capture(ShopCuboid cuboid) {
        List<RecordedBlock> blocks = new ArrayList<RecordedBlock>();
        for (Block block : cuboid.blocks()) {
            boolean collision = furniture.byBlock(block).isPresent();
            blocks.add(new RecordedBlock(block.getX() - cuboid.minX(), block.getY() - cuboid.minY(),
                    block.getZ() - cuboid.minZ(), collision ? Material.AIR.name() : block.getType().name(),
                    collision ? 0 : block.getData(), collision ? null : BlockDataSupport.capture(block)));
        }
        List<RecordedFurniture> recorded = new ArrayList<RecordedFurniture>();
        for (FurnitureInstance instance : furniture.instances()) {
            Location location = instance.location();
            if (!cuboid.contains(location)) continue;
            recorded.add(new RecordedFurniture(instance.definitionId().toString(),
                    location.getX() - cuboid.minX(), location.getY() - cuboid.minY(),
                    location.getZ() - cuboid.minZ(), instance.yaw()));
        }
        return new LayoutSnapshot(blocks, recorded);
    }

    public void clear(ShopCuboid cuboid) {
        List<FurnitureInstance> remove = new ArrayList<FurnitureInstance>();
        for (FurnitureInstance instance : furniture.instances()) if (cuboid.contains(instance.location())) remove.add(instance);
        for (FurnitureInstance instance : remove) furniture.remove(instance.id(), false);
        List<Block> blocks = cuboid.blocks();
        for (int i = blocks.size() - 1; i >= 0; i--) blocks.get(i).setType(Material.AIR, false);
    }

    public ApplyResult apply(ShopCuboid cuboid, LayoutSnapshot snapshot) {
        World world = cuboid.world();
        if (world == null) throw new IllegalStateException("World is not loaded: " + cuboid.worldName());
        validate(cuboid, snapshot);
        clear(cuboid);
        int restoredBlocks = 0;
        for (RecordedBlock value : snapshot.blocks()) {
            if (Material.AIR.name().equals(value.material())) continue;
            Block block = world.getBlockAt(cuboid.minX() + value.x(), cuboid.minY() + value.y(), cuboid.minZ() + value.z());
            BlockDataSupport.restore(block, value.material(), value.legacyData(), value.blockData());
            restoredBlocks++;
        }
        int restoredFurniture = 0;
        for (RecordedFurniture value : snapshot.furniture()) {
            ContentID id = ContentID.parse(value.definition(), "voxel");
            Optional<FurnitureDefinition> definition = furniture.definition(id);
            if (!definition.isPresent()) throw new IllegalStateException("Unknown furniture definition: " + id);
            Location location = new Location(world, cuboid.minX() + value.x(), cuboid.minY() + value.y(),
                    cuboid.minZ() + value.z(), value.yaw(), 0.0F);
            if (!furniture.place(definition.get(), location, value.yaw()).isPresent())
                throw new IllegalStateException("Could not restore " + id + " at " + location);
            restoredFurniture++;
        }
        return new ApplyResult(restoredBlocks, restoredFurniture);
    }

    private static void validate(ShopCuboid cuboid, LayoutSnapshot snapshot) {
        int sizeX = cuboid.maxX() - cuboid.minX();
        int sizeY = cuboid.maxY() - cuboid.minY();
        int sizeZ = cuboid.maxZ() - cuboid.minZ();
        for (RecordedBlock block : snapshot.blocks()) if (block.x() < 0 || block.x() > sizeX || block.y() < 0
                || block.y() > sizeY || block.z() < 0 || block.z() > sizeZ)
            throw new IllegalArgumentException("Snapshot does not fit target cuboid");
    }

    public static final class ApplyResult {
        private final int blocks, furniture;
        ApplyResult(int blocks, int furniture) { this.blocks = blocks; this.furniture = furniture; }
        public int blocks() { return blocks; }
        public int furniture() { return furniture; }
    }
}
