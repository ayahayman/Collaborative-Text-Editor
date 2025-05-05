package crdt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CRDTDocument {

    private final CRDTChar root; // Virtual root node
    private final String siteId;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CRDTDocument(String siteId) {
        this.siteId = siteId;
        this.root = new CRDTChar("^", new ArrayList<>(), "root");
    }

    // Insert a new character after the node with the given ID
    public CRDTChar localInsert(List<Integer> parentId, String value) {
        lock.writeLock().lock();
        try {
            CRDTChar parent = findNodeById(parentId, root);
            if (parent == null) parent = root;

            List<Integer> newId = new ArrayList<>(parent.id);
            newId.add(generateRandomDigit());

            CRDTChar newChar = new CRDTChar(value, newId, siteId);
            parent.addChild(newChar);
            return newChar;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Handle a remote insert by reconstructing the tree path
    public void remoteInsert(CRDTChar crdtChar) {
        lock.writeLock().lock();
        try {
            CRDTChar parent = findParentForRemoteInsert(crdtChar);
            if (parent != null && !parent.children.contains(crdtChar)) {
                parent.addChild(crdtChar);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Soft deletion using tombstone flag
    public boolean deleteById(List<Integer> id, String originSite) {
        lock.writeLock().lock();
        try {
            CRDTChar target = findNodeById(id, root);
            if (target != null && target.siteId.equals(originSite)) {
                target.tombstone = true;
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Get plain text representation
    public String toPlainText() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            buildText(root, sb);
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Recursively build the text
    private void buildText(CRDTChar node, StringBuilder sb) {
        List<CRDTChar> snapshot = new ArrayList<>(node.children);
        snapshot.sort(CRDTChar::compareTo);
        for (CRDTChar child : snapshot) {
            if (!child.tombstone) {
                sb.append(child.value);
            }
            buildText(child, sb);
        }
    }

    // Find a node in the tree by ID
    private CRDTChar findNodeById(List<Integer> id, CRDTChar current) {
        if (current.id.equals(id)) return current;
        for (CRDTChar child : current.children) {
            CRDTChar result = findNodeById(id, child);
            if (result != null) return result;
        }
        return null;
    }

    // Get parent node from ID prefix
    private CRDTChar findParentForRemoteInsert(CRDTChar crdtChar) {
        if (crdtChar.id.isEmpty()) return root;
        List<Integer> parentId = crdtChar.id.subList(0, crdtChar.id.size() - 1);
        return findNodeById(parentId, root);
    }

    private int generateRandomDigit() {
        return ThreadLocalRandom.current().nextInt(1, 10000);
    }

    public CRDTChar getRoot() {
        return root;
    }
    public List<CRDTChar> getCharList() {
        List<CRDTChar> result = new ArrayList<>();
        traverseTree(root, result);
        return result;
    }
    
    private void traverseTree(CRDTChar node, List<CRDTChar> list) {
        node.children.sort(CRDTChar::compareTo);
        for (CRDTChar child : node.children) {
            if (!child.tombstone) {
                list.add(child);
            }
            traverseTree(child, list);
        }
    }
    
}
