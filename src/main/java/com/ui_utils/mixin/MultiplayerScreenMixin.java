package com.ui_utils.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ui_utils.MainClient;
import com.ui_utils.SharedVariables;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen {
    private MultiplayerScreenMixin() {
        super(null);
    }

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        if (SharedVariables.enabled) {
            // Create "Bypass Resource Pack" option
            int columnX = MainClient.UI_TOOLBOX_COLUMN_X;
            int bottomY = this.height - MainClient.UI_TOOLBOX_BUTTON_HEIGHT - MainClient.UI_CHAT_FIELD_SPACING;
            int spacing = MainClient.UI_TOOLBOX_VERTICAL_SPACING;
            int yForceDeny = bottomY;
            int yBypass = bottomY - MainClient.UI_TOOLBOX_BUTTON_HEIGHT - spacing;

            this.addDrawableChild(ButtonWidget.builder(Text.of("Bypass Resource Pack: " + (SharedVariables.bypassResourcePack ? "ON" : "OFF")), (button) -> {
                SharedVariables.bypassResourcePack = !SharedVariables.bypassResourcePack;
                button.setMessage(Text.of("Bypass Resource Pack: " + (SharedVariables.bypassResourcePack ? "ON" : "OFF")));
            }).width(160).position(columnX, yBypass).build());

            // Create "Force Deny" option
            this.addDrawableChild(ButtonWidget.builder(Text.of("Force Deny: " + (SharedVariables.resourcePackForceDeny ? "ON" : "OFF")), (button) -> {
                SharedVariables.resourcePackForceDeny = !SharedVariables.resourcePackForceDeny;
                button.setMessage(Text.of("Force Deny: " + (SharedVariables.resourcePackForceDeny ? "ON" : "OFF")));
            }).width(160).position(columnX, yForceDeny).build());
        }
    }
}