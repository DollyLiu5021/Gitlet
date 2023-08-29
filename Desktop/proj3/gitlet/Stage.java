package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static gitlet.Main.*;

public class Stage implements Dumpable {

    public Map<String, String> fileMapAddition;

    public Map<String, String> fileMapDeletion;

    public Stage() {
        fileMapAddition = new HashMap<>();
        fileMapDeletion = new HashMap<>();
    }

    public void addFileToStage(String fileName, String head) throws IOException {
        File contentFile = Utils.join(CWD, fileName);
        if (!contentFile.exists()) {
            exitWithError("File does not exist.");
        }

        // Get current commit
        Commit curCommit = Commit.readFromFile(head);

        // Hash the content of this file
        String contentFileHash = Utils.sha1(Utils.readContents(contentFile));


        // For removing case
        if (fileMapDeletion.containsKey(fileName)) {
            // Restore file back to CWD
            File blobFile = Utils.join(GITLET_BLOBS, fileMapDeletion.get(fileName));
            File cwdFile = Utils.join(CWD, fileName);
            if (!cwdFile.exists()) {
                cwdFile.createNewFile();
            }
            Utils.writeContents(cwdFile, Utils.readContents(blobFile));
            fileMapDeletion.remove(fileName);
            saveToFile();
            return;
        }

        // If current commit include this file and it's identical to the one in CWD,
        // Remove it from fileMapAddition
        if (curCommit.fileMap.containsKey(fileName)
                && curCommit.fileMap.get(fileName).equals(contentFileHash)) {
            fileMapAddition.remove(fileName);
        } else {
            // If the file is new/changed, stage it
            fileMapAddition.put(fileName, contentFileHash);

            // Create the file if it's not exist in the blobs
            File blobFile = Utils.join(GITLET_BLOBS, contentFileHash);
            if (!blobFile.exists()) {
                blobFile.createNewFile();
                Utils.writeContents(blobFile, Utils.readContents(contentFile));
            }
        }

        // Save stage back to file
        saveToFile();
    }

    public void removeFileFromStage(String fileName, String head) {
        boolean removeFlag = false;

        // Un-stage the file if it is currently staged for addition
        if (fileMapAddition.containsKey(fileName)) {
            fileMapAddition.remove(fileName);
            removeFlag = true;
        }

        Commit curCommit = Commit.readFromFile(head);
        // If file is tracked in the current commit
        // 1. stage it for removal
        // 2. remove the file from CWD
        if (curCommit.fileMap.containsKey(fileName)) {
            fileMapDeletion.put(fileName, curCommit.fileMap.get(fileName));
            File cwdFile = Utils.join(CWD, fileName);
            if (cwdFile.exists()) {
                cwdFile.delete();
            }
            removeFlag = true;
        }

        if (!removeFlag) {
            exitWithError("No reason to remove the file.");
        }

        // Save stage back to file
        saveToFile();
    }

    public void clearMap() {
        fileMapAddition.clear();
        fileMapDeletion.clear();
    }

    public void saveToFile() {
        File stageFile = Utils.join(GITLET_STAGE);
        Stage newStage = new Stage();
        newStage.fileMapAddition = fileMapAddition;
        newStage.fileMapDeletion = fileMapDeletion;
        Utils.writeObject(stageFile, newStage);
    }

    public static Stage readFromFile() {
        File stageFile = Utils.join(GITLET_STAGE);
        return Utils.readObject(stageFile, Stage.class);
    }

    @Override
    public void dump() {
        System.out.println("Stage for addition: " + fileMapAddition.toString());
        System.out.println("Stage for deletion: " + fileMapDeletion.toString());
    }
}
