// reveal_in_finder.m
#include <Foundation/Foundation.h>
#include <AppKit/AppKit.h>

int main(int argc, char *argv[]) {
    @autoreleasepool {
        if (argc != 2) {
            fprintf(stderr, "Usage: reveal_in_finder <file_path>\n");
            return 1;
        }

        NSString *path = [NSString stringWithUTF8String:argv[1]];
        NSURL *fileURL = [NSURL fileURLWithPath:path];
        
        // Verify the path exists
        if (![[NSFileManager defaultManager] fileExistsAtPath:path]) {
            fprintf(stderr, "Error: Path does not exist: %s\n", [path UTF8String]);
            return 1;
        }

        // Activate Finder and reveal the file/folder
        [[NSWorkspace sharedWorkspace] activateFileViewerSelectingURLs:@[fileURL]];
        
        return 0;
    }
}
