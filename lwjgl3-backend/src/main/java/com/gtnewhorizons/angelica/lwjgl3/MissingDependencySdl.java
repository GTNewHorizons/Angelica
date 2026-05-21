package com.gtnewhorizons.angelica.lwjgl3;

import static org.lwjgl.sdl.SDLMessageBox.SDL_MESSAGEBOX_BUTTON_ESCAPEKEY_DEFAULT;
import static org.lwjgl.sdl.SDLMessageBox.SDL_MESSAGEBOX_BUTTON_RETURNKEY_DEFAULT;
import static org.lwjgl.sdl.SDLMessageBox.SDL_MESSAGEBOX_ERROR;
import static org.lwjgl.sdl.SDLMessageBox.SDL_ShowMessageBox;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import me.eigenraven.lwjgl3ify.client.MainThreadExec;
import org.lwjgl.sdl.SDL_MessageBoxButtonData;
import org.lwjgl.sdl.SDL_MessageBoxData;
import org.lwjgl.system.MemoryStack;

@Lwjgl3Aware
public final class MissingDependencySdl {

    private MissingDependencySdl() {}

    public static void showFatal(String title, String message) {
        MainThreadExec.runOnMainThread(() -> showFatalOnMainThread(title, message));
    }

    private static void showFatalOnMainThread(String title, String message) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_MessageBoxData box = SDL_MessageBoxData.calloc(stack);
            box.flags(SDL_MESSAGEBOX_ERROR);
            box.title(stack.UTF8(title));
            box.message(stack.UTF8(message));

            final SDL_MessageBoxButtonData.Buffer buttons = SDL_MessageBoxButtonData.calloc(1, stack);
            buttons.get(0).set(
                SDL_MESSAGEBOX_BUTTON_RETURNKEY_DEFAULT | SDL_MESSAGEBOX_BUTTON_ESCAPEKEY_DEFAULT,
                0,
                stack.UTF8("OK"));
            box.buttons(buttons);

            SDL_ShowMessageBox(box, null);
        }
    }
}
