package net.coderbot.iris.shaderpack.parsing;

import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.option.values.OptionValues;

import java.util.EmptyStackException;
import java.util.Stack;

public class BooleanParser {
	public static boolean parse(String expression, OptionValues valueLookup) {
		try {
			StringBuilder option = new StringBuilder();
			Stack<Operation> operationStack = new Stack<>();
			Stack<Boolean> valueStack = new Stack<>();
			for (int i = 0; i < expression.length(); i++) {
				char c = expression.charAt(i);
				switch (c) {
					case '!' -> operationStack.push(Operation.NOT);
					case '&' -> {
						if (!option.isEmpty()) {
							valueStack.push(processValue(option.toString(), valueLookup, operationStack));
							option = new StringBuilder();
						}
						if (operationStack.isEmpty() || !operationStack.peek().equals(Operation.AND)) {
							operationStack.push(Operation.OPEN);
						}
						i++;
						operationStack.push(Operation.AND);
					}
					case '|' -> {
						if (!option.isEmpty()) {
							valueStack.push(processValue(option.toString(), valueLookup, operationStack));
							option = new StringBuilder();
						}
						if (!operationStack.isEmpty() && operationStack.peek().equals(Operation.AND)) {
							evaluate(operationStack, valueStack, true);
						}
						i++;
						operationStack.push(Operation.OR);
					}
					case '(' -> operationStack.push(Operation.OPEN);
					case ')' -> {
						if (!option.isEmpty()) {
							valueStack.push(processValue(option.toString(), valueLookup, operationStack));
							option = new StringBuilder();
						}
						if (!operationStack.isEmpty() && operationStack.peek().equals(Operation.AND)) {
							evaluate(operationStack, valueStack, true);
						}
						evaluate(operationStack, valueStack, true);
					}
					case ' ' -> {}
					default -> option.append(c);
				}
			}
			if (!option.isEmpty()) {
				valueStack.push(processValue(option.toString(), valueLookup, operationStack));
			}
			evaluate(operationStack, valueStack, false);
			boolean result = valueStack.pop();
			if (!valueStack.isEmpty() || !operationStack.isEmpty()) {
				Iris.logger.warn("Failed to parse boolean expression, stack isn't empty, defaulting to true: '{}'", expression);
				return true;
			}
			return result;
		} catch (EmptyStackException e) {
			Iris.logger.warn("Failed to parse boolean expression, stack is empty unexpectedly, defaulting to true: '{}'", expression);
			return true;
		}
	}

	private static boolean processValue(String value, OptionValues valueLookup, Stack<Operation> operationStack) {
		boolean booleanValue = switch (value) {
			case "true", "1" -> true;
			case "false", "0" -> false;
			default -> valueLookup != null && valueLookup.getBooleanValueOrDefault(value);
		};
		if (!operationStack.isEmpty() && operationStack.peek() == Operation.NOT) {
			operationStack.pop();
			return !booleanValue;
		}
		return booleanValue;
	}

	private static void evaluate(Stack<Operation> operationStack, Stack<Boolean> valueStack, boolean currentBracket) {
		boolean value = valueStack.pop();
		while (!operationStack.isEmpty() && (!currentBracket || operationStack.peek() != Operation.OPEN)) {
			value = operationStack.pop().compute(value, valueStack);
		}
		if (!operationStack.isEmpty() && operationStack.peek() == Operation.OPEN) {
			operationStack.pop();
			if (!operationStack.isEmpty() && operationStack.peek() == Operation.NOT) {
				value = operationStack.pop().compute(value, valueStack);
			}
		}
		valueStack.push(value);
	}

	private enum Operation {
		AND {
			@Override
			boolean compute(boolean value, Stack<Boolean> valueStack) {
				return valueStack.pop() && value;
			}
		},
		OR {
			@Override
			boolean compute(boolean value, Stack<Boolean> valueStack) {
				return valueStack.pop() || value;
			}
		},
		NOT {
			@Override
			boolean compute(boolean value, Stack<Boolean> valueStack) {
				return !value;
			}
		},
		OPEN;

		boolean compute(boolean value, Stack<Boolean> valueStack) {
			return value;
		}
	}
}
