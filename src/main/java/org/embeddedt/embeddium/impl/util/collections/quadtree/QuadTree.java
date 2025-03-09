package org.embeddedt.embeddium.impl.util.collections.quadtree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author XFactHD (used under terms of LGPL-3.0 with some small modifications)
 */
public final class QuadTree<T> extends Rect2i
{
    private static final QuadTree<?> EMPTY = new QuadTree<>(new Rect2i(0, 0, 0, 0), 0, List.of(), null);

    private static final int MAX_DEPTH = 12;

    private QuadTree<T> child0, child1, child2, child3;

    private List<Entry<T>> entries = null;

    @SuppressWarnings("unchecked")
    public static <T> QuadTree<T> empty() {
        return (QuadTree<T>)EMPTY;
    }

    public QuadTree(Rect2i rect, int minSize, Collection<T> contents, Function<T, Rect2i> sizeFactory)
    {
        this(rect, minSize, 0);
        if (!contents.isEmpty()) {
            Objects.requireNonNull(sizeFactory);
            contents.forEach(c -> this.insert(c, sizeFactory.apply(c)));
            this.bake();
        }
    }

    private QuadTree(Rect2i rect, int minSize, int depth)
    {
        super(rect);

        depth++;

        if (depth < MAX_DEPTH && rect.width() > minSize && rect.width() % 2 == 0)
        {
            int childWidth = rect.width() / 2;
            int childHeight = rect.height() / 2;

            if (rect.width() == rect.height() / 2)
            {
                child0 = new QuadTree<>(new Rect2i(rect.x(), rect.y(), rect.width(), childHeight), minSize, depth);
                child1 = null;
                child2 = null;
                child3 = new QuadTree<>(new Rect2i(rect.x(), rect.y() + childHeight, rect.width(), childHeight), minSize, depth);
            }
            else if (rect.height() == rect.width() / 2)
            {
                child0 = new QuadTree<>(new Rect2i(rect.x(), rect.y(), childWidth, rect.height()), minSize, depth);
                child1 = new QuadTree<>(new Rect2i(rect.x() + childWidth, rect.y(), childWidth, rect.height()), minSize, depth);
                child2 = null;
                child3 = null;
            }
            else
            {
                child0 = new QuadTree<>(new Rect2i(rect.x(), rect.y(), childWidth, childHeight), minSize, depth);
                child1 = new QuadTree<>(new Rect2i(rect.x() + childWidth, rect.y(), childWidth, childHeight), minSize, depth);
                child2 = new QuadTree<>(new Rect2i(rect.x() + childWidth, rect.y() + childHeight, childWidth, childHeight), minSize, depth);
                child3 = new QuadTree<>(new Rect2i(rect.x(), rect.y() + childHeight, childWidth, childHeight), minSize, depth);
            }
        }
        else
        {
            child0 = null;
            child1 = null;
            child2 = null;
            child3 = null;
        }
    }

    private void bake() {
        // Postorder traversal
        if (child0 != null) {
            child0.bake();
            if (child0.isEmpty()) {
                child0 = null;
            }
        }
        if (child1 != null) {
            child1.bake();
            if (child1.isEmpty()) {
                child1 = null;
            }
        }
        if (child2 != null) {
            child2.bake();
            if (child2.isEmpty()) {
                child2 = null;
            }
        }
        if (child3 != null) {
            child3.bake();
            if (child3.isEmpty()) {
                child3 = null;
            }
        }
    }

    private boolean isEmpty() {
        return entries == null && child0 == null && child1 == null && child2 == null && child3 == null;
    }

    private boolean tryChildInsert(QuadTree<T> child, T item, Rect2i size) {
        if (child != null && child.contains(size)) {
            child.insert(item, size);
            return true;
        } else {
            return false;
        }
    }

    private void insert(T item, Rect2i size)
    {
        if (tryChildInsert(child0, item, size)) return;
        if (tryChildInsert(child1, item, size)) return;
        if (tryChildInsert(child2, item, size)) return;
        if (tryChildInsert(child3, item, size)) return;

        if (entries == null) {
            entries = new ArrayList<>();
        }

        entries.add(new Entry<>(size, item));
    }

    private T tryFind(QuadTree<T> child, int x, int y) {
        if (child != null) {
            return child.find(x, y);
        } else {
            return null;
        }
    }

    public T find(int x, int y) {
        if (!this.contains(x, y)) {
            return null;
        }

        if (entries != null)
        {
            for (Entry<T> e : entries)
            {
                if (e.contains(x, y))
                {
                    return e.item;
                }
            }
        }

        T childItem;
        childItem = tryFind(child0, x, y);
        if (childItem != null) return childItem;
        childItem = tryFind(child1, x, y);
        if (childItem != null) return childItem;
        childItem = tryFind(child2, x, y);
        if (childItem != null) return childItem;
        childItem = tryFind(child3, x, y);
        if (childItem != null) return childItem;

        return null;
    }

    private static class Entry<T> extends Rect2i {
        final T item;

        public Entry(Rect2i size, T item) {
            super(size);
            this.item = item;
        }
    }
}

