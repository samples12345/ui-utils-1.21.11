package com.ui_utils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ui_utils.MainClient;
import com.ui_utils.SharedVariables;

import java.util.regex.Pattern;

@Mixin(BookScreen.class)
public class BookScreenMixin extends Screen {
    protected BookScreenMixin(Text title) {
        super(title);
    }
    @Unique
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        if (SharedVariables.enabled) {
            int columnBottom = MainClient.createWidgets(mc, this);
            int maxChatY = Math.max(1, this.height - MainClient.UI_CHAT_FIELD_HEIGHT - 1);
            int chatFieldY = Math.min(columnBottom + MainClient.UI_CHAT_FIELD_SPACING, maxChatY);

            // create chat box
            TextFieldWidget addressField = new TextFieldWidget(textRenderer, MainClient.UI_TOOLBOX_COLUMN_X, chatFieldY, MainClient.UI_CHAT_FIELD_WIDTH, MainClient.UI_CHAT_FIELD_HEIGHT, Text.of("Chat ...")) {
                @Override
                public boolean keyPressed(KeyInput keyInput) {
                    if (keyInput.getKeycode() == GLFW.GLFW_KEY_ENTER) {
                        if (this.getText().equals("^toggleuiutils")) {
                            SharedVariables.enabled = !SharedVariables.enabled;
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.of("UI-Utils is now " + (SharedVariables.enabled ? "enabled" : "disabled") + "."), false);
                            }
                            return false;
                        }

                        if (mc.getNetworkHandler() != null) {
                            if (this.getText().startsWith("/")) {
                                mc.getNetworkHandler().sendChatCommand(this.getText().replaceFirst(Pattern.quote("/"), ""));
                            } else {
                                mc.getNetworkHandler().sendChatMessage(this.getText());
                            }
                        } else {
                            MainClient.LOGGER.warn("Minecraft network handler (mc.getNetworkHandler()) was null while trying to send chat message from UI Utils.");
                        }

                        this.setText("");
                    }
                        return super.keyPressed(keyInput);
                }
            };
            addressField.setText("");
            addressField.setMaxLength(MainClient.UI_CHAT_FIELD_MAX_LENGTH);

            this.addDrawableChild(addressField);
        }
    }
}