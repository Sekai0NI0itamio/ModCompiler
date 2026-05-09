// filedroplistener.m
#include <AppKit/AppKit.h>
#include <Foundation/Foundation.h>
#include <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#include <sys/stat.h>

@interface DropView : NSView
@property (nonatomic, copy) void (^filesDropped)(NSArray<NSURL *> *urls);
@end

@interface DropWindowController : NSWindowController <NSWindowDelegate>
@property (nonatomic, strong) DropView *dropView;
@end

@implementation DropView

- (instancetype)initWithFrame:(NSRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        [self registerForDraggedTypes:@[NSPasteboardTypeFileURL, UTTypeFileURL.identifier]];
    }
    return self;
}

- (NSDragOperation)draggingEntered:(id<NSDraggingInfo>)sender {
    return NSDragOperationCopy;
}

- (BOOL)performDragOperation:(id<NSDraggingInfo>)sender {
    NSPasteboard *pboard = [sender draggingPasteboard];
    NSArray *urls = [pboard readObjectsForClasses:@[[NSURL class]] options:@{NSPasteboardURLReadingFileURLsOnlyKey: @YES}];
    if (urls && self.filesDropped) {
        self.filesDropped(urls);
    }
    return YES;
}

@end

@implementation DropWindowController

- (instancetype)init {
    NSRect windowRect = NSMakeRect(0, 0, 400, 200);
    NSWindow *window = [[NSWindow alloc] initWithContentRect:windowRect
                                                   styleMask:NSWindowStyleMaskTitled | NSWindowStyleMaskClosable
                                                     backing:NSBackingStoreBuffered
                                                       defer:NO];
    
    [window setLevel:NSFloatingWindowLevel];
    [window setTitle:@"Drop Files Here"];
    
    NSPoint mouseLocation = [NSEvent mouseLocation];
    NSScreen *mainScreen = [NSScreen mainScreen];
    NSRect screenFrame = [mainScreen visibleFrame];
    windowRect.origin.x = mouseLocation.x - (NSWidth(windowRect) / 2);
    windowRect.origin.y = mouseLocation.y - (NSHeight(windowRect) / 2);
    windowRect.origin.x = fmax(NSMinX(screenFrame), fmin(windowRect.origin.x, NSMaxX(screenFrame) - NSWidth(windowRect)));
    windowRect.origin.y = fmax(NSMinY(screenFrame), fmin(windowRect.origin.y, NSMaxY(screenFrame) - NSHeight(windowRect)));
    [window setFrameOrigin:windowRect.origin];
    
    self = [super initWithWindow:window];
    if (self) {
        self.dropView = [[DropView alloc] initWithFrame:[window.contentView bounds]];
        self.dropView.wantsLayer = YES;
        self.dropView.layer.backgroundColor = [[NSColor lightGrayColor] CGColor];
        
        NSTextField *label = [[NSTextField alloc] initWithFrame:NSMakeRect(0, 80, 400, 40)];
        [label setStringValue:@"Drop files here"];
        [label setAlignment:NSTextAlignmentCenter];
        [label setEditable:NO];
        [label setBezeled:NO];
        [label setBackgroundColor:[NSColor clearColor]];
        [label setFont:[NSFont systemFontOfSize:18]];
        [self.dropView addSubview:label];
        
        [window setContentView:self.dropView];
        [window setDelegate:self];
    }
    return self;
}

- (void)windowWillClose:(NSNotification *)notification {
    [NSApp terminate:nil];
}

@end

@interface FileDropDelegate : NSObject <NSApplicationDelegate>
@property (nonatomic, strong) DropWindowController *windowController;
@end

@implementation FileDropDelegate

- (void)applicationDidFinishLaunching:(NSNotification *)notification {
    self.windowController = [[DropWindowController alloc] init];
    [(DropView *)self.windowController.dropView setFilesDropped:^(NSArray<NSURL *> *urls) {
        [self processURLs:urls];
    }];
    [self.windowController showWindow:nil];
    [NSApp activateIgnoringOtherApps:YES];
}

- (void)processURLs:(NSArray<NSURL *> *)urls {
    NSString *outputPath = @"/tmp/dropped_files.json";
    [[NSFileManager defaultManager] removeItemAtPath:outputPath error:nil];
    
    NSMutableArray *output = [NSMutableArray array];
    for (NSURL *url in urls) {
        if (![url isFileURL]) continue;
        NSString *path = [url path];
        struct stat st;
        if (stat([path UTF8String], &st) != 0 || !S_ISREG(st.st_mode)) continue;

        NSDictionary *json = @{
            @"name": [path lastPathComponent],
            @"size": @(st.st_size),
            @"path": path
        };
        [output addObject:json];
    }

    if ([output count] > 0) {
        NSData *data = [NSJSONSerialization dataWithJSONObject:output options:0 error:nil];
        [data writeToFile:outputPath atomically:YES];
    }

    [self.windowController.window close];
}

@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        NSApplication *app = [NSApplication sharedApplication];
        FileDropDelegate *delegate = [[FileDropDelegate alloc] init];
        [app setDelegate:delegate];
        [NSApp setActivationPolicy:NSApplicationActivationPolicyAccessory];
        [NSApp run];
    }
    return 0;
}
