package net.coderbot.iris;

import com.google.common.base.Throwables;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.config.IrisConfig;
import net.coderbot.iris.gl.shader.StandardMacros;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.DimensionId;
import net.coderbot.iris.shaderpack.OptionalBoolean;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.coderbot.iris.shaderpack.discovery.ShaderpackDirectoryManager;
import net.coderbot.iris.shaderpack.option.OptionSet;
import net.coderbot.iris.shaderpack.option.Profile;
import net.coderbot.iris.shaderpack.option.values.MutableOptionValues;
import net.coderbot.iris.shaderpack.option.values.OptionValues;
import net.coderbot.iris.sodium.block_context.BlockContextHolder;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipError;
import java.util.zip.ZipException;

public class Iris {
    public final boolean isDevelopmentEnvironment;

    /**
     * The user-facing name of the mod. Moved into a constant to facilitate easy branding changes (for forks). You'll still need to change this separately in
     * mixin plugin classes & the language files.
     */
    public static final String MODNAME = "AngelicaShaders";

    public static final IrisLogging logger = new IrisLogging(MODNAME);

    // Cached at class load - config must be loaded before Iris. Do not change at runtime.
    public static final boolean enabled = AngelicaConfig.enableIris;

    private static Path shaderpacksDirectory;
    private static ShaderpackDirectoryManager shaderpacksDirectoryManager;

    private static ShaderPack currentPack;
    @Getter
    private static String currentPackName;
    @Getter
    private static boolean initialized;

    private static PipelineManager pipelineManager;
    @Getter
    private static IrisConfig irisConfig;
    private static FileSystem zipFileSystem;

    @Getter
    private static final Map<String, String> shaderPackOptionQueue = new HashMap<>();
    // Flag variable used when reloading
    // Used in favor of queueDefaultShaderPackOptionValues() for resetting as the
    // behavior is more concrete and therefore is more likely to repair a user's issues
    private static boolean resetShaderPackOptions = false;

    private static String IRIS_VERSION;
    @Getter
    private static boolean fallback;

    private static KeyBinding reloadKeybind;
    private static KeyBinding toggleShadersKeybind;
    private static KeyBinding shaderpackScreenKeybind;

    public static Iris INSTANCE = new Iris();

    private Iris() {
        // Guard against null blackboard in test environments
        final Object deobfEnv = Launch.blackboard != null ? Launch.blackboard.get("fml.deobfuscatedEnvironment") : null;
        isDevelopmentEnvironment = deobfEnv != null && (boolean) deobfEnv;
    }

    @SubscribeEvent
    public void onKeypress(InputEvent.KeyInputEvent event) {
        if (reloadKeybind.isPressed()) {
            final Minecraft mc = Minecraft.getMinecraft();
            try {
                reload();
                if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("Shaders Reloaded!"));

            } catch (Exception e) {
                logger.error("Error while reloading Shaders for Iris!", e);
                if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText( "Failed tgo reload shaders! Reason: " + Throwables.getRootCause(e).getMessage()));
            }
        } else if (toggleShadersKeybind.isPressed()) {
            final Minecraft mc = Minecraft.getMinecraft();
            try {
                toggleShaders(mc, !irisConfig.areShadersEnabled());
            } catch (Exception e) {
                logger.error("Error while toggling shaders!", e);

                if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText( "Failed tgo toggle shaders! Reason: " + Throwables.getRootCause(e).getMessage()));
                setShadersDisabled();
                fallback = true;
            }
        } else if (shaderpackScreenKeybind.isPressed()) {
            final Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new ShaderPackScreen(null));
        }

    }

    @SubscribeEvent
    public void keyUp(InputEvent.KeyInputEvent event) {
        final int key = Keyboard.getEventKey();
        final boolean released = !Keyboard.getEventKeyState();
        if (Minecraft.getMinecraft().gameSettings.showDebugInfo && GuiScreen.isShiftKeyDown() && GuiScreen.isCtrlKeyDown() && released) {
            if (key == Keyboard.KEY_N) {
                AngelicaMod.animationsMode.next();
            }
        }
    }

    /**
     * Called very early on in Minecraft initialization. At this point we *cannot* safely access OpenGL, but we can do some very basic setup, config loading,
     * and environment checks.
     *
     * <p>This is roughly equivalent to Fabric Loader's ClientModInitializer#onInitializeClient entrypoint, except
     * it's entirely cross platform & we get to decide its exact semantics.</p>
     *
     * <p>This is called right before options are loaded, so we can add key bindings here.</p>
     */
    public void onEarlyInitialize() {
        try {
            if (!Files.exists(getShaderpacksDirectory())) {
                Files.createDirectories(getShaderpacksDirectory());
            }
        } catch (IOException e) {
            logger.warn("Failed to create the shaderpacks directory!");
            logger.warn("", e);
        }

        irisConfig = new IrisConfig(Minecraft.getMinecraft().mcDataDir.toPath().resolve("config").resolve("shaders.properties"));

        try {
            irisConfig.initialize();
        } catch (IOException e) {
            logger.error("Failed to initialize Angelica configuration, default values will be used instead");
            logger.error("", e);
        }


        initialized = true;
    }

    /**
     * Called once RenderSystem#initRenderer has completed. This means that we can safely access OpenGL.
     */
    public static void onRenderSystemInit() {
        if (!initialized) {
            Iris.logger.warn("Iris::onRenderSystemInit was called, but Iris::onEarlyInitialize was not called."
                + " Trying to avoid a crash but this is an odd state.");
            return;
        }

        PBRTextureManager.INSTANCE.init();

        // Only load the shader pack when we can access OpenGL
        loadShaderpack();
    }

    /**
     * Called when the title screen is initialized for the first time.
     */
    public static void onLoadingComplete() {
        if (!initialized) {
            Iris.logger.warn("Iris::onLoadingComplete was called, but Iris::onEarlyInitialize was not called."
                + " Trying to avoid a crash but this is an odd state.");
            return;
        }

        // Initialize the pipeline now so that we don't increase world loading time. Just going to guess that
        // the player is in the overworld.
        // See: https://github.com/IrisShaders/Iris/issues/323
        lastDimension = DimensionId.OVERWORLD;
        Iris.getPipelineManager().preparePipeline(DimensionId.OVERWORLD);
    }

    public static void toggleShaders(Minecraft minecraft, boolean enabled) throws IOException {
        irisConfig.setShadersEnabled(enabled);
        irisConfig.save();

        reload();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText(enabled ? I18n.format("iris.shaders.toggled", currentPackName) : I18n.format("iris.shaders.disabled")));
        }
    }

    public static void loadShaderpack() {
        if (irisConfig == null) {
            if (!initialized) {
                throw new IllegalStateException("Iris::loadShaderpack was called, but Iris::onInitializeClient wasn't" + " called yet. How did this happen?");
            } else {
                throw new NullPointerException("Iris.irisConfig was null unexpectedly");
            }
        }

        if (!irisConfig.areShadersEnabled()) {
            logger.info("Shaders are disabled because enableShaders is set to false in shaders.properties");

            setShadersDisabled();

            return;
        }

        // Attempt to load an external shaderpack if it is available
        final Optional<String> externalName = irisConfig.getShaderPackName();

        if (!externalName.isPresent()) {
            logger.info("Shaders are disabled because no valid shaderpack is selected");

            setShadersDisabled();

            return;
        }

        if (!loadExternalShaderpack(externalName.get())) {
            logger.warn("Falling back to normal rendering without shaders because the shaderpack could not be loaded");
            setShadersDisabled();
            fallback = true;
        }
    }

    private static boolean loadExternalShaderpack(String name) {
        final Path shaderPackRoot;
        final Path shaderPackConfigTxt;

        try {
            shaderPackRoot = getShaderpacksDirectory().resolve(name);
            shaderPackConfigTxt = getShaderpacksDirectory().resolve(name + ".txt");
        } catch (InvalidPathException e) {
            logger.error("Failed to load the shaderpack \"{}\" because it contains invalid characters in its path", name);

            return false;
        }

        final Path shaderPackPath;

        if (shaderPackRoot.toString().endsWith(".zip")) {
            final Optional<Path> optionalPath;

            try {
                optionalPath = loadExternalZipShaderpack(shaderPackRoot);
            } catch (FileSystemNotFoundException | NoSuchFileException e) {
                logger.error("Failed to load the shaderpack \"{}\" because it does not exist in your shaderpacks folder!", name);

                return false;
            } catch (ZipException e) {
                logger.error("The shaderpack \"{}\" appears to be corrupted, please try downloading it again!", name);

                return false;
            } catch (IOException e) {
                logger.error("Failed to load the shaderpack \"{}\"!", name);
                logger.error("", e);

                return false;
            }

            if (optionalPath.isPresent()) {
                shaderPackPath = optionalPath.get();
            } else {
                logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
                return false;
            }
        } else {
            if (!Files.exists(shaderPackRoot)) {
                logger.error("Failed to load the shaderpack \"{}\" because it does not exist!", name);
                return false;
            }

            // If it's a folder-based shaderpack, just use the shaders subdirectory
            shaderPackPath = shaderPackRoot.resolve("shaders");
        }

        if (!Files.exists(shaderPackPath)) {
            logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
            return false;
        }

        @SuppressWarnings("unchecked")
        final Map<String, String> changedConfigs = tryReadConfigProperties(shaderPackConfigTxt).map(properties -> (Map<String, String>) (Map<?, ?>) properties)
            .orElse(new HashMap<>());

        changedConfigs.putAll(shaderPackOptionQueue);
        clearShaderPackOptionQueue();

        if (resetShaderPackOptions) {
            changedConfigs.clear();
        }
        resetShaderPackOptions = false;

        try {
            currentPack = new ShaderPack(shaderPackPath, changedConfigs, StandardMacros.createStandardEnvironmentDefines());

            final MutableOptionValues changedConfigsValues = currentPack.getShaderPackOptions().getOptionValues().mutableCopy();

            // Store changed values from those currently in use by the shader pack
            final Properties configsToSave = new Properties();
            changedConfigsValues.getBooleanValues().forEach((k, v) -> configsToSave.setProperty(k, Boolean.toString(v)));
            changedConfigsValues.getStringValues().forEach(configsToSave::setProperty);

            tryUpdateConfigPropertiesFile(shaderPackConfigTxt, configsToSave);
        } catch (Exception e) {
            logger.error("Failed to load the shaderpack \"{}\"!", name);
            logger.error("", e);

            return false;
        }

        fallback = false;
        currentPackName = name;

        logger.info("Using shaderpack: " + name);

        return true;
    }

    private static Optional<Path> loadExternalZipShaderpack(Path shaderpackPath) throws IOException {
        final FileSystem zipSystem = FileSystems.newFileSystem(shaderpackPath, Iris.class.getClassLoader());
        zipFileSystem = zipSystem;

        // Should only be one root directory for a zip shaderpack
        final Path root = zipSystem.getRootDirectories().iterator().next();

        final Path potentialShaderDir = zipSystem.getPath("shaders");

        // If the shaders dir was immediately found return it
        // Otherwise, manually search through each directory path until it ends with "shaders"
        if (Files.exists(potentialShaderDir)) {
            return Optional.of(potentialShaderDir);
        }

        // Sometimes shaderpacks have their shaders directory within another folder in the shaderpack
        // For example Sildurs-Vibrant-Shaders.zip/shaders
        // While other packs have Trippy-Shaderpack-master.zip/Trippy-Shaderpack-master/shaders
        // This makes it hard to determine what is the actual shaders dir
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isDirectory).filter(path -> path.endsWith("shaders")).findFirst();
        }
    }

    private static void setShadersDisabled() {
        currentPack = null;
        fallback = false;
        currentPackName = "(off)";

        logger.info("Shaders are disabled");
    }

    private static Optional<Properties> tryReadConfigProperties(Path path) {
        final Properties properties = new Properties();

        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                // NB: config properties are specified to be encoded with ISO-8859-1 by OptiFine,
                //     so we don't need to do the UTF-8 workaround here.
                properties.load(is);
            } catch (IOException e) {
                // TODO: Better error handling
                return Optional.empty();
            }
        }

        return Optional.of(properties);
    }

    private static void tryUpdateConfigPropertiesFile(Path path, Properties properties) {
        try {
            if (properties.isEmpty()) {
                // Delete the file or don't create it if there are no changed configs
                if (Files.exists(path)) {
                    Files.delete(path);
                }

                return;
            }

            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, null);
            }
        } catch (IOException e) {
            // TODO: Better error handling
        }
    }

    public static boolean isValidShaderpack(Path pack) {
        if (Files.isDirectory(pack)) {
            // Sometimes the shaderpack directory itself can be
            // identified as a shader pack due to it containing
            // folders which contain "shaders" folders, this is
            // necessary to check against that
            if (pack.equals(getShaderpacksDirectory())) {
                return false;
            }
            try (Stream<Path> stream = Files.walk(pack)) {
                return stream.filter(Files::isDirectory)
                    // Prevent a pack simply named "shaders" from being
                    // identified as a valid pack
                    .filter(path -> !path.equals(pack)).anyMatch(path -> path.endsWith("shaders"));
            } catch (IOException ignored) {
                // ignored, not a valid shader pack.
            }
        }

        if (pack.toString().endsWith(".zip")) {
            try (FileSystem zipSystem = FileSystems.newFileSystem(pack, Iris.class.getClassLoader())) {
                final Path root = zipSystem.getRootDirectories().iterator().next();
                try (Stream<Path> stream = Files.walk(root)) {
                    return stream.filter(Files::isDirectory).anyMatch(path -> path.endsWith("shaders"));
                }
            } catch (ZipError zipError) {
                // Java 8 seems to throw a ZipError instead of a subclass of IOException
                Iris.logger.warn("The ZIP at " + pack + " is corrupt");
            } catch (IOException ignored) {
                // ignored, not a valid shader pack.
            }
        }

        return false;
    }

    public static void queueShaderPackOptionsFromProfile(Profile profile) {
        getShaderPackOptionQueue().putAll(profile.optionValues);
    }

    public static void queueShaderPackOptionsFromProperties(Properties properties) {
        queueDefaultShaderPackOptionValues();

        properties.stringPropertyNames().forEach(key -> getShaderPackOptionQueue().put(key, properties.getProperty(key)));
    }

    // Used in favor of resetShaderPackOptions as the aforementioned requires the pack to be reloaded
    public static void queueDefaultShaderPackOptionValues() {
        clearShaderPackOptionQueue();

        getCurrentPack().ifPresent(pack -> {
            final OptionSet options = pack.getShaderPackOptions().getOptionSet();
            final OptionValues values = pack.getShaderPackOptions().getOptionValues();

            options.getStringOptions().forEach((key, mOpt) -> {
                if (values.getStringValue(key).isPresent()) {
                    getShaderPackOptionQueue().put(key, mOpt.getOption().getDefaultValue());
                }
            });
            options.getBooleanOptions().forEach((key, mOpt) -> {
                if (values.getBooleanValue(key) != OptionalBoolean.DEFAULT) {
                    getShaderPackOptionQueue().put(key, Boolean.toString(mOpt.getOption().getDefaultValue()));
                }
            });
        });
    }

    public static void clearShaderPackOptionQueue() {
        getShaderPackOptionQueue().clear();
    }

    public static void resetShaderPackOptionsOnNextReload() {
        resetShaderPackOptions = true;
    }

    public static boolean shouldResetShaderPackOptionsOnNextReload() {
        return resetShaderPackOptions;
    }

    public static void reload() throws IOException {
        // allows shaderpacks to be changed at runtime
        irisConfig.initialize();

        // Destroy all allocated resources
        destroyEverything();

        // Load the new shaderpack
        loadShaderpack();

        // Very important - we need to re-create the pipeline straight away.
        // https://github.com/IrisShaders/Iris/issues/1330
        if (Minecraft.getMinecraft().theWorld != null) {
            Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
        }
    }

    /**
     * Destroys and deallocates all created OpenGL resources. Useful as part of a reload.
     */
    private static void destroyEverything() {
        currentPack = null;

        getPipelineManager().destroyPipeline();

        // Close the zip filesystem that the shaderpack was loaded from
        //
        // This prevents a FileSystemAlreadyExistsException when reloading shaderpacks.
        if (zipFileSystem != null) {
            try {
                zipFileSystem.close();
            } catch (NoSuchFileException e) {
                logger.warn("Failed to close the shaderpack zip when reloading because it was deleted, proceeding anyways.");
            } catch (IOException e) {
                logger.error("Failed to close zip file system?", e);
            }
        }
    }

    public static DimensionId lastDimension = null;

    public static DimensionId getCurrentDimension() {
        final WorldClient level = Minecraft.getMinecraft().theWorld;

        if (level != null) {
            if (level.provider == null) return DimensionId.OVERWORLD;

            if (level.provider.isHellWorld || level.provider.dimensionId == -1) {
                return DimensionId.NETHER;
            } else if (level.provider.dimensionId == 1) {
                return DimensionId.END;
            } else {
                return DimensionId.OVERWORLD;
            }
        } else {
            // This prevents us from reloading the shaderpack unless we need to. Otherwise, if the player is in the
            // nether and quits the game, we might end up reloading the shaders on exit and on entry to the level
            // because the code thinks that the dimension changed.
            return lastDimension;
        }
    }

    private static WorldRenderingPipeline createPipeline(DimensionId dimensionId) {
        if (currentPack == null) {
            // Completely disables shader-based rendering
            return new FixedFunctionWorldRenderingPipeline();
        }

        final ProgramSet programs = currentPack.getProgramSet(dimensionId);


        try {
            return new DeferredWorldRenderingPipeline(programs);
        } catch (Exception e) {
            logger.error("Failed to create shader rendering pipeline, disabling shaders!", e);
            // TODO: This should be reverted if a dimension change causes shaders to compile again
            fallback = true;

            return new FixedFunctionWorldRenderingPipeline();
        }
    }

    @NotNull
    public static PipelineManager getPipelineManager() {
        if (pipelineManager == null) {
            pipelineManager = new PipelineManager(Iris::createPipeline);
        }

        return pipelineManager;
    }

    @NotNull
    public static Optional<ShaderPack> getCurrentPack() {
        return Optional.ofNullable(currentPack);
    }

    public static String getVersion() {
        if (IRIS_VERSION == null) {
            return "Version info unknown!";
        }

        return IRIS_VERSION;
    }

    public static String getFormattedVersion() {
        final EnumChatFormatting color;
        String version = getVersion();

        if (version.endsWith("-development-environment")) {
            color = EnumChatFormatting.GOLD;
            version = version.replace("-development-environment", " (Development Environment)");
        } else if (version.endsWith("-dirty") || version.contains("unknown") || version.endsWith("-nogit")) {
            color = EnumChatFormatting.RED;
        } else if (version.contains("+rev.")) {
            color = EnumChatFormatting.LIGHT_PURPLE;
        } else {
            color = EnumChatFormatting.GREEN;
        }

        return color + version;
    }

    public static Path getShaderpacksDirectory() {
        if (shaderpacksDirectory == null) {
            shaderpacksDirectory = Minecraft.getMinecraft().mcDataDir.toPath().resolve("shaderpacks");
        }

        return shaderpacksDirectory;
    }

    public static ShaderpackDirectoryManager getShaderpacksDirectoryManager() {
        if (shaderpacksDirectoryManager == null) {
            shaderpacksDirectoryManager = new ShaderpackDirectoryManager(getShaderpacksDirectory());
        }

        return shaderpacksDirectoryManager;
    }

    public void fmlInitEvent() {
        IRIS_VERSION = Tags.VERSION;
        reloadKeybind = new KeyBinding("Reload Shaders", 0, "Iris Keybinds");
        toggleShadersKeybind = new KeyBinding("Toggle Shaders", 0, "Iris Keybinds");
        shaderpackScreenKeybind = new KeyBinding("Shaderpack Selection Screen", 0, "Iris Keybinds");

        ClientRegistry.registerKeyBinding(reloadKeybind);
        ClientRegistry.registerKeyBinding(toggleShadersKeybind);
        ClientRegistry.registerKeyBinding(shaderpackScreenKeybind);
    }

    static BlockContextHolder contextHolder;

    private static int getShaderMaterialOverrideId(Block block, int meta) {
        if (contextHolder == null) {
            final Object2IntMap<Block> blockMatches = BlockRenderingSettings.INSTANCE.getBlockMatches();
            if (blockMatches == null) {
                return -1;
            }
            contextHolder = new BlockContextHolder(blockMatches);

        }
        contextHolder.set(block, (short) block.getRenderType());
        return contextHolder.blockId;
    }

    public static void setShaderMaterialOverride(Block block, int meta) {
        if (!enabled)
            return;

        int blockId = getShaderMaterialOverrideId(block, meta);

        if (TessellatorManager.get() instanceof CapturingTessellator tess)
            tess.setShaderBlockId(blockId);
    }

    public static void resetShaderMaterialOverride() {
        if (!enabled)
            return;
        if (TessellatorManager.get() instanceof CapturingTessellator tess)
            tess.setShaderBlockId(-1);
    }
}
