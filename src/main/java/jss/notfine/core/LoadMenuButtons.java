package jss.notfine.core;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import jss.notfine.gui.GuiCustomMenuButton;
import jss.notfine.gui.MenuButtonLists;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;

import java.util.List;

public class LoadMenuButtons {

    public static final LoadMenuButtons INSTANCE = new LoadMenuButtons();

    //@SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGui(InitGuiEvent.Post event) {
        if(event.gui instanceof GuiOptions) {
            GuiButton videoSettings = ((List<GuiButton>)event.buttonList).stream().filter(button -> button.id == 101).findFirst().get();
            //Hide vanilla video settings button
            videoSettings.visible = false;
            //Add new video settings button
            event.buttonList.add(
                new GuiCustomMenuButton(
                    videoSettings.xPosition, videoSettings.yPosition,
                    videoSettings.width, videoSettings.height,
                    MenuButtonLists.VIDEO
                )
            );
        }
    }

}
