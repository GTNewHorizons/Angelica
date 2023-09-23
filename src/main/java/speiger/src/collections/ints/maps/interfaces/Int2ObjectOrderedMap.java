package speiger.src.collections.ints.maps.interfaces;

import speiger.src.collections.ints.sets.IntOrderedSet;
import speiger.src.collections.objects.collections.ObjectBidirectionalIterator;
import speiger.src.collections.objects.sets.ObjectOrderedSet;
/**
 * A Special Map Interface giving Access to some really usefull functions
 * The Idea behind this interface is to allow access to functions that give control to the Order of elements.
 * Since Linked implementations as examples can be reordered outside of the Insertion Order.
 * This interface provides basic access to such functions while also providing some Sorted/NaivgableMap implementations that still fit into here.
 *
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface Int2ObjectOrderedMap<V> extends Int2ObjectMap<V>
{
	/**
	 * A customized put method that allows you to insert into the first index.
	 * @param key the key that should be inserted
	 * @param value the value that should be inserted
	 * @return the previous present or default return value
	 * @see java.util.Map#put(Object, Object)
	 */
	public V putAndMoveToFirst(int key, V value);

	/**
	 * A customized put method that allows you to insert into the last index. (This may be nessesary depending on the implementation)
	 * @param key the key that should be inserted
	 * @param value the value that should be inserted
	 * @return the previous present or default return value
	 * @see java.util.Map#put(Object, Object)
	 */
	public V putAndMoveToLast(int key, V value);

	/**
	 * A specific move method to move a given key/value to the first index.
	 * @param key that should be moved to the first index
	 * @return true if the value was moved.
	 * @note returns false if the value was not present in the first place
	 */
	public boolean moveToFirst(int key);
	/**
	 * A specific move method to move a given key/value to the last index.
	 * @param key that should be moved to the first last
	 * @return true if the value was moved.
	 * @note returns false if the value was not present in the first place
	 */
	public boolean moveToLast(int key);

	/**
	 * A Specific get method that allows to move teh given key/value int the first index.
	 * @param key that is searched for
	 * @return the given value for the requested key or default return value
	 */
	public V getAndMoveToFirst(int key);
	/**
	 * A Specific get method that allows to move teh given key/value int the last index.
	 * @param key that is searched for
	 * @return the given value for the requested key or default return value
	 */
	public V getAndMoveToLast(int key);

	/**
	 * A method to get the first Key of a Map.
	 * @return the first key in the map
	 */
	public int firstIntKey();
	/**
	 * A method to get and remove the first Key of a Map.
	 * @return the first key in the map
	 */
	public int pollFirstIntKey();
	/**
	 * A method to get the last Key of a Map.
	 * @return the last key in the map
	 */
	public int lastIntKey();
	/**
	 * A method to get and remove the last Key of a Map.
	 * @return the last key in the map
	 */
	public int pollLastIntKey();

	/**
	 * A method to get the first Value of a Map.
	 * @return the first key in the map
	 */
	public V firstValue();
	/**
	 * A method to get the last Value of a Map.
	 * @return the last key in the map
	 */
	public V lastValue();

	@Override
	public Int2ObjectOrderedMap<V> copy();
	@Override
	public IntOrderedSet keySet();
	@Override
	public ObjectOrderedSet<Entry<V>> int2ObjectEntrySet();

	/**
	 * Fast Ordered Entry Set that allows for a faster Entry Iterator by recycling the Entry Object and just exchanging 1 internal value
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	interface FastOrderedSet<V> extends FastEntrySet<V>, ObjectOrderedSet<Entry<V>> {
		@Override
		public ObjectBidirectionalIterator<Entry<V>> fastIterator();
		/**
		 * Fast iterator that recycles the given Entry object to improve speed and reduce object allocation
		 * @param fromElement that is going to be started from.
		 * @return a improved iterator that starts from the desired element
		 */
		public ObjectBidirectionalIterator<Entry<V>> fastIterator(int fromElement);
	}
}
