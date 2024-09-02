#!/bin/bash

# Define source and destination directories
SOURCE_DIR=~/programming/mlec-impl/hadoop-MLEC/
DEST_DIR=aaronmao@mlec-mac:/home/aaronmao/hadoop-MLEC/

# Function to sync .java files
sync_java_files() {
    rsync -avzL --progress --include='*/' --include='*.java' --include='*.proto' --include='*.py' --include='*.properties' --exclude='*' "$SOURCE_DIR" "$DEST_DIR"
    echo "Sync complete."
}

echo "Syncing .java files..."
# Monitor the source directory for changes in .java files
fswatch -o --exclude='.*' --include='.*\.java$' --include='.*\.proto$' --include='.*\.py' --include='.*\.properties' "$SOURCE_DIR" | while read f ; do
    echo "Change detected, syncing..."
    sync_java_files
done
