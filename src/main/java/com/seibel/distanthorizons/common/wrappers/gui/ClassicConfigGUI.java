package com.seibel.distanthorizons.common.wrappers.gui;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// Logger (for debug stuff)

import com.mojang.realmsclient.gui.ChatFormatting;
import com.seibel.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.config.types.*;

// Uses https://github.com/TheElectronWill/night-config for toml (only for Fabric since Forge already includes this)

// Gets info from our own mod

// Minecraft imports

import com.seibel.distanthorizons.core.jar.updater.SelfUpdater;
import com.seibel.distanthorizons.core.util.AnnotationUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.IConfigGui;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.*;


/**
 * Based upon TinyConfig but is highly modified
 * https://github.com/Minenash/TinyConfig
 *
 * Credits to Motschen
 *
 * @author coolGi
 * @version 5-21-2022
 */
// FLOATS DONT WORK WITH THIS

/** This file is going to be removed sometime soon, please dont hook onto anything within this file until the new UI is compleated */
@SuppressWarnings("unchecked")
public class ClassicConfigGUI
{
	/*
	    This would be removed later on as it is going to be re-written in java swing
	 */

	private static final Logger LOGGER = LogManager.getLogger();

	public static final ConfigCoreInterface CONFIG_CORE_INTERFACE = new ConfigCoreInterface();



	//==============//
	// Initializers //
	//==============//

	// Some regexes to check if an input is valid
	private static final Pattern INTEGER_ONLY_REGEX = Pattern.compile("(-?[0-9]*)");
	private static final Pattern DECIMAL_ONLY_REGEX = Pattern.compile("-?([\\d]+\\.?[\\d]*|[\\d]*\\.?[\\d]+|\\.)");

	private static class ConfigScreenConfigs
	{
		// This contains all the configs for the configs
		public static final int SpaceFromRightScreen = 10;
		public static final int ButtonWidthSpacing = 5;
		public static final int ResetButtonWidth = 40;
		public static final int ResetButtonHeight = 20;

	}

	/**
	 * The terribly coded old stuff
	 */
	public static class EntryInfo
	{
		Object widget;
		Map.Entry<GuiTextField, String> error;
		String tempValue;
		int index;

	}

	/**
	 * creates a text field
	 */
	private static void textField(AbstractConfigType info, Function<String, Number> func, Pattern pattern, boolean cast)
	{
		((EntryInfo) info.guiValue).widget = (BiFunction<GuiTextField, GuiButton, Predicate<String>>) (editBox, button) -> stringValue ->
		{
			boolean isNumber = (pattern != null);

			stringValue = stringValue.trim();
			if (!(stringValue.isEmpty() || !isNumber || pattern.matcher(stringValue).matches()))
			{
				return false;
			}


			Number value = info.typeIsFloatingPointNumber() ? 0.0 : 0; // different default values are needed so implicit casting works correctly (if not done casting from 0 (an int) to a double will cause an exception)
			((EntryInfo) info.guiValue).error = null;
			if (isNumber && !stringValue.isEmpty() && !stringValue.equals("-") && !stringValue.equals("."))
			{
				try
				{
					value = func.apply(stringValue);
				}
				catch (Exception e)
				{
					value = null;
				}

				byte isValid = ((ConfigEntry) info).isValid(value);
				switch (isValid)
				{
					case 0:
						((EntryInfo) info.guiValue).error = null;
						break;
					case -1:
						((EntryInfo) info.guiValue).error = new AbstractMap.SimpleEntry<>(editBox, TextOrTranslatable("§cMinimum length is " + ((ConfigEntry) info).getMin()));
						break;
					case 1:
						((EntryInfo) info.guiValue).error = new AbstractMap.SimpleEntry<>(editBox, TextOrTranslatable("§cMaximum length is " + ((ConfigEntry) info).getMax()));
						break;
					case 2:
						((EntryInfo) info.guiValue).error = new AbstractMap.SimpleEntry<>(editBox, TextOrTranslatable("§cValue is invalid"));
						break;
				}
			}

			((EntryInfo) info.guiValue).tempValue = stringValue;
			editBox.setTextColor(((ConfigEntry) info).isValid(value) == 0 ? 0xFFFFFFFF : 0xFFFF7777); // white and red
//            button.active = entries.stream().allMatch(e -> e.inLimits);


			if (info.getType() == String.class
				|| info.getType() == List.class)
			{
				((ConfigEntry) info).uiSetWithoutSaving(stringValue);
			}
			else if (((ConfigEntry) info).isValid(value) == 0)
			{
				if (!cast)
				{
					((ConfigEntry) info).uiSetWithoutSaving(value);
				}
				else
				{
					((ConfigEntry) info).uiSetWithoutSaving(value.intValue());
				}
			}

			return true;
		};
	}

	//==============//
	// GUI handling //
	//==============//

	/**
	 * if you want to get this config gui's screen call this
	 */
	public static GuiScreen getScreen(ConfigBase configBase, GuiScreen parent, String category)
	{
		return new ConfigScreen(configBase, parent, category);
	}

	/**
	 * Pain
	 */
	private static class ConfigScreen extends DhScreen
	{
		protected ConfigScreen(ConfigBase configBase, GuiScreen parent, String category)
		{
			super(Translatable(
					StatCollector.canTranslate(configBase.modID + ".config" + (category.isEmpty() ? "." + category : "") + ".title") ?
							configBase.modID + ".config.title" :
							configBase.modID + ".config" + (category.isEmpty() ? "" : "." + category) + ".title")
			);
			this.configBase = configBase;
			this.parent = parent;
			this.category = category;
			this.translationPrefix = configBase.modID + ".config.";
		}
		private final ConfigBase configBase;

		private final String translationPrefix;
		private final GuiScreen parent;
		private final String category;
		private ConfigListWidget list;
		private boolean reload = false;

		private GuiButton doneButton;

		// Real Time config update //
		@Override
		public void updateScreen()
		{
			super.updateScreen();
			for (GuiTextField field : textFieldList.keySet())
			{
				field.updateCursorCounter();
			}
		}

        int nextId = 0;

        private Map<Integer, OnPressed> buttonMap = new HashMap<>();
		private Map<GuiTextField, Predicate<String>> textFieldList = new HashMap<>();

        private GuiButton MakeBtn(String name, int posX, int posZ, int width, int height, OnPressed action)
        {
            nextId++;
            buttonMap.put(nextId, action);
			if (StatCollector.canTranslate(name))
			{
				name = StatCollector.translateToLocal(name);
			}
            return addBtn(new GuiButton(nextId, posX, posZ, width, height, name));
        }

        @Override
        protected void actionPerformed(GuiButton button)
        {
            super.actionPerformed(button);
            if (buttonMap.containsKey(button.id))
            {
                buttonMap.get(button.id).pressed(button);
            }
        }

		@Override
		protected void keyTyped(char typedChar, int keyCode)
		{
			super.keyTyped(typedChar, keyCode);

			for (Map.Entry<GuiTextField, Predicate<String>> entry : textFieldList.entrySet())
			{
				GuiTextField field = entry.getKey();
				if (field.isFocused())
				{
					field.textboxKeyTyped(typedChar, keyCode);
					entry.getValue().test(field.getText());
				}
			}
		}

		@Override
		protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
			super.mouseClicked(mouseX, mouseY, mouseButton);
			for (GuiTextField field : textFieldList.keySet())
			{
				field.mouseClicked(mouseX, mouseY, mouseButton);
			}
		}

		/**
		 * When you close it, it goes to the previous screen and saves
		 */
		@Override
		public void onGuiClosed()
		{
			ConfigBase.INSTANCE.configFileINSTANCE.saveToFile();
			//Minecraft.getMinecraft().displayGuiScreen(this.parent);

			CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach((listener) -> listener.run());
		}

		@Override
		public void initGui()
		{
			super.initGui();
			if (!reload)
			{
				ConfigBase.INSTANCE.configFileINSTANCE.loadFromFile();
			}

            /*
			// Changelog button
			if (Config.Client.Advanced.AutoUpdater.enableAutoUpdater.get() && !ModInfo.IS_DEV_BUILD) // we only have changelogs for stable builds
			{
				this.addBtn(new TexturedButtonWidget(
						// Where the button is on the screen
						this.width - 28, this.height - 28,
						// Width and height of the button
						20, 20,
						// texture UV Offset
						0, 0,
						// Some textuary stuff
						0,
						new ResourceLocation(ModInfo.ID, "textures/gui/changelog.png"),
						20, 20,
						// Create the button and tell it where to go
						(buttonWidget) -> {
							ChangelogScreen changelogScreen = new ChangelogScreen(this);
							if (changelogScreen.usable)
								Objects.requireNonNull(minecraft).setScreen(changelogScreen);
							else
								LOGGER.warn("Changelog was not able to open");
						},
						// Add a title to the button
						Translatable(ModInfo.ID + ".updater.title")
				));
			}*/


			addBtn(MakeBtn(Translatable("distanthorizons.general.cancel"),
					this.width / 2 - 154, this.height - 28,
					150, 20,
					button ->
					{
						ConfigBase.INSTANCE.configFileINSTANCE.loadFromFile();
						Minecraft.getMinecraft().displayGuiScreen(parent);
					}));
			doneButton = addBtn(MakeBtn(Translatable("distanthorizons.general.done"), this.width / 2 + 4, this.height - 28, 150, 20, (button) -> {
				ConfigBase.INSTANCE.configFileINSTANCE.saveToFile();
                Minecraft.getMinecraft().displayGuiScreen(parent);
			}));

			this.list = new ConfigListWidget(this.width * 2, this.height, 32, 32, 25);

			/*#if MC_VER < MC_1_20_6 // no background is rendered in MC 1.20.6+
			if (this.minecraft != null && this.minecraft.level != null)
				this.list.setRenderBackground(false);
			#endif

			this.addWidget(this.list);*/

			for (AbstractConfigType info : ConfigBase.INSTANCE.entries)
			{
				try
				{
					if (info.getCategory().matches(category) && info.getAppearance().showInGui)
						addMenuItem(info);
				}
				catch (Exception e)
				{
					String message = "ERROR: Failed to show [\" + info.getNameWCategory() + \"], error: ["+e.getMessage()+"]";
					if (info.get() != null)
					{
						message += " with the value [" + info.get() + "] with type [" + info.getType() + "]";
					}

					LOGGER.error(message, e);
				}
			}



			CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach((listener) -> listener.run());

		}

		private void addMenuItem(AbstractConfigType info)
		{
			initEntry(info, this.translationPrefix);
			String name = Translatable(translationPrefix + info.getNameWCategory());


			if (ConfigEntry.class.isAssignableFrom(info.getClass()))
			{
				OnPressed btnAction = button -> {
					((ConfigEntry) info).uiSetWithoutSaving(((ConfigEntry) info).getDefaultValue());
					((EntryInfo) info.guiValue).index = 0;
					this.reload = true;
                    Minecraft.getMinecraft().displayGuiScreen(this);
				};
				int posX = this.width - ConfigScreenConfigs.SpaceFromRightScreen - 150 - ConfigScreenConfigs.ButtonWidthSpacing - ConfigScreenConfigs.ResetButtonWidth;
				int posZ = 0;

				GuiButton resetButton = MakeBtn(ChatFormatting.RED + Translatable("distanthorizons.general.reset"),
						posX, posZ, ConfigScreenConfigs.ResetButtonWidth, ConfigScreenConfigs.ResetButtonHeight,
						btnAction);

				if (((EntryInfo) info.guiValue).widget instanceof Map.Entry)
				{
					Map.Entry<OnPressed, Function<Object, String>> widget = (Map.Entry<OnPressed, Function<Object, String>>) ((EntryInfo) info.guiValue).widget;
					if (info.getType().isEnum())
					{
						widget.setValue(value -> Translatable(translationPrefix + "enum." + info.getType().getSimpleName() + "." + info.get().toString()));
					}
					this.list.addButton(MakeBtn(widget.getValue().apply(info.get()), this.width - 150 - ConfigScreenConfigs.SpaceFromRightScreen, 0, 150, 20, widget.getKey()), resetButton, null, name);
					return;
				}
				else if (((EntryInfo) info.guiValue).widget != null)
				{
					GuiTextField widget = new GuiTextField(fontRendererObj, this.width - 150 - ConfigScreenConfigs.SpaceFromRightScreen + 2, 0, 150 - 4, 20);
					widget.setMaxStringLength(150);
					widget.setText(String.valueOf(info.get()));
					Predicate<String> processor = ((BiFunction<GuiTextField, GuiButton, Predicate<String>>) ((EntryInfo) info.guiValue).widget).apply(widget, doneButton);
					textFieldList.put(widget, processor);
					this.list.addButton(widget, resetButton, null, name);
					return;
				}
			}
			if (ConfigCategory.class.isAssignableFrom(info.getClass()))
			{
				GuiButton widget = MakeBtn(name, this.width / 2 - 100, this.height - 28, 100 * 2, 20, (button -> {
					ConfigBase.INSTANCE.configFileINSTANCE.saveToFile();
					Minecraft.getMinecraft().displayGuiScreen(ClassicConfigGUI.getScreen(this.configBase, this, ((ConfigCategory) info).getDestination()));
				}));
				this.list.addButton(widget, null, null, null);
				return;
			}
			if (ConfigUIButton.class.isAssignableFrom(info.getClass()))
			{
				GuiButton widget = MakeBtn(name, this.width / 2 - 100, this.height - 28, 100 * 2, 20, (button -> {
					((ConfigUIButton) info).runAction();
				}));
				this.list.addButton(widget, null, null, null);
				return;
			}
			if (ConfigUIComment.class.isAssignableFrom(info.getClass()))
			{
				this.list.addButton(null, null, null, name);
				return;
			}
			if (ConfigUiLinkedEntry.class.isAssignableFrom(info.getClass()))
			{
				this.addMenuItem(((ConfigUiLinkedEntry) info).get());
				return;
			}

			LOGGER.warn("Config [" + info.getNameWCategory() + "] failed to show. Please try something like changing its type.");
		}

		@Override
		public void drawScreen(int mouseX, int mouseY, float delta)
		{
			this.drawDefaultBackground();
			this.list.render(mouseX, mouseY, delta); // Render buttons
			super.drawScreen(mouseX, mouseY, delta);

			DhDrawCenteredString(title, width / 2, 15, 0xFFFFFF); // Render title

			if (this.configBase.modID.equals("distanthorizons"))
			{
				// Display version
				DhDrawString(TextOrLiteral(ModInfo.VERSION), 2, height - 10, 0xAAAAAA);

				// If the update is pending, display this message to inform the user that it will apply when the game restarts
				if (SelfUpdater.deleteOldJarOnJvmShutdown)
					DhDrawString(Translatable(configBase.modID + ".updater.waitingForClose"), 4, height - 38, 0xFFFFFF);
			}


			// Render the tooltip only if it can find a tooltip in the language file
			for (AbstractConfigType info : ConfigBase.INSTANCE.entries)
			{
				if (info.getCategory().matches(category) && info.getAppearance().showInGui)
				{
					if (list.getHoveredButton(mouseX, mouseY).isPresent())
					{
						Gui buttonWidget = list.getHoveredButton(mouseX, mouseY).get();
						String text = ButtonEntry.buttonsWithText.get(buttonWidget);
						if (text == null)
						{
							continue;
						}

						// A quick fix for tooltips on linked entries
						AbstractConfigType newInfo = ConfigUiLinkedEntry.class.isAssignableFrom(info.getClass()) ?
								((ConfigUiLinkedEntry) info).get() :
								info;

						String name = Translatable(this.translationPrefix + (info.category.isEmpty() ? "" : info.category + ".") + info.getName());
						String key = translationPrefix + (newInfo.category.isEmpty() ? "" : newInfo.category + ".") + newInfo.getName() + ".@tooltip";

						if (((EntryInfo) newInfo.guiValue).error != null && text.equals(name))
						{
							DhRenderTooltip(((EntryInfo) newInfo.guiValue).error.getValue(), mouseX, mouseY);
						}
						else if (StatCollector.canTranslate(key) && (text != null && text.equals(name)))
						{
							List<String> list = new ArrayList<>();
							for (String str : StatCollector.translateToLocal(key).split("\n"))
							{
								list.add(TextOrTranslatable(str));
							}
							DhRenderTooltip(list, mouseX, mouseY);
						}
					}
				}
			}
		}

	}





	private static void initEntry(AbstractConfigType configType, String translationPrefix)
	{
		configType.guiValue = new EntryInfo();
		Class<?> fieldClass = configType.getType();

		if (ConfigEntry.class.isAssignableFrom(configType.getClass()))
		{
			if (fieldClass == Integer.class)
			{
				// For int
				textField(configType, Integer::parseInt, INTEGER_ONLY_REGEX, true);
			}
			else if (fieldClass == Double.class)
			{
				// For double
				textField(configType, Double::parseDouble, DECIMAL_ONLY_REGEX, false);
			}
			else if (fieldClass == String.class || fieldClass == List.class)
			{
				// For string or list
				textField(configType, String::length, null, true);
			}
			else if (fieldClass == Boolean.class)
			{
				// For boolean
				Function<Object, String> func = value -> ((Boolean) value ? ChatFormatting.GREEN : ChatFormatting.RED) + Translatable("distanthorizons.general."+((Boolean) value ? "true" : "false"));

				((EntryInfo) configType.guiValue).widget = new AbstractMap.SimpleEntry<OnPressed, Function<Object, String>>(button -> {
					((ConfigEntry) configType).uiSetWithoutSaving(!(Boolean) configType.get());
					button.displayString = func.apply(configType.get());
				}, func);
			}
			else if (fieldClass.isEnum())
			{
				// For enum
				List<?> values = Arrays.asList(configType.getType().getEnumConstants());
				Function<Object, String> func = value -> Translatable(translationPrefix + "enum." + fieldClass.getSimpleName() + "." + configType.get().toString());
				((EntryInfo) configType.guiValue).widget = new AbstractMap.SimpleEntry<OnPressed, Function<Object, String>>(button -> {

					// get the currently selected enum and enum index
					int startingIndex = values.indexOf(configType.get());
					Enum<?> enumValue = (Enum<?>) values.get(startingIndex);

					// search for the next enum that is selectable
					int index = startingIndex + 1;
					index = (index >= values.size()) ? 0 : index;
					while (index != startingIndex)
					{
						enumValue = (Enum<?>) values.get(index);
						if (!AnnotationUtil.doesEnumHaveAnnotation(enumValue, DisallowSelectingViaConfigGui.class))
						{
							// this enum shouldn't be selectable via the UI,
							// skip it
							break;
						}

						index++;
						index = (index >= values.size()) ? 0 : index;
					}

					if (index == startingIndex)
					{
						// none of the enums should be selectable, this is a programmer error
						enumValue = (Enum<?>) values.get(startingIndex);
						LOGGER.warn("Enum [" + enumValue.getClass() + "] doesn't contain any values that should be selectable via the UI, sticking to the currently selected value [" + enumValue + "].");
					}


					((ConfigEntry<Enum<?>>) configType).uiSetWithoutSaving(enumValue);
					button.displayString = func.apply(configType.get());
				}, func);
			}
		}
		else if (ConfigCategory.class.isAssignableFrom(configType.getClass()))
		{
//            if (!info.info.getName().equals(""))
//                info.name = new TranslatableComponent(info.info.getName());
		}
//        return info;
	}

	public static class ConfigListWidget
	{
        private List<ButtonEntry> children = new ArrayList<>();
		public ConfigListWidget(int canvasWidth, int canvasHeight, int topMargin, int botMargin, int itemSpacing)
		{

		}

		public void addButton(Gui button, GuiButton resetButton, GuiButton indexButton, String text)
		{
			this.children.add(ButtonEntry.create(button, text, resetButton, indexButton));
		}

		public Optional<Gui> getHoveredButton(double mouseX, double mouseY)
		{
			for (ButtonEntry buttonEntry : this.children)
			{
                if (buttonEntry.button instanceof GuiButton guiButton)
                {
                    int hoverState = guiButton.getHoverState(guiButton.field_146123_n);
                    if (hoverState == 2)
                    {
                        return Optional.of(buttonEntry.button);
                    }
                }
			}
			return Optional.empty();
		}

        public void render(int mouseX, int mouseY, float delta) {

			int y = 40;
            for (ButtonEntry buttonEntry : this.children)
            {
                buttonEntry.render(y, 0, mouseX, mouseY);
				y += 25;
            }
        }
    }


	public static class ButtonEntry
	{
		public final Gui button;
		private final GuiButton resetButton;
		private final GuiButton indexButton;
		private final String text;
		private final List<Gui> children = new ArrayList<>();
		public static final Map<Gui, String> buttonsWithText = new HashMap<>();

		private ButtonEntry(Gui button, String text, GuiButton resetButton, GuiButton indexButton)
		{
			buttonsWithText.put(button, text);
			this.button = button;
			this.resetButton = resetButton;
			this.text = text;
			this.indexButton = indexButton;
			if (button != null)
				children.add(button);
			if (resetButton != null)
				children.add(resetButton);
			if (indexButton != null)
				children.add(indexButton);
		}

		public static ButtonEntry create(Gui button, String text, GuiButton resetButton, GuiButton indexButton)
		{
			return new ButtonEntry(button, text, resetButton, indexButton);
		}

		public void render(int y, int x, int mouseX, int mouseY)
		{
			if (button != null)
			{
                if (button instanceof GuiButton guiButton)
                {
					guiButton.yPosition = y;
                    //guiButton.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
                }
                if (button instanceof GuiTextField guiTextField)
                {
					guiTextField.yPosition = y;
                    guiTextField.drawTextBox();
                }
			}
			if (resetButton != null)
			{
				resetButton.yPosition = y;
				//resetButton.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
			}
			if (indexButton != null)
			{
				indexButton.yPosition = y;
				//indexButton.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
			}
			if (text != null && (!text.contains("spacer") || button != null))
                button.drawString(Minecraft.getMinecraft().fontRenderer, text, 12, y + 5, 0xFFFFFF);
		}
	}





	//================//
	// event handling //
	//================//

	private static class ConfigCoreInterface implements IConfigGui
	{
		/**
		 * in the future it would be good to pass in the current page and other variables,
		 * but for now just knowing when the page is closed is good enough
		 */
		public final ArrayList<Runnable> onScreenChangeListenerList = new ArrayList<>();



		@Override
		public void addOnScreenChangeListener(Runnable newListener) { this.onScreenChangeListenerList.add(newListener); }
		@Override
		public void removeOnScreenChangeListener(Runnable oldListener) { this.onScreenChangeListenerList.remove(oldListener); }

	}

}
