package com.example.donutauction;

import com.example.donutauction.calc.BidCalculator;
import com.example.donutauction.chat.ChatTracker;
import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import com.example.donutauction.notify.Notifier;
import com.example.donutauction.timer.AuctionTimer;
import com.example.donutauction.ui.HudRenderer;
import com.example.donutauction.ui.MainScreen;
import com.example.donutauction.util.Fmt;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class DonutAuctionClient implements ClientModInitializer {
    public static final String MOD_ID = "donutauction";
    public static final AuctionTimer TIMER = new AuctionTimer();
    public static final ChatTracker TRACKER = new ChatTracker();
    public static final Notifier NOTIFIER = new Notifier();

    private static KeyBinding openKey, toggleKey, resetKey, copyKey;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = key("open", GLFW.GLFW_KEY_K, cat);
        toggleKey = key("toggle", GLFW.GLFW_KEY_UNKNOWN, cat);
        resetKey = key("reset", GLFW.GLFW_KEY_UNKNOWN, cat);
        copyKey = key("copy", GLFW.GLFW_KEY_UNKNOWN, cat);

        // Chat: server/system messages and player chat. Both callbacks run on the client thread.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> safe(() ->
                TRACKER.onMessage(message.getString(), overlay ? ChatTracker.Source.OVERLAY : ChatTracker.Source.GAME)));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> safe(() ->
                TRACKER.onMessage(message.getString(), ChatTracker.Source.CHAT)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TRACKER.setConnected(true));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TRACKER.setConnected(false);
            TIMER.pauseIfRunning(); // an estimate is meaningless across a disconnect
        });

        ClientTickEvents.END_CLIENT_TICK.register(DonutAuctionClient::onTick);
        HudRenderer.register();
        registerCommands();
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("donutauction");

    /** A bug in message handling must never break chat for the player. */
    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            LOG.error("Auction Assistant failed to handle a chat message", e);
        }
    }

    private static KeyBinding key(String id, int code, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key." + MOD_ID + "." + id, InputUtil.Type.KEYSYM, code, cat));
    }

    private static void onTick(MinecraftClient mc) {
        ModConfig c = ConfigManager.get();
        while (openKey.wasPressed()) mc.setScreen(new MainScreen(mc.currentScreen));
        while (toggleKey.wasPressed()) {
            switch (TIMER.state()) {
                case RUNNING -> TIMER.pause();
                case PAUSED -> TIMER.resume();
                default -> {
                    TRACKER.clearBids(); // starting = new auction
                    TIMER.start(c.defaultDurationMs, AuctionTimer.Origin.MANUAL_ESTIMATE, c);
                }
            }
        }
        while (resetKey.wasPressed()) { TIMER.reset(); TRACKER.clearBids(); }
        while (copyKey.wasPressed()) copyBid(mc);
        TIMER.tick(c, NOTIFIER);
    }

    private static void copyBid(MinecraftClient mc) {
        BidCalculator.Suggestion s = TRACKER.suggestion();
        if (!s.hasBase()) { Notifier.info("No observed bid yet."); return; }
        mc.keyboard.setClipboard(Fmt.copy(s.suggested(), ConfigManager.get().copyShortForm));
        Notifier.info("Copied suggested bid " + Fmt.copy(s.suggested(), ConfigManager.get().copyShortForm) + " to the clipboard. Nothing was sent.");
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("dsmpa")
                        .then(ClientCommandManager.literal("test")
                                .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String t = StringArgumentType.getString(ctx, "text");
                                            TRACKER.onMessage(t);
                                            ctx.getSource().sendFeedback(Text.literal("[DSMPA] fed to parser: " + t));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("log").executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal("[DSMPA] seen: game=" + TRACKER.seenGame()
                                    + " chat=" + TRACKER.seenChat() + " actionbar=" + TRACKER.seenOverlay()
                                    + " | matched=" + TRACKER.matched() + " unmatched=" + TRACKER.unmatched()));
                            ctx.getSource().sendFeedback(Text.literal("[DSMPA] last chat lines (newest first):"));
                            TRACKER.recentChat().stream().limit(10)
                                    .forEach(s -> ctx.getSource().sendFeedback(Text.literal(" - " + s)));
                            ctx.getSource().sendFeedback(Text.literal("[DSMPA] events (newest first):"));
                            TRACKER.eventLog().stream().limit(10)
                                    .forEach(s -> ctx.getSource().sendFeedback(Text.literal(" * " + s)));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            ConfigManager.load();
                            ctx.getSource().sendFeedback(Text.literal("[DSMPA] config reloaded"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(ctx -> {
                            TRACKER.clearAll();
                            TIMER.reset();
                            ctx.getSource().sendFeedback(Text.literal("[DSMPA] state cleared"));
                            return 1;
                        }))));
    }
}
