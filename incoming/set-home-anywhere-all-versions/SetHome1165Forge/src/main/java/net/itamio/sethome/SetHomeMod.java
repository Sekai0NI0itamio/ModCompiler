package net.itamio.sethome;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
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
        CommandDispatcher<CommandSource> d = e.getDispatcher();
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

    private static int setHome(CommandSource src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
        ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
        String uuid = p.getUUID().toString();
        HomeData d = HomeData.get(src.getServer());
        int max = MAX_HOMES.get();
        if (max > 0 && !d.hasHome(uuid, name) && d.getHomes(uuid).size() >= max) {
            src.sendSuccess(new StringTextComponent("You have reached the maximum number of homes (" + max + ")."), false); return 0;
        }
        d.setHome(uuid, name, p.getX(), p.getY(), p.getZ(), p.yRot, p.xRot);
        src.sendSuccess(new StringTextComponent("Home '" + name + "' set."), false); return 1;
    }
    private static int home(CommandSource src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if ("list".equalsIgnoreCase(name)) {
            if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
            ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
            Set<String> homes = HomeData.get(src.getServer()).getHomes(p.getUUID().toString());
            if (homes.isEmpty()) { src.sendSuccess(new StringTextComponent("You have no homes set."), false); return 1; }
            src.sendSuccess(new StringTextComponent("Your homes: " + String.join(", ", new ArrayList<>(homes))), false); return 1;
        }
        if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
        ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
        double[] h = HomeData.get(src.getServer()).getHome(p.getUUID().toString(), name);
        if (h == null) { src.sendSuccess(new StringTextComponent("Home '" + name + "' not found."), false); return 0; }
        p.teleportTo(h[0], h[1], h[2]);
        src.sendSuccess(new StringTextComponent("Teleported to home '" + name + "'."), false); return 1;
    }
    private static int delHome(CommandSource src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
        ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
        if (!HomeData.get(src.getServer()).removeHome(p.getUUID().toString(), name)) {
            src.sendSuccess(new StringTextComponent("Home '" + name + "' not found."), false); return 0;
        }
        src.sendSuccess(new StringTextComponent("Home '" + name + "' deleted."), false); return 1;
    }

    public static class HomeData extends WorldSavedData {
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<>();
        public HomeData() { super(NAME); }
        public HomeData(String n) { super(n); }

        public static HomeData get(MinecraftServer srv) {
            net.minecraft.world.storage.DimensionSavedDataManager mgr = srv.overworld().getDataStorage();
            HomeData d = mgr.get(HomeData::new, NAME);
            if (d == null) { d = new HomeData(); mgr.set(d); }
            return d;
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

        @Override
        public void load(CompoundNBT tag) {
            data.clear();
            ListNBT players = tag.getList("players", 10);
            for (int i = 0; i < players.size(); i++) {
                CompoundNBT pc = players.getCompound(i);
                Map<String, double[]> homes = new HashMap<>();
                ListNBT hl = pc.getList("homes", 10);
                for (int j = 0; j < hl.size(); j++) {
                    CompoundNBT hc = hl.getCompound(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"),hc.getDouble("y"),hc.getDouble("z"),
                        hc.getFloat("yaw"),hc.getFloat("pitch")});
                }
                data.put(pc.getString("uuid"), homes);
            }
        }
        @Override
        public CompoundNBT save(CompoundNBT tag) {
            ListNBT players = new ListNBT();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                CompoundNBT pc = new CompoundNBT();
                pc.putString("uuid", pe.getKey());
                ListNBT hl = new ListNBT();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    CompoundNBT hc = new CompoundNBT();
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
    }
}
