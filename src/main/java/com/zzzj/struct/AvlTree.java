package com.zzzj.struct;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Zzzj
 * @create 2021-01-04 14:21
 */
public class AvlTree<E extends Comparable<E>> {

    private Node<E> root;
    private int size;

    public int getSize() {
        return size;
    }

    public void add(E e) {
        root = add(root, e);
    }

    public boolean contains(E e) {
        return contains(root, e);
    }

    public void inOrder(Consumer<Node<E>> consumer) {
        inOrder(root, consumer);
    }

    private int getHeight(Node<E> node) {
        if (node == null) {
            return 0;
        }
        return node.height;
    }

    private int getLevel(Node<E> node) {
        if (node == null) {
            return 0;
        }
        return Math.abs(getHeight(node.left) - getHeight(node.right));
    }

    private void inOrder(Node<E> node, Consumer<Node<E>> consumer) {
        if (node == null) {
            return;
        }

        inOrder(node.left, consumer);
        consumer.accept(node);
        inOrder(node.right, consumer);
    }

    public void levelOrder(Consumer<Node<E>> consumer) {
        if (root == null) {
            return;
        }
        Deque<Node<E>> queue = new LinkedList<>();
        queue.push(root);

        Node<E> pop;

        while (!queue.isEmpty()) {
            pop = queue.pop();
            consumer.accept(pop);
            if (pop.left != null) {
                queue.addLast(pop.left);
            }
            if (pop.right != null) {
                queue.addLast(pop.right);
            }
        }

    }

    public boolean isBST() {
        ArrayList<E> list = new ArrayList<>(size);

        inOrder(node -> {
            list.add(node.e);
        });

        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).compareTo(list.get(i - 1)) < 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isBalanced() {
        AtomicBoolean ret = new AtomicBoolean(true);

        levelOrder(node -> {
            if (getLevel(node) > 1) {
                ret.set(false);
            }
        });

        return ret.get();
    }

    public void remove(E e) {
        if (root == null) {
            return;
        }
        root = remove(root, e);
    }

    private void computeHeight(Node<E> node) {
        if (node == null) {
            return;
        }
        node.height = 1 + Math.max(getHeight(node.left), getHeight(node.right));
    }


    private Node<E> remove(Node<E> node, E e) {

        if (node == null) {
            return null;
        }

        if (node.e.equals(e)) {
            size--;
            if (node.right == null) {
                return node.left;
            } else if (node.left == null) {
                return node.right;
            } else {
                Node<E> min = min(node.right);
                node.right = rotateIfNecessary(removeMin(node.right));
                min.left = node.left;
                min.right = node.right;
                return rotateIfNecessary(min);
            }
        } else if (node.e.compareTo(e) < 0) {
            node.right = remove(node.right, e);
        } else {
            node.left = remove(node.left, e);
        }

        return rotateIfNecessary(node);
    }

    public void removeMax() {
        if (root == null) {
            return;
        }
        root = removeMax(root);
    }

    public void removeMin() {
        if (root == null) {
            return;
        }
        root = removeMin(root);
    }

    private Node<E> removeMax(Node<E> node) {
        if (node.right == null) {
            return node.left;
        }
        node.right = removeMax(node.right);
        return node;
    }

    private Node<E> removeMin(Node<E> node) {
        if (node.left == null) {
            return node.right;
        }
        node.left = removeMin(node.left);
        return node;
    }

    public E max() {
        if (root == null) {
            return null;
        }
        return max(root).e;
    }

    private Node<E> max(Node<E> node) {
        if (node.right == null) {
            return node;
        }
        return max(node.right);
    }

    public E min() {
        if (root == null) {
            return null;
        }
        return min(root).e;
    }

    private Node<E> min(Node<E> node) {
        if (node.left == null) {
            return node;
        }
        return min(node.left);
    }

    private Node<E> add(Node<E> node, E e) {
        if (node == null) {
            size++;
            return new Node<>(e);
        }

        if (node.e.compareTo(e) > 0) {
            node.left = add(node.left, e);
        } else if (node.e.compareTo(e) < 0) {
            node.right = add(node.right, e);
        }

        return rotateIfNecessary(node);
    }

    private Node<E> rotateIfNecessary(Node<E> node) {
        if (node == null) {
            return null;
        }

        node.height = 1 + Math.max(getHeight(node.left), getHeight(node.right));

        if (getLevel(node) > 1 && (getHeight(node.left) > getHeight(node.right))) {
            Node<E> left = node.left;

            if (getHeight(left.right) > getHeight(left.left)) {
                node.left = leftRotate(left);
                return rightRotate(node);
            } else {
                return rightRotate(node);
            }
        } else if (getLevel(node) > 1 && (getHeight(node.right) > getHeight(node.left))) {
            Node<E> right = node.right;
            if (getHeight(right.left) > getHeight(right.right)) {
                node.right = rightRotate(right);
                return leftRotate(node);
            } else {
                return leftRotate(node);
            }
        }

        return node;
    }

    private Node<E> leftRotate(Node<E> node) {
        Node<E> right = node.right;
        node.right = right.left;
        right.left = node;

        computeHeight(node);
        computeHeight(right);
        return right;
    }

    private Node<E> rightRotate(Node<E> node) {
        Node<E> left = node.left;
        node.left = left.right;
        left.right = node;
        computeHeight(node);
        computeHeight(left);
        return left;
    }

    private boolean contains(Node<E> node, E e) {
        if (node == null) {
            return false;
        }

        if (node.e.compareTo(e) == 0) {
            return true;
        } else if (node.e.compareTo(e) < 0) {
            return contains(node.left.e);
        } else {
            return contains(node.right.e);
        }
    }

    private void preOrder(Node<E> node, Consumer<Node<E>> consumer) {
        if (node == null) {
            return;
        }

        consumer.accept(node);

        preOrder(node.left, consumer);
        preOrder(node.right, consumer);
    }


    private static class Node<E extends Comparable<E>> {
        public E e;
        public Node<E> left;
        public Node<E> right;
        public int height;

        public Node(E e) {
            this.e = e;
            this.height = 1;
        }

    }

    public static void main(String[] args) {

        AvlTree<Integer> bst;

        int nums[] = new int[10];

        bst = new AvlTree<>();

        Random random = new Random();

        for (int i = 0; i < nums.length; i++) {
            nums[i] = random.nextInt(nums.length * 10);
        }


        for (int num : nums) {
            bst.add(num);
        }

        for (int i = 0; i < 500; i++) {
            bst.remove(random.nextInt(nums.length * 10));
        }


        System.out.println("isBalanced : " + bst.isBalanced());
        System.out.println(bst.isBST());

        System.out.println(bst.size);

    }

}