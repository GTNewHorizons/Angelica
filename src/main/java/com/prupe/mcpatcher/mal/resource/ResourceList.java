package com.prupe.mcpatcher.mal.resource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;

public class ResourceList {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.TEXTURE_PACK);

    private static ResourceList instance;
    private static final Map<IResourcePack, Integer> resourcePackOrder = new WeakHashMap<>();

    private final IResourcePack resourcePack;
    private final Set<ResourceLocationWithSource> allResources = new TreeSet<>(
        new ResourceLocationWithSource.Comparator1());

    public static ResourceList getInstance() {
        if (instance == null) {
            List<IResourcePack> resourcePacks = TexturePackAPI.getResourcePacks(null);
            int order = resourcePacks.size();
            resourcePackOrder.clear();
            for (IResourcePack resourcePack : resourcePacks) {
                resourcePackOrder.put(resourcePack, order);
                order--;
            }
            instance = new ResourceList();
        }
        return instance;
    }

    public static void clearInstance() {
        instance = null;
    }

    public static int getResourcePackOrder(IResourcePack resourcePack) {
        Integer i = resourcePackOrder.get(resourcePack);
        return i == null ? Integer.MAX_VALUE : i;
    }

    private ResourceList() {
        this.resourcePack = null;
        for (IResourcePack resourcePack : TexturePackAPI.getResourcePacks(null)) {
            ResourceList sublist;
            if (resourcePack instanceof FileResourcePack) {
                sublist = new ResourceList((FileResourcePack) resourcePack);
            } else if (resourcePack instanceof DefaultResourcePack) {
                sublist = new ResourceList((DefaultResourcePack) resourcePack);
            } else if (resourcePack instanceof AbstractResourcePack) {
                sublist = new ResourceList((AbstractResourcePack) resourcePack);
            } else {
                continue;
            }
            allResources.removeAll(sublist.allResources);
            allResources.addAll(sublist.allResources);
        }
        logger.fine("new %s", this);
        if (logger.isLoggable(Level.FINEST)) {
            for (ResourceLocationWithSource resource : allResources) {
                logger.finest(
                    "%s -> %s",
                    resource,
                    resource.getSource()
                        .getPackName());
            }
        }
    }

    private ResourceList(FileResourcePack resourcePack) {
        this.resourcePack = resourcePack;
        try {
            scanZipFile(resourcePack.getResourcePackZipFile());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        logger.fine("new %s", this);
    }

    private ResourceList(DefaultResourcePack resourcePack) {
        this.resourcePack = resourcePack;
        String version = "1.7.10";
        File jar = MCPatcherUtils.getMinecraftPath("versions", version, version + ".jar");
        if (jar.isFile()) {
            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(jar);
                scanZipFile(zipFile);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                MCPatcherUtils.close(zipFile);
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, File> map = resourcePack.field_152781_b; // map
        if (map != null) {
            for (Map.Entry<String, File> entry : map.entrySet()) {
                String key = entry.getKey();
                File file = entry.getValue();
                ResourceLocation resource = new ResourceLocation(key);
                addResource(resource, file.isFile(), file.isDirectory());
            }
        }
        if (!allResources.isEmpty()) {
            logger.fine("new %s", this);
        }
    }

    private ResourceList(AbstractResourcePack resourcePack) {
        this.resourcePack = resourcePack;
        File directory = resourcePack.resourcePackFile;
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        Set<String> allFiles = new HashSet<>();
        listAllFiles(directory, "", allFiles);
        for (String path : allFiles) {
            ResourceLocation resource = TexturePackAPI.parsePath(path);
            if (resource != null) {
                File file = new File(directory, path);
                addResource(resource, file.isFile(), file.isDirectory());
            }
        }
        logger.fine("new %s", this);
    }

    private void scanZipFile(ZipFile zipFile) {
        if (zipFile == null) {
            return;
        }
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            String path = entry.getName();
            ResourceLocation resource = TexturePackAPI.parsePath(path);
            if (resource != null) {
                addResource(resource, !entry.isDirectory(), entry.isDirectory());
            }
        }
    }

    private static void listAllFiles(File base, String subdir, Set<String> files) {
        File[] entries = new File(base, subdir).listFiles();
        if (entries == null) {
            return;
        }
        for (File file : entries) {
            String newPath = subdir + file.getName();
            if (files.add(newPath)) {
                if (file.isDirectory()) {
                    listAllFiles(base, subdir + file.getName() + '/', files);
                }
            }
        }
    }

    private void addResource(ResourceLocation resource, boolean isFile, boolean isDirectory) {
        if (isFile) {
            allResources.add(new ResourceLocationWithSource(resourcePack, resource));
        } else if (isDirectory) {
            if (!resource.getResourcePath()
                .endsWith("/")) {
                resource = new ResourceLocation(resource.getResourceDomain(), resource.getResourcePath() + '/');
            }
            allResources.add(new ResourceLocationWithSource(resourcePack, resource));
        }
    }

    public List<ResourceLocation> listResources(String directory, String suffix, boolean sortByFilename) {
        return listResources(directory, suffix, true, false, sortByFilename);
    }

    public List<ResourceLocation> listResources(String directory, String suffix, boolean recursive, boolean directories,
        boolean sortByFilename) {
        return listResources(null, directory, suffix, recursive, directories, sortByFilename);
    }

    public List<ResourceLocation> listResources(String namespace, String directory, String suffix, boolean recursive,
        boolean directories, final boolean sortByFilename) {
        if (suffix == null) {
            suffix = "";
        }
        if (MCPatcherUtils.isNullOrEmpty(directory)) {
            directory = "";
        } else if (!directory.endsWith("/")) {
            directory += '/';
        }

        Set<ResourceLocationWithSource> tmpList = new TreeSet<>(
            new ResourceLocationWithSource.Comparator1(true, sortByFilename ? suffix : null));
        boolean allNamespaces = MCPatcherUtils.isNullOrEmpty(namespace);
        for (ResourceLocationWithSource resource : allResources) {
            if (directories != resource.isDirectory()) {
                continue;
            }
            if (!allNamespaces && !namespace.equals(resource.getResourceDomain())) {
                continue;
            }
            String path = resource.getResourcePath();
            if (!path.endsWith(suffix)) {
                continue;
            }
            if (!path.startsWith(directory)) {
                continue;
            }
            if (!recursive) {
                String subpath = path.substring(directory.length());
                if (subpath.contains("/")) {
                    continue;
                }
            }
            tmpList.add(resource);
        }

        return new ArrayList<>(tmpList);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ResourceList: ");
        if (resourcePack == null) {
            sb.append("(combined) ");
        } else {
            sb.append(resourcePack.getPackName())
                .append(' ');
        }
        int fileCount = 0;
        int directoryCount = 0;
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocationWithSource resource : allResources) {
            if (resource.isDirectory()) {
                directoryCount++;
            } else {
                fileCount++;
            }
            namespaces.add(resource.getResourceDomain());
        }
        sb.append(fileCount)
            .append(" files, ");
        sb.append(directoryCount)
            .append(" directories in ");
        sb.append(namespaces.size())
            .append(" namespaces");
        return sb.toString();
    }
}
