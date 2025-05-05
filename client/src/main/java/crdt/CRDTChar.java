package crdt;

import java.util.List;
import java.util.Objects;

public class CRDTChar implements Comparable<CRDTChar> {
    public final String value;
    public final List<Integer> id;
    public final String siteId;
    public final CRDTChar parent;
    public final long timestamp;

    public CRDTChar(String value, List<Integer> id, String siteId, CRDTChar parent) {
        this.value = value;
        this.id = id;
        this.siteId = siteId;
        this.parent = parent;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
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
        return value;
    }
}
