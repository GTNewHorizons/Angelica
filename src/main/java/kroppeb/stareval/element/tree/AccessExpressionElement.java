package kroppeb.stareval.element.tree;

import com.github.bsideup.jabel.Desugar;
import kroppeb.stareval.element.AccessibleExpressionElement;

@Desugar
public record AccessExpressionElement(AccessibleExpressionElement base,
									  String index) implements AccessibleExpressionElement {


	@Override
	public String toString() {
		return "Access{" + this.base + "[" + this.index + "]}";
	}
}
