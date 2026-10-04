package net.coderbot.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Write tests for this code
public class IncludeProcessor {
	private final IncludeGraph graph;
	private final Map<AbsolutePackPath, ImmutableList<String>> cache;

	private final Map<AbsolutePackPath, byte[]> hashes;

	public IncludeProcessor(IncludeGraph graph) {
		this.graph = graph;
		this.cache = new ConcurrentHashMap<>();
		this.hashes = new ConcurrentHashMap<>();
	}

	public byte[] getIncludedFileHash(AbsolutePackPath path) {
		byte[] hash = hashes.get(path);

		if (hash == null) {
			hash = hash(path);
			if (hash != null) hashes.put(path, hash);
		}

		return hash;
	}

	private byte[] hash(AbsolutePackPath path) {
		final FileNode fileNode = graph.getNodes().get(path);

		if (fileNode == null) {
			return null;
		}

		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}

		final ImmutableList<String> lines = fileNode.getLines();
		final ImmutableMap<Integer, AbsolutePackPath> includes = fileNode.getIncludes();

		// A line can't contain '\n' and an include is a fixed 32 bytes, so the encoding is unambiguous
		for (int i = 0; i < lines.size(); i++) {
			final AbsolutePackPath include = includes.get(i);

			if (include != null) {
				digest.update((byte) 1);
				digest.update(Objects.requireNonNull(getIncludedFileHash(include)));
			} else {
				digest.update((byte) 0);
				digest.update(lines.get(i).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
		}

		return digest.digest();
	}

	// TODO: Actual error handling

	public ImmutableList<String> getIncludedFile(AbsolutePackPath path) {
		ImmutableList<String> lines = cache.get(path);

		if (lines == null) {
			lines = process(path);
			if (lines != null) cache.put(path, lines);
		}

		return lines;
	}

	private ImmutableList<String> process(AbsolutePackPath path) {
		FileNode fileNode = graph.getNodes().get(path);

		if (fileNode == null) {
			return null;
		}

		ImmutableList.Builder<String> builder = ImmutableList.builder();

		ImmutableList<String> lines = fileNode.getLines();
		ImmutableMap<Integer, AbsolutePackPath> includes = fileNode.getIncludes();

		for (int i = 0; i < lines.size(); i++) {
			AbsolutePackPath include = includes.get(i);

			if (include != null) {
				// TODO: Don't recurse like this, and check for cycles
				// TODO: Better diagnostics
				builder.addAll(Objects.requireNonNull(getIncludedFile(include)));
			} else {
				builder.add(lines.get(i));
			}
		}

		return builder.build();
	}
}
