package kroppeb.stareval.exception;

public class MissingTokenException extends ParseException {

	private static final long serialVersionUID = -2096321911994168858L;

    public MissingTokenException(String message, int index) {
		super(message + " at index " + index);
	}
}
