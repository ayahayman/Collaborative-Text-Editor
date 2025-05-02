package crdt;
import java.util.List;
import java.util.Objects;

public class CRDTChar {
    public String value;         
    public List<Integer> id;     // Virtual position ID
    public String siteId;        // Who inserted it

    public CRDTChar(String value, List<Integer> id, String siteId) {
        this.value = value;
        this.id = id;
        this.siteId = siteId;
    }

    // For sorting: smaller IDs come first
    public int compareTo(CRDTChar other) {
        int minLength = Math.min(this.id.size(), other.id.size());
        for (int i = 0; i < minLength; i++) {
            int diff = this.id.get(i) - other.id.get(i);
            if (diff != 0) return diff;
        }
        return this.id.size() - other.id.size();
    }

    // For deletion
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
        return value;
    }
}
