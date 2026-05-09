// read_file_content.m
#include <Foundation/Foundation.h>
#include <stdio.h>
#include <sys/stat.h>

int main(int argc, char *argv[]) {
  @autoreleasepool {
    if (argc != 2) {
      fprintf(stderr, "Usage: read_file_content <file_path>\n");
      return 1;
    }

    NSString *filePath = [NSString stringWithUTF8String:argv[1]];
    struct stat st;
    if (stat([filePath UTF8String], &st) != 0 || !S_ISREG(st.st_mode)) {
      fprintf(stderr, "Error: Invalid or inaccessible file: %s\n", [filePath UTF8String]);
      return 1;
    }

    NSError *error;
    NSString *content = [NSString stringWithContentsOfFile:filePath encoding:NSUTF8StringEncoding error:&error];
    if (error) {
      fprintf(stderr, "Error reading file: %s\n", [[error localizedDescription] UTF8String]);
      return 1;
    }

    NSDictionary *json = @{
      @"name": [filePath lastPathComponent],
      @"size": @(st.st_size),
      @"path": filePath,
      @"content": content
    };

    NSData *data = [NSJSONSerialization dataWithJSONObject:json options:0 error:nil];
    fwrite([data bytes], 1, [data length], stdout);
  }
  return 0;
}