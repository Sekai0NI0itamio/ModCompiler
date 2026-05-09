// process_paste_files.m
#include <AppKit/NSPasteboard.h>
#include <AppKit/NSPasteboardItem.h> // Added to resolve forward declaration
#include <Foundation/Foundation.h>
#include <string.h>
#include <sys/stat.h>
#include <stdio.h>

int main(int argc, char *argv[]) {
  @autoreleasepool {
    if (argc != 2 || strcmp(argv[1], "--paste") != 0) {
      fprintf(stderr, "Usage: process_paste_files --paste\n");
      return 1;
    }

    NSPasteboard *pb = [NSPasteboard generalPasteboard];
    NSMutableArray *output = [NSMutableArray array];

    for (NSPasteboardItem *item in [pb pasteboardItems] ?: @[]) {
      NSString *fileURL = [item stringForType:NSPasteboardTypeFileURL];
      if (!fileURL) continue;

      NSURL *url = [NSURL URLWithString:fileURL];
      if (!url || ![url isFileURL]) continue;

      NSString *path = [url path];
      struct stat st;
      if (stat([path UTF8String], &st) != 0 || !S_ISREG(st.st_mode)) continue;

      NSDictionary *json = @{
        @"name" : [path lastPathComponent],
        @"size" : @(st.st_size),
        @"path" : path
      };
      [output addObject:json];
    }

    NSData *data = [NSJSONSerialization dataWithJSONObject:output options:0 error:nil];
    fwrite([data bytes], 1, [data length], stdout);
  }
  return 0;
}