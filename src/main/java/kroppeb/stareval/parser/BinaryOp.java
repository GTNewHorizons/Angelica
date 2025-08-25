package kroppeb.stareval.parser;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record BinaryOp(String name, int priority) {


	@Override
	public String toString() {
		return this.name + "{" + this.priority + "}";
	}


}
