package me.papyapi;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.material.Cauldron;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PapyAPI extends JavaPlugin implements PapyRequestDispatcher, CommandExecutor {

    private Logger logger;

    private PapyServer papyServer;

    @Override
    public void onEnable() {
        logger = getLogger();
        papyServer = new PapyServer("0.0.0.0", 32932, this);

    }

    @Override
    public void onDisable() {
        logger.log(Level.INFO, "Stopping PapyServer...");
        papyServer.stop();
    }

    @Override
    public PapyResponse dispatch(PapyRequest request) {

        if (request.isInvalid())
            return new PapyResponse(false, "Invalid request.");

        BukkitScheduler scheduler = getServer().getScheduler();
        Executor executor = scheduler.getMainThreadExecutor(this);


        CompletableFuture<PapyResponse> result = new CompletableFuture<>();

        switch (request.getType()) {
            case UNIMPLEMENTED -> {
                log_warn("Request not implemented:");
                log_warn(request.getBody());
                result.complete(new PapyResponse(false, "Request not implemented."));
            }
            case RUN_COMMAND -> {
                RunCommandRequest runCommandRequest = new RunCommandRequest(request);
                executor.execute(() -> {
                    boolean success = getServer().dispatchCommand(getServer().getConsoleSender(), runCommandRequest.cmdline);
                    result.complete(new PapyResponse(success));
                });
            }
            case CLOSE_CONNECTION -> {
                logger.log(Level.INFO, "Closing client socket...");
                result.complete(new PapyResponse(true, "Connection closed.", true));
            }

            case PING -> {
                logger.log(Level.INFO, "PING!");
                result.complete(new PapyResponse(true, "PONG!"));
            }

            case SET_BLOCK -> {
                SetBlockRequest setBlockRequest = new SetBlockRequest(request);
                log_info(setBlockRequest.getBody());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        World world = getServer().getWorld(setBlockRequest.world);
                        if (world == null) {
                            result.complete(new PapyResponse(false, "World \""+ setBlockRequest.world +"\" not found."));
                            return;
                        }


                        try {
                            Material material = Material.matchMaterial(setBlockRequest.block);
                            //BlockData blockData = getServer().createBlockData(setBlockRequest.block);
                            assert material != null;
                            Block block = world.getBlockAt(setBlockRequest.x, setBlockRequest.y, setBlockRequest.z);
                            block.setType(material);

                            //world.setBlockData(setBlockRequest.x, setBlockRequest.y, setBlockRequest.z, getServer().createBlockData(material));
                        } catch (Exception ignore) {
                            result.complete(new PapyResponse(false, "Invalid BlockData: \""+setBlockRequest.block+"\"."));
                            return;
                        }


                        result.complete(new PapyResponse(true));
                    }
                }.runTask(this);

            }
        }

        return result.join();

    }

    @Override
    public void log_info(String msg) {
        logger.log(Level.INFO, msg);
    }

    @Override
    public void log_warn(String msg) {
        logger.log(Level.WARNING, msg);
    }

    @Override
    public void log_error(String msg) {
        logger.log(Level.SEVERE, msg);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("papyapi.papi"))
            return false;

        if (!command.getName().equals("papy"))
            return false;

        if (args.length < 1)
            return false;

        switch (args[0]) {
            case "reload" -> {
                papyServer.stop();
                papyServer = new PapyServer(papyServer.bindAddress, this);
                return true;
            }

            case "disconnect" -> {
                log_warn("disconnect not implemented!");

            }

            case "info" -> {
                sender.sendMessage(Component.text("PapyServer info:\nServer state: "+papyServer.state));
            }
        }

        return false;
    }

}