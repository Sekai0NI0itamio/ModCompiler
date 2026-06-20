package asd.itamio.multiplayerlikesingleplayer.event;

import asd.itamio.multiplayerlikesingleplayer.gui.GuiJoinAsPrompt;
import asd.itamio.multiplayerlikesingleplayer.service.LanDiscoveryService;
import asd.itamio.multiplayerlikesingleplayer.service.SubWindowManager;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class MLSPClientEvents {
   private static final int BUTTON_SPAWN_SUB = 54001;
   private static final int BUTTON_QUICK_JOIN = 54002;
   private String statusMessage = "";
   private long statusMessageAt = 0L;

   @SubscribeEvent
   public void onInitGui(Post event) {
      GuiScreen gui = event.getGui();
      SubWindowManager.getInstance().initialize();
      if (SubWindowManager.getInstance().isMainWindow() && (gui instanceof GuiMainMenu || gui instanceof GuiIngameMenu)) {
         int x = 8;
         int y = gui.field_146295_m - 28;
         if (gui instanceof GuiIngameMenu) {
            x = gui.field_146294_l / 2 + 104;
            y = gui.field_146295_m / 4 + 96;
         }

         event.getButtonList().add(new GuiButton(54001, x, y, 90, 20, "Spawn SUB"));
      }

      if (gui instanceof GuiMultiplayer) {
         boolean available = LanDiscoveryService.getInstance().isLocalLanAvailable();
         GuiButton quickJoin = new GuiButton(54002, 8, 8, 140, 20, "Join Localhost:56567");
         quickJoin.field_146125_m = available;
         event.getButtonList().add(quickJoin);
         LanDiscoveryService.getInstance().syncTemporaryServerEntry((GuiMultiplayer)gui);
      }
   }

   @SubscribeEvent
   public void onButtonClick(Pre event) {
      GuiButton button = event.getButton();
      if (button != null) {
         if (button.field_146127_k == 54001) {
            String message = SubWindowManager.getInstance().spawnSubWindow();
            this.setStatusMessage(message);
         } else {
            if (button.field_146127_k == 54002 && event.getGui() instanceof GuiMultiplayer) {
               event.setCanceled(true);
               GuiMultiplayer multiplayer = (GuiMultiplayer)event.getGui();
               GuiJoinAsPrompt prompt = new GuiJoinAsPrompt(multiplayer, LanDiscoveryService.getInstance().createLocalServerData());
               Minecraft.func_71410_x().func_147108_a(prompt);
            }
         }
      }
   }

   @SubscribeEvent
   public void onDrawScreen(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post event) {
      GuiScreen gui = event.getGui();
      if (gui instanceof GuiMultiplayer) {
         GuiMultiplayer multiplayer = (GuiMultiplayer)gui;
         LanDiscoveryService discovery = LanDiscoveryService.getInstance();
         boolean available = discovery.isLocalLanAvailable();
         discovery.syncTemporaryServerEntry(multiplayer);
         this.toggleQuickJoinButton(gui, available);
         int color = available ? 5635925 : 16733525;
         String text = available ? "MLSP Local LAN detected: 127.0.0.1:56567" : "MLSP Local LAN not detected on 127.0.0.1:56567";
         FontRenderer fontRenderer = Minecraft.func_71410_x().field_71466_p;
         fontRenderer.func_175063_a(text, 8.0F, 32.0F, color);
      }

      if (!this.statusMessage.isEmpty() && System.currentTimeMillis() - this.statusMessageAt < 4000L) {
         FontRenderer fontRenderer = Minecraft.func_71410_x().field_71466_p;
         int centerX = gui.field_146294_l / 2;
         int x = centerX - fontRenderer.func_78256_a(this.statusMessage) / 2;
         fontRenderer.func_175063_a(this.statusMessage, (float)x, (float)(gui.field_146295_m - 12), 16777215);
      }
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         SubWindowManager.getInstance().tick();
      }
   }

   private void toggleQuickJoinButton(GuiScreen gui, boolean visible) {
      try {
         Field field = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");

         for(GuiButton button : (List)field.get(gui)) {
            if (button.field_146127_k == 54002) {
               button.field_146125_m = visible;
               break;
            }
         }
      } catch (Throwable var7) {
      }
   }

   private void setStatusMessage(String message) {
      this.statusMessage = message == null ? "" : message;
      this.statusMessageAt = System.currentTimeMillis();
   }
}
