package org.unitego.lobecorp.animation.adapter.geckolib;

import com.geckolib.loading.math.MathParser;
import com.geckolib.loading.math.MathValue;
import com.geckolib.loading.math.Operator;
import com.mojang.datafixers.util.Either;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.pose.ILcAnimationQueries;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/// 复用 GeckoLib 5.5.2 的语法和数学函数，变量叶子只访问本次采样的上下文。
@NullMarked
public class LcMolangParser extends MathParser {
	private static final String INVALID_ASSIGNMENT = "Molang 赋值目标必须是变量";
	private static final String MISSING_CONTEXT = "Molang 求值必须处于动画采样上下文中";

	private final ThreadLocal<@Nullable ILcAnimationQueries> activeQueries = new ThreadLocal<>();

	public LcMolangParser() {
		super();
	}

	public <T> T evaluate(ILcAnimationQueries queries, Supplier<T> evaluation) {
		Objects.requireNonNull(queries);
		var previous = activeQueries.get();
		activeQueries.set(queries);

		try {
			return evaluation.get();
		} finally {
			if (previous == null) {
				activeQueries.remove();
			} else {
				activeQueries.set(previous);
			}
		}
	}

	@Override
	protected Optional<MathValue> compileSingleValue(Either<String, List<MathValue>> symbol) {
		var name = symbol.left().orElse(null);

		if (name == null || name.startsWith("!") || isNumeric(name) || isFunctionRegistered(name)) {
			return super.compileSingleValue(symbol);
		}

		if (!isLikelyVariable(name)) {
			return Optional.empty();
		}

		var negative = name.startsWith("-");
		var variableName = normalize(negative ? name.substring(1) : name);
		MathValue variable = _ -> read(variableName);

		return Optional.of(negative ? _ -> -variable.get(null) : variable);
	}

	@Override
	protected Optional<MathValue> compileCalculation(List<Either<String, List<MathValue>>> symbols) {
		for (var index = 0; index < symbols.size(); index++) {
			var operator = symbols.get(index).left().filter(Operator::isOperator)
					.map(this::getOperatorFor).orElse(null);

			if (operator != Operator.ASSIGN_VARIABLE) {
				continue;
			}

			var name = index == 1 ? symbols.getFirst().left().orElse(null) : null;

			if (name == null || !isLikelyVariable(name) || name.startsWith("-") || name.startsWith("!")) {
				throw new IllegalArgumentException(INVALID_ASSIGNMENT);
			}

			var variableName = normalize(name);
			var value = parseSymbols(symbols.subList(index + 1, symbols.size()));
			return Optional.of(_ -> {
				context().set(variableName, value.get(null));
				return 0;
			});
		}

		return super.compileCalculation(symbols);
	}

	@Override
	protected boolean isLikelyVariable(String name) {
		// 不调用父实现，避免访问 GeckoLib 的全局 MolangQueries 和客户端查询注册表。
		return !isNumeric(name) && !isFunctionRegistered(name) && !Operator.isOperator(name)
				&& !name.equals("?") && !name.equals(":");
	}

	private double read(String name) {
		return switch (name) {
			case "pi" -> Math.PI;
			case "e" -> Math.E;
			case "true" -> 1;
			case "false" -> 0;
			default -> context().get(name);
		};
	}

	private ILcAnimationQueries context() {
		return Objects.requireNonNull(activeQueries.get(), MISSING_CONTEXT);
	}

	private static String normalize(String name) {
		if (name.startsWith("q.")) {
			return "query." + name.substring(2);
		}

		if (name.startsWith("v.")) {
			return "variable." + name.substring(2);
		}

		if (name.startsWith("t.")) {
			return "temp." + name.substring(2);
		}

		return name;
	}
}
