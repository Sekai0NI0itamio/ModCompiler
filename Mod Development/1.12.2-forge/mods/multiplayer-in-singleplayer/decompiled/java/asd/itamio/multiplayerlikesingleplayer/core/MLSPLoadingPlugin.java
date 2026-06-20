package asd.itamio.multiplayerlikesingleplayer.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

@Name("MultiplayerLikeSingleplayerCore")
@MCVersion("1.12.2")
@SortingIndex(1001)
@TransformerExclusions({"asd.itamio.multiplayerlikesingleplayer.core"})
public class MLSPLoadingPlugin implements IFMLLoadingPlugin {
   public String[] getASMTransformerClass() {
      return new String[]{MLSPTransformer.class.getName()};
   }

   public String getModContainerClass() {
      return null;
   }

   public String getSetupClass() {
      return null;
   }

   public void injectData(Map<String, Object> data) {
   }

   public String getAccessTransformerClass() {
      return null;
   }
}
