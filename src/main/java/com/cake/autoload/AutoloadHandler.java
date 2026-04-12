package com.cake.autoload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber
public class AutoloadHandler {

    private static final String AUTOLOAD_FILE = "autoload.txt";
    private static final Component TOAST_TITLE = Component.literal("World Autoload");
    private static final SystemToast.SystemToastId AUTOLOAD_TOAST_ID = new SystemToast.SystemToastId();

    private static boolean autoloadAttemptedThisLaunch = false;
    private static boolean toastShownThisLaunch = false;
    private static boolean userQuitCleanly = false;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            handleTitleScreen();
        } else if (event.getScreen() instanceof PauseScreen) {
            handlePauseScreen(event);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isLocalServer()) {
            return;
        }

        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }

        String levelId = extractLevelId(server);
        if (levelId == null) {
            return;
        }

        writeAutoloadFile(mc, levelId);
    }

    @SubscribeEvent
    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        if (userQuitCleanly) {
            Minecraft mc = Minecraft.getInstance();
            deleteAutoloadFile(mc);
            userQuitCleanly = false;
        }
    }

    private static void handleTitleScreen() {
        if (autoloadAttemptedThisLaunch) {
            return;
        }
        autoloadAttemptedThisLaunch = true;

        Minecraft mc = Minecraft.getInstance();

        mc.tell(() -> {
            if (!(mc.screen instanceof TitleScreen)) {
                return;
            }

            if (AutoloadConfig.ALWAYS_LOAD_LAST.get()) {
                handleAlwaysLoadLast(mc);
            } else {
                handleFileBasedAutoload(mc);
            }
        });
    }

    private static void handleAlwaysLoadLast(Minecraft mc) {
        List<LevelSummary> summaries = loadWorldSummaries(mc);
        if (summaries.isEmpty()) {
            showToast(mc, "No singleplayer worlds found.");
            return;
        }

        String levelId = summaries.getFirst().getLevelId();
        AutoloadMod.LOGGER.info("Always-load-last: opening most recent world '{}'", levelId);
        openWorld(mc, levelId);
    }

    private static void handleFileBasedAutoload(Minecraft mc) {
        Path autoloadPath = getAutoloadPath(mc);

        if (!Files.exists(autoloadPath)) {
            if (!toastShownThisLaunch) {
                toastShownThisLaunch = true;
                showToast(mc, "Autoload skipped: no world was open when the game last closed.");
            }
            return;
        }

        String worldName = readWorldName(autoloadPath);
        if (worldName == null) {
            AutoloadMod.LOGGER.warn("Autoload file is empty or malformed, deleting");
            deleteAutoloadFile(mc);
            return;
        }

        if (worldExistsInSaves(mc, worldName)) {
            AutoloadMod.LOGGER.info("Autoloading world '{}'", worldName);
            openWorld(mc, worldName);
        } else {
            AutoloadMod.LOGGER.warn("World '{}' no longer exists, deleting autoload file", worldName);
            deleteAutoloadFile(mc);
            showToast(mc, "Saved world no longer exists.");
        }
    }

    private static void handlePauseScreen(ScreenEvent.Init.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isLocalServer()) {
            return;
        }

        String expectedText = Component.translatable("menu.returnToMenu").getString();

        for (GuiEventListener listener : new ArrayList<>(event.getListenersList())) {
            if (!(listener instanceof Button button)) {
                continue;
            }
            if (!button.getMessage().getString().equals(expectedText)) {
                continue;
            }

            Button wrapper = Button.builder(button.getMessage(), btn -> {
                userQuitCleanly = true;
                AutoloadMod.LOGGER.info("Clean exit initiated from pause menu");
                button.onPress();
            }).bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight()).build();

            event.removeListener(button);
            event.addListener(wrapper);
            AutoloadMod.LOGGER.debug("Wrapped 'Save and Quit' button for clean exit detection");
            break;
        }
    }

    private static void openWorld(Minecraft mc, String levelId) {
        mc.createWorldOpenFlows().openWorld(levelId, () -> {
            AutoloadMod.LOGGER.warn("Failed to open world '{}'", levelId);
            mc.setScreen(new TitleScreen());
        });
    }

    private static boolean worldExistsInSaves(Minecraft mc, String worldName) {
        Path worldDir = mc.gameDirectory.toPath().resolve("saves").resolve(worldName);
        return Files.isDirectory(worldDir) && Files.exists(worldDir.resolve("level.dat"));
    }

    private static List<LevelSummary> loadWorldSummaries(Minecraft mc) {
        try {
            LevelStorageSource.LevelCandidates candidates = mc.getLevelSource().findLevelCandidates();
            return mc.getLevelSource().loadLevelSummaries(candidates).join();
        } catch (LevelStorageException e) {
            AutoloadMod.LOGGER.error("Failed to load world summaries", e);
            return List.of();
        }
    }

    private static String extractLevelId(IntegratedServer server) {
        Path levelDatPath = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
        Path worldDir = levelDatPath.getParent();
        if (worldDir == null) {
            AutoloadMod.LOGGER.error("Failed to extract level ID: world directory is null");
            return null;
        }
        Path folderName = worldDir.getFileName();
        if (folderName == null) {
            AutoloadMod.LOGGER.error("Failed to extract level ID: folder name is null");
            return null;
        }
        return folderName.toString();
    }

    private static void writeAutoloadFile(Minecraft mc, String levelId) {
        Path autoloadPath = getAutoloadPath(mc);
        try {
            Files.writeString(autoloadPath, levelId);
            AutoloadMod.LOGGER.info("Wrote autoload file for world '{}'", levelId);
        } catch (IOException e) {
            AutoloadMod.LOGGER.error("Failed to write autoload file", e);
        }
    }

    private static void deleteAutoloadFile(Minecraft mc) {
        Path autoloadPath = getAutoloadPath(mc);
        try {
            if (Files.deleteIfExists(autoloadPath)) {
                AutoloadMod.LOGGER.info("Deleted autoload file (clean exit)");
            }
        } catch (IOException e) {
            AutoloadMod.LOGGER.error("Failed to delete autoload file", e);
        }
    }

    private static String readWorldName(Path autoloadPath) {
        try {
            String content = Files.readString(autoloadPath);
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        } catch (IOException e) {
            AutoloadMod.LOGGER.error("Failed to read autoload file", e);
        }
        return null;
    }

    private static Path getAutoloadPath(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve(AUTOLOAD_FILE);
    }

    private static void showToast(Minecraft mc, String message) {
        if (AutoloadConfig.SILENCE_ALERTS.get()) return;
        SystemToast.add(mc.getToasts(), AUTOLOAD_TOAST_ID, TOAST_TITLE, Component.literal(message));
    }
}
