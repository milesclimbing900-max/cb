package com.example.donutauction.ui;

import com.example.donutauction.chat.MessageParser;
import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import com.example.donutauction.util.Fmt;
import com.example.donutauction.util.TimeParse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SettingsScreen extends Screen {
    private record Lbl(String text, int x, int y) {}

    private final Screen parent;
    private int page = 0;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private final List<Lbl> labels = new ArrayList<>();
    private String status = "";

    public SettingsScreen(Screen parent) {
        super(Text.literal("Auction Assistant Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        fields.clear();
        labels.clear();
        ModConfig c = ConfigManager.get();
        int cx = width / 2, L = cx - 155, R = cx + 5, w = 150;

        if (page == 0) {
            toggle(L, 30, w, () -> "Sound: " + (c.soundEnabled ? "ON" : "OFF"), () -> c.soundEnabled = !c.soundEnabled);
            toggle(R, 30, w, () -> "Sound type: " + c.soundType, () -> c.soundType = c.soundType.next());
            toggle(L, 54, w, () -> "Volume: " + Math.round(c.soundVolume * 100) + "%",
                    () -> c.soundVolume = c.soundVolume >= 0.99f ? 0.25f : c.soundVolume + 0.25f);
            toggle(R, 54, w, () -> "Chat alerts: " + (c.chatNotifications ? "ON" : "OFF"), () -> c.chatNotifications = !c.chatNotifications);
            toggle(L, 78, w, () -> "Action-bar alerts: " + (c.overlayNotifications ? "ON" : "OFF"), () -> c.overlayNotifications = !c.overlayNotifications);
            toggle(R, 78, w, () -> "Screen flash: " + (c.screenFlash ? "ON" : "OFF"), () -> c.screenFlash = !c.screenFlash);
            toggle(L, 102, w, () -> "HUD: " + (c.hudEnabled ? "ON" : "OFF"), () -> c.hudEnabled = !c.hudEnabled);
            toggle(R, 102, w, () -> "HUD when idle: " + (c.hudShowWhenIdle ? "ON" : "OFF"), () -> c.hudShowWhenIdle = !c.hudShowWhenIdle);

            labels.add(new Lbl("Warning seconds (comma list)", L, 130));
            fields.add(field(L, 140, w, String.join(",", c.warningThresholdsSec.stream().map(String::valueOf).toList())));
            labels.add(new Lbl("Final alert: ms before est. end (0=off)", R, 130));
            fields.add(field(R, 140, w, String.valueOf(c.bidAlertMs)));
            labels.add(new Lbl("Show milliseconds below (seconds)", L, 166));
            fields.add(field(L, 176, w, String.valueOf(c.preciseBelowSec)));
        } else {
            toggle(L, 30, w, () -> "Increment: " + c.incrementMode,
                    () -> c.incrementMode = ModConfig.IncrementMode.values()[
                            (c.incrementMode.ordinal() + 1) % ModConfig.IncrementMode.values().length]);
            toggle(R, 30, w, () -> "Chat parsing: " + (c.parsingEnabled ? "ON" : "OFF"), () -> c.parsingEnabled = !c.parsingEnabled);
            toggle(L, 54, w, () -> "Payments as bids: " + (c.paymentsCountAsBids ? "ON" : "OFF"), () -> c.paymentsCountAsBids = !c.paymentsCountAsBids);
            toggle(R, 54, w, () -> "Log unmatched chat: " + (c.logUnmatchedChat ? "ON" : "OFF"), () -> c.logUnmatchedChat = !c.logUnmatchedChat);
            toggle(L, 78, w, () -> "Auto-start from chat: " + (c.autoStartTimerFromChat ? "ON" : "OFF"), () -> c.autoStartTimerFromChat = !c.autoStartTimerFromChat);
            toggle(R, 78, w, () -> "Only selected streamer: " + (c.onlySelectedStreamer ? "ON" : "OFF"), () -> c.onlySelectedStreamer = !c.onlySelectedStreamer);
            toggle(R, 102, w, () -> "New-bid alerts: " + (c.notifyOnBid ? "ON" : "OFF"), () -> c.notifyOnBid = !c.notifyOnBid);
            addBtn(L, 102, w, "Move HUD / opacity...", () -> { applyFields(); if (client != null) client.setScreen(new HudEditScreen(this)); });

            labels.add(new Lbl("Fixed increment (1500, 2k, 1.5m)", L, 130));
            fields.add(field(L, 140, w, Fmt.raw(c.incrementFixed)));
            labels.add(new Lbl("Percent increment (%)", R, 130));
            fields.add(field(R, 140, w, String.valueOf(c.incrementPercent)));
            labels.add(new Lbl("Max budget (0 = none)", L, 166));
            fields.add(field(L, 176, w, Fmt.raw(c.maxBudget)));
            labels.add(new Lbl("Default auction duration", R, 166));
            fields.add(field(R, 176, w, Fmt.delay(c.defaultDurationMs)));
        }

        int by = height - 28;
        addBtn(cx - 155, by, 100, "Page " + (page + 1) + "/2", () -> { applyFields(); page = 1 - page; clearAndInit(); });
        addBtn(cx - 50, by, 100, "Reload file", () -> { ConfigManager.load(); status = "Reloaded from disk."; clearAndInit(); });
        addBtn(cx + 55, by, 100, "Done", this::close);
    }

    private void toggle(int x, int y, int w, Supplier<String> label, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label.get()), bt -> {
            action.run();
            bt.setMessage(Text.literal(label.get()));
            ConfigManager.get().sanitize();
            ConfigManager.save();
        }).dimensions(x, y, w, 20).build();
        addDrawableChild(b);
    }

    private void addBtn(int x, int y, int w, String label, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), bt -> r.run()).dimensions(x, y, w, 20).build());
    }

    private TextFieldWidget field(int x, int y, int w, String text) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(64);
        f.setText(text);
        addDrawableChild(f);
        return f;
    }

    private void applyFields() {
        ModConfig c = ConfigManager.get();
        try {
            if (page == 0 && fields.size() >= 3) {
                List<Integer> th = new ArrayList<>();
                for (String p : fields.get(0).getText().split(",")) {
                    String s = p.trim();
                    if (!s.isEmpty()) th.add(Integer.parseInt(s));
                }
                c.warningThresholdsSec = th;
                c.bidAlertMs = Integer.parseInt(fields.get(1).getText().trim());
                c.preciseBelowSec = Integer.parseInt(fields.get(2).getText().trim());
            } else if (page == 1 && fields.size() >= 4) {
                Double fixed = MessageParser.parseAmount(fields.get(0).getText());
                Double budget = MessageParser.parseAmount(fields.get(2).getText());
                Long dur = TimeParse.parseMs(fields.get(3).getText());
                if (fixed == null || budget == null || dur == null) throw new NumberFormatException();
                c.incrementFixed = fixed;
                c.incrementPercent = Double.parseDouble(fields.get(1).getText().trim());
                c.maxBudget = budget;
                c.defaultDurationMs = dur;
            }
            status = "";
        } catch (NumberFormatException e) {
            status = "Some values were invalid and were not saved.";
        }
        c.sanitize();
        ConfigManager.save();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        for (Lbl l : labels) ctx.drawTextWithShadow(textRenderer, l.text(), l.x(), l.y(), 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Parsing rules are edited in: " + ConfigManager.file().getFileName()
                + " (then press Reload file)", width / 2 - 155, height - 52, 0xFF888888);
        if (!status.isEmpty()) ctx.drawTextWithShadow(textRenderer, status, width / 2 - 155, height - 40, 0xFFFF5555);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        applyFields();
        if (client != null) client.setScreen(parent);
    }
}
