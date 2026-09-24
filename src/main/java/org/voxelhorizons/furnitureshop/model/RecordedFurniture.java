package org.voxelhorizons.furnitureshop.model;

public final class RecordedFurniture {
    private final String definition;
    private final double x, y, z;
    private final float yaw;

    public RecordedFurniture(String definition, double x, double y, double z, float yaw) {
        this.definition = definition; this.x = x; this.y = y; this.z = z; this.yaw = yaw;
    }

    public String definition() { return definition; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
}

