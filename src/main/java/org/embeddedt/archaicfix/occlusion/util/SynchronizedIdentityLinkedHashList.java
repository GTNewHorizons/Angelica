package org.embeddedt.archaicfix.occlusion.util;

public class SynchronizedIdentityLinkedHashList<E> extends IdentityLinkedHashList<E> {

	private static final long serialVersionUID = -4821513020637725603L;

	public SynchronizedIdentityLinkedHashList() {

	}

	@Override
	protected synchronized boolean add(E obj, int hash) {

		return super.add(obj, hash);
	}

	@Override
	public synchronized E set(int index, E obj) {

		return super.set(index, obj);
	}

	@Override
	public synchronized void add(int index, E obj) {

		super.add(index, obj);
	}

	@Override
	public synchronized E get(int index) {

		return super.get(index);
	}

	@Override
	public synchronized int indexOf(Object o) {

		return super.indexOf(o);
	}

	@Override
	public synchronized boolean push(E obj) {

		return super.add(obj);
	}

	@Override
	public synchronized E pop() {

		return super.pop();
	}

	@Override
	public synchronized E peek() {

		return super.peek();
	}

	@Override
	public synchronized E poke() {

		return super.poke();
	}

	@Override
	public synchronized boolean unshift(E obj) {

		return super.unshift(obj);
	}

	@Override
	public synchronized E shift() {

		return super.shift();
	}

	@Override
	public synchronized boolean contains(Object obj) {

		return super.contains(obj);
	}

	@Override
	public synchronized boolean remove(Object obj) {

		return super.remove(obj);
	}

	@Override
	public synchronized E remove(int index) {

		return super.remove(index);
	}

	@Override
	protected synchronized Entry index(int index) {

		return super.index(index);
	}

	@Override
	protected synchronized Entry seek(Object obj, int hash) {

		return super.seek(obj, hash);
	}

	@Override
	protected synchronized void insert(Entry entry) {

		super.insert(entry);
	}

	@Override
	protected synchronized boolean linkBefore(E obj, Entry succ) {

		return super.linkBefore(obj, succ);
	}

	@Override
	protected synchronized void delete(Entry entry) {

		super.delete(entry);
	}

	@Override
	protected synchronized E unlink(Entry x) {

		return super.unlink(x);
	}

	@Override
	protected synchronized void rehashIfNecessary() {

		super.rehashIfNecessary();
	}

}
