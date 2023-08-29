package gitlet;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static gitlet.Main.GITLET_META;

public class Metadata implements Dumpable {

    public String head;

    public String currentBranch;

    public Map<String, String> branchMap;

    public Metadata() {};

    public Metadata(String head, String currentBranch) {
        this.head = head;
        this.currentBranch = currentBranch;
        branchMap = new HashMap<>();
        branchMap.put(currentBranch, head);
    }

    public void createNewBranch(String branchName) {

    }

    public void saveToFile() {
        File metadataFile = Utils.join(GITLET_META);
        Metadata newMetadata = new Metadata();
        newMetadata.head = head;
        newMetadata.currentBranch = currentBranch;
        newMetadata.branchMap = branchMap;
        Utils.writeObject(metadataFile, newMetadata);
    }
    
    public static Metadata readFromFile() {
        File metadataFile = Utils.join(GITLET_META);
        return Utils.readObject(metadataFile, Metadata.class);
    }

    @Override
    public void dump() {
        System.out.println("HEAD: " + head);
        System.out.println("Current Branch: " + currentBranch);
        System.out.println("Branch Map: " + branchMap.toString());
    }
}
