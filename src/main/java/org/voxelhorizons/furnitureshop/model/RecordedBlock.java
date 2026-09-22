package org.voxelhorizons.furnitureshop.model;

public final class RecordedBlock {
    private final int x, y, z;
    private final String material;
    private final int legacyData;
    private final String blockData;

    public RecordedBlock(int x, int y, int z, String material, int legacyData, String blockData) {
        this.x = x; this.y = y; this.z = z; this.material = material;
        this.legacyData = legacyData; this.blockData = blockData;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String material() { return material; }
    public int legacyData() { return legacyData; }
    public String blockData() { return blockData; }
}

