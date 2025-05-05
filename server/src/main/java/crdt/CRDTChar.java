package crdt;
import java.util.List;
import java.util.Objects;

public class CRDTChar {
    public String value;         
    public List<Integer> id;     // Virtual position ID
    public String siteId;        // Who inserted it
    public long timestamp;       // Timestamp of creation
    public CRDTChar(String value, List<Integer> id, String siteId) {
        this.value = value;
        this.id = id;
        this.siteId = siteId;
        this.timestamp = System.currentTimeMillis(); // capture when it's created

    }

    // For sorting: smaller IDs come first
    public int compareTo(CRDTChar other) {
        int minLength = Math.min(this.id.size(), other.id.size());
    
        //  Compare each integer in the ID list
        for (int i = 0; i < minLength; i++) {
            int diff = this.id.get(i) - other.id.get(i);
            if (diff != 0) return diff;
        }
    
        //: If one is a prefix of the other (e.g., [5] vs [5,1])
        if (this.id.size() != other.id.size()) {
            return this.id.size() - other.id.size();
        }
    
        // compare siteId 
        int siteDiff = this.siteId.compareTo(other.siteId);
        if (siteDiff != 0) return siteDiff;
        return Long.compare(this.timestamp, other.timestamp);

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