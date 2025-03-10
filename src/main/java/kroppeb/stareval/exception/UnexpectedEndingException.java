package kroppeb.stareval.exception;

public class UnexpectedEndingException extends ParseException {

	private static final long serialVersionUID = -3704913315979481520L;

    public UnexpectedEndingException() {
		this("Expected to read more text, but the string has ended");
	}

	public UnexpectedEndingException(String message) {
		super(message);
	}
}
