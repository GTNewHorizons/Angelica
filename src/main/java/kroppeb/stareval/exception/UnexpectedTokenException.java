package kroppeb.stareval.exception;

public class UnexpectedTokenException extends ParseException {

	private static final long serialVersionUID = 689385399380177588L;

    public UnexpectedTokenException(String message, int index) {
		super(message + " at index " + index);
	}
}
