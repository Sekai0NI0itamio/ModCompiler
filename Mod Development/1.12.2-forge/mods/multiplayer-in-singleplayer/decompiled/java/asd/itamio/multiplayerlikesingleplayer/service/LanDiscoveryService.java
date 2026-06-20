package asd.itamio.multiplayerlikesingleplayer.service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public final class LanDiscoveryService {
   public static final int FIXED_LAN_PORT = 56567;
   public static final String FIXED_ADDRESS = "127.0.0.1:56567";
   private static final LanDiscoveryService INSTANCE = new LanDiscoveryService();
   private long lastProbeAt;
   private boolean cachedAvailability;

   private LanDiscoveryService() {
   }

   public static LanDiscoveryService getInstance() {
      return INSTANCE;
   }

   public synchronized boolean isLocalLanAvailable() {
      long now = System.currentTimeMillis();
      if (now - this.lastProbeAt > 1200L) {
         this.cachedAvailability = this.probeLocalLan();
         this.lastProbeAt = now;
      }

      return this.cachedAvailability;
   }

   public synchronized void forceProbe() {
      this.cachedAvailability = this.probeLocalLan();
      this.lastProbeAt = System.currentTimeMillis();
   }

   public synchronized ServerData createLocalServerData() {
      return new ServerData("[MLSP] Localhost LAN", "127.0.0.1:56567", false);
   }

   public synchronized void syncTemporaryServerEntry(GuiMultiplayer gui) {
      boolean available = this.isLocalLanAvailable();

      try {
         Field savedListField = ReflectionHelper.findField(GuiMultiplayer.class, "savedServerList", "field_146804_i");
         Field selectorField = ReflectionHelper.findField(GuiMultiplayer.class, "serverListSelector", "field_146803_h");
         ServerList savedList = (ServerList)savedListField.get(gui);
         ServerSelectionList selector = (ServerSelectionList)selectorField.get(gui);
         if (savedList == null || selector == null) {
            return;
         }

         int existingIndex = this.findTemporaryEntry(savedList);
         boolean changed = false;
         if (available && existingIndex < 0) {
            savedList.func_78849_a(this.createLocalServerData());
            changed = true;
         } else if (!available && existingIndex >= 0) {
            savedList.func_78851_b(existingIndex);
            changed = true;
         }

         if (changed) {
            selector.func_148195_a(savedList);
         }
      } catch (Throwable var9) {
      }
   }

   private int findTemporaryEntry(ServerList list) {
      for(int i = 0; i < list.func_78856_c(); ++i) {
         ServerData data = list.func_78850_a(i);
         if ("127.0.0.1:56567".equalsIgnoreCase(data.field_78845_b) || "[MLSP] Localhost LAN".equals(data.field_78847_a)) {
            return i;
         }
      }

      return -1;
   }

   private boolean probeLocalLan() {
      Socket socket = new Socket();

      boolean var3;
      try {
         socket.connect(new InetSocketAddress("127.0.0.1", 56567), 200);
         return true;
      } catch (IOException var13) {
         var3 = false;
      } finally {
         try {
            socket.close();
         } catch (IOException var12) {
         }
      }

      return var3;
   }
}
