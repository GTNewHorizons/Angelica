package speiger.src.collections.utils;


/**
 * The Stack Interface represents the Last-In-First-Out layout (LIFO).
 * It provides a simple {@link #push(Object)}, {@link #pop()} function,
 * with a fast {@link #clear()} function.
 * The {@link #peek(int)} function allows to view the contents of the stack (top to bottom).
 * @param <T> the type of elements maintained by this Collection
 */
public interface Stack<T>
{
	/**
	 * Inserts a given Object on top of the stack
	 * @param e the Object to insert
	 */
	public void push(T e);
	
	/**
	 * Removes the Object on top of the stack.
	 * @return the element that is on top of the stack
	 * @throws ArrayIndexOutOfBoundsException if the stack is empty
	 */
	public T pop();
	
	/**
	 * Provides the amount of elements currently in the stack
	 * @return amount of elements in the list
	 */
	public int size();
	
	
	/**
	 * @return if the stack is empty
	 */
	public default boolean isEmpty() {
		return size() == 0;
	}
	
	/**
	 * Clears the stack
	 */
	public void clear();
	
	/**
	 * Provides the Object on top of the stack
	 * @return the element that is on top of the stack
	 * @throws ArrayIndexOutOfBoundsException if the stack is empty
	 */
	public default T top() {
		return peek(0);
	}
	
	/**
	 * Provides the Selected Object from the stack.
	 * Top to bottom
	 * @param index of the element that should be provided
	 * @return the element that was requested
 	 * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
	 */
	public T peek(int index);
}
