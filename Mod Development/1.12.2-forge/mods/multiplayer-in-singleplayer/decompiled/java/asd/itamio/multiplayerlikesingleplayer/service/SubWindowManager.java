package asd.itamio.multiplayerlikesingleplayer.service;

import asd.itamio.multiplayerlikesingleplayer.util.MinecraftPathUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.Display;

public final class SubWindowManager {
   private static final SubWindowManager INSTANCE = new SubWindowManager();
   private static final String ROLE_MAIN = "MAIN";
   private static final String ROLE_SUB = "SUB";
   private final String role;
   private final int subIndex;
   private final AtomicInteger nextSubIndex;
   private final Map<Integer, Process> children = new ConcurrentHashMap<>();
   private File heartbeatFile;
   private File parentHeartbeatFile;
   private long lastMainHeartbeatWrite;
   private long lastSubHeartbeatCheck;
   private boolean initialized;

   private SubWindowManager() {
      this.role = System.getProperty("mlsp.windowRole", "MAIN").toUpperCase(Locale.ROOT);
      this.subIndex = parseInt(System.getProperty("mlsp.subIndex"), 0);
      this.nextSubIndex = new AtomicInteger(Math.max(0, this.subIndex));
   }

   public static SubWindowManager getInstance() {
      return INSTANCE;
   }

   public synchronized void initialize() {
      if (!this.initialized) {
         this.initialized = true;
         if (this.isMainWindow()) {
            File subRoot = new File(MinecraftPathUtil.getGameDirectory(), "mlsp_sub");
            if (!subRoot.exists()) {
               subRoot.mkdirs();
            }

            this.heartbeatFile = new File(subRoot, "main_heartbeat.txt");
            writeHeartbeat(this.heartbeatFile);
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
               @Override
               public void run() {
                  SubWindowManager.this.shutdownChildren();
               }
            }, "MLSP-SubWindow-Shutdown"));
         } else if (this.isSubWindow()) {
            String parentHeartbeatPath = System.getProperty("mlsp.parentHeartbeat", "");
            if (!parentHeartbeatPath.isEmpty()) {
               this.parentHeartbeatFile = new File(parentHeartbeatPath);
            }
         }

         this.applyWindowTitle();
      }
   }

   public boolean isMainWindow() {
      return "MAIN".equals(this.role);
   }

   public boolean isSubWindow() {
      return "SUB".equals(this.role);
   }

   public String getWindowTitle() {
      return this.isMainWindow() ? "Main Minecraft" : "SUB #" + this.subIndex;
   }

   public synchronized void tick() {
      if (this.initialized) {
         long now = System.currentTimeMillis();
         if (this.isMainWindow() && this.heartbeatFile != null && now - this.lastMainHeartbeatWrite > 1000L) {
            writeHeartbeat(this.heartbeatFile);
            this.lastMainHeartbeatWrite = now;
         }

         if (this.isSubWindow() && this.parentHeartbeatFile != null && now - this.lastSubHeartbeatCheck > 2000L) {
            this.lastSubHeartbeatCheck = now;
            if (!this.isParentAlive()) {
               this.requestClientShutdown();
            }
         }
      }
   }

   public synchronized String spawnSubWindow() {
      if (!this.isMainWindow()) {
         return "Only Main window can spawn SUB windows.";
      } else {
         this.initialize();
         int index = this.nextSubIndex.incrementAndGet();
         File gameDir = MinecraftPathUtil.getGameDirectory();
         File subGameDir = new File(new File(gameDir, "mlsp_sub"), "sub_" + index);
         if (!subGameDir.exists() && !subGameDir.mkdirs()) {
            return "Failed to create SUB game directory: " + subGameDir.getAbsolutePath();
         } else {
            List<String> command = this.buildChildCommand(index, subGameDir);
            if (command.isEmpty()) {
               return "Unable to build child process command from current launch context.";
            } else {
               try {
                  ProcessBuilder builder = new ProcessBuilder(command);
                  builder.directory(gameDir);
                  Process process = builder.start();
                  this.children.put(index, process);
                  return "Spawned SUB #" + index;
               } catch (IOException var7) {
                  return "Failed to spawn SUB process: " + var7.getMessage();
               }
            }
         }
      }
   }

   private List<String> buildChildCommand(int index, File subGameDir) {
      String javaHome = System.getProperty("java.home", "");
      File javaBinary = new File(new File(javaHome, "bin"), "java");
      if (!javaBinary.exists()) {
         javaBinary = new File("java");
      }

      String classPath = System.getProperty("java.class.path", "");
      String launchCommand = System.getProperty("sun.java.command", "");
      List<String> launchTokens = splitCommandLine(launchCommand);
      if (launchTokens.isEmpty()) {
         return new ArrayList<>();
      } else {
         launchTokens = this.withGameDir(launchTokens, subGameDir);
         List<String> command = new ArrayList<>();
         command.add(javaBinary.getPath());
         command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
         command.add("-Dmlsp.windowRole=SUB");
         command.add("-Dmlsp.subIndex=" + index);
         if (this.heartbeatFile != null) {
            command.add("-Dmlsp.parentHeartbeat=" + this.heartbeatFile.getAbsolutePath());
         }

         command.add("-cp");
         command.add(classPath);
         command.addAll(launchTokens);
         return command;
      }
   }

   private List<String> withGameDir(List<String> launchTokens, File subGameDir) {
      List<String> result = new ArrayList<>(launchTokens);
      boolean replaced = false;

      for(int i = 0; i < result.size() - 1; ++i) {
         if ("--gameDir".equals(result.get(i))) {
            result.set(i + 1, subGameDir.getAbsolutePath());
            replaced = true;
            break;
         }
      }

      if (!replaced) {
         result.add("--gameDir");
         result.add(subGameDir.getAbsolutePath());
      }

      return result;
   }

   private void shutdownChildren() {
      for(Process process : this.children.values()) {
         process.destroy();
      }

      for(Process process : this.children.values()) {
         try {
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
               process.destroyForcibly();
            }
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
         }
      }

      this.children.clear();
   }

   private boolean isParentAlive() {
      if (!this.parentHeartbeatFile.exists()) {
         return false;
      } else {
         long ageMillis = System.currentTimeMillis() - this.parentHeartbeatFile.lastModified();
         return ageMillis <= 15000L;
      }
   }

   private static List<String> splitCommandLine(String commandLine) {
      List<String> tokens = new ArrayList<>();
      if (commandLine != null && !commandLine.trim().isEmpty()) {
         StringBuilder token = new StringBuilder();
         boolean inQuotes = false;

         for(int i = 0; i < commandLine.length(); ++i) {
            char c = commandLine.charAt(i);
            if (c == '"') {
               inQuotes = !inQuotes;
            } else if (inQuotes || !Character.isWhitespace(c)) {
               token.append(c);
            } else if (token.length() > 0) {
               tokens.add(token.toString());
               token.setLength(0);
            }
         }

         if (token.length() > 0) {
            tokens.add(token.toString());
         }

         return tokens;
      } else {
         return tokens;
      }
   }

   private void requestClientShutdown() {
      try {
         final Minecraft minecraft = Minecraft.func_71410_x();
         minecraft.func_152344_a(new Runnable() {
            @Override
            public void run() {
               SubWindowManager.invokeClientShutdown(minecraft);
            }
         });
      } catch (Throwable var2) {
         System.exit(0);
      }
   }

   private void applyWindowTitle() {
      try {
         Display.setTitle(this.getWindowTitle());
      } catch (Throwable var2) {
      }
   }

   private static void writeHeartbeat(File file) {
      FileOutputStream outputStream = null;

      try {
         outputStream = new FileOutputStream(file);
         outputStream.write(Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
         outputStream.flush();
      } catch (IOException var11) {
      } finally {
         if (outputStream != null) {
            try {
               outputStream.close();
            } catch (IOException var10) {
            }
         }
      }
   }

   private static int parseInt(String value, int fallback) {
      if (value != null && !value.trim().isEmpty()) {
         try {
            return Integer.parseInt(value);
         } catch (NumberFormatException var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static void invokeClientShutdown(Minecraft minecraft) {
      try {
         Method shutdown;
         try {
            shutdown = ReflectionHelper.findMethod(Minecraft.class, "shutdown", "func_71400_g", new Class[0]);
         } catch (Throwable var3) {
            shutdown = ReflectionHelper.findMethod(Minecraft.class, "shutdownMinecraftApplet", "func_71400_g", new Class[0]);
         }

         shutdown.setAccessible(true);
         shutdown.invoke(minecraft);
      } catch (Throwable var4) {
         System.exit(0);
      }
   }
}
