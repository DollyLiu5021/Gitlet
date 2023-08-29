package gitlet;

import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static gitlet.Main.GITLET_COMMITS;

public class Commit implements Dumpable {

    public String message;

    public ZonedDateTime timeStamp;

    public String parent1;

    public String parent2;

    public Map<String, String> fileMap;

    public Commit() {};

    public Commit(String message, String parent1, String parent2) {
        this.message = message;
        this.parent1 = parent1;
        this.parent2 = parent2;
        this.timeStamp = ZonedDateTime.now(ZoneId.systemDefault());
        if (this.parent1 == null && this.parent2 == null) {
            this.timeStamp =
                    ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).
                            withZoneSameInstant(ZoneId.systemDefault());
        }
        fileMap = new HashMap<>();
    }

    public void setFileMap(Map<String, String> parentFileMap,
                           Map<String, String> stageForAddition,
                           Map<String, String> stageForDeletion) {
        // By default equals to it's parent
        fileMap = parentFileMap;
        // Set for addition
        for (String key : stageForAddition.keySet()) {
            fileMap.put(key, stageForAddition.get(key));
        }
        // Set for removal
        for (String key : stageForDeletion.keySet()) {
            fileMap.remove(key);
        }
    }

    public String getTimeStampAsString() {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("E MMM d HH:mm:ss yyyy Z");
        return formatter.format(timeStamp);
    }

    public void saveToFile(String fileName) {
        File commitFile = Utils.join(GITLET_COMMITS, fileName);
        Commit newCommit = new Commit();
        newCommit.message = message;
        newCommit.parent1 = parent1;
        newCommit.parent2 = parent2;
        newCommit.timeStamp = timeStamp;
        newCommit.fileMap = fileMap;
        Utils.writeObject(commitFile, newCommit);
    }

    public static Commit readFromFile(String fileName) {
        File commitFile = Utils.join(GITLET_COMMITS, fileName);
        return Utils.readObject(commitFile, Commit.class);
    }

    @Override
    public void dump() {
        System.out.println("Date: " + getTimeStampAsString());
        System.out.println("Message: " + message);
        System.out.println("FileMap: " + fileMap.toString());
    }

    public void printLog() {
        System.out.println("Date: " + getTimeStampAsString());
        System.out.println(message);
        System.out.println();
    }
}
