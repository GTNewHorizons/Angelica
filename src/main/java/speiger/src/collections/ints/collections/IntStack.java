package speiger.src.collections.ints.collections;

import speiger.src.collections.utils.Stack;

/**
 * A Type-Specific {@link Stack} that reduces (un)boxing
 */
public interface IntStack
{
	/**
	 * Inserts a given Object on top of the stack
	 * @param e the Object to insert
	 * @see Stack#push(Object)
	 */
	public void push(int e);
	
	/**
	 * Removes the Object on top of the stack.
	 * @return the element that is on top of the stack
	 * @throws ArrayIndexOutOfBoundsException if the stack is empty
	 * @see Stack#pop()
	 */
	public int pop();
	
	/**
	 * Provides the Object on top of the stack
	 * @return the element that is on top of the stack
	 * @throws ArrayIndexOutOfBoundsException if the stack is empty
 	 * @see Stack#top()
	 */
	public default int top() {
		return peek(0);
	}
	
	/**
	 * Provides the Selected Object from the stack.
	 * Top to bottom
	 * @param index of the element that should be provided
	 * @return the element that was requested
 	 * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
 	 * @see Stack#peek(int)
	 */
	public int peek(int index);
	
	/**
	 * Clears the stack
	 */
	public void clear();
	
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
}