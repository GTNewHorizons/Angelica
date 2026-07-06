package net.coderbot.iris.shaderpack.loading;

import lombok.Getter;

@Getter
public enum ProgramGroup {
	Setup("setup"),
	Begin("begin"),
	Shadow("shadow"),
	ShadowComposite("shadowcomp"),
	Prepare("prepare"),
	Gbuffers("gbuffers"),
	Deferred("deferred"),
	Composite("composite"),
	Final("final"),
	Dh("dh")
	;

	private final String baseName;

	ProgramGroup(String baseName) {
		this.baseName = baseName;
	}

}
