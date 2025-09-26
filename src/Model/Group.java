package Model;

import java.util.ArrayList;
import java.util.List;

public class Group {
    private final String name;
    private final String creator;
    List<String> groupMembers = new ArrayList<>();

    public Group(String name, String creator) {
        this.name = name;
        this.creator = creator;
        groupMembers.add(creator);
    }

    public void add(String member) {
        // Evita duplicatas ao adicionar
        if (!groupMembers.contains(member)) {
            groupMembers.add(member);
        }
    }

    public void remove(String member) {
        groupMembers.remove(member);
    }

    public List<String> getGroupMembers() {
        return groupMembers;
    }

    public String getName(){
        return name;
    }

    public String getCreator(){
        return creator;
    }

    public boolean isMember(String member) {
        return groupMembers.contains(member);
    }

    @Override
    public String toString() {
        return "Nome do grupo: " + getName() + "\n"
                + "Criador do grupo: " + getCreator() + "\n"
                + "Membros/Convidados: " + groupMembers.toString();
    }
}
