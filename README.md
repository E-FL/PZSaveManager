Usage: java PZSaver <backup|restore> <baseSavePath> [<backupPath>] [-v|--verbose] [-np|--noprompt] [-n|--new] [-t|--test [deep|shallow]] [-s|--skip]

Commands:

  backup     Create a differential backup from the base save directory to the backup directory.
  
  restore    Restore the base save directory from the backup directory, making it identical to the backup.

  

Parameters:

  <baseSavePath>   The path to the base save directory.
  
  <backupPath>     The path to the backup directory. If omitted, the last existing directory will be used (e.g., Dan-1, Dan-2).

  

Flags:

  -v, --verbose    Enable verbose output, showing detailed information about each file operation.
  
  -np, --noprompt  Bypass the approval prompt before starting the operation.
  
  -n, --new        Create a new backup directory, copying all files (full backup).
  
  -t, --test       Run a test to compare the file counts or file contents.
  
                   Use 'deep' to enable deep test (compare file contents). Default is 'shallow'.
                   
  -s, --skip       Skip the main backup or restore operation and only run tests.
  
  -h, --help       Display this help message and exit.
  

Examples:

  java PZSaver backup C:\Users\****\Zomboid\Saves\[Builder]\Dan
  
  java PZSaver backup C:\Users\****\Zomboid\Saves\[Builder]\Dan -v
  
  java PZSaver restore C:\Users\****\Zomboid\Saves\[Builder]\Dan [C:\Users\****\Zomboid\Saves\[Builder]\Dan-1] -np
  
  java PZSaver backup C:\Users\****\Zomboid\Saves\[Builder]\Dan -n
  

Backup Options:

  If <backupPath> is omitted, the last existing backup directory will be used as the target directory.
  
  If the --new flag is used, a new backup directory will be created (e.g., Dan-3 for full backup).
  


Restore Options:

  If <backupPath> is omitted, the last existing backup directory will be used as the source directory.
  
  If <backupPath> is provided, the specified directory will be used as the source directory.
  

