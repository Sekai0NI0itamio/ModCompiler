// bin/open_terminal.m
#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>

int main(int argc, const char * argv[]) {
    @autoreleasepool {
        if (argc < 2) {
            fprintf(stderr, "Usage: open_terminal <script-string>\n");
            return 1;
        }

        // Reconstruct the script from argv[1..] (execFile should pass it as a single arg, but be safe).
        NSMutableString *script = [NSMutableString string];
        for (int i = 1; i < argc; i++) {
            const char *partC = argv[i];
            NSString *part = partC ? [NSString stringWithUTF8String:partC] : @"";
            if (i > 1) [script appendString:@" "];
            [script appendString:part];
        }

        // Use osascript to run AppleScript that tells Terminal to open a new window and run the provided script.
        // We'll open a new window with `do script ""` and then run theScript in that new window.
        NSArray<NSString *> *args = @[
            @"-e", @"on run argv",
            @"-e", @"set theScript to item 1 of argv",
            @"-e", @"tell application \"Terminal\"",
            @"-e", @"activate",
            @"-e", @"set newWindow to do script \"\"",
            @"-e", @"delay 0.12",
            @"-e", @"do script theScript in newWindow",
            @"-e", @"end tell",
            @"-e", @"end run",
            script
        ];

        NSTask *task = [[NSTask alloc] init];
        task.launchPath = @"/usr/bin/osascript";
        task.arguments = args;
        task.standardOutput = [NSPipe pipe];
        task.standardError = [NSPipe pipe];

        @try {
            [task launch];
        } @catch (NSException *ex) {
            fprintf(stderr, "Failed to launch osascript: %s\n", [[ex reason] UTF8String]);
            return 1;
        }

        // Small delay to let Terminal open/run the command.
        [NSThread sleepForTimeInterval:0.2];

        // Bring Terminal frontmost (best-effort).
        NSArray<NSRunningApplication *> *running =
            [NSRunningApplication runningApplicationsWithBundleIdentifier:@"com.apple.Terminal"];
        if (running.count > 0) {
            NSRunningApplication *term = running[0];
            [term activateWithOptions:(NSApplicationActivateAllWindows | NSApplicationActivateIgnoringOtherApps)];
        } else {
            // As a last resort, try to launch Terminal.
            [[NSWorkspace sharedWorkspace] launchApplication:@"Terminal"];
        }

        return 0;
    }
}