package net.itamio.sethome;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import java.util.*;

@Mod(SetHomeMod.MODID)
public class SetHomeMod {
    public static final String MODID = "sethome";
    static ForgeConfigSpec.IntValue MAX_HOMES;
    static ForgeConfigSpec SPEC;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        MAX_HOMES = b.comment("Maximum homes per player. Use -1 for unlimited.")
                     .defineInRange("maxHomes", -1, -1, Integer.MAX_VALUE);
        b.pop(); SPEC = b.build();
    }
    public SetHomeMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("sethome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> setHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("home")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> home(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("delhome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> delHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
    }

    private static int setHome(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        String uuid = p.getUUID().toString();
        HomeData d = HomeData.get(src.getServer());
        int max = MAX_HOMES.get();
        if (max > 0 && !d.hasHome(uuid, name) && d.getHomes(uuid).size() >= max) {
            src.sendSuccess(() -> Component.literal("You have reached the maximum number of homes (" + max + ")."), false); return 0;
        }
        d.setHome(uuid, name, p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        src.sendSuccess(() -> Component.literal("Home '" + name + "' set."), false); return 1;
    }
    private static int home(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if ("list".equalsIgnoreCase(name)) {
            ServerPlayer p = src.getPlayerOrException();
            Set<String> homes = HomeData.get(src.getServer()).getHomes(p.getUUID().toString());
            if (homes.isEmpty()) { src.sendSuccess(() -> Component.literal("You have no homes set."), false); return 1; }
            src.sendSuccess(() -> Component.literal("Your homes: " + String.join(", ", new ArrayList<>(homes))), false); return 1;
        }
        ServerPlayer p = src.getPlayerOrException();
        double[] h = HomeData.get(src.getServer()).getHome(p.getUUID().toString(), name);
        if (h == null) { src.sendSuccess(() -> Component.literal("Home '" + name + "' not found."), false); return 0; }
        p.teleportTo(h[0], h[1], h[2]);
        src.sendSuccess(() -> Component.literal("Teleported to home '" + name + "'."), false); return 1;
    }
    private static int delHome(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!HomeData.get(src.getServer()).removeHome(p.getUUID().toString(), name)) {
            src.sendSuccess(() -> Component.literal("Home '" + name + "' not found."), false); return 0;
        }
        src.sendSuccess(() -> Component.literal("Home '" + name + "' deleted."), false); return 1;
    }

    public static class HomeData extends SavedData {
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<>();
        public HomeData() {}

        public static HomeData get(MinecraftServer srv) {
            DimensionDataStorage storage = srv.overworld().getDataStorage();
            return storage.computeIfAbsent(new SavedData.Factory<HomeData>(HomeData::new, (tag, provider) -> HomeData.load(tag), null), NAME);
        }
        public static HomeData load(CompoundTag tag) {
            HomeData d = new HomeData();
            ListTag players = tag.getList("players").orElse(new ListTag());
            for (int i = 0; i < players.size(); i++) {
                CompoundTag pc = players.getCompound(i).orElse(new CompoundTag());
                Map<String, double[]> homes = new HashMap<>();
                ListTag hl = pc.getList("homes").orElse(new ListTag());
                for (int j = 0; j < hl.size(); j++) {
                    CompoundTag hc = hl.getCompound(j).orElse(new CompoundTag());
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"),hc.getDouble("y"),hc.getDouble("z"),
                        hc.getFloat("yaw"),hc.getFloat("pitch")});
                }
                d.data.put(pc.getString("uuid"), homes);
            }
            return d;
        }
        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            ListTag players = new ListTag();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                CompoundTag pc = new CompoundTag();
                pc.putString("uuid", pe.getKey());
                ListTag hl = new ListTag();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    CompoundTag hc = new CompoundTag();
                    hc.putString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.putDouble("x",v[0]); hc.putDouble("y",v[1]); hc.putDouble("z",v[2]);
                    hc.putFloat("yaw",(float)v[3]); hc.putFloat("pitch",(float)v[4]);
                    hl.add(hc);
                }
                pc.put("homes", hl); players.add(pc);
            }
            tag.put("players", players); return tag;
        }
        private Map<String, double[]> player(String uuid) { return data.computeIfAbsent(uuid, k -> new HashMap<>()); }
        public void setHome(String uuid, String name, double x, double y, double z, float yaw, float pitch) {
            player(uuid).put(name, new double[]{x,y,z,yaw,pitch}); setDirty();
        }
        public double[] getHome(String uuid, String name) { return player(uuid).get(name); }
        public boolean hasHome(String uuid, String name) { return player(uuid).containsKey(name); }
        public boolean removeHome(String uuid, String name) {
            boolean r = player(uuid).remove(name) != null; if (r) setDirty(); return r;
        }
        public Set<String> getHomes(String uuid) { return player(uuid).keySet(); }
    }
}
