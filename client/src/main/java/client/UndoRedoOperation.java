package client;

import crdt.CRDTChar;
import java.util.List;

public class UndoRedoOperation {
    private final List<CRDTChar> affectedChars;
    private boolean thisOperationIsForInsertion; // true for undoing insertion and false for undoing deletion
    private boolean isUndo;
    private int caretPosition;

    public UndoRedoOperation(List<CRDTChar> affectedChars, boolean isInsert, boolean isUndo, int caretPosition) {
        this.affectedChars = affectedChars;
        this.thisOperationIsForInsertion = isInsert;
        this.isUndo = isUndo;
        this.caretPosition = caretPosition; // the position to go to
    }

    public List<CRDTChar> getAffectedChars() {
        return affectedChars;
    }

    public boolean thisOperationIsForInsertion() {
        return thisOperationIsForInsertion;
    }

    public boolean isUndo() {
        return isUndo;
    }

    public int caretPosition() {
        return caretPosition;
    }

    public int editLength() {
        return affectedChars.size(); // Returns the number of characters affected by the operation
    } // Returns the number of characters affected by the operation

    public void caretPosition(int caretPosition) {
        this.caretPosition = caretPosition; // Setter for caretPosition, if needed
    } // Setter for caretPosition, if needed

    public void setUndo(boolean isUndo) {
        this.isUndo = isUndo;
    } // Setter for isUndo, if needed

    public void setIsInsert(boolean thisOperationIsForInsertion) {
        this.thisOperationIsForInsertion = thisOperationIsForInsertion;
    } // Setter for isInsert, if needed

    public void FlipType() {
        this.isUndo = !this.isUndo; // Flips the type of operation (insert/delete)

    } // Flips the type of operation (insert/delete)
}
