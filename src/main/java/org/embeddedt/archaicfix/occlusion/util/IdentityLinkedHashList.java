package org.embeddedt.archaicfix.occlusion.util;

import java.util.Collection;

@SuppressWarnings("unchecked")
public class IdentityLinkedHashList<E extends Object> extends LinkedHashList<E> {

	private static final long serialVersionUID = 4893829808146776641L;

	public IdentityLinkedHashList() {

		super();
	}

	public IdentityLinkedHashList(int size) {

		super(size);
	}

	public IdentityLinkedHashList(Collection<E> col) {

		super(col);
	}

	@Override
	protected int hash(Object o) {

		return System.identityHashCode(o);
	}

	@Override
	protected Entry seek(Object obj, int hash) {

		for (Entry entry = hashTable[hash & mask]; entry != null; entry = entry.nextInBucket) {
			if (obj == entry.key) {
				return entry;
			}
		}

		return null;
	}

	@Override
	public IdentityLinkedHashList<E> clone() {

		return new IdentityLinkedHashList<E>(this);
	}
}
