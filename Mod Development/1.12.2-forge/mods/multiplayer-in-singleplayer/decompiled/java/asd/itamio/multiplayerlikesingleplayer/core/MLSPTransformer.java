package asd.itamio.multiplayerlikesingleplayer.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class MLSPTransformer implements IClassTransformer {
   private static final String MC_CLASS = "net.minecraft.client.Minecraft";
   private static final String GUI_MP_CLASS = "net.minecraft.client.gui.GuiMultiplayer";
   private static final String INTEGRATED_SERVER_CLASS = "net.minecraft.server.integrated.IntegratedServer";
   private static final String CORE_HOOKS = "asd/itamio/multiplayerlikesingleplayer/core/hooks/MLSPCoreHooks";

   public byte[] transform(String name, String transformedName, byte[] basicClass) {
      if (basicClass == null) {
         return null;
      } else if ("net.minecraft.client.Minecraft".equals(transformedName)) {
         return this.patchMinecraft(basicClass);
      } else if ("net.minecraft.client.gui.GuiMultiplayer".equals(transformedName)) {
         return this.patchGuiMultiplayer(basicClass);
      } else {
         return "net.minecraft.server.integrated.IntegratedServer".equals(transformedName) ? this.patchIntegratedServer(basicClass) : basicClass;
      }
   }

   private byte[] patchMinecraft(byte[] basicClass) {
      ClassNode classNode = readClass(basicClass);
      boolean changed = false;

      for(MethodNode method : classNode.methods) {
         if ("(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V".equals(method.desc)
            && isMethodName(method.name, "launchIntegratedServer", "func_71371_a")) {
            LabelNode continueLabel = new LabelNode();
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(25, 0));
            hook.add(new VarInsnNode(25, 1));
            hook.add(new VarInsnNode(25, 2));
            hook.add(new VarInsnNode(25, 3));
            hook.add(
               new MethodInsnNode(
                  184,
                  "asd/itamio/multiplayerlikesingleplayer/core/hooks/MLSPCoreHooks",
                  "beforeLaunchIntegratedServer",
                  "(Lnet/minecraft/client/Minecraft;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)Z",
                  false
               )
            );
            hook.add(new JumpInsnNode(154, continueLabel));
            hook.add(new InsnNode(177));
            hook.add(continueLabel);
            method.instructions.insert(hook);
            changed = true;
         }
      }

      return changed ? writeClass(classNode) : basicClass;
   }

   private byte[] patchGuiMultiplayer(byte[] basicClass) {
      ClassNode classNode = readClass(basicClass);
      boolean changed = false;

      for(MethodNode method : classNode.methods) {
         if ("()V".equals(method.desc) && isMethodName(method.name, "connectToSelected", "func_146791_a")) {
            LabelNode continueLabel = new LabelNode();
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(25, 0));
            hook.add(
               new MethodInsnNode(
                  184,
                  "asd/itamio/multiplayerlikesingleplayer/core/hooks/MLSPCoreHooks",
                  "beforeGuiMultiplayerConnect",
                  "(Lnet/minecraft/client/gui/GuiMultiplayer;)Z",
                  false
               )
            );
            hook.add(new JumpInsnNode(154, continueLabel));
            hook.add(new InsnNode(177));
            hook.add(continueLabel);
            method.instructions.insert(hook);
            changed = true;
         }
      }

      return changed ? writeClass(classNode) : basicClass;
   }

   private byte[] patchIntegratedServer(byte[] basicClass) {
      ClassNode classNode = readClass(basicClass);
      boolean changed = false;

      for(MethodNode method : classNode.methods) {
         if ("(Lnet/minecraft/world/GameType;Z)Ljava/lang/String;".equals(method.desc) && isMethodName(method.name, "shareToLAN", "func_71206_a")) {
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(25, 0));
            hook.add(
               new MethodInsnNode(
                  184,
                  "asd/itamio/multiplayerlikesingleplayer/core/hooks/MLSPCoreHooks",
                  "beforeShareToLAN",
                  "(Lnet/minecraft/server/integrated/IntegratedServer;)V",
                  false
               )
            );
            method.instructions.insert(hook);
            changed = true;

            for(AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
               if (node instanceof MethodInsnNode) {
                  MethodInsnNode invoke = (MethodInsnNode)node;
                  if (invoke.getOpcode() == 184
                     && "net/minecraft/util/HttpUtil".equals(invoke.owner)
                     && "()I".equals(invoke.desc)
                     && isMethodName(invoke.name, "getSuitableLanPort", "func_76181_a")) {
                     MethodInsnNode replacement = new MethodInsnNode(
                        184, "asd/itamio/multiplayerlikesingleplayer/core/hooks/MLSPCoreHooks", "getFixedLanPort", "()I", false
                     );
                     method.instructions.set(invoke, replacement);
                     changed = true;
                  }
               }
            }
         }
      }

      return changed ? writeClass(classNode) : basicClass;
   }

   private static ClassNode readClass(byte[] basicClass) {
      ClassReader classReader = new ClassReader(basicClass);
      ClassNode classNode = new ClassNode();
      classReader.accept(classNode, 0);
      return classNode;
   }

   private static byte[] writeClass(ClassNode classNode) {
      ClassWriter writer = new ClassWriter(1);
      classNode.accept(writer);
      return writer.toByteArray();
   }

   private static boolean isMethodName(String actualName, String... names) {
      for(String name : names) {
         if (name.equals(actualName)) {
            return true;
         }
      }

      return false;
   }
}
