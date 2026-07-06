package kroppeb.stareval.parser;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record UnaryOp(String name) {


	@Override
	public String toString() {
		return this.name;
	}
}
