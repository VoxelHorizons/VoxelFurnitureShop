package org.voxelhorizons.furnitureshop.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LayoutSnapshot {
    private final List<RecordedBlock> blocks;
    private final List<RecordedFurniture> furniture;

    public LayoutSnapshot(List<RecordedBlock> blocks, List<RecordedFurniture> furniture) {
        this.blocks = Collections.unmodifiableList(new ArrayList<RecordedBlock>(blocks));
        this.furniture = Collections.unmodifiableList(new ArrayList<RecordedFurniture>(furniture));
    }

    public List<RecordedBlock> blocks() { return blocks; }
    public List<RecordedFurniture> furniture() { return furniture; }
}
