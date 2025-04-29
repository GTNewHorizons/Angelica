package kroppeb.stareval.function;

import kroppeb.stareval.expression.Expression;

@FunctionalInterface
public interface FFF2FFunction extends TypedFunction {
	float eval(float a, float b, float c);

	@Override
	default void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
		params[0].evaluateTo(context, functionReturn);
		float a = functionReturn.floatReturn;

		params[1].evaluateTo(context, functionReturn);
		float b = functionReturn.floatReturn;

		params[2].evaluateTo(context, functionReturn);
		float c = functionReturn.floatReturn;

		functionReturn.floatReturn = this.eval(a, b, c);
	}

	@Override
	default Type getReturnType() {
		return Type.Float;
	}

	@Override
	default Parameter[] getParameters() {
		return new Parameter[]{Type.FloatParameter, Type.FloatParameter, Type.FloatParameter};
	}
}
