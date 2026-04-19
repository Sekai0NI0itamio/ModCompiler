package asd.itamio.noparticles.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class ParticleTransformer implements IClassTransformer {
    
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Target the World class to intercept spawnParticle method
        if (transformedName.equals("net.minecraft.world.World")) {
            return transformWorld(basicClass);
        }
        
        // Target the ParticleManager class to intercept addEffect method
        if (transformedName.equals("net.minecraft.client.particle.ParticleManager")) {
            return transformParticleManager(basicClass);
        }
        
        return basicClass;
    }
    
    private byte[] transformWorld(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(basicClass);
        classReader.accept(classNode, 0);
        
        // Find and modify spawnParticle methods
        for (MethodNode method : classNode.methods) {
            // spawnParticle method signature
            if (method.name.equals("spawnParticle") || method.name.equals("func_175688_a")) {
                // Clear the method and just return immediately
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                System.out.println("[NoParticles] Patched World.spawnParticle");
            }
        }
        
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
    
    private byte[] transformParticleManager(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(basicClass);
        classReader.accept(classNode, 0);
        
        // Find and modify addEffect method
        for (MethodNode method : classNode.methods) {
            // addEffect method - prevents particles from being added to render queue
            if (method.name.equals("addEffect") || method.name.equals("func_78873_a")) {
                // Clear the method and just return immediately
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                System.out.println("[NoParticles] Patched ParticleManager.addEffect");
            }
        }
        
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
