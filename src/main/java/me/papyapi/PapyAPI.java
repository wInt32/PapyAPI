package me.papyapi;

import jdk.jfr.StackTrace;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PapyAPI extends JavaPlugin implements PapyRequestDispatcher, CommandExecutor {

    private Logger logger;
    private PapyServer papyServer;

    private final BlockingQueue<PapyResponse> asyncResponseQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<SetBlockRequest> asyncSetBlockRequestQueue = new LinkedBlockingQueue<>();

    public PapyAPI papyAPI;
    private boolean debugEnabled = false;

    @Override
    public void onEnable() {
        this.papyAPI = this;
        logger = getLogger();
        papyServer = new PapyServer("0.0.0.0", 32932, this);

    }

    @Override
    public void onDisable() {
        logger.log(Level.INFO, "Stopping PapyServer...");
        papyServer.stop();
    }

    private PapyResponse setBlock(String worldname, int x, int y, int z, String blockname) {
        World world = getServer().getWorld(worldname);
        if (world == null) {
            return new PapyResponse(false, "World not found");
        }

        Material material = Material.matchMaterial(blockname);
        if (material == null) {
            return new PapyResponse(false, "Invalid block");
        }

        Block block = world.getBlockAt(x, y, z);
        block.setType(material);


        return new PapyResponse(true);
    }


    public boolean syncMsgEnabled = false;
    @Override
    public PapyResponse dispatch(PapyRequest request) throws InterruptedException {

        log_debug("Dispatching request #" + request.number);

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
                log_info("Client disconnected.");
                result.complete(new PapyResponse(true, "Connection closed.", true));
            }

            case PING -> {
                log_info("PING");
                result.complete(new PapyResponse(true, "PONG!"));
            }

            case SET_BLOCK -> {
                SetBlockRequest setBlockRequest = new SetBlockRequest(request);
                log_debug(setBlockRequest.getBody());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        result.complete(setBlock(
                                setBlockRequest.world,
                                setBlockRequest.x,
                                setBlockRequest.y,
                                setBlockRequest.z,
                                setBlockRequest.block
                        ));
                    }
                }.runTask(papyAPI);

            }
            case ASYNC_SET_BLOCK -> {
                SetBlockRequest setBlockRequest = new SetBlockRequest(request);
                log_debug(setBlockRequest.getBody());
                asyncSetBlockRequestQueue.put(setBlockRequest);
                return new PapyResponse(true);
            }

            case SYNC -> {
                if (syncMsgEnabled)
                    log_info("Syncing...");
                else
                    log_debug("Syncing...");

                asyncResponseQueue.clear();
                int setBlockRequestCount = asyncSetBlockRequestQueue.size();
                CountDownLatch latch = new CountDownLatch(setBlockRequestCount);

                if (asyncSetBlockRequestQueue.isEmpty())
                    return new PapyResponse(false, "No async data");

                log_debug(setBlockRequestCount + " async setblock requests");

                // async set block requests
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            for (int i = 0; i < setBlockRequestCount; i++) {
                                SetBlockRequest setBlockRequest = asyncSetBlockRequestQueue.take();
                                asyncResponseQueue.put(setBlock(
                                        setBlockRequest.world,
                                        setBlockRequest.x,
                                        setBlockRequest.y,
                                        setBlockRequest.z,
                                        setBlockRequest.block
                                ));
                                latch.countDown();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                }.runTask(papyAPI);

                log_debug("Waiting for tasks to finish...");

                latch.await();

                if (syncMsgEnabled)
                    log_info("Sync finished.");
                else
                    log_debug("Sync finished.");

                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < asyncResponseQueue.size(); i++) {
                    PapyResponse asyncResponse = asyncResponseQueue.take();

                    stringBuilder.append(asyncResponse.success);
                    stringBuilder.append(';');
                    stringBuilder.append(asyncResponse.message);
                    stringBuilder.append('\t');
                }
                return new PapyResponse(true, stringBuilder.toString());
            }
        }

        return result.join();

    }

    @Override
    public void log_debug(String msg) {
        if(debugEnabled)
            logger.log(Level.INFO, msg);
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
                sender.sendMessage(Component.text("PapyServer info:", NamedTextColor.AQUA));
                sender.sendMessage(Component.text("Server state: "+papyServer.state));
                if (papyServer == null)
                    break;
                if (papyServer.serverThread != null) {
                    StackTraceElement[] st = papyServer.serverThread.getStackTrace();
                    sender.sendMessage(Component.text("ServerThread:", NamedTextColor.YELLOW));
                    for (StackTraceElement s : st) {
                        sender.sendMessage(Component.text(s.toString()));
                    }

                }
                if (papyServer.dispatcherThread != null) {
                    StackTraceElement[] st = papyServer.dispatcherThread.getStackTrace();
                    sender.sendMessage(Component.text("DispatcherThread:", NamedTextColor.YELLOW));
                    for (StackTraceElement s : st) {
                        sender.sendMessage(Component.text(s.toString()));
                    }

                }
                if (papyServer.responseThread != null) {
                    StackTraceElement[] st = papyServer.responseThread.getStackTrace();
                    sender.sendMessage(Component.text("ResponseThread:", NamedTextColor.YELLOW));
                    for (StackTraceElement s : st) {
                        sender.sendMessage(Component.text(s.toString()));
                    }

                }
            }
            case "debug" -> {
                debugEnabled = !debugEnabled;
                Component c;
                if (debugEnabled)
                    c = Component.text("enabled", NamedTextColor.GREEN, TextDecoration.BOLD);
                else
                    c = Component.text("disabled", NamedTextColor.RED, TextDecoration.BOLD);
                sender.sendMessage(Component.text("Debug: ").append(c));
            }
            case "syncmsg" -> {
                syncMsgEnabled = !syncMsgEnabled;
                Component c;
                if (syncMsgEnabled)
                    c = Component.text("enabled", NamedTextColor.GREEN, TextDecoration.BOLD);
                else
                    c = Component.text("disabled", NamedTextColor.RED, TextDecoration.BOLD);
                sender.sendMessage(Component.text("Sync messages: ").append(c));
            }
        }

        return false;
    }

}