package com.ui_utils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.LecternScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ui_utils.MainClient;
import com.ui_utils.SharedVariables;
import com.ui_utils.mixin.accessor.ScreenAccessor;

import java.util.regex.Pattern;

@SuppressWarnings("all")
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    public abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private TextFieldWidget addressField;
    private boolean initialized = false;

    // inject at the end of the render method (if instanceof LecternScreen)
    @Inject(at = @At("TAIL"), method = "init(II)V")
    public void init(int width, int height, CallbackInfo ci) {
        // check if the current gui is a lectern gui and if ui-utils is enabled
        if (mc.currentScreen instanceof LecternScreen screen && SharedVariables.enabled) {
            // setup widgets
            if (/*!this.initialized*/ true) { // bro why did you do this cxg :skull:
                // check if the current gui is a lectern gui and ui-utils is enabled
                // if you do not message me about this @coderx-gamer you are not reading my commits
                // why would you read them anyway tbh
                // ill clean this up later if you dont fix it

                TextRenderer textRenderer = ((ScreenAccessor) this).getTextRenderer();
                int columnBottom = MainClient.createWidgets(mc, screen);
                int windowHeight = mc.getWindow().getScaledHeight();
                int maxChatY = Math.max(1, windowHeight - MainClient.UI_CHAT_FIELD_HEIGHT - 1);
                int chatFieldY = Math.min(columnBottom + MainClient.UI_CHAT_FIELD_SPACING, maxChatY);

                // create chat box
                this.addressField = new TextFieldWidget(textRenderer, MainClient.UI_TOOLBOX_COLUMN_X, chatFieldY, MainClient.UI_CHAT_FIELD_WIDTH, MainClient.UI_CHAT_FIELD_HEIGHT, Text.of("Chat ...")) {
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
                this.addressField.setText("");
                this.addressField.setMaxLength(MainClient.UI_CHAT_FIELD_MAX_LENGTH);

                this.addDrawableChild(this.addressField);
                this.initialized = true;
            }
        }
    }

}