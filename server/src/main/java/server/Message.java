package server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable message class for storing messages that need to be
 * delivered to disconnected clients upon reconnection
 */
public class Message {
    private final String type;
    private final List<Object> parameters = new ArrayList<>();

    public Message(String type) {
        this.type = type;
    }

    public void addParam(Object param) {
        parameters.add(param);
    }

    public void sendTo(DataOutputStream out) throws IOException {
        out.writeUTF(type);

        switch (type) {
            case "edit":
                out.writeInt((Integer) parameters.get(0)); // offset
                out.writeUTF((String) parameters.get(1)); // inserted text
                out.writeInt((Integer) parameters.get(2)); // deletedLength
                break;

            case "crdt_insert":
                out.writeUTF((String) parameters.get(0)); // value
                List<Integer> insertId = (List<Integer>) parameters.get(1);
                out.writeInt(insertId.size());
                for (Integer i : insertId) {
                    out.writeInt(i);
                }
                out.writeUTF((String) parameters.get(2)); // site
                break;

            case "crdt_delete":
                List<Integer> deleteId = (List<Integer>) parameters.get(0);
                out.writeInt(deleteId.size());
                for (Integer i : deleteId) {
                    out.writeInt(i);
                }
                out.writeUTF((String) parameters.get(1)); // site
                break;

            case "cursor_update":
                out.writeInt((Integer) parameters.get(0)); // userId
                List<Integer> cursorId = (List<Integer>) parameters.get(1);
                out.writeInt(cursorId.size());
                for (Integer i : cursorId) {
                    out.writeInt(i);
                }
                out.writeUTF((String) parameters.get(2)); // color
                break;

            case "remove_cursor":
                out.writeInt((Integer) parameters.get(0)); // userId
                break;
        }
    }

    public static Message createInsertMessage(String value, List<Integer> id, String site) {
        Message msg = new Message("crdt_insert");
        msg.addParam(value);
        msg.addParam(id);
        msg.addParam(site);
        return msg;
    }

    public static Message createDeleteMessage(List<Integer> id, String site) {
        Message msg = new Message("crdt_delete");
        msg.addParam(id);
        msg.addParam(site);
        return msg;
    }

    public static Message createEditMessage(int offset, String inserted, int deletedLength) {
        Message msg = new Message("edit");
        msg.addParam(offset);
        msg.addParam(inserted);
        msg.addParam(deletedLength);
        return msg;
    }

    public static Message createCursorUpdateMessage(int userId, List<Integer> id, String color) {
        Message msg = new Message("cursor_update");
        msg.addParam(userId);
        msg.addParam(id);
        msg.addParam(color);
        return msg;
    }

    public static Message createRemoveCursorMessage(int userId) {
        Message msg = new Message("remove_cursor");
        msg.addParam(userId);
        return msg;
    }
}