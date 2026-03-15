package com.ui_utils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.ui_utils.MainClient;
import com.ui_utils.SharedVariables;

import java.util.regex.Pattern;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    private HandledScreenMixin() {
        super(null);
    }

    @Shadow
    protected abstract boolean handleHotbarKeyPressed(KeyInput keyInput);
    @Shadow
    protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);
    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Unique
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Unique
    private TextFieldWidget addressField;

    // called when creating a HandledScreen
    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        if (SharedVariables.enabled) {
            int columnBottom = MainClient.createWidgets(mc, this);
            int maxChatY = Math.max(1, this.height - MainClient.UI_CHAT_FIELD_HEIGHT - 1);
            int chatFieldY = Math.min(columnBottom + MainClient.UI_CHAT_FIELD_SPACING, maxChatY);

            // create chat box
            this.addressField = new TextFieldWidget(this.textRenderer, MainClient.UI_TOOLBOX_COLUMN_X, chatFieldY, MainClient.UI_CHAT_FIELD_WIDTH, MainClient.UI_CHAT_FIELD_HEIGHT, Text.of("Chat ...")) {
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
        }
    }

    @Inject(at = @At("HEAD"), method = "keyPressed(Lnet/minecraft/client/input/KeyInput;)Z", cancellable = true)
    public void keyPressed(KeyInput keyInput, CallbackInfoReturnable<Boolean> cir) {
        if (this.addressField != null && this.addressField.isSelected() && this.addressField.keyPressed(keyInput)) {
            cir.cancel();
            cir.setReturnValue(true);
            return;
        }
        cir.cancel();
        if (super.keyPressed(keyInput)) {
            cir.setReturnValue(true);
        } else if (MainClient.mc.options.inventoryKey.matchesKey(keyInput) && (this.addressField == null || !this.addressField.isSelected())) {
            // Crashes if address field does not exist (because of ui utils disabled, this is a temporary fix.)
            this.close();
            cir.setReturnValue(true);
        } else {
            this.handleHotbarKeyPressed(keyInput);
            if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
                if (mc.options.pickItemKey.matchesKey(keyInput)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.id, 0, SlotActionType.CLONE);
                } else if (mc.options.dropKey.matchesKey(keyInput)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.id, isControlDown(keyInput) ? 1 : 0, SlotActionType.THROW);
                }
            }

            cir.setReturnValue(true);
        }
    }

    private static boolean isControlDown(KeyInput keyInput) {
        return (keyInput.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
    }

}