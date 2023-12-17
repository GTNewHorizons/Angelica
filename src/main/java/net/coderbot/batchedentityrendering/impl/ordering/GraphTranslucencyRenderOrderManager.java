package net.coderbot.batchedentityrendering.impl.ordering;

import de.odysseus.ithaka.digraph.Digraph;
import de.odysseus.ithaka.digraph.Digraphs;
import de.odysseus.ithaka.digraph.MapDigraph;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSet;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSetPolicy;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSetProvider;
import de.odysseus.ithaka.digraph.util.fas.SimpleFeedbackArcSetProvider;
import net.coderbot.batchedentityrendering.impl.BlendingStateHolder;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.batchedentityrendering.impl.WrappableRenderType;
import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class GraphTranslucencyRenderOrderManager implements RenderOrderManager {
    private final FeedbackArcSetProvider feedbackArcSetProvider;
    private final EnumMap<TransparencyType, Digraph<RenderLayer>> types;

    private boolean inGroup = false;
    private final EnumMap<TransparencyType, RenderLayer> currentTypes;

    public GraphTranslucencyRenderOrderManager() {
        feedbackArcSetProvider = new SimpleFeedbackArcSetProvider();
        types = new EnumMap<>(TransparencyType.class);
        currentTypes = new EnumMap<>(TransparencyType.class);

        for (TransparencyType type : TransparencyType.values()) {
            types.put(type, new MapDigraph<>());
        }
    }

    private static TransparencyType getTransparencyType(RenderLayer type) {
        while (type instanceof WrappableRenderType) {
            type = ((WrappableRenderType) type).unwrap();
        }

        if (type instanceof BlendingStateHolder blendingState) {
            return blendingState.getTransparencyType();
        }

        // Default to "generally transparent" if we can't figure it out.
        return TransparencyType.GENERAL_TRANSPARENT;
    }

    public void begin(RenderLayer renderType) {
        TransparencyType transparencyType = getTransparencyType(renderType);
        Digraph<RenderLayer> graph = types.get(transparencyType);
        graph.add(renderType);

        if (inGroup) {
			RenderLayer previous = currentTypes.put(transparencyType, renderType);

            if (previous == null) {
                return;
            }

            int weight = graph.get(previous, renderType).orElse(0);
            weight += 1;
            graph.put(previous, renderType, weight);
        }
    }

    public void startGroup() {
        if (inGroup) {
            throw new IllegalStateException("Already in a group");
        }

        currentTypes.clear();
        inGroup = true;
    }

    public boolean maybeStartGroup() {
        if (inGroup) {
            return false;
        }

        currentTypes.clear();
        inGroup = true;
        return true;
    }

    public void endGroup() {
        if (!inGroup) {
            throw new IllegalStateException("Not in a group");
        }

        currentTypes.clear();
        inGroup = false;
    }

    @Override
    public void reset() {
        // TODO: Is reallocation efficient?
        types.clear();

        for (TransparencyType type : TransparencyType.values()) {
            types.put(type, new MapDigraph<>());
        }
    }

    public Iterable<RenderLayer> getRenderOrder() {
        int layerCount = 0;

        for (Digraph<RenderLayer> graph : types.values()) {
            layerCount += graph.getVertexCount();
        }

        List<RenderLayer> allLayers = new ArrayList<>(layerCount);

        for (Digraph<RenderLayer> graph : types.values()) {
            // TODO: Make sure that FAS can't become a bottleneck!
            // Running NP-hard algorithms in a real time rendering loop might not be an amazing idea.
            // This shouldn't be necessary in sane scenes, though, and if there aren't cycles,
            // then this *should* be relatively inexpensive, since it'll bail out and return an empty set.
            FeedbackArcSet<RenderLayer> arcSet =
                    feedbackArcSetProvider.getFeedbackArcSet(graph, graph, FeedbackArcSetPolicy.MIN_WEIGHT);

            if (arcSet.getEdgeCount() > 0) {
                // This means that our dependency graph had cycles!!!
                // This is very weird and isn't expected - but we try to handle it gracefully anyways.

                // Our feedback arc set algorithm finds some dependency links that can be removed hopefully
                // without disrupting the overall order too much. Hopefully it isn't too slow!
                for (RenderLayer source : arcSet.vertices()) {
                    for (RenderLayer target : arcSet.targets(source)) {
                        graph.remove(source, target);
                    }
                }
            }

            allLayers.addAll(Digraphs.toposort(graph, false));
        }

        return allLayers;
    }
}
