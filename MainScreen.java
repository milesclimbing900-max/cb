package com.example.donutauction.ui;

import com.example.donutauction.DonutAuctionClient;
import com.example.donutauction.calc.BidCalculator;
import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import com.example.donutauction.config.StreamerProfile;
import com.example.donutauction.notify.Notifier;
import com.example.donutauction.timer.AuctionTimer;
import com.example.donutauction.util.Fmt;
import com.example.donutauction.util.TimeParse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class MainScreen extends Screen {
    private record Lbl(String text, int x, int y) {}

    private final Screen parent;
    private final List<Lbl> labels = new ArrayList<>();
    private TextFieldWidget nameField, delayField, durationField;
    private ButtonWidget pauseBtn;
    private String status = "";

    public MainScreen(Screen parent) {
        super(Text.literal("DonutSMP Auction Assistant"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        ModConfig c = ConfigManager.get();
        int cx = width / 2, left = cx - 205, right = cx + 5;

        // --- streamer column ---
        if (c.streamers.size() > 1) {
            btn(left, 40, 98, "< Prev", () -> cycle(-1));
            btn(left + 102, 40, 98, "Next >", () -> cycle(1));
        }
        labels.add(new Lbl("Name", left, 70));
        nameField = field(left, 80, 200, "", "streamer name");
        labels.add(new Lbl("Stream delay (2.5s or 2500ms; bare number = seconds)", left, 102));
        delayField = field(left, 112, 200, "", "e.g. 2.5s");
        btn(left, 136, 98, "Add / Update", this::addOrUpdate);
        btn(left + 102, 136, 98, "Remove selected", this::removeSelected);
        fillFields();

        // --- timer column ---
        labels.add(new Lbl("Auction duration (90, 1m30s, 1:30)", right, 30));
        durationField = field(right, 40, 200, Fmt.delay(c.defaultDurationMs), "duration");
        btn(right, 64, 64, "Start", this::startTimer);
        pauseBtn = btn(right + 68, 64, 64, "Pause", this::pauseResume);
        btn(right + 136, 64, 64, "Reset", () -> { DonutAuctionClient.TIMER.reset(); DonutAuctionClient.TRACKER.clearBids(); status = "Timer and bids reset."; });
        btn(right, 88, 48, "-1s", () -> adjust(-1000));
        btn(right + 50, 88, 48, "+1s", () -> adjust(1000));
        btn(right + 100, 88, 48, "-0.1s", () -> adjust(-100));
        btn(right + 152, 88, 48, "+0.1s", () -> adjust(100));
        btn(right, 112, 200, "Move HUD / opacity...", () -> { if (client != null) client.setScreen(new HudEditScreen(this)); });

        // --- bottom row ---
        int by = height - 28;
        btn(cx - 155, by, 100, "Settings", () -> { if (client != null) client.setScreen(new SettingsScreen(this)); });
        btn(cx - 50, by, 100, "Copy bid", this::copyBid);
        btn(cx + 55, by, 100, "Done", this::close);
    }

    private ButtonWidget btn(int x, int y, int w, String label, Runnable r) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), bt -> r.run()).dimensions(x, y, w, 20).build();
        addDrawableChild(b);
        return b;
    }

    private TextFieldWidget field(int x, int y, int w, String text, String hint) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(hint));
        f.setMaxLength(48);
        f.setText(text);
        f.setPlaceholder(Text.literal(hint));
        addDrawableChild(f);
        return f;
    }

    private void fillFields() {
        StreamerProfile sp = ConfigManager.get().selected();
        nameField.setText(sp == null ? "" : sp.name);
        delayField.setText(sp == null ? "" : Fmt.delay(sp.delayMs));
    }

    private void cycle(int dir) {
        ModConfig c = ConfigManager.get();
        if (c.streamers.isEmpty()) { status = "No streamers yet: add one."; return; }
        int idx = c.streamers.indexOf(c.selected());
        c.selectedStreamer = c.streamers.get(Math.floorMod(idx + dir, c.streamers.size())).name;
        ConfigManager.save();
        fillFields();
    }

    private void addOrUpdate() {
        ModConfig c = ConfigManager.get();
        String name = nameField.getText().trim();
        Long delay = TimeParse.parseMs(delayField.getText());
        if (name.isEmpty() || name.length() > 32) { status = "Enter a name (1-32 characters)."; return; }
        if (delay == null || delay > 600_000) { status = "Delay must be a valid time up to 10 minutes."; return; }
        StreamerProfile existing = c.find(name);
        if (existing != null) existing.delayMs = delay;
        else c.streamers.add(new StreamerProfile(name, delay));
        c.selectedStreamer = name;
        c.sanitize();
        ConfigManager.save();
        status = (existing != null ? "Updated " : "Added ") + name + " (" + Fmt.delay(delay) + ").";
        clearAndInit();
    }

    private void removeSelected() {
        ModConfig c = ConfigManager.get();
        StreamerProfile sp = c.selected();
        if (sp == null) { status = "Nothing selected."; return; }
        c.streamers.remove(sp);
        c.sanitize();
        ConfigManager.save();
        fillFields();
        status = "Removed " + sp.name + ".";
        clearAndInit();
    }

    private void startTimer() {
        ModConfig c = ConfigManager.get();
        Long d = TimeParse.parseMs(durationField.getText());
        if (d == null || d < 1000) { status = "Invalid duration (min 1s)."; return; }
        c.defaultDurationMs = d;
        ConfigManager.save();
        DonutAuctionClient.TRACKER.clearBids(); // new auction: forget the previous top bid
        DonutAuctionClient.TIMER.start(d, AuctionTimer.Origin.MANUAL_ESTIMATE, c);
        status = "New auction: timer started (estimate), bids cleared.";
    }

    private void pauseResume() {
        AuctionTimer t = DonutAuctionClient.TIMER;
        if (t.state() == AuctionTimer.State.RUNNING) t.pause();
        else if (t.state() == AuctionTimer.State.PAUSED) t.resume();
    }

    private void adjust(long deltaMs) {
        DonutAuctionClient.TIMER.adjust(deltaMs, ConfigManager.get());
    }

    private void copyBid() {
        BidCalculator.Suggestion s = DonutAuctionClient.TRACKER.suggestion();
        if (!s.hasBase()) { status = "No observed bid yet."; return; }
        if (client != null) client.keyboard.setClipboard(Fmt.copy(s.suggested(), ConfigManager.get().copyShortForm));
        status = "Copied " + Fmt.copy(s.suggested(), ConfigManager.get().copyShortForm) + " to clipboard. Nothing was sent.";
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        AuctionTimer.State st = DonutAuctionClient.TIMER.state();
        pauseBtn.setMessage(Text.literal(st == AuctionTimer.State.PAUSED ? "Resume" : "Pause"));

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        for (Lbl l : labels) ctx.drawTextWithShadow(textRenderer, l.text(), l.x(), l.y(), 0xFFAAAAAA);
        StreamerProfile sp = ConfigManager.get().selected();
        ctx.drawTextWithShadow(textRenderer, "Selected: " + (sp == null ? "(none)" : sp.name),
                width / 2 - 205, 28, 0xFFFFFF55);

        int n = ConfigManager.get().streamers.size();
        if (n <= 1) {
            ctx.drawTextWithShadow(textRenderer, n == 0 ? "No streamer yet: fill in below, then Add"
                    : "Add a 2nd streamer to switch between them", width / 2 - 205, 46, 0xFF888888);
        }
        GlassPanel.draw(ctx, textRenderer, width / 2 - 205, 166, InfoLines.build(), "Auction status",
                ConfigManager.get().hudOpacity, 410);
        if (!status.isEmpty()) ctx.drawTextWithShadow(textRenderer, status, width / 2 - 205, height - 42, 0xFF55FFFF);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
