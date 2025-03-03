package com.seibel.distanthorizons.common.wrappers.gui;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// Logger (for debug stuff)

import com.seibel.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.config.types.*;
import com.seibel.distanthorizons.common.wrappers.gui.updater.ChangelogScreen;

// Uses https://github.com/TheElectronWill/night-config for toml (only for Fabric since Forge already includes this)

// Gets info from our own mod

// Minecraft imports

import com.seibel.distanthorizons.core.jar.updater.SelfUpdater;
import com.seibel.distanthorizons.core.util.AnnotationUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.IConfigGui;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
#if MC_VER < MC_1_20_1
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
#else
import net.minecraft.client.gui.GuiGraphics;
#endif
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;    // translation
#if MC_VER >= MC_1_17_1
import net.minecraft.client.gui.narration.NarratableEntry;
#endif
import net.minecraft.resources.ResourceLocation;
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
		Map.Entry<EditBox, Component> error;
		String tempValue;
		int index;
		
	}
	
	/**
	 * creates a text field
	 */
	private static void textField(AbstractConfigType info, Function<String, Number> func, Pattern pattern, boolean cast)
	{
		((EntryInfo) info.guiValue).widget = (BiFunction<EditBox, Button, Predicate<String>>) (editBox, button) -> stringValue ->
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
	public static Screen getScreen(ConfigBase configBase, Screen parent, String category)
	{
		return new ConfigScreen(configBase, parent, category);
	}
	
	/**
	 * Pain
	 */
	private static class ConfigScreen extends DhScreen
	{
		protected ConfigScreen(ConfigBase configBase, Screen parent, String category)
		{
			super(Translatable(
					I18n.exists(configBase.modID + ".config" + (category.isEmpty() ? "." + category : "") + ".title") ?
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
		private final Screen parent;
		private final String category;
		private ConfigListWidget list;
		private boolean reload = false;
		
		private Button doneButton;
		
		// Real Time config update //
		@Override
		public void tick()
		{
			super.tick();
		}
		
		
		/**
		 * When you close it, it goes to the previous screen and saves
		 */
		@Override
		public void onClose()
		{
			ConfigBase.INSTANCE.configFileINSTANCE.saveToFile();
			Objects.requireNonNull(this.minecraft).setScreen(this.parent);
			
			CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach((listener) -> listener.run());
		}
		
		@Override
		protected void init()
		{
			super.init();
			if (!reload)
			{
				ConfigBase.INSTANCE.configFileINSTANCE.loadFromFile();
			}
			
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
						#if MC_VER < MC_1_21_1
						new ResourceLocation(ModInfo.ID, "textures/gui/changelog.png"),
						#else
						ResourceLocation.fromNamespaceAndPath(ModInfo.ID, "textures/gui/changelog.png"),
						#endif
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
			}
			
			
			addBtn(MakeBtn(Translatable("distanthorizons.general.cancel"), 
					this.width / 2 - 154, this.height - 28, 
					150, 20, 
					button -> 
					{
						ConfigBase.INSTANCE.configFileINSTANCE.loadFromFile();
						Objects.requireNonNull(minecraft).setScreen(parent);
					}));
			doneButton = addBtn(MakeBtn(Translatable("distanthorizons.general.done"), this.width / 2 + 4, this.height - 28, 150, 20, (button) -> {
				ConfigBase.INSTANCE.configFileINSTANCE.saveToFile();
				Objects.requireNonNull(minecraft).setScreen(parent);
			}));
			
			this.list = new ConfigListWidget(this.minecraft, this.width * 2, this.height, 32, 32, 25);
			
			#if MC_VER < MC_1_20_6 // no background is rendered in MC 1.20.6+
			if (this.minecraft != null && this.minecraft.level != null)
				this.list.setRenderBackground(false);
			#endif
			
			this.addWidget(this.list);
			
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
			Component name = Translatable(translationPrefix + info.getNameWCategory());
			
			
			if (ConfigEntry.class.isAssignableFrom(info.getClass()))
			{
				Button.OnPress btnAction = button -> {
					((ConfigEntry) info).uiSetWithoutSaving(((ConfigEntry) info).getDefaultValue());
					((EntryInfo) info.guiValue).index = 0;
					this.reload = true;
					Objects.requireNonNull(minecraft).setScreen(this);
				};
				int posX = this.width - ConfigScreenConfigs.SpaceFromRightScreen - 150 - ConfigScreenConfigs.ButtonWidthSpacing - ConfigScreenConfigs.ResetButtonWidth;
				int posZ = 0;
				
				Button resetButton = MakeBtn(Translatable("distanthorizons.general.reset").withStyle(ChatFormatting.RED), 
						posX, posZ, ConfigScreenConfigs.ResetButtonWidth, ConfigScreenConfigs.ResetButtonHeight, 
						btnAction);
				
				if (((EntryInfo) info.guiValue).widget instanceof Map.Entry)
				{
					Map.Entry<Button.OnPress, Function<Object, Component>> widget = (Map.Entry<Button.OnPress, Function<Object, Component>>) ((EntryInfo) info.guiValue).widget;
					if (info.getType().isEnum())
					{
						widget.setValue(value -> Translatable(translationPrefix + "enum." + info.getType().getSimpleName() + "." + info.get().toString()));
					}
					this.list.addButton(MakeBtn(widget.getValue().apply(info.get()), this.width - 150 - ConfigScreenConfigs.SpaceFromRightScreen, 0, 150, 20, widget.getKey()), resetButton, null, name);
					return;
				}
				else if (((EntryInfo) info.guiValue).widget != null)
				{
					EditBox widget = new EditBox(font, this.width - 150 - ConfigScreenConfigs.SpaceFromRightScreen + 2, 0, 150 - 4, 20, null);
					widget.setMaxLength(150);
					widget.insertText(String.valueOf(info.get()));
					Predicate<String> processor = ((BiFunction<EditBox, Button, Predicate<String>>) ((EntryInfo) info.guiValue).widget).apply(widget, doneButton);
					widget.setFilter(processor);
					this.list.addButton(widget, resetButton, null, name);
					return;
				}
			}
			if (ConfigCategory.class.isAssignableFrom(info.getClass()))
			{
				Button widget = MakeBtn(name, this.width / 2 - 100, this.height - 28, 100 * 2, 20, (button -> {
					ConfigBase.INSTANCE.configFileINSTANCE.saveToFile();
					Objects.requireNonNull(minecraft).setScreen(ClassicConfigGUI.getScreen(this.configBase, this, ((ConfigCategory) info).getDestination()));
				}));
				this.list.addButton(widget, null, null, null);
				return;
			}
			if (ConfigUIButton.class.isAssignableFrom(info.getClass()))
			{
				Button widget = MakeBtn(name, this.width / 2 - 100, this.height - 28, 100 * 2, 20, (button -> {
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
        #if MC_VER < MC_1_20_1
		public void render(PoseStack matrices, int mouseX, int mouseY, float delta)
        #else
		public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta)
		#endif
		{
			#if MC_VER < MC_1_20_2 // 1.20.2 now enables this by default in the `this.list.render` function
			this.renderBackground(matrices); // Renders background
			#else
			super.render(matrices, mouseX, mouseY, delta);
			#endif
			this.list.render(matrices, mouseX, mouseY, delta); // Render buttons
			
			DhDrawCenteredString(matrices, font, title, width / 2, 15, 0xFFFFFF); // Render title
			
			if (this.configBase.modID.equals("distanthorizons"))
			{
				// Display version
				DhDrawString(matrices, font, TextOrLiteral(ModInfo.VERSION), 2, height - 10, 0xAAAAAA);
				
				// If the update is pending, display this message to inform the user that it will apply when the game restarts
				if (SelfUpdater.deleteOldJarOnJvmShutdown)
					DhDrawString(matrices, font, Translatable(configBase.modID + ".updater.waitingForClose"), 4, height - 38, 0xFFFFFF);
			}
			
			
			// Render the tooltip only if it can find a tooltip in the language file
			for (AbstractConfigType info : ConfigBase.INSTANCE.entries)
			{
				if (info.getCategory().matches(category) && info.getAppearance().showInGui)
				{
					if (list.getHoveredButton(mouseX, mouseY).isPresent())
					{
						AbstractWidget buttonWidget = list.getHoveredButton(mouseX, mouseY).get();
						Component text = ButtonEntry.buttonsWithText.get(buttonWidget);
						if (text == null)
						{
							continue;
						}
						
						// A quick fix for tooltips on linked entries
						AbstractConfigType newInfo = ConfigUiLinkedEntry.class.isAssignableFrom(info.getClass()) ?
								((ConfigUiLinkedEntry) info).get() :
								info;
						
						Component name = Translatable(this.translationPrefix + (info.category.isEmpty() ? "" : info.category + ".") + info.getName());
						String key = translationPrefix + (newInfo.category.isEmpty() ? "" : newInfo.category + ".") + newInfo.getName() + ".@tooltip";
						
						if (((EntryInfo) newInfo.guiValue).error != null && text.equals(name))
						{
							DhRenderTooltip(matrices, font, ((EntryInfo) newInfo.guiValue).error.getValue(), mouseX, mouseY);
						}
						else if (I18n.exists(key) && (text != null && text.equals(name)))
						{
							List<Component> list = new ArrayList<>();
							for (String str : I18n.get(key).split("\n"))
							{
								list.add(TextOrTranslatable(str));
							}
							DhRenderComponentTooltip(matrices, font, list, mouseX, mouseY);
						}
					}
				}
			}
			#if MC_VER < MC_1_20_2
			super.render(matrices, mouseX, mouseY, delta);
			#endif
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
				Function<Object, Component> func = value -> Translatable("distanthorizons.general."+((Boolean) value ? "true" : "false")).withStyle((Boolean) value ? ChatFormatting.GREEN : ChatFormatting.RED);
				
				((EntryInfo) configType.guiValue).widget = new AbstractMap.SimpleEntry<Button.OnPress, Function<Object, Component>>(button -> {
					((ConfigEntry) configType).uiSetWithoutSaving(!(Boolean) configType.get());
					button.setMessage(func.apply(configType.get()));
				}, func);
			}
			else if (fieldClass.isEnum())
			{
				// For enum
				List<?> values = Arrays.asList(configType.getType().getEnumConstants());
				Function<Object, Component> func = value -> Translatable(translationPrefix + "enum." + fieldClass.getSimpleName() + "." + configType.get().toString());
				((EntryInfo) configType.guiValue).widget = new AbstractMap.SimpleEntry<Button.OnPress, Function<Object, Component>>(button -> {
					
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
					button.setMessage(func.apply(configType.get()));
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
	
	public static class ConfigListWidget extends ContainerObjectSelectionList<ButtonEntry>
	{
		Font textRenderer;
		
		public ConfigListWidget(Minecraft minecraftClient, int canvasWidth, int canvasHeight, int topMargin, int botMargin, int itemSpacing)
		{
			#if MC_VER < MC_1_20_4
			super(minecraftClient, canvasWidth, canvasHeight, topMargin, canvasHeight - botMargin, itemSpacing);
			#else
			super(minecraftClient, canvasWidth, canvasHeight - (topMargin + botMargin), topMargin, itemSpacing);
			#endif
			this.centerListVertically = false;
			textRenderer = minecraftClient.font;
		}
		
		public void addButton(AbstractWidget button, AbstractWidget resetButton, AbstractWidget indexButton, Component text)
		{
			this.addEntry(ButtonEntry.create(button, text, resetButton, indexButton));
		}
		
		@Override
		public int getRowWidth()
		{
			return 10000;
		}
		
		public Optional<AbstractWidget> getHoveredButton(double mouseX, double mouseY)
		{
			for (ButtonEntry buttonEntry : this.children())
			{
				if (buttonEntry.button != null && buttonEntry.button.isMouseOver(mouseX, mouseY))
				{
					return Optional.of(buttonEntry.button);
				}
			}
			return Optional.empty();
		}
		
	}
	
	
	public static class ButtonEntry extends ContainerObjectSelectionList.Entry<ButtonEntry>
	{
		private static final Font textRenderer = Minecraft.getInstance().font;
		public final AbstractWidget button;
		private final AbstractWidget resetButton;
		private final AbstractWidget indexButton;
		private final Component text;
		private final List<AbstractWidget> children = new ArrayList<>();
		public static final Map<AbstractWidget, Component> buttonsWithText = new HashMap<>();
		
		private ButtonEntry(AbstractWidget button, Component text, AbstractWidget resetButton, AbstractWidget indexButton)
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
		
		public static ButtonEntry create(AbstractWidget button, Component text, AbstractWidget resetButton, AbstractWidget indexButton)
		{
			return new ButtonEntry(button, text, resetButton, indexButton);
		}
		
		@Override
        #if MC_VER < MC_1_20_1
		public void render(PoseStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta)
        #else
		public void render(GuiGraphics matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta)
		#endif
		{
			if (button != null)
			{
				SetY(button, y);
				button.render(matrices, mouseX, mouseY, tickDelta);
			}
			if (resetButton != null)
			{
				SetY(resetButton, y);
				resetButton.render(matrices, mouseX, mouseY, tickDelta);
			}
			if (indexButton != null)
			{
				SetY(indexButton, y);
				indexButton.render(matrices, mouseX, mouseY, tickDelta);
			}
			if (text != null && (!text.getString().contains("spacer") || button != null))
                #if MC_VER < MC_1_20_1
				GuiComponent.drawString(matrices, textRenderer, text, 12, y + 5, 0xFFFFFF);
				#else
				matrices.drawString(textRenderer, text, 12, y + 5, 0xFFFFFF);
				#endif
		}
		
		@Override
		public List<? extends GuiEventListener> children()
		{
			return children;
		}
		
		// Only for 1.17 and over
		// Remove in 1.16 and below
		#if MC_VER >= MC_1_17_1
		@Override
		public List<? extends NarratableEntry> narratables()
		{
			return children;
		}
		#endif
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
