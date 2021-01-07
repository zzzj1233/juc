package com.zzzj.struct;


/**
 * @author Zzzj
 * @create 2021-01-05 15:51
 */
public class BTree<E extends Comparable<E>> {

    private Node root;

    public void add(E e) {
        add(root, e);
    }

    private Node add(Node node, E e) {
        if (node == null) {
            return new Node(e);
        }

        return node;
    }

    private class Node {
        public E leftValue;
        public E middleValue;
        public E rightValue;

        public Node left;
        public Node leftMiddle;
        public Node right;
        public Node rightMiddle;

        public Node(E e) {
            this.leftValue = e;
        }
    }

}
