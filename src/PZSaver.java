import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class PZSaver {
    private static final String BACKUP = "backup";
    private static final String RESTORE = "restore";
    private static final String VERBOSE_FLAG = "--verbose";
    private static final String VERBOSE_SHORT_FLAG = "-v";
    private static final String NOPROMPT_FLAG = "--noprompt";
    private static final String NOPROMPT_SHORT_FLAG = "-np";
    private static final String NEW_FLAG = "--new";
    private static final String NEW_SHORT_FLAG = "-n";
    private static final String TEST_FLAG = "--test";
    private static final String TEST_SHORT_FLAG = "-t";
    private static final String SKIP_FLAG = "--skip";
    private static final String SKIP_SHORT_FLAG = "-s";
    private static final String HELP_FLAG = "--help";
    private static final String HELP_SHORT_FLAG = "-h";

    private final File baseSaveDir;
    private final File backupDir;
    private final boolean verbose;
    private final boolean noPrompt;
    private final boolean createNewBackup;

    public PZSaver(String baseSavePath, String backupPath, boolean verbose, boolean noPrompt, boolean createNewBackup) {
        this.baseSaveDir = new File(baseSavePath);
        this.backupDir = new File(backupPath);
        this.verbose = verbose;
        this.noPrompt = noPrompt;
        this.createNewBackup = createNewBackup;
    }

    private boolean getApproval(String operationType, boolean testRequested, boolean deepTest) {
        if (noPrompt) {
            return true;
        }

        System.out.println("Operation: " + operationType);
        if (operationType.equals(BACKUP)) {
            System.out.println("Base directory (source): " + baseSaveDir.getAbsolutePath());
            System.out.println("Target directory (destination): " + backupDir.getAbsolutePath());
        } else if (operationType.equals(RESTORE)) {
            System.out.println("Backup directory (source): " + backupDir.getAbsolutePath());
            System.out.println("Base directory (target): " + baseSaveDir.getAbsolutePath());
        }
        if (verbose) {
            System.out.println("Verbose mode: enabled");
        }
        if (deepTest) {
            System.out.println("Test mode: deep");
        } else if (testRequested) {
            System.out.println("Test mode: shallow");
        }
        System.out.print("Do you want to continue with the operation? ([Y]es/[n]o): ");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                int response = reader.read();
                if (response == 'Y' || response == 'y') {
                    return true;
                } else if (response == 'N' || response == 'n') {
                    return false;
                } else {
                    System.out.print("Invalid input. Please enter 'Y' for yes or 'n' for no: ");
                    reader.readLine(); // Clear the buffer
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void createBackup() throws IOException {
        if (!baseSaveDir.exists()) {
            throw new IOException("Base save directory does not exist.");
        }

        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        long startTime = System.currentTimeMillis();
        int filesCopied = 0;
        int filesSkipped = 0;
        Collection<File> files = FileUtils.listFilesAndDirs(baseSaveDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        int totalFiles = files.size();
        int processedFiles = 0;

        for (File file : files) {
            File destFile = new File(backupDir, getRelativePath(baseSaveDir, file));
            if (file.isDirectory()) {
                destFile.mkdirs();
            } else {
                if (!destFile.exists() || FileUtils.isFileNewer(file, destFile)) {
                    FileUtils.copyFile(file, destFile);
                    filesCopied++;
                } else {
                    filesSkipped++;
                }
            }
            processedFiles++;
            printProgress("Backup", processedFiles, totalFiles, startTime, file.getPath(), filesCopied, filesSkipped);
        }

        // Ensure final progress is 100%
        printProgress("Backup", totalFiles, totalFiles, startTime, "", filesCopied, filesSkipped);

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        String formattedTime = formatDuration(totalTime);
        System.out.println("\nBackup Summary:");
        System.out.println("Base directory: " + baseSaveDir.getAbsolutePath());
        System.out.println("Target directory: " + backupDir.getAbsolutePath());
        System.out.println("Files copied: " + filesCopied);
        System.out.println("Files skipped: " + filesSkipped);
        System.out.println("Total time: " + formattedTime);
    }

    public void restoreBackup() throws IOException {
        if (!backupDir.exists()) {
            throw new IOException("Backup directory does not exist.");
        }

        long startTime = System.currentTimeMillis();
        int filesDeleted = 0;
        int filesRestored = 0;
        int filesSkipped = 0;

        // Delete files and directories in baseSaveDir that are not present in backupDir
        Collection<File> baseFiles = FileUtils.listFilesAndDirs(baseSaveDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        int totalFiles = baseFiles.size();
        int processedFiles = 0;

        for (File baseFile : baseFiles) {
            if (!baseFile.equals(baseSaveDir)) {
                File correspondingFile = new File(backupDir, getRelativePath(baseSaveDir, baseFile));
                if (!correspondingFile.exists()) {
                    FileUtils.forceDelete(baseFile);
                    filesDeleted++;
                }
            }
            processedFiles++;
            printProgress("Restore (Deleting)", processedFiles, totalFiles, startTime, baseFile.getPath(), filesDeleted, filesRestored);
        }

        // Copy files and directories from backupDir to baseSaveDir, overwriting existing files if they are older or do not exist
        Collection<File> backupFiles = FileUtils.listFilesAndDirs(backupDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        totalFiles = backupFiles.size();
        processedFiles = 0;

        for (File backupFile : backupFiles) {
            File destFile = new File(baseSaveDir, getRelativePath(backupDir, backupFile));
            if (backupFile.isDirectory()) {
                destFile.mkdirs();
            } else {
                if (!destFile.exists() || FileUtils.isFileNewer(backupFile, destFile)) {
                    FileUtils.copyFile(backupFile, destFile);
                    filesRestored++;
                } else {
                    filesSkipped++;
                }
            }
            processedFiles++;
            printProgress("Restore (Copying)", processedFiles, totalFiles, startTime, backupFile.getPath(), filesRestored, filesSkipped);
        }

        // Ensure final progress is 100%
        printProgress("Restore", totalFiles, totalFiles, startTime, "", filesRestored, filesSkipped);

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        String formattedTime = formatDuration(totalTime);
        System.out.println("\nRestore Summary:");
        System.out.println("Base directory: " + baseSaveDir.getAbsolutePath());
        System.out.println("Backup directory: " + backupDir.getAbsolutePath());
        System.out.println("Files restored: " + filesRestored);
        System.out.println("Files deleted: " + filesDeleted);
        System.out.println("Files skipped: " + filesSkipped);
        System.out.println("Total time: " + formattedTime);
    }

    // Helper to get the relative path
    private String getRelativePath(File base, File child) {
        return base.toURI().relativize(child.toURI()).getPath();
    }

    // Helper to format duration from milliseconds to minutes and seconds
    private String formatDuration(long durationMillis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%d min, %d sec", minutes, seconds);
    }

    // Helper to determine the last existing ordinal backup directory
    private static String getLastBackupDirectory(String baseBackupPath) {
        int counter = 1;
        String lastBackupPath = baseBackupPath + "-" + counter;
        while (new File(baseBackupPath + "-" + counter).exists()) {
            lastBackupPath = baseBackupPath + "-" + counter;
            counter++;
        }
        return lastBackupPath;
    }

    // Helper to determine the next ordinal backup directory
    private static String getNextBackupDirectory(String baseBackupPath) {
        int counter = 1;
        while (new File(baseBackupPath + "-" + counter).exists()) {
            counter++;
        }
        return baseBackupPath + "-" + counter;
    }

    private static void printUsage() {
        System.out.println("Usage: java PZSaver <backup|restore> <baseSavePath> [<backupPath>] [-v|--verbose] [-np|--noprompt] [-n|--new] [-t|--test [deep|shallow]] [-s|--skip]");
        System.out.println("Use -h or --help for detailed instructions.");
    }

    private static void printHelp() {
        System.out.println("Usage: java PZSaver <backup|restore> <baseSavePath> [<backupPath>] [-v|--verbose] [-np|--noprompt] [-n|--new] [-t|--test [deep|shallow]] [-s|--skip]");
        System.out.println("\nCommands:");
        System.out.println("  backup     Create a differential backup from the base save directory to the backup directory.");
        System.out.println("  restore    Restore the base save directory from the backup directory, making it identical to the backup.");
        System.out.println("\nParameters:");
        System.out.println("  <baseSavePath>   The path to the base save directory.");
        System.out.println("  <backupPath>     The path to the backup directory. If omitted, the last existing directory will be used (e.g., Dan-1, Dan-2).");
        System.out.println("\nFlags:");
        System.out.println("  -v, --verbose    Enable verbose output, showing detailed information about each file operation.");
        System.out.println("  -np, --noprompt  Bypass the approval prompt before starting the operation.");
        System.out.println("  -n, --new        Create a new backup directory, copying all files (full backup).");
        System.out.println("  -t, --test       Run a test to compare the file counts or file contents.");
        System.out.println("                   Use 'deep' to enable deep test (compare file contents). Default is 'shallow'.");
        System.out.println("  -s, --skip       Skip the main backup or restore operation and only run tests.");
        System.out.println("  -h, --help       Display this help message and exit.");
        System.out.println("\nExamples:");
        System.out.println("  java PZSaver backup C:\\path\\to\\Dan");
        System.out.println("  java PZSaver backup C:\\path\\to\\Dan -v");
        System.out.println("  java PZSaver restore C:\\path\\to\\Dan [C:\\path\\to\\Dan-1] -np");
        System.out.println("  java PZSaver backup C:\\path\\to\\Dan -n");
        System.out.println("\nBackup Options:");
        System.out.println("  If <backupPath> is omitted, the last existing backup directory will be used as the target directory.");
        System.out.println("  If the --new flag is used, a new backup directory will be created (e.g., Dan-3 for full backup).");
        System.out.println("\nRestore Options:");
        System.out.println("  If <backupPath> is omitted, the last existing backup directory will be used as the source directory.");
        System.out.println("  If <backupPath> is provided, the specified directory will be used as the source directory.");
    }

    private void printProgress(String operation, int processedFiles, int totalFiles, long startTime, String filePath, int filesProcessed1, int filesProcessed2) {
        int progressPercentage = (int) (((double) processedFiles / totalFiles) * 100);
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        long estimatedTotalTime = (long) ((elapsedTime / (double) processedFiles) * totalFiles);
        long estimatedRemainingTime = estimatedTotalTime - elapsedTime;

        String formattedElapsedTime = formatDuration(elapsedTime);
        String formattedRemainingTime = formatDuration(estimatedRemainingTime);

        // Ensure progress is 100% when the operation is complete
        if (processedFiles == totalFiles) {
            progressPercentage = 100;
        }

        String progressMessage = String.format("\r%s Progress: %d%% | Elapsed: %s | Remaining: %s",
                operation, progressPercentage, formattedElapsedTime, formattedRemainingTime);

        if (operation.contains("Test")) {
            String fileMessage = String.format(" | File: %s | matched: %d, mismatched: %d",
                    filePath, filesProcessed1, filesProcessed2);
            System.out.print(progressMessage + fileMessage);
        } else if (verbose) {
            String fileMessage = String.format(" | File: %s | %s: %d, %s: %d",
                    filePath, operation.equals("Backup") ? "copied" : "restored", filesProcessed1, operation.equals("Backup") ? "skipped" : "deleted", filesProcessed2);
            System.out.print(progressMessage + fileMessage);
        } else {
            System.out.print(progressMessage);
        }
    }

    private void runShallowTest(File sourceDir, File targetDir) throws IOException {
        long startTime = System.currentTimeMillis();
        int filesTested = 0;
        int directoriesTested = 0;
        int filesMatched = 0;
        int filesMismatched = 0;

        System.out.println("\nRunning shallow test...");
        Collection<File> sourceFiles = FileUtils.listFilesAndDirs(sourceDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        Collection<File> targetFiles = FileUtils.listFilesAndDirs(targetDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        int totalFiles = sourceFiles.size();

        if (sourceFiles.size() != targetFiles.size()) {
            System.out.println("Shallow test failed: different number of files/directories.");
            filesMismatched++;
        } else {
            System.out.println("Shallow test passed: same number of files/directories.");

            for (File sourceFile : sourceFiles) {
                File correspondingFile = new File(targetDir, getRelativePath(sourceDir, sourceFile));
                if (sourceFile.isDirectory()) {
                    directoriesTested++;
                } else {
                    filesTested++;
                    if (!correspondingFile.exists()) {
                        filesMismatched++;
                        printProgress("Shallow Test", filesTested, totalFiles, startTime, sourceFile.getPath(), filesMatched, filesMismatched);
                    } else {
                        filesMatched++;
                        printProgress("Shallow Test", filesTested, totalFiles, startTime, sourceFile.getPath(), filesMatched, filesMismatched);
                    }
                }
            }

            // Ensure final progress is 100%
            printProgress("Shallow Test", totalFiles, totalFiles, startTime, "", filesMatched, filesMismatched);
        }
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        String formattedTime = formatDuration(totalTime);
        System.out.println("\nShallow Test Summary:");
        System.out.println("Directories tested: " + directoriesTested);
        System.out.println("Files tested: " + filesTested);
        System.out.println("Files matched: " + filesMatched);
        System.out.println("Files mismatched: " + filesMismatched);
        System.out.println("Total time: " + formattedTime);
    }

    private void runDeepTest(File sourceDir, File targetDir) throws IOException {
        long startTime = System.currentTimeMillis();
        int filesTested = 0;
        int directoriesTested = 0;
        int filesMatched = 0;
        int filesMismatched = 0;

        System.out.println("\nRunning deep test...");
        Collection<File> sourceFiles = FileUtils.listFilesAndDirs(sourceDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        Collection<File> targetFiles = FileUtils.listFilesAndDirs(targetDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        int totalFiles = sourceFiles.size();

        if (sourceFiles.size() != targetFiles.size()) {
            System.out.println("Deep test failed: different number of files/directories.");
            filesMismatched++;
        } else {
            System.out.println("Deep test passed: same number of files/directories.");

            for (File sourceFile : sourceFiles) {
                File correspondingFile = new File(targetDir, getRelativePath(sourceDir, sourceFile));
                if (sourceFile.isDirectory()) {
                    directoriesTested++;
                } else {
                    filesTested++;
                    if (!correspondingFile.exists()) {
                        filesMismatched++;
                        printProgress("Deep Test", filesTested, totalFiles, startTime, sourceFile.getPath(), filesMatched, filesMismatched);
                    } else {
                        if (sourceFile.isFile()) {
                            if (sourceFile.length() != correspondingFile.length()) {
                                filesMismatched++;
                                printProgress("Deep Test", filesTested, totalFiles, startTime, sourceFile.getPath(), filesMatched, filesMismatched);
                            } else if (!FileUtils.contentEquals(sourceFile, correspondingFile)) {
                                filesMismatched++;
                                printProgress("Deep Test", filesTested, totalFiles, startTime, sourceFile.getPath(), filesMatched, filesMismatched);
                            } else {
                                String sourceSha = DigestUtils.sha256Hex(FileUtils.openInputStream(sourceFile));
                                String targetSha = DigestUtils.sha256Hex(FileUtils.openInputStream(correspondingFile));
                                if (!sourceSha.equals(targetSha)) {
                                    filesMismatched++;
                                    printProgress("Deep Test", filesTested, totalFiles, startTime, sourceFile.getPath(), filesMatched, filesMismatched);
                                } else {
                                    filesMatched++;
                                    printProgress("Deep Test", filesTested, totalFiles, startTime, sourceFile.getPath(), filesMatched, filesMismatched);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ensure final progress is 100%
        printProgress("Deep Test", totalFiles, totalFiles, startTime, "", filesMatched, filesMismatched);

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        String formattedTime = formatDuration(totalTime);
        System.out.println("\nDeep Test Summary:");
        System.out.println("Directories tested: " + directoriesTested);
        System.out.println("Files tested: " + filesTested);
        System.out.println("Files matched: " + filesMatched);
        System.out.println("Files mismatched: " + filesMismatched);
        System.out.println("Total time: " + formattedTime);
    }

    public static void main(String[] args) {
        if (args.length < 2 || args[0].equalsIgnoreCase(HELP_FLAG) || args[0].equalsIgnoreCase(HELP_SHORT_FLAG)) {
            printHelp();
            return;
        }

        String command = null;
        String baseSavePath = null;
        String backupPath = null;
        boolean verbose = false;
        boolean noPrompt = false;
        boolean createNewBackup = false;
        boolean deepTest = false;
        boolean skipOperation = false;
        boolean testRequested = false;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase(BACKUP) || arg.equalsIgnoreCase(RESTORE)) {
                if (command != null) {
                    System.out.println("Error: Multiple commands specified. Only one of 'backup' or 'restore' should be used.");
                    printUsage();
                    return;
                }
                command = arg;
            } else if (arg.startsWith("--") || arg.startsWith("-")) {
                if (arg.equalsIgnoreCase(VERBOSE_FLAG) || arg.equals(VERBOSE_SHORT_FLAG)) {
                    verbose = true;
                } else if (arg.equalsIgnoreCase(NOPROMPT_FLAG) || arg.equals(NOPROMPT_SHORT_FLAG)) {
                    noPrompt = true;
                } else if (arg.equalsIgnoreCase(NEW_FLAG) || arg.equals(NEW_SHORT_FLAG)) {
                    createNewBackup = true;
                } else if (arg.equalsIgnoreCase(TEST_FLAG) || arg.equals(TEST_SHORT_FLAG)) {
                    testRequested = true;
                    if (i + 1 < args.length && (args[i + 1].equalsIgnoreCase("deep") || args[i + 1].equalsIgnoreCase("full"))) {
                        deepTest = true;
                        i++;
                    }
                } else if (arg.equalsIgnoreCase(SKIP_FLAG) || arg.equals(SKIP_SHORT_FLAG)) {
                    skipOperation = true;
                } else {
                    System.out.println("Error: Unknown flag " + arg);
                    printUsage();
                    return;
                }
            } else if (baseSavePath == null) {
                baseSavePath = arg;
            } else if (backupPath == null) {
                backupPath = arg;
            } else {
                System.out.println("Error: Too many arguments.");
                printUsage();
                return;
            }
        }

        // Validate required arguments
        if (command == null || baseSavePath == null) {
            System.out.println("Error: Missing required arguments.");
            printUsage();
            return;
        }

        // Determine backup path if not provided
        if (backupPath == null) {
            if (command.equalsIgnoreCase(BACKUP) && createNewBackup) {
                backupPath = getNextBackupDirectory(baseSavePath);
            } else if (command.equalsIgnoreCase(RESTORE)) {
                backupPath = getLastBackupDirectory(baseSavePath);
            } else {
                backupPath = getLastBackupDirectory(baseSavePath);
            }
        }

        PZSaver manager = new PZSaver(baseSavePath, backupPath, verbose, noPrompt, createNewBackup);

        // Get approval before proceeding
        if (!manager.getApproval(command, testRequested, deepTest)) {
            System.out.println("Operation aborted by the user.");
            return;
        }

        try {
            if (!skipOperation) {
                if (command.equalsIgnoreCase(BACKUP)) {
                    manager.createBackup();
                } else if (command.equalsIgnoreCase(RESTORE)) {
                    manager.restoreBackup();
                } else {
                    System.out.println("Unknown command: " + command);
                    printUsage();
                    return;
                }
            }

            if (testRequested) {
                if (deepTest) {
                    manager.runDeepTest(new File(baseSavePath), new File(backupPath));
                } else {
                    manager.runShallowTest(new File(baseSavePath), new File(backupPath));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
