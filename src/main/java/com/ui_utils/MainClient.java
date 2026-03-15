package com.ui_utils;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ui_utils.mixin.accessor.ClientConnectionAccessor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.Locale;

public class MainClient implements ClientModInitializer {
    public static Font monospace;
    public static Color darkWhite;

    public static KeyBinding restoreScreenKey;

    public static Logger LOGGER = LoggerFactory.getLogger("ui-utils");
    public static MinecraftClient mc = MinecraftClient.getInstance();
    public static final int UI_TOOLBOX_COLUMN_X = 5;
    public static final int UI_TOOLBOX_BUTTON_HEIGHT = 20;
    public static final int UI_TOOLBOX_VERTICAL_SPACING = 4;
    public static final int UI_CHAT_FIELD_WIDTH = 160;
    public static final int UI_CHAT_FIELD_HEIGHT = 20;
    public static final int UI_CHAT_FIELD_SPACING = 4;
    public static final int UI_CHAT_FIELD_MAX_LENGTH = 256;
    public static final String RESTORE_SCREEN_KEY = "key.uiutils.restore_screen";
    public static final KeyBinding.Category UI_UTILS_CATEGORY = KeyBinding.Category.create(Identifier.of("ui_utils", "ui_utils"));
    @Override
    public void onInitializeClient() {
        // register "restore screen" key
        restoreScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(RESTORE_SCREEN_KEY, GLFW.GLFW_KEY_V, UI_UTILS_CATEGORY));

        // register event for END_CLIENT_TICK
        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            // detect if the "restore screen" keybinding is pressed
            while (restoreScreenKey.wasPressed()) {
                if (SharedVariables.storedScreen != null && SharedVariables.storedScreenHandler != null && client.player != null) {
                    client.setScreen(SharedVariables.storedScreen);
                    client.player.currentScreenHandler = SharedVariables.storedScreenHandler;
                }
            }
        });

        // set java.awt.headless to false if os is not mac (allows for JFrame guis to be used)
        if (!isMac()) {
            System.setProperty("java.awt.headless", "false");
            monospace = new Font(Font.MONOSPACED, Font.PLAIN, 10);
            darkWhite = new Color(220, 220, 220);
        }
    }

    private static boolean isMac() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ENGLISH).contains("mac");
    }

    // bro are you ever going to clean this up?
    // this code is very messy, ill clean it up if you dont
    // -- MrBreakNFix
    @SuppressWarnings("all")
    public static int createWidgets(MinecraftClient mc, Screen screen) {
        List<ButtonWidget> buttonWidgets = new ArrayList<>();

        // register "close without packet" button in all HandledScreens
        ButtonWidget closeButton = ButtonWidget.builder(Text.of("Close without packet"), (button) -> mc.setScreen(null))
                .width(115)
                .build();
        buttonWidgets.add(closeButton);

        // register "de-sync" button in all HandledScreens
        ButtonWidget deSyncButton = ButtonWidget.builder(Text.of("De-sync"), (button) -> {
            if (mc.getNetworkHandler() != null && mc.player != null) {
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            } else {
                LOGGER.warn("Minecraft network handler or player was null while using 'De-sync' in UI Utils.");
            }
        }).width(115).build();
        buttonWidgets.add(deSyncButton);

        // register "send packets" button in all HandledScreens
        ButtonWidget sendPacketsButton = ButtonWidget.builder(Text.of("Send packets: " + SharedVariables.sendUIPackets), (button) -> {
            SharedVariables.sendUIPackets = !SharedVariables.sendUIPackets;
            button.setMessage(Text.of("Send packets: " + SharedVariables.sendUIPackets));
        }).width(115).build();
        buttonWidgets.add(sendPacketsButton);

        // register "delay packets" button in all HandledScreens
        ButtonWidget delayPacketsButton = ButtonWidget.builder(Text.of("Delay packets: " + SharedVariables.delayUIPackets), (button) -> {
            SharedVariables.delayUIPackets = !SharedVariables.delayUIPackets;
            button.setMessage(Text.of("Delay packets: " + SharedVariables.delayUIPackets));
            if (!SharedVariables.delayUIPackets && !SharedVariables.delayedUIPackets.isEmpty() && mc.getNetworkHandler() != null) {
                for (Packet<?> packet : SharedVariables.delayedUIPackets) {
                    mc.getNetworkHandler().sendPacket(packet);
                }
                if (mc.player != null) {
                    mc.player.sendMessage(Text.of("Sent " + SharedVariables.delayedUIPackets.size() + " packets."), false);
                }
                SharedVariables.delayedUIPackets.clear();
            }
        }).width(115).build();
        buttonWidgets.add(delayPacketsButton);

        // register "save gui" button in all HandledScreens
        ButtonWidget saveGuiButton = ButtonWidget.builder(Text.of("Save GUI"), (button) -> {
            if (mc.player != null) {
                SharedVariables.storedScreen = mc.currentScreen;
                SharedVariables.storedScreenHandler = mc.player.currentScreenHandler;
            }
        }).width(115).build();
        buttonWidgets.add(saveGuiButton);

        // register "disconnect and send packets" button in all HandledScreens
        ButtonWidget disconnectButton = ButtonWidget.builder(Text.of("Disconnect and send packets"), (button) -> {
            SharedVariables.delayUIPackets = false;
            if (mc.getNetworkHandler() != null) {
                for (Packet<?> packet : SharedVariables.delayedUIPackets) {
                    mc.getNetworkHandler().sendPacket(packet);
                }
                mc.getNetworkHandler().getConnection().disconnect(Text.of("Disconnecting (UI-UTILS)"));
            } else {
                LOGGER.warn("Minecraft network handler (mc.getNetworkHandler()) is null while client is disconnecting.");
            }
            SharedVariables.delayedUIPackets.clear();
        }).width(160).build();
        buttonWidgets.add(disconnectButton);

        // register "fabricate packet" button in all HandledScreens
        ButtonWidget fabricatePacketButton = ButtonWidget.builder(Text.of("Fabricate packet"), (button) -> {
            JFrame frame = new JFrame("Choose Packet");
            frame.setBounds(0, 0, 450, 100);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setLayout(null);

            JButton clickSlotButton = getPacketOptionButton("Click Slot");
            clickSlotButton.setBounds(100, 25, 110, 20);
            clickSlotButton.addActionListener((event) -> {
                frame.setVisible(false);

                JFrame clickSlotFrame = new JFrame("Click Slot Packet");
                clickSlotFrame.setBounds(0, 0, 450, 300);
                clickSlotFrame.setResizable(false);
                clickSlotFrame.setLocationRelativeTo(null);
                clickSlotFrame.setLayout(null);

                JLabel syncIdLabel = new JLabel("Sync Id:");
                syncIdLabel.setFocusable(false);
                syncIdLabel.setFont(monospace);
                syncIdLabel.setBounds(25, 25, 100, 20);

                JLabel revisionLabel = new JLabel("Revision:");
                revisionLabel.setFocusable(false);
                revisionLabel.setFont(monospace);
                revisionLabel.setBounds(25, 50, 100, 20);

                JLabel slotLabel = new JLabel("Slot:");
                slotLabel.setFocusable(false);
                slotLabel.setFont(monospace);
                slotLabel.setBounds(25, 75, 100, 20);

                JLabel buttonLabel = new JLabel("Button:");
                buttonLabel.setFocusable(false);
                buttonLabel.setFont(monospace);
                buttonLabel.setBounds(25, 100, 100, 20);

                JLabel actionLabel = new JLabel("Action:");
                actionLabel.setFocusable(false);
                actionLabel.setFont(monospace);
                actionLabel.setBounds(25, 125, 100, 20);

                JLabel timesToSendLabel = new JLabel("Times to send:");
                timesToSendLabel.setFocusable(false);
                timesToSendLabel.setFont(monospace);
                timesToSendLabel.setBounds(25, 190, 100, 20);

                JTextField syncIdField = new JTextField(1);
                syncIdField.setFont(monospace);
                syncIdField.setBounds(125, 25, 100, 20);

                JTextField revisionField = new JTextField(1);
                revisionField.setFont(monospace);
                revisionField.setBounds(125, 50, 100, 20);

                JTextField slotField = new JTextField(1);
                slotField.setFont(monospace);
                slotField.setBounds(125, 75, 100, 20);

                JTextField buttonField = new JTextField(1);
                buttonField.setFont(monospace);
                buttonField.setBounds(125, 100, 100, 20);

                JComboBox<String> actionField = new JComboBox<>(new Vector<>(ImmutableList.of(
                        "PICKUP",
                        "QUICK_MOVE",
                        "SWAP",
                        "CLONE",
                        "THROW",
                        "QUICK_CRAFT",
                        "PICKUP_ALL"
                )));
                actionField.setFocusable(false);
                actionField.setEditable(false);
                actionField.setBorder(BorderFactory.createEmptyBorder());
                actionField.setBackground(darkWhite);
                actionField.setFont(monospace);
                actionField.setBounds(125, 125, 100, 20);

                JLabel statusLabel = new JLabel();
                statusLabel.setVisible(false);
                statusLabel.setFocusable(false);
                statusLabel.setFont(monospace);
                statusLabel.setBounds(210, 150, 190, 20);

                JCheckBox delayBox = new JCheckBox("Delay");
                delayBox.setBounds(115, 150, 85, 20);
                delayBox.setSelected(false);
                delayBox.setFont(monospace);
                delayBox.setFocusable(false);

                JTextField timesToSendField = new JTextField("1");
                timesToSendField.setFont(monospace);
                timesToSendField.setBounds(125, 190, 100, 20);

                JButton sendButton = new JButton("Send");
                sendButton.setFocusable(false);
                sendButton.setBounds(25, 150, 75, 20);
                sendButton.setBorder(BorderFactory.createEtchedBorder());
                sendButton.setBackground(darkWhite);
                sendButton.setFont(monospace);
                sendButton.addActionListener((event0) -> {
                    if (
                            MainClient.isInteger(syncIdField.getText()) &&
                                    MainClient.isInteger(revisionField.getText()) &&
                                    MainClient.isInteger(slotField.getText()) &&
                                    MainClient.isInteger(buttonField.getText()) &&
                                    MainClient.isInteger(timesToSendField.getText()) &&
                                    actionField.getSelectedItem() != null) {
                        int syncId = Integer.parseInt(syncIdField.getText());
                        int revision = Integer.parseInt(revisionField.getText());
                        short slot = Short.parseShort(slotField.getText());
                        byte button0 = Byte.parseByte(buttonField.getText());
                        SlotActionType action = MainClient.stringToSlotActionType(actionField.getSelectedItem().toString());
                        int timesToSend = Integer.parseInt(timesToSendField.getText());

                        if (action != null) {
                            ClickSlotC2SPacket packet = new ClickSlotC2SPacket(syncId, revision, slot, button0, action, new Int2ObjectArrayMap<>(), ItemStackHash.EMPTY);
                            try {
                                Runnable toRun = getFabricatePacketRunnable(mc, delayBox.isSelected(), packet);
                                for (int i = 0; i < timesToSend; i++) {
                                    toRun.run();
                                }
                            } catch (Exception e) {
                                statusLabel.setForeground(Color.RED.darker());
                                statusLabel.setText("You must be connected to a server!");
                                MainClient.queueTask(() -> {
                                    statusLabel.setVisible(false);
                                    statusLabel.setText("");
                                }, 1500L);
                                return;
                            }
                            statusLabel.setVisible(true);
                            statusLabel.setForeground(Color.GREEN.darker());
                            statusLabel.setText("Sent successfully!");
                            MainClient.queueTask(() -> {
                                statusLabel.setVisible(false);
                                statusLabel.setText("");
                            }, 1500L);
                        } else {
                            statusLabel.setVisible(true);
                            statusLabel.setForeground(Color.RED.darker());
                            statusLabel.setText("Invalid arguments!");
                            MainClient.queueTask(() -> {
                                statusLabel.setVisible(false);
                                statusLabel.setText("");
                            }, 1500L);
                        }
                    } else {
                        statusLabel.setVisible(true);
                        statusLabel.setForeground(Color.RED.darker());
                        statusLabel.setText("Invalid arguments!");
                        MainClient.queueTask(() -> {
                            statusLabel.setVisible(false);
                            statusLabel.setText("");
                        }, 1500L);
                    }
                });

                clickSlotFrame.add(syncIdLabel);
                clickSlotFrame.add(revisionLabel);
                clickSlotFrame.add(slotLabel);
                clickSlotFrame.add(buttonLabel);
                clickSlotFrame.add(actionLabel);
                clickSlotFrame.add(timesToSendLabel);
                clickSlotFrame.add(syncIdField);
                clickSlotFrame.add(revisionField);
                clickSlotFrame.add(slotField);
                clickSlotFrame.add(buttonField);
                clickSlotFrame.add(actionField);
                clickSlotFrame.add(sendButton);
                clickSlotFrame.add(statusLabel);
                clickSlotFrame.add(delayBox);
                clickSlotFrame.add(timesToSendField);
                clickSlotFrame.setVisible(true);
            });

            JButton buttonClickButton = getPacketOptionButton("Button Click");
            buttonClickButton.setBounds(250, 25, 110, 20);
            buttonClickButton.addActionListener((event) -> {
                frame.setVisible(false);

                JFrame buttonClickFrame = new JFrame("Button Click Packet");
                buttonClickFrame.setBounds(0, 0, 450, 250);
                buttonClickFrame.setResizable(false);
                buttonClickFrame.setLocationRelativeTo(null);
                buttonClickFrame.setLayout(null);

                JLabel syncIdLabel = new JLabel("Sync Id:");
                syncIdLabel.setFocusable(false);
                syncIdLabel.setFont(monospace);
                syncIdLabel.setBounds(25, 25, 100, 20);

                JLabel buttonIdLabel = new JLabel("Button Id:");
                buttonIdLabel.setFocusable(false);
                buttonIdLabel.setFont(monospace);
                buttonIdLabel.setBounds(25, 50, 100, 20);

                JTextField syncIdField = new JTextField(1);
                syncIdField.setFont(monospace);
                syncIdField.setBounds(125, 25, 100, 20);

                JTextField buttonIdField = new JTextField(1);
                buttonIdField.setFont(monospace);
                buttonIdField.setBounds(125, 50, 100, 20);

                JLabel statusLabel = new JLabel();
                statusLabel.setVisible(false);
                statusLabel.setFocusable(false);
                statusLabel.setFont(monospace);
                statusLabel.setBounds(210, 95, 190, 20);

                JCheckBox delayBox = new JCheckBox("Delay");
                delayBox.setBounds(115, 95, 85, 20);
                delayBox.setSelected(false);
                delayBox.setFont(monospace);
                delayBox.setFocusable(false);

                JLabel timesToSendLabel = new JLabel("Times to send:");
                timesToSendLabel.setFocusable(false);
                timesToSendLabel.setFont(monospace);
                timesToSendLabel.setBounds(25, 130, 100, 20);

                JTextField timesToSendField = new JTextField("1");
                timesToSendField.setFont(monospace);
                timesToSendField.setBounds(125, 130, 100, 20);

                JButton sendButton = new JButton("Send");
                sendButton.setFocusable(false);
                sendButton.setBounds(25, 95, 75, 20);
                sendButton.setBorder(BorderFactory.createEtchedBorder());
                sendButton.setBackground(darkWhite);
                sendButton.setFont(monospace);
                sendButton.addActionListener((event0) -> {
                    if (
                            MainClient.isInteger(syncIdField.getText()) &&
                            MainClient.isInteger(buttonIdField.getText()) &&
                            MainClient.isInteger(timesToSendField.getText())) {
                        int syncId = Integer.parseInt(syncIdField.getText());
                        int buttonId = Integer.parseInt(buttonIdField.getText());
                        int timesToSend = Integer.parseInt(timesToSendField.getText());

                        ButtonClickC2SPacket packet = new ButtonClickC2SPacket(syncId, buttonId);
                        try {
                            Runnable toRun = getFabricatePacketRunnable(mc, delayBox.isSelected(), packet);
                            for (int i = 0; i < timesToSend; i++) {
                                toRun.run();
                            }
                        } catch (Exception e) {
                            statusLabel.setVisible(true);
                            statusLabel.setForeground(Color.RED.darker());
                            statusLabel.setText("You must be connected to a server!");
                            MainClient.queueTask(() -> {
                                statusLabel.setVisible(false);
                                statusLabel.setText("");
                            }, 1500L);
                            return;
                        }
                        statusLabel.setVisible(true);
                        statusLabel.setForeground(Color.GREEN.darker());
                        statusLabel.setText("Sent successfully!");
                        MainClient.queueTask(() -> {
                            statusLabel.setVisible(false);
                            statusLabel.setText("");
                        }, 1500L);
                    } else {
                        statusLabel.setVisible(true);
                        statusLabel.setForeground(Color.RED.darker());
                        statusLabel.setText("Invalid arguments!");
                        MainClient.queueTask(() -> {
                            statusLabel.setVisible(false);
                            statusLabel.setText("");
                        }, 1500L);
                    }
                });

                buttonClickFrame.add(syncIdLabel);
                buttonClickFrame.add(buttonIdLabel);
                buttonClickFrame.add(syncIdField);
                buttonClickFrame.add(timesToSendLabel);
                buttonClickFrame.add(buttonIdField);
                buttonClickFrame.add(sendButton);
                buttonClickFrame.add(statusLabel);
                buttonClickFrame.add(delayBox);
                buttonClickFrame.add(timesToSendField);
                buttonClickFrame.setVisible(true);
            });

            frame.add(clickSlotButton);
            frame.add(buttonClickButton);
            frame.setVisible(true);
        }).width(115).build();
        fabricatePacketButton.active = !isMac();
        buttonWidgets.add(fabricatePacketButton);

        ButtonWidget copyTitleButton = ButtonWidget.builder(Text.of("Copy GUI Title JSON"), (button) -> {
            try {
                if (mc.currentScreen == null) {
                    throw new IllegalStateException("The current minecraft screen (mc.currentScreen) is null");
                }
                mc.keyboard.setClipboard(new Gson().toJson(TextCodecs.CODEC.encodeStart(JsonOps.INSTANCE, mc.currentScreen.getTitle()).getOrThrow()));
            } catch (IllegalStateException e) {
                LOGGER.error("Error while copying title JSON to clipboard", e);
            }
        }).width(115).build();
        buttonWidgets.add(copyTitleButton);

        int buttonCount = buttonWidgets.size();
        int totalHeight = buttonCount == 0 ? 0 : buttonCount * UI_TOOLBOX_BUTTON_HEIGHT + Math.max(0, buttonCount - 1) * UI_TOOLBOX_VERTICAL_SPACING;
        int windowHeight = mc.getWindow().getScaledHeight();
        int columnStartY = Math.max(1, (windowHeight - totalHeight) / 2);
        int y = columnStartY;
        for (ButtonWidget widget : buttonWidgets) {
            widget.setPosition(UI_TOOLBOX_COLUMN_X, y);
            screen.addDrawableChild(widget);
            y += UI_TOOLBOX_BUTTON_HEIGHT + UI_TOOLBOX_VERTICAL_SPACING;
        }
        return columnStartY + totalHeight;
    }

    @NotNull
    private static JButton getPacketOptionButton(String label) {
        JButton button = new JButton(label);
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEtchedBorder());
        button.setBackground(darkWhite);
        button.setFont(monospace);
        return button;
    }

    @NotNull
    private static Runnable getFabricatePacketRunnable(MinecraftClient mc, boolean delay, Packet<?> packet) {
        Runnable toRun;
        if (delay) {
            toRun = () -> {
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(packet);
                } else {
                    LOGGER.warn("Minecraft network handler (mc.getNetworkHandler()) is null while sending fabricated packets.");
                }
            };
        } else {
            toRun = () -> {
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(packet);
                } else {
                    LOGGER.warn("Minecraft network handler (mc.getNetworkHandler()) is null while sending fabricated packets.");
                }
                ((ClientConnectionAccessor) mc.getNetworkHandler().getConnection()).getChannel().writeAndFlush(packet);
            };
        }
        return toRun;
    }

    public static boolean isInteger(String string) {
        try {
            Integer.parseInt(string);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static SlotActionType stringToSlotActionType(String string) {
        // converts a string to SlotActionType
        return switch (string) {
            case "PICKUP" -> SlotActionType.PICKUP;
            case "QUICK_MOVE" -> SlotActionType.QUICK_MOVE;
            case "SWAP" -> SlotActionType.SWAP;
            case "CLONE" -> SlotActionType.CLONE;
            case "THROW" -> SlotActionType.THROW;
            case "QUICK_CRAFT" -> SlotActionType.QUICK_CRAFT;
            case "PICKUP_ALL" -> SlotActionType.PICKUP_ALL;
            default -> null;
        };
    }

    public static void queueTask(Runnable runnable, long delayMs) {
        // queues a task for minecraft to run
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                MinecraftClient.getInstance().send(runnable);
            }
        };
        timer.schedule(task, delayMs);
    }

    public static String getModVersion(String modId) {
        ModMetadata modMetadata = FabricLoader.getInstance().getModContainer(modId).isPresent() ? FabricLoader.getInstance().getModContainer(modId).get().getMetadata() : null;

        return modMetadata != null ? modMetadata.getVersion().getFriendlyString() : "null";
    }
}