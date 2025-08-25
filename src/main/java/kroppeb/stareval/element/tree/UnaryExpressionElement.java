package kroppeb.stareval.element.tree;

import com.github.bsideup.jabel.Desugar;
import kroppeb.stareval.element.ExpressionElement;
import kroppeb.stareval.parser.UnaryOp;

@Desugar
public record UnaryExpressionElement(UnaryOp op, ExpressionElement inner) implements ExpressionElement {


	@Override
	public String toString() {
		return "UnaryExpr{" + this.op + " {" + this.inner + "} }";
	}
}
