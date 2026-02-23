package com.dp.collections;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class FixedCircularArray<E> implements Iterable<E> {
    private static final int INITIAL_SIZE = 16;
    public final int capacity;
    private Object[] array;
    private int head;
    private int next;


    public FixedCircularArray(int capacity) {
        this(capacity, INITIAL_SIZE);
    }
    public FixedCircularArray(int capacity, int initialSize) {
        if(capacity <= 0) {
            throw new IllegalStateException("capacity (= " + capacity + ") must be > 0");
        }
        this.capacity = capacity;
        int alloc = Math.min(capacity, Math.max(1, initialSize));
        this.array = new Object[alloc];
        resetHead();
    }

    public int size() {
        if(head == -1) return 0;
        if(next <= head) return (capacity - head) + next;
        return next - head;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean isNotEmpty() {
        return size() != 0;
    }

    public boolean isFull() {
        return size() == capacity;
    }

    public void addAll(Iterable<? extends E> list) {
        for (E e : list) add(e);
    }

    public void add(E e) {
        tryGrow();

        if(head < 0) {
            head = 0;
        } else if (next == head) {
            head = (head + 1) % capacity;
        }

        array[next] = e;
        next = (next + 1) % capacity;
    }

    public E remove(E e) {
        if (e == null) return null;
        int idx = indexOf(e);
        if (idx != -1) return removeAt(idx);
        return null;
    }

    @SuppressWarnings("unchecked")
    public E removeAt(int index) {
        checkIOBAndTrow(index);

        int sz = size();
        int physicalIndex = (head + index) % capacity;
        E result = (E) array[physicalIndex];

        for (int i = index; i < sz - 1; i++) {
            array[(head + i) % capacity] = array[(head + i + 1) % capacity];
        }

        array[(head + sz - 1) % capacity] = null;

        next  = (head + sz - 1) % capacity;
        if(next == head) {
            resetHead();
        }

        return result;
    }

    public int indexOf(E e) {
        if(e == null) return -1;

        int sz = size();
        for (int i = 0; i < sz; i++) {
            Object v = array[(head + i) % capacity];
            if (e.equals(v)) return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        checkIOBAndTrow(index);
        return(E) array[(head + index) % capacity];
    }

    public boolean contains(E element) {
        int sz = size();
        for (int i = 0; i < sz; i++) {
            Object v = array[(head + i) % capacity];
            if (element == null) {
                if (v == null) return true;
            } else if (element.equals(v)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        resetHead();
        for (int i = 0; i < array.length; i++) array[i] = null;
    }

    private void checkIOBAndTrow(int index) {
        int sz = size();
        if(index < 0 || index >= sz) {
            throw new IndexOutOfBoundsException("index = " + index + ", size = " + sz);
        }
    }

    private void tryGrow() {
        int sz = size();
        if (sz < array.length || array.length == capacity) return;
        int newSize = Math.min(array.length * 2, capacity);
        Object[] newArray = new Object[newSize];
        System.arraycopy(array, 0, newArray, 0, array.length);
        array = newArray;
    }

    private void resetHead() {
        head = -1;
        next = 0;
    }

    @NonNull
    @Override
    public Iterator<E> iterator() {
        return new FixedCircularArrayIterator();
    }

    private final class FixedCircularArrayIterator implements Iterator<E> {
        private int index = 0;
        @Override
        public boolean hasNext() {
            return index < size();
        }

        @Override
        public E next() {
            if(!hasNext()) throw new NoSuchElementException();
            return get(index++);
        }
    }


}
