package crdt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

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

    private List<Integer> generateIdBetween(List<Integer> id1, List<Integer> id2, int depth) {
        int base = (int) Math.pow(2, depth + 4); // growth factor

        int digit1 = (id1.size() > depth) ? id1.get(depth) : 0;
        int digit2 = (id2.size() > depth) ? id2.get(depth) : base;

        List<Integer> newId = new ArrayList<>();

        // If digits are too close to generate a new unique digit
        if (digit2 - digit1 < 2) {
            if (depth > 100) {
                System.err.println("⚠️ generateIdBetween() recursion too deep. Returning fallback ID.");
                // Fallback: return a unique ID including siteId and a random number
                newId.add(digit1);  // Inherit the last usable digit
                newId.add(siteId.hashCode());  // Make sure ID is unique per client
                newId.add(ThreadLocalRandom.current().nextInt(0, 100000)); // In case of same siteId, add randomness
                return newId;
            }

            // Prepare tails early for recursion
            List<Integer> tailId1 = (id1.size() > depth + 1) ? id1.subList(depth + 1, id1.size()) : new ArrayList<>();
            List<Integer> tailId2 = (id2.size() > depth + 1) ? id2.subList(depth + 1, id2.size()) : new ArrayList<>();

            newId.add(digit1);
            return generateIdBetween(tailId1, tailId2, depth + 1);
        }

        int newDigit = digit1 + 1 + (int) (Math.random() * (digit2 - digit1 - 1));
        newId.add(newDigit);
        return newId;
    }

    public List<CRDTChar> getCharList() {
        return charList;
    }
}
