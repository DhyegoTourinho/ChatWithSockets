package Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

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
        groupMembers.add(member);
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

    @Override
    public String toString() {
        return "Nome do grupo: " + getName() + "\n"
               + "Criador do grupo: " + getCreator() + "\n"
               + "Membros: " + groupMembers.toString();
    }
}
