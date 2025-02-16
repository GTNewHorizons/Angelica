package net.coderbot.iris.shaderpack.materialmap;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class BlockPropEntry implements Entry {

    private final NamespacedId id;
    private final Map<String, String> propertyPredicates;
}
