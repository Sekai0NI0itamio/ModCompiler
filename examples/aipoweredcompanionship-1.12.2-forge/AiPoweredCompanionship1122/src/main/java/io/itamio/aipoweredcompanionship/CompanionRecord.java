package io.itamio.aipoweredcompanionship;

public final class CompanionRecord {
    private final String name;
    private final java.util.UUID ownerUuid;
    private String memory = "";

    public CompanionRecord(String name, java.util.UUID ownerUuid) {
        this.name = name;
        this.ownerUuid = ownerUuid;
    }

    public String getName() { return name; }
    public java.util.UUID getOwnerUuid() { return ownerUuid; }
    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory == null ? "" : memory; }
}
