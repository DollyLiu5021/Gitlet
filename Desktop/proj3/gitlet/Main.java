package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author
 */
public class Main {

    /** Current Working Directory. */
    public static final File CWD = new File(".");

    /** Main gitlet folder. */
    public static final File GITLET = Utils.join(CWD, "/.gitlet");

    /** Main gitlet folder. */
    public static final File GITLET_META = Utils.join(GITLET, "/metadata");

    /** Gitlet commit folder. */
    public static final File GITLET_COMMITS = Utils.join(GITLET, "/commits");

    /** Gitlet stage_add file. */
    public static final File GITLET_STAGE = Utils.join(GITLET, "/stage");

    /** Gitlet blobs folder. */
    public static final File GITLET_BLOBS = Utils.join(GITLET, "/blobs");

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) throws IOException {
        if (args.length == 0) {
            exitWithError("Please enter a command.");
        }

        switch (args[0]) {
            case "init":
                init(args);
                break;
            case "log":
                log(args);
                break;
            case "add":
                add(args);
                break;
            case "commit":
                commit(args);
                break;
            case "checkout":
                checkout(args);
                break;
            case "global-log":
                globalLog(args);
                break;
            case "find":
                find(args);
                break;
            case "status":
                status(args);
                break;
            case "rm":
                remove(args);
                break;
            case "branch":
                branch(args);
                break;
            case "rm-branch":
                removeBranch(args);
                break;
            case "reset":
                reset(args);
                break;
            case "merge":
                merge(args);
                break;

            default:
                exitWithError("No command with that name exists.");
        }
    }

    public static void init(String[] args) throws IOException {
        validateNumArgs(args, 1);
        if (isInited()) {
            exitWithError("A Gitlet version-control system already exists in the current directory.");
        }

        // Init files and dirs
        GITLET.mkdir();
        GITLET_BLOBS.mkdir();
        GITLET_COMMITS.mkdir();
        GITLET_META.createNewFile();
        GITLET_STAGE.createNewFile();

        // Create new commit
        Commit commit = new Commit("initial commit", null, null);
        String commitUid = Utils.sha1(Utils.serialize(commit));
        commit.saveToFile(commitUid);

        // Set up metadata
        Metadata metadata = new Metadata(commitUid, "master");
        metadata.saveToFile();

        // Set up stage
        Stage stage = new Stage();
        stage.saveToFile();
    }

    public static void log(String[] args) {
        validateNumArgs(args, 1);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        Metadata metadata = Metadata.readFromFile();
        String currentCommitID = metadata.head;
        while (currentCommitID != null) {
            System.out.println("===");
            System.out.println("commit " + currentCommitID);
            Commit curCommit = Commit.readFromFile(currentCommitID);
            // Check if it's a merge commit
            if (curCommit.parent2 != null) {
                System.out.println("Merge: " +
                        curCommit.parent1.substring(0, 7) + " " + curCommit.parent2.substring(0, 7));
            }
            curCommit.printLog();
            currentCommitID = curCommit.parent1;
        }
    }

    public static void add(String[] args) throws IOException {
        validateNumArgs(args, 2);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        String fileName = args[1];
        Metadata metadata = Metadata.readFromFile();
        Stage stage = Stage.readFromFile();
        stage.addFileToStage(fileName, metadata.head);
    }

    public static void remove(String[] args) {
        validateNumArgs(args, 2);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        String fileName = args[1];
        Metadata metadata = Metadata.readFromFile();
        Stage stage = Stage.readFromFile();
        stage.removeFileFromStage(fileName, metadata.head);
    }

    public static void commit(String[] args) {
        if (args.length > 2) {
            exitWithError("Incorrect operands.");
        }
        if (args.length == 1) {
            exitWithError("Please enter a commit message.");
        }
        if (args[1].isBlank()) {
            exitWithError("Please enter a commit message.");
        }
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        // Get current data
        Metadata metadata = Metadata.readFromFile();
        Stage stage = Stage.readFromFile();
        Commit parentCommit = Commit.readFromFile(metadata.head);

        // If nothing to be staged, abort
        if (stage.fileMapAddition.isEmpty() && stage.fileMapDeletion.isEmpty()) {
            exitWithError("No changes added to the commit.");
        }

        // Create new commit and save it to the file
        Commit newCommit = new Commit(args[1], metadata.head, null);
        newCommit.setFileMap(parentCommit.fileMap, stage.fileMapAddition, stage.fileMapDeletion);
        String newCommitUID = Utils.sha1(Utils.serialize(newCommit));
        newCommit.saveToFile(newCommitUID);

        // Update current status and persist
        stage.clearMap();
        stage.saveToFile();

        metadata.head = newCommitUID;
        metadata.branchMap.put(metadata.currentBranch, newCommitUID);
        metadata.saveToFile();
    }

    public static void branch(String[] args) {
        validateNumArgs(args, 2);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        Metadata metadata = Metadata.readFromFile();
        String branchName = args[1];
        if (metadata.branchMap.containsKey(branchName)) {
            exitWithError("A branch with that name already exists.");
        }

        metadata.branchMap.put(branchName, metadata.head);
        metadata.saveToFile();
    }

    public static void removeBranch(String[] args) {
        validateNumArgs(args, 2);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        Metadata metadata = Metadata.readFromFile();
        String branchName = args[1];
        if (!metadata.branchMap.containsKey(branchName)) {
            exitWithError("A branch with that name does not exist.");
        }
        if (branchName.equals(metadata.currentBranch)) {
            exitWithError("Cannot remove the current branch.");
        }

        metadata.branchMap.remove(branchName);
        metadata.saveToFile();
    }

    public static void checkout(String[] args) throws IOException {
        if (args.length > 4 || args.length == 1) {
            exitWithError("Incorrect operands.");
        }
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        // Check out current commit file
        if (args.length == 3) {
            if (!args[1].equals("--")) {
                exitWithError("Incorrect operands.");
            }

            Metadata metadata = Metadata.readFromFile();
            String fileName = args[2];
            checkoutFileWithCommitID(fileName, metadata.head);
        }

        // Check out give commit file
        if (args.length == 4) {
            if (!args[2].equals("--")) {
                exitWithError("Incorrect operands.");
            }

            String commitID = args[1];
            if (commitID.length() < 40) {
                commitID = findFullIDByAbbreviate(commitID);
            }
            String fileName = args[3];
            checkoutFileWithCommitID(fileName, commitID);
        }

        // Check out given branch name
        if (args.length == 2) {
            String branchName = args[1];
            Metadata metadata = Metadata.readFromFile();
            Stage stage = Stage.readFromFile();
            Commit curCommit = Commit.readFromFile(metadata.head);

            // Validation
            if (!metadata.branchMap.containsKey(branchName)) {
                exitWithError("No such branch exists.");
            }
            if (branchName.equals(metadata.currentBranch)) {
                exitWithError("No need to checkout the current branch.");
            }

            Commit targetCommit = Commit.readFromFile(metadata.branchMap.get(branchName));
            checkoutCommitFiles(curCommit, targetCommit);

            // 3. Clear staging area
            stage.clearMap();
            stage.saveToFile();

            // Change commit and branch in metadata
            metadata.currentBranch = branchName;
            metadata.head = metadata.branchMap.get(branchName);
            metadata.saveToFile();
        }
    }

    public static void reset(String[] args) throws IOException {
        validateNumArgs(args, 2);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        String commitID = args[1];
        File targetCommitFile = Utils.join(GITLET_COMMITS, commitID);
        if (!targetCommitFile.exists()) {
            exitWithError("No commit with that id exists.");
        }

        Metadata metadata = Metadata.readFromFile();
        Stage stage = Stage.readFromFile();
        Commit curCommit = Commit.readFromFile(metadata.head);
        Commit targetCommit = Commit.readFromFile(commitID);
        checkoutCommitFiles(curCommit, targetCommit);

        stage.clearMap();
        stage.saveToFile();

        metadata.head = commitID;
        metadata.branchMap.put(metadata.currentBranch, commitID);
        metadata.saveToFile();
    }

    public static void globalLog(String[] args) {
        validateNumArgs(args, 1);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        List<String> commitFileNameList = Utils.plainFilenamesIn(GITLET_COMMITS);
        for (String commitFileName : commitFileNameList) {
            Commit curCommit = Commit.readFromFile(commitFileName);
            System.out.println("===");
            System.out.println("commit " + commitFileName);
            // Check if it's a merge commit
            if (curCommit.parent2 != null) {
                System.out.println("Merge: " +
                        curCommit.parent1.substring(0, 7) + " " + curCommit.parent2.substring(0, 7));
            }
            curCommit.printLog();
        }
    }

    public static void status(String[] args) {
        validateNumArgs(args, 1);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        Metadata metadata = Metadata.readFromFile();
        Stage stage = Stage.readFromFile();
        Commit curCommit = Commit.readFromFile(metadata.head);

        System.out.println("=== Branches ===");
        for (String branchName : metadata.branchMap.keySet().stream().sorted().toList()) {
            if (branchName.equals(metadata.currentBranch)) {
                System.out.println("*" + branchName);
            } else {
                System.out.println(branchName);
            }
        }

        System.out.println();
        System.out.println("=== Staged Files ===");
        for (String fileName : stage.fileMapAddition.keySet().stream().sorted().toList()) {
            System.out.println(fileName);
        }

        System.out.println();
        System.out.println("=== Removed Files ===");
        for (String fileName : stage.fileMapDeletion.keySet().stream().sorted().toList()) {
            System.out.println(fileName);
        }

        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        List<String> modifiedFiles = getModifiedNotStagedFileNames(stage, curCommit);
        for (String fileName : modifiedFiles) {
            System.out.println(fileName);
        }

        System.out.println();
        System.out.println("=== Untracked Files ===");
        List<String> untrackedFiles = getUntrackedFiles(stage, curCommit);
        for (String fileName : untrackedFiles) {
            System.out.println(fileName);
        }
    }

    public static void find(String[] args) {
        validateNumArgs(args, 2);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        List<String> commitFileNameList = Utils.plainFilenamesIn(GITLET_COMMITS);
        boolean isFound = false;
        for (String commitFileName : commitFileNameList) {
            Commit curCommit = Commit.readFromFile(commitFileName);
            if (curCommit.message.equals(args[1])) {
                isFound = true;
                System.out.println(commitFileName);
            }
        }
        if (!isFound) {
            exitWithError("Found no commit with that message.");
        }
    }

    public static void merge(String[] args) throws IOException {
        validateNumArgs(args, 2);
        if (!isInited()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        // Get current data
        String branchName = args[1];
        Metadata metadata = Metadata.readFromFile();
        Stage stage = Stage.readFromFile();
        Commit currentCommit = Commit.readFromFile(metadata.head);

        if (!stage.fileMapAddition.isEmpty() || !stage.fileMapDeletion.isEmpty()) {
            exitWithError("e You have uncommitted changes.");
        }
        if (!metadata.branchMap.containsKey(branchName)) {
            exitWithError("A branch with that name does not exist.");
        }
        if (branchName.equals(metadata.currentBranch)) {
            exitWithError("Cannot merge a branch with itself.");
        }

        String branchCommitID = metadata.branchMap.get(branchName);
        String splitCommitID = findSplitPoint(metadata.head, branchCommitID);

        // If the split point is the same commit as the given branch, then we do nothing
        if (splitCommitID.equals(branchCommitID)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }
        // If the split point is the current branch, then the effect is to check out the given branch
        if (splitCommitID.equals(metadata.head)) {
            checkout(new String[]{"checkout", branchName});
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        // Otherwise
        Commit branchCommit = Commit.readFromFile(branchCommitID);
        Commit splitPointCommit = Commit.readFromFile(splitCommitID);
        for (String targetFileName : branchCommit.fileMap.keySet()) {
            if (splitPointCommit.fileMap.containsKey(targetFileName)
                    && currentCommit.fileMap.containsKey(targetFileName)) {
                // Any files that have been modified in the given branch since the split point,
                // but not modified in the current branch since the split point should be changed to
                // their versions in the given branch
                if (!branchCommit.fileMap.get(targetFileName).
                        equals(splitPointCommit.fileMap.get(targetFileName))
                        && currentCommit.fileMap.get(targetFileName).equals(splitPointCommit.fileMap.get(targetFileName))) {
                    checkout(new String[]{"checkout", branchCommitID, "--", targetFileName});
                    autoStageForAddition(targetFileName, branchCommit.fileMap.get(targetFileName));

                    // Both changed, conflict
                } else if (!branchCommit.fileMap.get(targetFileName).
                        equals(splitPointCommit.fileMap.get(targetFileName))
                        && !currentCommit.fileMap.get(targetFileName).
                        equals(splitPointCommit.fileMap.get(targetFileName))
                        && !branchCommit.fileMap.get(targetFileName).
                        equals(currentCommit.fileMap.get(targetFileName))) {
                    File blobCurFile = Utils.join(GITLET_BLOBS, currentCommit.fileMap.get(targetFileName));
                    File blobBranchFile = Utils.join(GITLET_BLOBS, branchCommit.fileMap.get(targetFileName));
                    conflictSolver(targetFileName,
                            Utils.readContentsAsString(blobCurFile),
                            Utils.readContentsAsString(blobBranchFile));
                    System.out.println("Encountered a merge conflict.");
                }

                // Any files that were not present at the split point and are present
                // only in the given branch should be checked out and staged.
            } else if (!splitPointCommit.fileMap.containsKey(targetFileName)
                    && !currentCommit.fileMap.containsKey(targetFileName)) {
                mergeUntrackFileOverrideCheck(targetFileName, branchCommit.fileMap.get(targetFileName));
                checkout(new String[]{"checkout", branchCommitID, "--", targetFileName});
                autoStageForAddition(targetFileName, branchCommit.fileMap.get(targetFileName));

                // Both changed, conflict
            } else if (!splitPointCommit.fileMap.containsKey(targetFileName)
                    && currentCommit.fileMap.containsKey(targetFileName)
                    && !branchCommit.fileMap.get(targetFileName).
                    equals(currentCommit.fileMap.get(targetFileName))) {
                File blobCurFile = Utils.join(GITLET_BLOBS, currentCommit.fileMap.get(targetFileName));
                File blobBranchFile = Utils.join(GITLET_BLOBS, branchCommit.fileMap.get(targetFileName));
                conflictSolver(targetFileName,
                        Utils.readContentsAsString(blobCurFile),
                        Utils.readContentsAsString(blobBranchFile));
                System.out.println("Encountered a merge conflict.");

                // One changed one deleted, conflict
            } else if (splitPointCommit.fileMap.containsKey(targetFileName)
                    && !currentCommit.fileMap.containsKey(targetFileName)
                    && !branchCommit.fileMap.get(targetFileName).
                    equals(splitPointCommit.fileMap.get(targetFileName))) {
                File blobBranchFile = Utils.join(GITLET_BLOBS, branchCommit.fileMap.get(targetFileName));
                conflictSolver(targetFileName,
                        "",
                        Utils.readContentsAsString(blobBranchFile));
                System.out.println("Encountered a merge conflict.");
            }
        }

        for (String curCommitFileName : currentCommit.fileMap.keySet()) {
            // Any files present at the split point, unmodified in the current branch,
            // and absent in the given branch should be removed (and untracked).
            if (splitPointCommit.fileMap.containsKey(curCommitFileName)) {
                if (currentCommit.fileMap.get(curCommitFileName).
                        equals(splitPointCommit.fileMap.get(curCommitFileName))
                        && !branchCommit.fileMap.containsKey(curCommitFileName)) {
                    autoStageForDeletion(curCommitFileName, currentCommit.fileMap.get(curCommitFileName));

                    // One changed one deleted, conflict
                } else if (!branchCommit.fileMap.containsKey(curCommitFileName)
                        && !currentCommit.fileMap.get(curCommitFileName).
                        equals(splitPointCommit.fileMap.get(curCommitFileName))) {
                    File blobCurFile = Utils.join(GITLET_BLOBS, currentCommit.fileMap.get(curCommitFileName));
                    conflictSolver(curCommitFileName,
                            Utils.readContentsAsString(blobCurFile),
                            "");
                    System.out.println("Encountered a merge conflict.");
                }
            }
        }

        // Create new commit
        stage = Stage.readFromFile();
        String commitMessage = "Merged " + branchName + " into " + metadata.currentBranch + ".";
        Commit mergeCommit = new Commit(commitMessage, metadata.head, branchCommitID);
        mergeCommit.setFileMap(currentCommit.fileMap, stage.fileMapAddition, stage.fileMapDeletion);
        String mergeCommitID = Utils.sha1(Utils.serialize(mergeCommit));
        mergeCommit.saveToFile(mergeCommitID);

        // Update current status and persist
        stage.clearMap();
        stage.saveToFile();

        metadata.head = mergeCommitID;
        metadata.branchMap.put(metadata.currentBranch, mergeCommitID);
        metadata.saveToFile();
    }

    public static String findFullIDByAbbreviate(String abbreviate) {
        List<String> commitFileNameList = Utils.plainFilenamesIn(GITLET_COMMITS);
        for (String commitFileName : commitFileNameList) {
            if (commitFileName.startsWith(abbreviate)) {
                return commitFileName;
            }
        }
        return abbreviate;
    }

    public static void autoStageForAddition(String fileName, String contentSHA) {
        Stage stage = Stage.readFromFile();
        stage.fileMapAddition.put(fileName, contentSHA);
        stage.saveToFile();
    }

    public static void autoStageForDeletion(String fileName, String contentSHA) {
        File cwdFile = Utils.join(CWD, fileName);
        if (cwdFile.exists()) {
            cwdFile.delete();
        }
        Stage stage = Stage.readFromFile();
        stage.fileMapDeletion.put(fileName, contentSHA);
        stage.saveToFile();
    }

    public static void conflictSolver(String fileName, String contentFromHead, String contentFromBranch) throws IOException {
        File cwdFile = Utils.join(CWD, fileName);
        if (!cwdFile.exists()) {
            cwdFile.createNewFile();
        }
        String resultContent = "<<<<<<< HEAD\n" +
                contentFromHead + "=======\n" + contentFromBranch + ">>>>>>>\n";
        Utils.writeContents(cwdFile, resultContent.getBytes());

        String contentSHA = Utils.sha1(resultContent.getBytes());
        autoStageForAddition(fileName, contentSHA);
    }

    public static void mergeUntrackFileOverrideCheck(String fileName, String contentSHA) {
        File cwdFile = Utils.join(CWD, fileName);
        if (cwdFile.exists()) {
            String cwdSHA = Utils.sha1(Utils.readContents(cwdFile));
            if (!cwdSHA.equals(contentSHA)) {
                exitWithError("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
    }

    public static List<String> getModifiedNotStagedFileNames(Stage stage, Commit commit) {
        List<String> resultList = new ArrayList<>();

        // Iterate over tracked files
        for (String trackedFile : commit.fileMap.keySet()) {
            File cwdFile = Utils.join(CWD, trackedFile);
            if (!cwdFile.exists()
                    && !stage.fileMapDeletion.containsKey(trackedFile)) {
                resultList.add(trackedFile + " (deleted)");
            }
            if (cwdFile.exists()) {
                String contentSHA = Utils.sha1(Utils.readContents(cwdFile));
                if (!contentSHA.equals(commit.fileMap.get(trackedFile))
                        && !stage.fileMapAddition.containsKey(trackedFile)) {
                    resultList.add(trackedFile + " (modified)");
                }
            }
        }

        // Iterate over staged for addition
        for (String addedFile : stage.fileMapAddition.keySet()) {
            File cwdFile = Utils.join(CWD, addedFile);
            if (!cwdFile.exists()) {
                resultList.add(addedFile + " (deleted)");
            } else {
                String contentSHA = Utils.sha1(Utils.readContents(cwdFile));
                if (!contentSHA.equals(stage.fileMapAddition.get(addedFile))) {
                    resultList.add(addedFile + " (modified)");
                }
            }
        }

        Collections.sort(resultList);
        return resultList;
    }

    public static List<String> getUntrackedFiles(Stage stage, Commit commit) {
        List<String> resultList = new ArrayList<>();
        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);
        for (String cwdFile : cwdFiles) {
            if (!stage.fileMapAddition.containsKey(cwdFile) && !commit.fileMap.containsKey(cwdFile)) {
                resultList.add(cwdFile);
            }
        }

        Collections.sort(resultList);
        return resultList;
    }

    public static void checkoutFileWithCommitID(String fileName, String commitID) throws IOException {
        // Check if the commitID exist
        if (!isCommitExist(commitID)) {
            exitWithError("No commit with that id exists.");
        }

        // Get the commit
        Commit curCommit = Commit.readFromFile(commitID);

        // Check if file exist in the commit
        if (!curCommit.fileMap.containsKey(fileName)) {
            exitWithError("File does not exist in that commit.");
        }

        // Restore file back to CWD
        File blobFile = Utils.join(GITLET_BLOBS, curCommit.fileMap.get(fileName));
        File cwdFile = Utils.join(CWD, fileName);
        if (!cwdFile.exists()) {
            cwdFile.createNewFile();
        }
        Utils.writeContents(cwdFile, Utils.readContents(blobFile));
    }

    public static void checkoutCommitFiles(Commit curCommit, Commit targetCommit) throws IOException {
        // Checking
        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);
        for (String cwdFile : cwdFiles) {
            // Not tracked by the current commit
            if (!curCommit.fileMap.containsKey(cwdFile)) {
//                // Not in the target commit as well, will be removed
//                if (!targetCommit.fileMap.containsKey(cwdFile)) {
//                    exitWithError("There is an untracked file in the way; delete it, or add and commit it first.");
//                }
                // In the target commit but the content is different
                String contentSHA = Utils.sha1(Utils.readContents(Utils.join(CWD, cwdFile)));
                if (targetCommit.fileMap.containsKey(cwdFile)
                        && !contentSHA.equals(targetCommit.fileMap.get(cwdFile))) {
                    exitWithError("There is an untracked file in the way; delete it, or add and commit it first.");
                }
            }
        }

        // Do the actual checkout work
        // 1. take all files in the target commit to CWD
        for(String targetFile : targetCommit.fileMap.keySet()) {
            File cwdFile = Utils.join(CWD, targetFile);
            if (!cwdFile.exists()) {
                cwdFile.createNewFile();
            }
            Utils.writeContents(cwdFile,
                    Utils.readContents(Utils.join(GITLET_BLOBS,
                            targetCommit.fileMap.get(targetFile))));
        }

        // 2. Remove files tracked in the current commit but not in the target commit
        for(String curFile : curCommit.fileMap.keySet()) {
            File cwdFile = Utils.join(CWD, curFile);
            if (cwdFile.exists() && !targetCommit.fileMap.containsKey(curFile)) {
                cwdFile.delete();
            }
        }
    }

    public static String findSplitPoint(String commitID1, String commitID2) {
        HashSet<String> commitSet = new HashSet<>();
        while (commitID1 != null) {
            commitSet.add(commitID1);
            Commit curCommit = Commit.readFromFile(commitID1);
            if (curCommit.parent2 != null) {
                commitSet.add(curCommit.parent2);
            }
            commitID1 = curCommit.parent1;
        }

        while (commitID2 != null) {
            if (commitSet.contains(commitID2)) {
                return commitID2;
            }
            Commit curCommit = Commit.readFromFile(commitID2);
            commitID2 = curCommit.parent1;
        }
        return null;
    }

    public static boolean isCommitExist(String commitID) {
        File commitFile = Utils.join(GITLET_COMMITS, commitID);
        return commitFile.exists();
    }

    public static boolean isInited() {
        return GITLET.exists();
    }

    /**
     * Prints out MESSAGE and exits with error code -1.
     * Note:
     *     The functionality for erroring/exit codes is different within Gitlet
     *     so DO NOT use this as a reference.
     *     Refer to the spec for more information.
     * @param message message to print
     */
    public static void exitWithError(String message) {
        if (message != null && !message.equals("")) {
            System.out.println(message);
        }
        System.exit(0);
    }

    /**
     * Checks the number of arguments versus the expected number,
     * throws a RuntimeException if they do not match.
     *
     * @param args Argument array from command line
     * @param n Number of expected arguments
     */
    public static void validateNumArgs(String[] args, int n) {
        if (args.length != n) {
            exitWithError("Incorrect operands.");
        }
    }
}
