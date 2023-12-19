package com.prupe.mcpatcher.mal.resource;

import java.util.Comparator;
import java.util.regex.Pattern;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCPatcherUtils;

public class ResourceLocationWithSource extends ResourceLocation {

    private final IResourcePack source;
    private final int order;
    private final boolean isDirectory;

    public ResourceLocationWithSource(IResourcePack source, ResourceLocation resource) {
        super(
            resource.getResourceDomain(),
            resource.getResourcePath()
                .replaceFirst("/$", ""));
        this.source = source;
        order = ResourceList.getResourcePackOrder(source);
        isDirectory = resource.getResourcePath()
            .endsWith("/");
    }

    public IResourcePack getSource() {
        return source;
    }

    public int getOrder() {
        return order;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    static class Comparator1 implements Comparator<ResourceLocationWithSource> {

        private final boolean bySource;
        private final String suffixExpr;

        Comparator1() {
            this(false, null);
        }

        Comparator1(boolean bySource, String suffix) {
            this.bySource = bySource;
            this.suffixExpr = MCPatcherUtils.isNullOrEmpty(suffix) ? null : Pattern.quote(suffix) + "$";
        }

        @Override
        public int compare(ResourceLocationWithSource o1, ResourceLocationWithSource o2) {
            int result;
            if (bySource) {
                result = o1.getOrder() - o2.getOrder();
                if (result != 0) {
                    return result;
                }
            }
            String n1 = o1.getResourceDomain();
            String n2 = o2.getResourceDomain();
            result = n1.compareTo(n2);
            if (result != 0) {
                return result;
            }
            String p1 = o1.getResourcePath();
            String p2 = o2.getResourcePath();
            if (suffixExpr != null) {
                String f1 = p1.replaceAll(".*/", "")
                    .replaceFirst(suffixExpr, "");
                String f2 = p2.replaceAll(".*/", "")
                    .replaceFirst(suffixExpr, "");
                result = f1.compareTo(f2);
                if (result != 0) {
                    return result;
                }
            }
            return p1.compareTo(p2);
        }
    }
}
