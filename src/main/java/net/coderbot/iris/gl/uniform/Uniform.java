package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;

public abstract class Uniform {
	protected final int location;
	protected final ValueUpdateNotifier notifier;
	protected boolean dirty = true;

	Uniform(int location) {
		this(location, null);
	}

	Uniform(int location, ValueUpdateNotifier notifier) {
		this.location = location;
		this.notifier = notifier;
	}

	public abstract void update();

	protected final Runnable uploadWhileBound(Runnable upload) {
		return () -> {
			if (DisplayListManager.getRecordMode() != DisplayListManager.RecordMode.COMPILE
				&& ProgramUniforms.isActiveProgramBound()) {
				upload.run();
			} else {
				dirty = true;
				ProgramUniforms.markActiveDeferred();
			}
		};
	}

	public final int getLocation() {
		return location;
	}

	public final ValueUpdateNotifier getNotifier() {
		return notifier;
	}
}
