package net.coderbot.iris.pipeline.transform.parameter;

import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.pipeline.transform.PatchShaderType;

public class Parameters implements JobParameters {
    public final Patch patch;
    public PatchShaderType type; // may only be set by TransformPatcher
    // WARNING: adding new fields requires updating hashCode and equals methods!

    // name of the shader, this should not be part of hash/equals
    public String name; // set by TransformPatcher

    public Parameters(Patch patch) {
        this.patch = patch;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((patch == null) ? 0 : patch.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Parameters other = (Parameters) obj;
        return patch == other.patch;
    }
}
