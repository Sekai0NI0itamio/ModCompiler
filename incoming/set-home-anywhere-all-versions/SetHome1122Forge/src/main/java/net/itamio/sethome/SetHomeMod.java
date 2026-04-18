package net.itamio.sethome;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import java.util.*;

@Mod(modid=SetHomeMod.MODID, name="Set Home", version="1.0.1",
     acceptedMinecraftVersions="[1.12,1.12.2]")
public class SetHomeMod {
    public static final String MODID = "sethome";
    private static int maxHomes = -1;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        Configuration cfg = new Configuration(e.getSuggestedConfigurationFile());
        cfg.load();
        maxHomes = cfg.getInt("maxHomes","general",-1,-1,Integer.MAX_VALUE,
                "Maximum homes per player. Use -1 for unlimited.");
        if (cfg.hasChanged()) cfg.save();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new SetHomeCmd());
        e.registerServerCommand(new HomeCmd());
        e.registerServerCommand(new DelHomeCmd());
    }

    public static class HomeData extends WorldSavedData {
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<>();

        public HomeData() { super(NAME); }
        public HomeData(String n) { super(n); }

        public static HomeData get(MinecraftServer srv) {
            MapStorage ms = srv.getWorld(0).getPerWorldStorage();
            HomeData d = (HomeData) ms.getOrLoadData(HomeData.class, NAME);
            if (d == null) { d = new HomeData(); ms.setData(NAME, d); }
            return d;
        }

        private Map<String, double[]> player(String uuid) {
            return data.computeIfAbsent(uuid, k -> new HashMap<>());
        }

        public void setHome(String uuid, String name, double x, double y, double z, float yaw, float pitch) {
            player(uuid).put(name, new double[]{x,y,z,yaw,pitch});
            markDirty();
        }

        public double[] getHome(String uuid, String name) { return player(uuid).get(name); }
        public boolean hasHome(String uuid, String name) { return player(uuid).containsKey(name); }
        public boolean removeHome(String uuid, String name) {
            boolean r = player(uuid).remove(name) != null; if (r) markDirty(); return r;
        }
        public Set<String> getHomes(String uuid) { return player(uuid).keySet(); }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            data.clear();
            NBTTagList players = tag.getTagList("players", 10);
            for (int i = 0; i < players.tagCount(); i++) {
                NBTTagCompound pc = players.getCompoundTagAt(i);
                Map<String, double[]> homes = new HashMap<>();
                NBTTagList hl = pc.getTagList("homes", 10);
                for (int j = 0; j < hl.tagCount(); j++) {
                    NBTTagCompound hc = hl.getCompoundTagAt(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"), hc.getDouble("y"), hc.getDouble("z"),
                        hc.getFloat("yaw"), hc.getFloat("pitch")
                    });
                }
                data.put(pc.getString("uuid"), homes);
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            NBTTagList players = new NBTTagList();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                NBTTagCompound pc = new NBTTagCompound();
                pc.setString("uuid", pe.getKey());
                NBTTagList hl = new NBTTagList();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    NBTTagCompound hc = new NBTTagCompound();
                    hc.setString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.setDouble("x",v[0]); hc.setDouble("y",v[1]); hc.setDouble("z",v[2]);
                    hc.setFloat("yaw",(float)v[3]); hc.setFloat("pitch",(float)v[4]);
                    hl.appendTag(hc);
                }
                pc.setTag("homes", hl);
                players.appendTag(pc);
            }
            tag.setTag("players", players);
            return tag;
        }
    }

    static class SetHomeCmd extends CommandBase {
        public String getCommandName() { return "sethome"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/sethome <name>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /sethome <name>");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            String uuid = p.getUniqueID().toString();
            HomeData d = HomeData.get(srv);
            if (maxHomes > 0 && !d.hasHome(uuid, args[0]) && d.getHomes(uuid).size() >= maxHomes)
                throw new CommandException("You have reached the maximum number of homes (" + maxHomes + ").");
            d.setHome(uuid, args[0], p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch);
            sender.sendMessage(new TextComponentString("Home '" + args[0] + "' set."));
        }
    }

    static class HomeCmd extends CommandBase {
        public String getCommandName() { return "home"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/home <name> or /home list"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /home <name> or /home list");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            String uuid = p.getUniqueID().toString();
            HomeData d = HomeData.get(srv);
            if ("list".equalsIgnoreCase(args[0])) {
                Set<String> homes = d.getHomes(uuid);
                if (homes.isEmpty()) { sender.sendMessage(new TextComponentString("You have no homes set.")); return; }
                sender.sendMessage(new TextComponentString("Your homes: " + String.join(", ", new ArrayList<>(homes))));
                return;
            }
            double[] h = d.getHome(uuid, args[0]);
            if (h == null) throw new CommandException("Home '" + args[0] + "' not found.");
            p.setPositionAndUpdate(h[0], h[1], h[2]);
            sender.sendMessage(new TextComponentString("Teleported to home '" + args[0] + "'."));
        }
    }

    static class DelHomeCmd extends CommandBase {
        public String getCommandName() { return "delhome"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/delhome <name>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /delhome <name>");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            if (!HomeData.get(srv).removeHome(p.getUniqueID().toString(), args[0]))
                throw new CommandException("Home '" + args[0] + "' not found.");
            sender.sendMessage(new TextComponentString("Home '" + args[0] + "' deleted."));
        }
    }
}
