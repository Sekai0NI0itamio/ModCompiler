/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.network.listener;

import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;

/**
 * A packet listener listens to packets on a {@linkplain
 * net.minecraft.network.ClientConnection connection}.
 * 
 * <p>Its listener methods will be called on the netty event loop than the
 * client or server game engine threads.
 */
public interface PacketListener {
    public NetworkSide getSide();

    public NetworkPhase getPhase();

    /**
     * Called when the connection this listener listens to has disconnected.
     * Can be used to display the disconnection reason.
     * 
     * @param reason the reason of disconnection; may be a generic message
     */
    public void onDisconnected(Text var1);

    default public void onPacketException(Packet packet, Exception exception) throws CrashException {
        throw NetworkThreadUtils.createCrashException(exception, packet, this);
    }

    public boolean isConnectionOpen();

    default public boolean accepts(Packet<?> packet) {
        return this.isConnectionOpen();
    }

    default public void fillCrashReport(CrashReport report) {
        CrashReportSection crashReportSection = report.addElement("Connection");
        crashReportSection.add("Protocol", () -> this.getPhase().getId());
        crashReportSection.add("Flow", () -> this.getSide().toString());
        this.addCustomCrashReportInfo(crashReportSection);
    }

    default public void addCustomCrashReportInfo(CrashReportSection section) {
    }
}

