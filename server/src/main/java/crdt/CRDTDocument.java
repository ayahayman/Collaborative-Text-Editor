
package crdt;
import java.util.*;

public class CRDTDocument {

    private final List<CRDTChar> charList = new ArrayList<>();
    private final String siteId;
    private final Random random = new Random();

    public CRDTDocument(String siteId) {
        this.siteId = siteId;
    }

    // Insert a new character at a visual position
    public CRDTChar localInsert(int index, String value) {
        CRDTChar right = (index >= 0 && index < charList.size()) ? charList.get(index) : null;
        CRDTChar left = (index - 1 >= 0 && index - 1 < charList.size()) ? charList.get(index - 1) : null;

        List<Integer> newId = generateIdBetween(
            left != null ? left.id : new ArrayList<>(),
            right != null ? right.id : new ArrayList<>(),
            0
        );

        CRDTChar newChar = new CRDTChar(value, newId, siteId);
        insertSorted(newChar);
        return newChar;
    }

    // Apply a remote insert    
    public void remoteInsert(CRDTChar crdtChar) {
        if (!charList.contains(crdtChar)) {
            insertSorted(crdtChar);
        }
    }

    // Delete by ID
    public boolean deleteById(List<Integer> id, String originSite) {
        return charList.removeIf(c -> c.id.equals(id) && c.siteId.equals(originSite));
    }

    // Get the string representation of the CRDT document
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (CRDTChar c : charList) {
            sb.append(c.value);
        }
        return sb.toString();
    }

    // Inserts a char in the correct sorted position
    private void insertSorted(CRDTChar newChar) {
        int i = 0;
        while (i < charList.size() && newChar.compareTo(charList.get(i)) > 0) {
            i++;
        }
        charList.add(i, newChar);
    }

    // Generate a new ID between two existing ones
    private List<Integer> generateIdBetween(List<Integer> left, List<Integer> right, int depth) {
        int base = 10; // The base granularity

        int leftId = (left.size() > depth) ? left.get(depth) : 0;
        int rightId = (right.size() > depth) ? right.get(depth) : base;

        if (rightId - leftId > 1) {
            int newId = leftId + 1 + random.nextInt(rightId - leftId - 1);
            List<Integer> newPath = new ArrayList<>(left.subList(0, depth));
            newPath.add(newId);
            return newPath;
        } else {
            List<Integer> prefix = new ArrayList<>(left.subList(0, depth));
            prefix.add(leftId);
            return generateIdBetween(left, right, depth + 1);
        }
    }

    public List<CRDTChar> getCharList() {
        return charList;
    }
}