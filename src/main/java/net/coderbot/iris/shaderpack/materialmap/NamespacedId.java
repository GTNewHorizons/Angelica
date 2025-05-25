package net.coderbot.iris.shaderpack.materialmap;

import java.util.Objects;

import net.minecraft.block.Block;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import lombok.Getter;

public class NamespacedId {
    @Getter
	private final String namespace;
    @Getter
	private final String name;

    private Block block;

	public NamespacedId(String combined) {
		int colonIdx = combined.indexOf(':');

		if (colonIdx == -1) {
			namespace = "minecraft";
			name = combined;
		} else {
			namespace = combined.substring(0, colonIdx);
			name = combined.substring(colonIdx + 1);
		}
	}

	public NamespacedId(String namespace, String name) {
		this.namespace = Objects.requireNonNull(namespace);
		this.name = Objects.requireNonNull(name);
	}

    private static final String ETFUTURUM = "etfuturum";

    private static Boolean EFT = null;

    public Block getBlock() {
        if (block == null) {
            block = GameRegistry.findBlock(namespace, name);

            // very cursed, but unless we want to manually edit every shaderpack this is probably the best option
            if (block == null && namespace.equals("minecraft")) {
                if (EFT == null) {
                    EFT = Loader.isModLoaded(ETFUTURUM);
                }

                if (EFT) {
                    block = GameRegistry.findBlock(ETFUTURUM, name);
                }
            }
        }

        return block;
    }

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		NamespacedId that = (NamespacedId) o;

		return namespace.equals(that.namespace) && name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(namespace, name);
	}

	@Override
	public String toString() {
		return "NamespacedId{" +
				"namespace='" + namespace + '\'' +
				", name='" + name + '\'' +
				'}';
	}

    public String describe() {
        return String.format("%s:%s", namespace, name);
    }
}
