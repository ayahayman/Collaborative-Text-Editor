package client.documentFrames;

public class DocumentDisplayItem {
    private final String name;
    private final String code;

    public DocumentDisplayItem(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return name; // This is what gets displayed in the JList
    }
}
