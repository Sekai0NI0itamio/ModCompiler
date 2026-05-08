package asd.itamio.heartsystem;

public class HeartConfig {
    private final int startHearts;
    private final int maxHearts;
    private final int minHearts;

    public HeartConfig() {
        // Simple hardcoded defaults — config file support can be added later
        this.startHearts = 10;
        this.maxHearts   = 20;
        this.minHearts   = 0;
    }

    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
