# Gitlet - A Simple Version of GitHub

Gitlet is a simple version-control system that is a backup system for related collections of files just like GitHub!

## The function It can provide 

### 1. init
Creates a new Gitlet version-control system in the current directory. This system will automatically start with one commit.

### 2. add
Adds a copy of the file as it currently exists to the add staging area. Staging an already-staged file overwrites the previous entry in the staging area with the new contents.

###3. commit
Saves a snapshot of tracked files in the current commit and staging area so they can be restored later, creating a new commit. The commit is said to be tracking the saved files. 

###4. rm
Unstage the file if it is currently staged for addition. If the file is tracked in the current commit, stage it for removal and remove it from the working directory if the user has not already done so (do not remove it unless it is tracked in the current commit).

###5. log
Starting at the current head commit, display information about each commit backwards along the commit tree until the initial commit, following the first parent commit links, ignoring any second parents found in merge commits. (In regular Git, this is what you get with git log --first-parent). This set of commit nodes is called the commit’s history. For every node in this history, the information it should display is the commit id, the time the commit was made, and the commit message.

###6. global-log
Like log, except displays information about all commits ever made. The order of the commits does not matter. Hint: there is a useful method in gitlet.Utils that will help you iterate over files within a directory.

###7. find
Prints out the ids of all commits that have the given commit message, one per line. If there are multiple such commits, it prints the ids out on separate lines. The commit message is a single operand; to indicate a multiword message, put the operand in quotation marks, as for the commit command below. Hint: the hint for this command is the same as the one for global-log.

