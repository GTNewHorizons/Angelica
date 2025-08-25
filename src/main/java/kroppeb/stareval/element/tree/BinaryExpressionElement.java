package kroppeb.stareval.element.tree;

import com.github.bsideup.jabel.Desugar;
import kroppeb.stareval.element.ExpressionElement;
import kroppeb.stareval.parser.BinaryOp;

@Desugar
public record BinaryExpressionElement(BinaryOp op, ExpressionElement left,
									  ExpressionElement right) implements ExpressionElement {


	@Override
	public String toString() {
		return "BinaryExpr{ {" + this.left + "} " + this.op + " {" + this.right + "} }";
	}
}
