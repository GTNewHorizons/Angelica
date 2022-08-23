package com.gtnewhorizons.angelica.loading;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class SMCTweaker implements ITweaker {
    public List<String> args;
    public File gameDir;
    public File assetsDir;
    public String version;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String version) {
        this.args = args;
        this.gameDir = gameDir;
        this.assetsDir = assetsDir;
        this.version = version;
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
        launchClassLoader.addTransformerExclusion("com.gtnewhorizons.angelica.loading.");
        launchClassLoader.addTransformerExclusion("com.gtnewhorizons.angelica.transform.");
        launchClassLoader.registerTransformer("com.gtnewhorizons.angelica.transform.SMCClassTransformer");
    }

    @Override
    public String[] getLaunchArguments() {
        ArrayList argumentList = (ArrayList) Launch.blackboard.get("ArgumentList");
        if (argumentList.isEmpty()) {
            List<String> argsList = new ArrayList();
            if (gameDir != null) {
                argumentList.add("--gameDir");
                argumentList.add(gameDir.getPath());
            }
            if (assetsDir != null) {
                argumentList.add("--assetsDir");
                argumentList.add(assetsDir.getPath());
            }
            argumentList.add("--version");
            argumentList.add(version);
            argumentList.addAll(args);
        }
        return new String[0];
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }
}
