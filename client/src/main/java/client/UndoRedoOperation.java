package client;

import crdt.CRDTChar;
import java.util.List;

public class UndoRedoOperation {
    private final List<CRDTChar> affectedChars;
    private boolean isInsert;
    private boolean isUndo;

    public UndoRedoOperation(List<CRDTChar> affectedChars, boolean isInsert, boolean isUndo) {
        this.affectedChars = affectedChars;
        this.isInsert = isInsert;
        this.isUndo = isUndo; // Default to false for new operations
    }

    public List<CRDTChar> getAffectedChars() {
        return affectedChars;
    }

    public boolean isInsert() {
        return isInsert;
    }

    public boolean isUndo() {
        return isUndo;
    }

    public void setUndo(boolean isUndo) {
        this.isUndo = isUndo;
    } // Setter for isUndo, if needed

    public void setIsInsert(boolean isInsert) {
        this.isInsert = isInsert;
    } // Setter for isInsert, if needed

    public void FlipType() {
        this.isInsert = !this.isInsert;
        this.isUndo = !this.isUndo; // Flips the type of operation (insert/delete)
    } // Flips the type of operation (insert/delete)
}
