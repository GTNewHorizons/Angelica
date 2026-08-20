package net.coderbot.iris.uniforms.custom;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import kroppeb.stareval.element.ExpressionElement;
import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.expression.VariableExpression;
import kroppeb.stareval.function.FunctionContext;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import kroppeb.stareval.parser.Parser;
import kroppeb.stareval.resolver.ExpressionResolver;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.uniform.LocationalUniformHolder;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.parsing.IrisFunctions;
import net.coderbot.iris.parsing.IrisOptions;
import net.coderbot.iris.parsing.VectorType;
import net.coderbot.iris.uniforms.custom.cached.CachedUniform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class CustomUniforms implements FunctionContext {
	private final Map<String, CachedUniform> variables = new Object2ObjectLinkedOpenHashMap<>();
	private final Map<String, Expression> variablesExpressions = new Object2ObjectLinkedOpenHashMap<>();
	private final CustomUniformFixedInputUniformsHolder inputHolder;
	private final List<CachedUniform> uniformOrder;
	private final Map<Object, PassUniforms> locationMap = new Object2ObjectOpenHashMap<>();
	private final Map<CachedUniform, List<CachedUniform>> dependsOn;

	private final Object2LongOpenHashMap<Object> lastPushedGen = new Object2LongOpenHashMap<>();
	private long generation;

	private static final long PLOT_CUSTOM_PUSHED = Tracy.ENABLED ? Tracy.plotHandle("iris.customPushed") : 0;
	private static final long PLOT_CUSTOM_SKIPPED = Tracy.ENABLED ? Tracy.plotHandle("iris.customSkipped") : 0;
	private int pushedThisFrame;
	private int skippedThisFrame;

	private static final class PassUniforms {
		final CachedUniform[] uniforms;
		final int[] locations;
		final int[] lastPushedValueGen;

		PassUniforms(CachedUniform[] uniforms, int[] locations) {
			this.uniforms = uniforms;
			this.locations = locations;
			this.lastPushedValueGen = new int[uniforms.length];
			Arrays.fill(lastPushedValueGen, -1);
		}
	}

	private CustomUniforms(CustomUniformFixedInputUniformsHolder inputHolder, Map<String, Builder.Variable> variables) {
		this.inputHolder = inputHolder;
		this.lastPushedGen.defaultReturnValue(-1);
		ExpressionResolver resolver = new ExpressionResolver(
			IrisFunctions.functions,
			(name) -> {
				Type type = this.inputHolder.getType(name);
				if (type != null)
					return type;
				Builder.Variable variable = variables.get(name);
				if (variable != null)
					return variable.type;
				return null;
			},
			true);

		for (Builder.Variable variable : variables.values()) {
			try {
				Expression expression = resolver.resolveExpression(variable.type, variable.expression);
				CachedUniform cachedUniform = CachedUniform
					.forExpression(variable.name, variable.type, expression, this);
				this.addVariable(expression, cachedUniform);
				if (variable.uniform) {
					List<CachedUniform> uniforms = new ArrayList<>();
					uniforms.add(cachedUniform);
				}
				//Iris.logger.info("Was able to resolve uniform " + variable.name + " = " + variable.expression);
			} catch (Exception e) {
				Iris.logger
					.warn("Failed to resolve uniform " + variable.name + ", reason: " + e
						.getMessage() + " ( = " + variable.expression + ")", e);
			}
		}

		{
			// toposort

			this.dependsOn = new Object2ObjectOpenHashMap<>();
			Map<CachedUniform, List<CachedUniform>> requiredBy = new Object2ObjectOpenHashMap<>();
			Object2IntMap<CachedUniform> dependsOnCount = new Object2IntOpenHashMap<>();

			for (CachedUniform input : this.inputHolder.getAll()) {
				requiredBy.put(input, new ObjectArrayList<>());
			}

			for (CachedUniform input : this.variables.values()) {
				requiredBy.put(input, new ObjectArrayList<>());
			}

			FunctionReturn functionReturn = new FunctionReturn();
			Set<VariableExpression> requires = new ObjectOpenHashSet<>();
			Set<CachedUniform> brokenUniforms = new ObjectOpenHashSet<>();

			for (Map.Entry<String, Expression> entry : this.variablesExpressions.entrySet()) {
				requires.clear();

				entry.getValue().listVariables(requires);
				if (requires.isEmpty()) {
					continue;
				}

				CachedUniform uniform = this.variables.get(entry.getKey());

				List<CachedUniform> dependencies = new ArrayList<>();
				for (VariableExpression v : requires) {
					Expression evaluated = v.partialEval(this, functionReturn);
					if (evaluated instanceof CachedUniform) {
						dependencies.add((CachedUniform) evaluated);
					} else {
						// we are depending on a broken uniform
						brokenUniforms.add(uniform);
					}
				}

				if (dependencies.isEmpty()) {
					// can be empty if we rely on broken uniforms
					continue;
				}

				dependsOn.put(uniform, dependencies);
				dependsOnCount.put(uniform, dependencies.size());

				for (CachedUniform dependency : dependencies) {
					requiredBy.get(dependency).add(uniform);
				}
			}

			// actual toposort:
			List<CachedUniform> ordered = new ObjectArrayList<>();
			List<CachedUniform> free = new ObjectArrayList<>();

			// init
			for (CachedUniform entry : requiredBy.keySet()) {
				if (!dependsOnCount.containsKey(entry)) {
					free.add(entry);
				}
			}

			while (!free.isEmpty()) {
				CachedUniform pop = free.remove(free.size() - 1);
				if (!brokenUniforms.contains(pop)) {
					// only add those that aren't broken
					ordered.add(pop);
				} else {
					// mark all those that rely on use as broken too
					brokenUniforms.addAll(requiredBy.get(pop));
				}
				for (CachedUniform dependent : requiredBy.get(pop)) {
					int count = dependsOnCount.mergeInt(dependent, -1, Integer::sum);
					assert count >= 0;
					if (count == 0) {
						free.add(dependent);
						dependsOnCount.removeInt(dependent);
					}
				}
			}

			if (!brokenUniforms.isEmpty()) {
				Iris.logger.warn(
					"The following uniforms won't work, either because they are broken, or reference a broken uniform: \n" +
						brokenUniforms.stream().map(CachedUniform::getName).collect(Collectors.joining(", ")));
			}

			if (!dependsOnCount.isEmpty()) {
				throw new IllegalStateException("Circular reference detected between: " +
					dependsOnCount.object2IntEntrySet()
						.stream()
						.map(entry -> entry.getKey().getName() + " (" + entry.getIntValue() + ")")
						.collect(Collectors.joining(", "))
				);
			}

			this.uniformOrder = ordered;
		}
	}

	private void addVariable(Expression expression, CachedUniform uniform) throws Exception {
		String name = uniform.getName();
		if (this.variables.containsKey(name))
			throw new Exception("Duplicated variable: " + name);
		if (this.inputHolder.containsKey(name))
			throw new Exception("Variable shadows build in uniform: " + name);

		this.variables.put(name, uniform);
		this.variablesExpressions.put(name, expression);
	}

	public void assignTo(LocationalUniformHolder targetHolder) {
		final List<CachedUniform> uniforms = new ObjectArrayList<>();
		final IntArrayList locations = new IntArrayList();
		for (CachedUniform uniform : this.uniformOrder) {
			try {
				OptionalInt location = targetHolder.location(uniform.getName(), Type.convert(uniform.getType()));
				if (location.isPresent()) {
					uniforms.add(uniform);
					locations.add(location.getAsInt());
				}
			} catch (Exception e) {
				throw new RuntimeException(uniform.getName(), e);
			}
		}
		this.locationMap.put(targetHolder, new PassUniforms(uniforms.toArray(new CachedUniform[0]), locations.toIntArray()));
	}

	public void mapholderToPass(LocationalUniformHolder holder, Object pass) {
		locationMap.put(pass, locationMap.remove(holder));
	}


	public void update() {
		if (Tracy.ENABLED) {
			Tracy.plotInt(PLOT_CUSTOM_PUSHED, pushedThisFrame);
			Tracy.plotInt(PLOT_CUSTOM_SKIPPED, skippedThisFrame);
			pushedThisFrame = 0;
			skippedThisFrame = 0;
		}
		generation++;
		final List<CachedUniform> ordered = this.uniformOrder;
		for (int i = 0, n = ordered.size(); i < n; i++) {
			ordered.get(i).update();
		}
	}

	public void push(Object pass) {
		final PassUniforms pu = this.locationMap.get(pass);
		if (pu == null) return;
		if (lastPushedGen.getLong(pass) == generation) {
			return;
		}
		lastPushedGen.put(pass, generation);
		final CachedUniform[] uniforms = pu.uniforms;
		final int[] locations = pu.locations;
		final int[] gens = pu.lastPushedValueGen;
		for (int i = 0; i < uniforms.length; i++) {
			final int gen = uniforms[i].valueGen();
			if (gens[i] != gen) {
				uniforms[i].push(locations[i]);
				gens[i] = gen;
				if (Tracy.ENABLED) pushedThisFrame++;
			} else if (Tracy.ENABLED) {
				skippedThisFrame++;
			}
		}
	}

	/**
	 * This function will do the following:
	 * <ul>
	 *     <li>
	 *         Remove unused uniforms
	 *     </li>
	 *     <li>
	 *         TODO: Create separate push lists for each renderpass
	 *     </li>
	 *     <li>
	 *         TODO: Sort the others in the correct execution line <p/>
	 *               note: that if a `EVERY_FRAME` depends on a `EVERY_TICK`, it has to correctly now that it's
	 *               dependency hasn't updated <br/>
	 *                  suggestion: set a boolean in the `EVERY_TICK` execution line saying this is a tick
	 *                  and have it set to false in the `EVERY_FRAME`. Depending on the value, `frameDependencies` or
	 *                  `allDependencies` lists are used
	 *     </li>
	 * </ul>
	 */
	public void optimise() {

		Object2IntMap<CachedUniform> dependedByCount = new Object2IntOpenHashMap<>();

		// Count the times a uniform is depended on
		for (List<CachedUniform> dependencies : this.dependsOn.values()) {
			for (CachedUniform dependency : dependencies) {
				dependedByCount.mergeInt(dependency, 1, Integer::sum);
			}
		}

		// Count the times a pass depends on a uniform ensures they wont ever be removed
		for (PassUniforms pu : this.locationMap.values()) {
			for (CachedUniform cachedUniform : pu.uniforms) {
				dependedByCount.mergeInt(cachedUniform, 1, Integer::sum);
			}
		}


		Set<CachedUniform> unused = new ObjectOpenHashSet<>();
		for (int i = this.uniformOrder.size() - 1; i >= 0; i--) {
			CachedUniform uniform = this.uniformOrder.get(i);
			if (!dependedByCount.containsKey(uniform)) {
				// not used
				unused.add(uniform);
				// remove dependencies
				List<CachedUniform> dependencies = this.dependsOn.get(uniform);
				if (dependencies != null) {
					for (CachedUniform dependency : dependencies) {
						// reduce count by 1
						dependedByCount.computeIntIfPresent(dependency, (key, value) -> value - 1);
					}
				}
			}
		}

		this.uniformOrder.removeAll(unused);
	}

	@Override
	public boolean hasVariable(String name) {
		return this.inputHolder.containsKey(name) || this.variables.containsKey(name);
	}

	@Override
	public Expression getVariable(String name) {
		// TODO: Make the simplify just return these ones
		final CachedUniform inputUniform = this.inputHolder.getUniform(name);
		if (inputUniform != null)
			return inputUniform;
		final CachedUniform customUniform = this.variables.get(name);
		if (customUniform != null)
			return customUniform;
		throw new RuntimeException("Unknown variable: " + name);
	}

	public static class Builder {
		final private static Map<String, Type> types = new ImmutableMap.Builder<String, Type>()
			.put("bool", Type.Boolean)
			.put("float", Type.Float)
			.put("int", Type.Int)
			.put("vec2", VectorType.VEC2)
			.put("vec3", VectorType.VEC3)
			.put("vec4", VectorType.VEC4)
			.build();
		final Map<String, Variable> variables = new Object2ObjectLinkedOpenHashMap<>();

		public void addVariable(String type, String name, String expression, boolean isUniform) {
			if (variables.containsKey(name)) {
				Iris.logger.warn("Ignoring duplicated custom uniform name: " + name);
				return;
			}

			Type parsedType = types.get(type);
			if (parsedType == null) {
				Iris.logger.warn("Ignoring invalid uniform type: " + type + " of " + name);
				return;
			}

			try {
				ExpressionElement ast = Parser.parse(expression, IrisOptions.options);
				variables.put(name, new Variable(parsedType, name, ast, isUniform));
			} catch (Exception e) {
				Iris.logger.warn("Failed to parse custom variable/uniform " + name + " with expression " + expression, e);
			}
		}

		public CustomUniforms build(
			CustomUniformFixedInputUniformsHolder inputHolder
		) {
			return new CustomUniforms(inputHolder, this.variables);
		}

		@SafeVarargs
		public final CustomUniforms build(
			Consumer<UniformHolder>... uniforms
		) {
			CustomUniformFixedInputUniformsHolder.Builder inputs = new CustomUniformFixedInputUniformsHolder.Builder();
			for (Consumer<UniformHolder> uniform : uniforms) {
				uniform.accept(inputs);
			}
			return this.build(inputs.build());
		}

		@Desugar
		private record Variable(Type type, String name, ExpressionElement expression, boolean uniform) {
		}


	}
}
