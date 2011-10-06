/*
 * Copyright (c) 2009-2011. Created by serso aka se.solovyev.
 * For more information, please, contact se.solovyev@gmail.com
 * or visit http://se.solovyev.org
 */

package org.solovyev.android.calculator.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.solovyev.android.calculator.JsclOperation;
import org.solovyev.android.calculator.StartsWithFinder;
import org.solovyev.android.calculator.math.Functions;
import org.solovyev.android.calculator.math.MathType;
import org.solovyev.common.utils.CollectionsUtils;
import org.solovyev.common.utils.FilterType;
import org.solovyev.common.utils.Finder;

class ToJsclTextProcessor implements TextProcessor {

	@Override
	@NotNull
	public String process(@NotNull String s) {

		final StartsWithFinder startsWithFinder = new StartsWithFinder(s, 0);
		final StringBuilder sb = new StringBuilder();

		MathType.Result mathTypeResult = null;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			startsWithFinder.setI(i);

			mathTypeResult = checkMultiplicationSignBeforeFunction(sb, s, i, mathTypeResult);

			final MathType mathType = mathTypeResult.getMathType();
			if (mathType == MathType.open_group_symbol) {
				sb.append('(');
			} else if (mathType == MathType.close_group_symbol) {
				sb.append(')');
			} else if (ch == '×' || ch == '∙') {
				sb.append("*");
			} else if ( mathType == MathType.function  ){
				sb.append(toJsclFunction(mathTypeResult.getMatch()));
				i += mathTypeResult.getMatch().length() - 1;
			} else if ( mathType == MathType.constant ) {
				sb.append(mathTypeResult.getMatch());
				i += mathTypeResult.getMatch().length() - 1;
			} else {
				sb.append(ch);
			}
		}

		return replaceVariables(sb.toString());
	}

	private String replaceVariables(@NotNull final String s) {
		final StartsWithFinder startsWithFinder = new StartsWithFinder(s, 0);

		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			startsWithFinder.setI(i);

			int offset = 0;
			String functionName = CollectionsUtils.get(MathType.prefixFunctions, startsWithFinder);
			if (functionName == null) {
				String varName = CollectionsUtils.get(CalculatorModel.instance.getVarsRegister().getVarNames(), startsWithFinder);
				if (varName != null) {
					final Var var = CalculatorModel.instance.getVarsRegister().getVar(varName);
					if (var != null) {
						result.append(var.getValue());
						offset = varName.length();
					}
				}
			} else {
				result.append(functionName);
				offset = functionName.length();
			}


			if (offset == 0) {
				result.append(s.charAt(i));
			} else {
				i += offset - 1;
			}
		}

		return result.toString();
	}

	private void replaceVariables(StringBuilder sb, String s, int i, @NotNull StartsWithFinder startsWithFinder) {
		for (Var var : CalculatorModel.instance.getVarsRegister().getVars()) {
			if (!var.isSystem()) {
				if (s.startsWith(var.getName(), i)) {
					if (CollectionsUtils.get(MathType.prefixFunctions, startsWithFinder) == null) {
					}
				}
			}
		}
	}

	public int getPostfixFunctionStart(@NotNull String s, int position) {
		assert s.length() > position;

		int numberOfOpenGroups = 0;
		int result = position;
		for (; result >= 0; result--) {

			final MathType mathType = MathType.getType(s, result).getMathType();

			if (CollectionsUtils.contains(mathType, MathType.digit, MathType.dot)) {
				// continue
			} else if (mathType == MathType.close_group_symbol) {
				numberOfOpenGroups++;
			} else if (mathType == MathType.open_group_symbol) {
				numberOfOpenGroups--;
			} else {
				if (stop(s, numberOfOpenGroups, result)) break;
			}
		}

		return result;
	}

	private boolean stop(String s, int numberOfOpenGroups, int i) {
		if (numberOfOpenGroups == 0) {
			if (i > 0) {
				final EndsWithFinder endsWithFinder = new EndsWithFinder(s);
				endsWithFinder.setI(i + 1);
				if (!CollectionsUtils.contains(MathType.prefixFunctions, FilterType.included, endsWithFinder)) {
					MathType type = MathType.getType(s, i).getMathType();
					if (type != MathType.constant) {
						return true;
					}
				}
			} else {
				return true;
			}
		}

		return false;
	}

	@NotNull
	private static String toJsclFunction(@NotNull String function) {
		final String result;

		if (function.equals(Functions.LN)) {
			result = Functions.LOG;
		} else if (function.equals(Functions.SQRT_SIGN)) {
			result = Functions.SQRT;
		} else {
			result = function;
		}

		return result;
	}

	private static class EndsWithFinder implements Finder<String> {

		private int i;

		@NotNull
		private final String targetString;

		private EndsWithFinder(@NotNull String targetString) {
			this.targetString = targetString;
		}

		@Override
		public boolean isFound(@Nullable String s) {
			return targetString.substring(0, i).endsWith(s);
		}

		public void setI(int i) {
			this.i = i;
		}
	}

	@NotNull
	private static MathType.Result checkMultiplicationSignBeforeFunction(@NotNull StringBuilder sb,
																		@NotNull String s,
																		int i,
																		@Nullable MathType.Result mathTypeBeforeResult) {
		MathType.Result result = MathType.getType(s, i);

		if (i > 0) {

			final MathType mathType = result.getMathType();
			assert mathTypeBeforeResult != null;
			final MathType mathTypeBefore = mathTypeBeforeResult.getMathType();

			if (mathTypeBefore == MathType.constant || (mathTypeBefore != MathType.binary_operation &&
					mathTypeBefore != MathType.unary_operation &&
					mathTypeBefore != MathType.function &&
					mathTypeBefore != MathType.open_group_symbol)) {

				if (mathType == MathType.constant) {
					sb.append("*");
				} else if (mathType == MathType.open_group_symbol && mathTypeBefore != null) {
					sb.append("*");
				} else if (mathType == MathType.digit && ((mathTypeBefore != MathType.digit && mathTypeBefore != MathType.dot) || mathTypeBefore == MathType.constant)) {
					sb.append("*");
				} else {
					for (String function : MathType.prefixFunctions) {
						if (s.startsWith(function, i)) {
							sb.append("*");
							break;
						}
					}
				}
			}
		}

		return result;
	}

	public static String wrap(@NotNull JsclOperation operation, @NotNull String s) {
		return operation.name() + "(\"" + s + "\");";
	}
}