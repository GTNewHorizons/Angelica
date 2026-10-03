package net.coderbot.iris.shaderpack.materialmap;

import java.util.Objects;

public class NamespacedId {
	private final String namespace;
	private final String name;
	private final int hash;

	public NamespacedId(String combined) {
		int colonIdx = combined.indexOf(':');

		if (colonIdx == -1) {
			namespace = "minecraft";
			name = combined;
		} else {
			namespace = combined.substring(0, colonIdx);
			name = combined.substring(colonIdx + 1);
		}
		hash = hash(namespace, name);
	}

	public NamespacedId(String namespace, String name) {
		this.namespace = Objects.requireNonNull(namespace);
		this.name = Objects.requireNonNull(name);
		this.hash = hash(namespace, name);
	}

	private static int hash(String namespace, String name) {
		return 31 * (31 + namespace.hashCode()) + name.hashCode();
	}

	public String getNamespace() {
		return namespace;
	}

	public String getName() {
		return name;
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

		return hash == that.hash && namespace.equals(that.namespace) && name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return "NamespacedId{" +
				"namespace='" + namespace + '\'' +
				", name='" + name + '\'' +
				'}';
	}
}
