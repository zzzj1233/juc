package com.zzzj.struct;

import java.util.ArrayList;
import java.util.Random;

/**
 * @author Zzzj
 * @create 2021-01-02 15:03
 */
public class MinHeap<E extends Comparable<E>> {

    public ArrayList<E> data = new ArrayList<>();


    public int getParent(int i) {
        return (i - 1) / 2;
    }

    public int getLeftChild(int i) {
        return i * 2 + 1;
    }

    public int getRightChild(int i) {
        return i * 2 + 2;
    }

    public void add(E e) {
        data.add(e);
        siftUp(data.size() - 1);
    }

    private void swap(int i, int j) {
        E temp = data.get(i);
        data.set(i, data.get(j));
        data.set(j, temp);
    }

    private void siftUp(int idx) {
        int parent = getParent(idx);

        if (parent < 0) {
            return;
        }

        while (data.get(idx).compareTo(data.get(parent)) < 0) {
            // swap
            swap(idx, parent);

            idx = parent;
            parent = getParent(parent);

            if (parent < 0) {
                return;
            }
        }
    }

    public E extractMax() {
        E max = findMax();

        if (max == null) {
            return null;
        }

        if (data.size() == 1) {
            data.remove(0);
            return max;
        }

        swap(0, data.size() - 1);

        data.remove(data.size() - 1);

        siftDown(0);

        return max;
    }

    private void siftDown(int idx) {

        int leftChildIdx = getLeftChild(idx);
        int rightChildIdx = getRightChild(idx);
        E current = data.get(idx);
        E left = null;
        E right = null;

        if (leftChildIdx < data.size()) {
            left = data.get(leftChildIdx);
        }

        if (rightChildIdx < data.size()) {
            right = data.get(rightChildIdx);
        }

        boolean transferLeft = false;
        boolean transferRight = false;

        if (left != null && current.compareTo(left) > 0) {
            transferLeft = true;
        }

        if (right != null && current.compareTo(right) > 0) {
            transferRight = true;
        }

        if (!transferLeft && !transferRight) {
            return;
        }

        if (transferLeft && (right == null || left.compareTo(right) < 0)) {
            swap(idx, leftChildIdx);
            siftDown(leftChildIdx);
        } else {
            swap(idx, rightChildIdx);
            siftDown(rightChildIdx);
        }

    }

    public E findMax() {
        if (data.isEmpty()) {
            return null;
        }
        return data.get(0);
    }

    public static void main(String[] args) {
        MinHeap<Integer> heap = new MinHeap<>();

        Random random = new Random();

        for (int i = 0; i < 10; i++) {
            heap.add(random.nextInt(999));
        }

        System.out.println(heap.data);

        for (int i = 0; i < 10; i++) {
            System.out.println(heap.extractMax());
        }

    }

}
