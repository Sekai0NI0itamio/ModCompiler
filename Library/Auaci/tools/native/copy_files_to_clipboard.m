// copy_files_to_clipboard.m
#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>

int main(int argc, char * argv[]) {
    @autoreleasepool {
        if (argc < 2) {
            fprintf(stderr, "Usage: %s [read|append|replace] [file...]\n", argv[0]);
            return 1;
        }
        NSString *mode = [NSString stringWithUTF8String:argv[1]];
        NSPasteboard *pb = [NSPasteboard generalPasteboard];

        if ([mode isEqualToString:@"read"]) {
            NSArray *classes = @[[NSURL class]];
            NSArray *items = [pb readObjectsForClasses:classes options:nil] ?: @[];
            for (NSURL *u in items) {
                if ([u isFileURL]) {
                    printf("%s\n", [[u path] fileSystemRepresentation]);
                }
            }
            return 0;
        }

        // build NSURL array from CLI args
        NSMutableArray<NSURL*> *urls = [NSMutableArray array];
        for (int i = 2; i < argc; i++) {
            NSString *p = [NSString stringWithUTF8String:argv[i]];
            if (!p) continue;
            NSURL *u = [NSURL fileURLWithPath:p];
            if (u) [urls addObject:u];
        }

        if ([mode isEqualToString:@"replace"]) {
            [pb clearContents];
            BOOL ok = [pb writeObjects:urls];
            return ok ? 0 : 2;
        } else if ([mode isEqualToString:@"append"]) {
            NSArray *classes = @[[NSURL class]];
            NSArray *existing = [pb readObjectsForClasses:classes options:nil] ?: @[];
            NSMutableArray<NSURL*> *combined = [NSMutableArray arrayWithArray:existing];

            for (NSURL *u in urls) {
                BOOL found = NO;
                for (NSURL *e in combined) {
                    if ([[e path] isEqualToString:[u path]]) { found = YES; break; }
                }
                if (!found) [combined addObject:u];
            }

            [pb clearContents];
            BOOL ok = [pb writeObjects:combined];
            return ok ? 0 : 3;
        } else {
            fprintf(stderr, "Unknown mode: %s\n", argv[1]);
            return 4;
        }
    }
}