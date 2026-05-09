// tools/native/clipgrab.m
#import <Cocoa/Cocoa.h>

int main(int argc, const char * argv[]) {
    @autoreleasepool {
        NSPasteboard *pb = [NSPasteboard generalPasteboard];
        NSString *s = nil;

        // Try modern type
        s = [pb stringForType:NSPasteboardTypeString];
        // Fallback for older macOS
        if (!s) s = [pb stringForType:NSStringPboardType];
        if (!s) s = @"";

        // Keep only the first line (up to the first newline, without including it)
        NSRange newlineRange = [s rangeOfCharacterFromSet:[NSCharacterSet newlineCharacterSet]];
        if (newlineRange.location != NSNotFound) {
            s = [s substringToIndex:newlineRange.location];
        }

        // Write the full first line as UTF-8 bytes to stdout
        NSData *data = [s dataUsingEncoding:NSUTF8StringEncoding];
        if (data && [data length] > 0) {
            fwrite([data bytes], 1, (size_t)[data length], stdout);
        }
    }
    return 0;
}