package com.github.crowmisia.closuretable

import java.util.LinkedList

/**
 * 閉包テーブル
 *
 * @param ID IDの型
 */
class ClosureTable<ID: Comparable<ID>> {
    private val nodeHolder: MutableMap<ID, Node<ID>> = mutableMapOf()

    fun addNodes(parentId: ID, vararg childIds: ID) {
        val parent = getNode(parentId)

        childIds
            .asSequence()
            .filter { it != parentId }
            .forEach { parent.addNode(it) }
    }

    fun removeNodes(parentId: ID, vararg childIds: ID) {
        val parent = getNode(parentId)

        childIds
            .asSequence()
            .filter { it != parentId }
            .forEach { parent.removeNode(it) }
    }

    fun calculateDiff(
        beforeTable: List<Path<ID>>,
        addFunc: (Path<ID>) -> Unit,
        removeFunc: (Path<ID>) -> Unit,
        updateFunc: (Path<ID>) -> Unit
    ): List<Path<ID>> {

        val comparator = PathComparator<ID>()
        val beforeSortedTable = beforeTable.sortedWith(comparator)
        val afterSortedTable = nodeHolder.values
            .asSequence()
            .flatMap { it.paths.values.asSequence() }
            .sortedWith(comparator)

        val beforeIt = beforeSortedTable.iterator()
        val afterIt = afterSortedTable.iterator()
        val nextBeforeFunc = { if (beforeIt.hasNext()) beforeIt.next() else null }
        val nextAfterFunc = { if (afterIt.hasNext()) afterIt.next() else null }

        var beforePath = nextBeforeFunc()
        var afterPath = nextAfterFunc()
        while (beforePath != null || afterPath != null) {
            if (beforePath == null) {
                while (afterPath != null) {
                    addFunc(afterPath)
                    afterPath = nextAfterFunc()
                }
                break
            } else if (afterPath == null) {
                while (beforePath != null) {
                    removeFunc(beforePath)
                    beforePath = nextBeforeFunc()
                }
                break
            }

            when {
                beforePath.src < afterPath.src -> {
                    removeFunc(beforePath)
                    beforePath = nextBeforeFunc()
                }
                beforePath.src > afterPath.src -> {
                    addFunc(afterPath)
                    afterPath = nextAfterFunc()
                }
                beforePath.src == afterPath.src -> {
                    when {
                        beforePath.dest < afterPath.dest -> {
                            removeFunc(beforePath)
                            beforePath = nextBeforeFunc()
                        }
                        beforePath.dest > afterPath.dest -> {
                            addFunc(afterPath)
                            afterPath = nextAfterFunc()
                        }
                        beforePath.dest == afterPath.dest -> {
                            if (beforePath.depth != afterPath.depth) {
                                updateFunc(afterPath)
                            }
                            beforePath = nextBeforeFunc()
                            afterPath = nextAfterFunc()
                        }
                    }
                }
            }
        }

        return afterSortedTable.map { it.freeze() }.toList()
    }

    private fun getNode(id: ID) = nodeHolder.getOrPut(id) { Node.newNode(id, nodeHolder) }
}

internal class PathComparator<ID: Comparable<ID>> : Comparator<Path<ID>> {
    override fun compare(o1: Path<ID>, o2: Path<ID>): Int {
        val srcComp = o1.src.compareTo(o2.src)
        if (srcComp != 0) {
            return srcComp
        }
        return o1.dest.compareTo(o2.dest)
    }
}

internal class Node<ID>(
    private val nodeId: ID,
    private val parents: MutableSet<Node<ID>>,
    private val children: MutableSet<Node<ID>>,
    internal val paths: MutableMap<ID, MutablePath<ID>>,
    private val nodeHolder: MutableMap<ID, Node<ID>>
) {
    init {
        addPath(nodeId, 0)
    }

    fun addNode(id: ID) {
        val node = getNode(id)

        // add children
        node.parents.add(this)
        children.add(node)

        // add children for parents
        val queue = LinkedList<Pair<Node<ID>, Int>>()
        val check = HashSet<Node<ID>>()
        queue.add(this to 1)
        check.add(this)

        while (queue.isNotEmpty()) {
            val (currentNode, depth) = queue.poll()
            currentNode.addPath(id, depth, children)

            val nextDepth = depth + 1
            currentNode.parents
                .asSequence()
                .filter { check.add(it) }
                .forEach { queue.add(it to nextDepth) }
        }
    }

    fun removeNode(id: ID) {
        val node = getNode(id)

        // remove children
        node.parents.remove(this)
        children.remove(node)

        // rebuild paths for parents
        val queue = LinkedList<Node<ID>>()
        val check = HashSet<Node<ID>>()
        queue.add(this)
        check.add(this)
        while (queue.isNotEmpty()) {
            val currentNode = queue.poll()

            currentNode.rebuildPath()
            currentNode.parents
                .asSequence()
                .filter { check.add(it) }
                .forEach { queue.add(it) }
        }
    }

    private fun getNode(id: ID): Node<ID> {
        return nodeHolder.getOrPut(id) { newNode(id, nodeHolder) }
    }

    private fun addPath(id: ID, depth: Int, appendChildren: MutableSet<Node<ID>>): Boolean {
        return if (addPath(id, depth) == null) {
            appendChildren
                .asSequence()
                .flatMap { it.paths.values.asSequence() }
                .forEach { addPath(it.dest, it.depth + depth) }
            true
        } else false
    }

    private fun rebuildPath() {
        paths.values
            .asSequence()
            .filter { it.depth > 0 }
            .forEach {
                it.remove = true
                it.resetDepth()
            }

        children
            .asSequence()
            .flatMap { it.paths.values.asSequence() }
            .forEach {
                addPath(it.dest, it.depth + 1)?.run {
                    remove = false
                }
            }

        paths.values.removeIf { it.remove }
    }

    private fun addPath(id: ID, depth: Int): MutablePath<ID>? {
        return paths[id]?.also {
            it.setDepth(depth)
        } ?: run {
            paths[id] = MutablePath(nodeId, id, depth)
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Node<*>) {
            nodeId == other.nodeId
        } else false
    }

    override fun hashCode() = nodeId.hashCode()

    companion object {
        fun <ID> newNode(id: ID, nodeHolder: MutableMap<ID, Node<ID>>): Node<ID> {
            return Node(id, mutableSetOf(), mutableSetOf(), mutableMapOf(), nodeHolder)
        }
    }
}

interface Path<ID> {
    val src: ID
    val dest: ID
    val depth: Int

    fun freeze(): Path<ID>
}

class FrozePath<ID>(
    override val src: ID,
    override val dest: ID,
    override val depth: Int
) : Path<ID> {
    override fun freeze(): Path<ID> = this
}

class MutablePath<ID>(
    override val src: ID,
    override val dest: ID,
    depth: Int
) : Path<ID> {
    internal var remove: Boolean = false

    override var depth = depth
        private set

    fun setDepth(depth: Int) {
        if (this.depth > depth) {
            this.depth = depth
        }
    }

    fun resetDepth() {
        depth = Integer.MAX_VALUE
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Path<*>) {
            dest == other.dest
        } else false
    }

    override fun hashCode() = dest.hashCode()

    override fun freeze(): Path<ID> = FrozePath(src, dest, depth)
}
