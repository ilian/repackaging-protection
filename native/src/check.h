#ifndef CHECK_H
#define CHECK_H

#include <stdint.h>

char *get_apk_path();
int32_t hash_bytes(const char *arr, size_t count);
int32_t hash_in_zip(const char *apk_path, const char *file_name, size_t count, size_t offset);
int32_t hash_file_in_apk(const char *file_name, size_t count, size_t offset);
int decrypt_code(void *offset, size_t count, const unsigned char *key);

#endif /* CHECK_H */