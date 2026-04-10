package asd.itamio.noparticles.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import javax.annotation.Nullable;
import java.util.Map;

@IFMLLoadingPlugin.Name("NoParticlesCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public class NoParticlesLoadingPlugin implements IFMLLoadingPlugin {
    
    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "asd.itamio.noparticles.asm.ParticleTransformer" };
    }
    
    @Override
    public String getModContainerClass() {
        return null;
    }
    
    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }
    
    @Override
    public void injectData(Map<String, Object> data) {
    }
    
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
