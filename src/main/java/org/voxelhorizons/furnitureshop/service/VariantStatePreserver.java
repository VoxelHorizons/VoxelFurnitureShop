package org.voxelhorizons.furnitureshop.service;

import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.RecordedBlock;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Refreshes the mutable metadata of the currently displayed variant, without
 * rewriting its authored geometry. Transient changes in material/placement
 * must not be promoted into the permanent variant without "variant update".
 *
 * Matching is strictly positional. If a block's material or a furniture
 * definition differs, preserve the last known good state rather than
 * attaching unrelated metadata to another item or furniture instance.
 */
final class VariantStatePreserver {
    private VariantStatePreserver() {}

    static LayoutSnapshot merge(LayoutSnapshot saved, LayoutSnapshot displayed) {
        Map<String, RecordedBlock> liveBlocks = new HashMap<String, RecordedBlock>();
        for (RecordedBlock block : displayed.blocks()) {
            String key = blockKey(block);
            if (liveBlocks.put(key, block) != null)
                throw new IllegalStateException("Duplicate live block coordinate: " + key);
        }

        List<RecordedBlock> blocks = new ArrayList<RecordedBlock>();
        for (RecordedBlock original : saved.blocks()) {
            RecordedBlock live = liveBlocks.get(blockKey(original));
            if (live != null && original.material().equals(live.material())) {
                blocks.add(new RecordedBlock(original.x(), original.y(), original.z(),
                        original.material(), live.legacyData(), live.blockData(),
                        live.skullProfile(), live.tileData()));
            } else {
                blocks.add(original);
            }
        }

        Map<String, RecordedFurniture> liveFurniture = new HashMap<String, RecordedFurniture>();
        for (RecordedFurniture piece : displayed.furniture()) {
            String key = furnitureKey(piece);
            if (liveFurniture.put(key, piece) != null)
                throw new IllegalStateException("Duplicate live furniture coordinate: " + key);
        }
        List<RecordedFurniture> restored = new ArrayList<RecordedFurniture>();
        for (RecordedFurniture original : saved.furniture()) {
            RecordedFurniture live = liveFurniture.get(furnitureKey(original));
            if (live == null) {
                restored.add(original);
                continue;
            }
            restored.add(new RecordedFurniture(original.definition(), original.x(), original.y(),
                    original.z(), original.yaw(), live.dyeColor(), live.inventoryContents(),
                    live.useAnimationActive()));
        }
        return new LayoutSnapshot(blocks, restored);
    }

    private static String blockKey(RecordedBlock block) {
        return block.x() + "/" + block.y() + "/" + block.z();
    }

    private static String furnitureKey(RecordedFurniture piece) {
        return piece.definition() + ":" + Double.doubleToLongBits(piece.x()) + ":"
                + Double.doubleToLongBits(piece.y()) + ":" + Double.doubleToLongBits(piece.z());
    }
}
