#include <string>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <errno.h>
#include <zip.h>
#include <zipint.h>
#include <aes.hpp>
#include <cstdlib>
#include <sys/mman.h>
#include "vendor.h"
#include "check.h"

#include <android/log.h>
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "NATIVE", __VA_ARGS__);

static char * cached_path;

// Gets APK path of currently running APK, assuming no ptrace debugging is performed
// libc hooking can be mitigated by performing syscalls w/ inline asm
char *get_apk_path() {
    if(cached_path != 0) { return cached_path; }
    // Get package info, makes sure
    // const char *cmd = "DUMP=$(/system/bin/dumpsys package APK_PACKAGE_NAME); /system/bin/echo $DUMP | /system/bin/grep -q 'userId=`/system/bin/id -u`' && && /system/bin/echo $DUMP | /system/bin/sed -n -E 's;.* path: ([^ ]*\\.apk).*;\\1;p'";

    // Check if APK_PACKAGE_NAME is the currently running package by checking permissions to access private dir
    // Prevents attack scenario where original app is installed (and possibly hidden), while modified app has another package name
    if (opendir("/data/data/" APK_PACKAGE_NAME "/") == NULL) {
        printf("Tampering detected!!1!");
        return NULL;
    }

    const char *cmd = "/system/bin/pm path " APK_PACKAGE_NAME " | /system/bin/sed 's/package://'";

    FILE *fp;
    char *path = new char[1024];
    fp = popen(cmd, "r");
    if (fp == NULL) {
        printf("Failed to run shell command");
        return NULL;
    }

    int readCnt = 0;
    while (fgets(path, 1023, fp) != NULL) {
        printf("%s", path);
        readCnt++;
    }
    pclose(fp);

    path[strcspn(path, "\n")] = 0; // Substitute newline char by \0 so we can pass it to fopen

    if(readCnt != 1) {
        printf("Invalid amount of apk path matches. Possible tampering detected.")
        return NULL;
    }

    return cached_path = path;
}

int32_t hash_bytes(const char *arr, size_t count) {
    // Simple XOR for now
    char hash[4];
    memset(hash, 0, sizeof(hash));
    for(size_t read = 0; read < count; read++) {
        hash[read % 4] ^= arr[read];
    }
    return hash[0] + (hash[1] << 8) + (hash[2] << 16) + (hash[3] << 24);
}

int32_t hash_in_zip(const char *apk_path, const char *file_name, size_t count, size_t offset) {
    int err = 0;
    // Open APK
    zip *z = zip_open(apk_path, 0, &err);
    if(err) {
        printf("Failed to open apk: %i", err);
        return 0;
    }

    // Stat file inside APK archive
    struct zip_stat st;
    zip_stat_init(&st);
    zip_stat(z, file_name, 0, &st);

    printf("Found %s in %s of size %li", file_name, apk_path, st.size);
    if(offset + count > st.size) {
        printf("Requested hash of file range that is out of bounds\n");
        return 0;
    }

    char *uncompressed = new char[offset+count];

    zip_file *f;
    if ((f = zip_fopen(z, file_name, 0)) == NULL) {
        printf("Could not open file in archive: %i", z->error);
        return 0;
    }

    ssize_t read = zip_fread(f, uncompressed, offset + count);
    if(read < 0) {
        printf("Failed to read file in archive");
        return 0;
    }
    printf("Read %zi bytes", read);
    zip_fclose(f);
    zip_close(z);

    int32_t hash = hash_bytes(uncompressed + offset, count);

    delete[] uncompressed;

    return hash;
}

int32_t hash_file_in_apk(const char *file_name, size_t count, size_t offset) {
    return hash_in_zip(get_apk_path(), file_name, count, offset);
}

int decrypt_code(void *offset, size_t count, const unsigned char *key) {
    int page_size = getpagesize();

    char *page_start = ((char *)offset) - (((unsigned long)offset) % page_size);
    size_t page_count = 1; // Pages to mprotect
    while(((char *)offset) + count > (page_start + page_size * page_count)) {
        page_count++;
    }

    /*
    printf("Dumping encrypted instructions:")
    uint16_t *iptr = (uint16_t *)offset;
    for(int i = 0; i < (count/2); i++) {
        printf("%04x", *(iptr++));
    } */

    printf("Marking %d pages (each of size %d) starting from %p as W&X\n", page_count, page_size, page_start);
    // Mark all pages where code lies in as W&X
    if(mprotect(page_start, page_count * page_size, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        printf("Err mprotect\n");
        return -1;
    } else {
        printf("Ok mprotect\n");
    }

    // Decrypt and write decrypted code back to .text segment
    // We use AES in CTR mode to provide a stream cipher instead of CBC, as this requires our code to be aligned in a
    // 16-byte boundary, which we can't guarantee.
    struct AES_ctx ctx;
    const size_t chk_size = 16;
    uint8_t iv[chk_size];
    memset(iv, 0, chk_size);

    /*
    printf("Initializing AES context with IV 0 and key: ")
    for(int i = 0; i < chk_size; i++) {
        printf("%02x", key[i])
    } */

    AES_init_ctx_iv(&ctx, key, iv);

    const size_t direct_buffer_size = count - (count % chk_size);
    if(direct_buffer_size > 0) {
        AES_CTR_xcrypt_buffer(&ctx, (uint8_t *)offset, direct_buffer_size);
    }

    printf("Directly decrypted %d bytes\n", direct_buffer_size);

    uint8_t buf[chk_size];
    if(count - direct_buffer_size > 0) {
        char *remaining_offset = ((char *)offset) + direct_buffer_size;
        int remaining_count = count - direct_buffer_size;
        printf("Decrypted remaining %d bytes (offset=%p) that didn't fit AES chunk of size %d\n", remaining_count, remaining_offset, chk_size);
        memcpy(buf, remaining_offset, remaining_count); // Read memory of last chunk into buf
        AES_CTR_xcrypt_buffer(&ctx, buf, chk_size); // XOR entire chunk with stream cipher (we ignore part after count)
        memcpy(remaining_offset, buf, remaining_count); // Write decrypted memory back into .text segment
    }

    /*
    printf("Dumping decrypted instructions:")
    iptr = (uint16_t *)offset;
    for(int i = 0; i < (count/2); i++) {
        printf("%04x", *(iptr++));
    } */

    // Clean instruction cache
    __builtin___clear_cache(page_start, page_start + (page_count * page_size));
    return 0;
}