package crdt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CRDTChar {

    public String value;                 // Actual character
    public List<Integer> id;            // Position ID
    public String siteId;               // Inserting site/client
    public long timestamp;              // Logical timestamp
    public boolean tombstone = false;   // Deletion marker

    // Tree structure
    public CRDTChar parent;             
    public List<CRDTChar> children;     

    public CRDTChar(String value, List<Integer> id, String siteId) {
        this.value = value;
        this.id = id;
        this.siteId = siteId;
        this.timestamp = System.currentTimeMillis();
        this.children = new ArrayList<>();
    }

    // Ordering logic for CRDT insertion
    public int compareTo(CRDTChar other) {
        int minLength = Math.min(this.id.size(), other.id.size());

        for (int i = 0; i < minLength; i++) {
            int diff = this.id.get(i) - other.id.get(i);
            if (diff != 0) return diff;
        }

        if (this.id.size() != other.id.size()) {
            return this.id.size() - other.id.size();
        }

        int siteDiff = this.siteId.compareTo(other.siteId);
        if (siteDiff != 0) return siteDiff;

        return Long.compare(this.timestamp, other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CRDTChar)) return false;
        CRDTChar other = (CRDTChar) o;
        return id.equals(other.id) && siteId.equals(other.siteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, siteId);
    }

    @Override
    public String toString() {
        return value + " (" + id + ")";
    }

    public void addChild(CRDTChar child) {
        this.children.add(child);
        child.parent = this;
    }
}
