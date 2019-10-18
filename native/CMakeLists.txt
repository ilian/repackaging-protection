# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# TODO: Remove in prod
# set(CMAKE_BUILD_TYPE Debug)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/check.cpp
             src/check.h
             src/vendor.cpp
             src/vendor.h
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

add_library(zlib STATIC
	libs/zlib/adler32.c
	libs/zlib/compress.c
	libs/zlib/crc32.c
	libs/zlib/crc32.h
	libs/zlib/deflate.c
	libs/zlib/deflate.h
	libs/zlib/gzclose.c
	libs/zlib/gzguts.h
	libs/zlib/gzlib.c
	libs/zlib/gzread.c
	libs/zlib/gzwrite.c
	libs/zlib/infback.c
	libs/zlib/inffast.c
	libs/zlib/inffast.h
	libs/zlib/inffixed.h
	libs/zlib/inflate.c
	libs/zlib/inflate.h
	libs/zlib/inftrees.c
	libs/zlib/inftrees.h
	libs/zlib/make_vms.com
	libs/zlib/trees.c
	libs/zlib/trees.h
	libs/zlib/uncompr.c
	libs/zlib/zconf.h
	libs/zlib/zlib.h
	libs/zlib/zutil.c
	libs/zlib/zutil.h
)
include_directories(libs/zlib)
set(ZLIB_LIBRARY zlib)


add_library(libzip STATIC
	libs/libzip/zip.h
	libs/libzip/mkstemp.c
	libs/libzip/zip_add.c
	libs/libzip/zip_add_dir.c
	libs/libzip/zip_close.c
	libs/libzip/zip_delete.c
	libs/libzip/zip_dirent.c
	libs/libzip/zip_entry_free.c
	libs/libzip/zip_entry_new.c
	libs/libzip/zip_err_str.c
	libs/libzip/zip_error.c
	libs/libzip/zip_error_clear.c
	libs/libzip/zip_error_get.c
	libs/libzip/zip_error_get_sys_type.c
	libs/libzip/zip_error_strerror.c
	libs/libzip/zip_error_to_str.c
	libs/libzip/zip_fclose.c
	libs/libzip/zip_file_error_clear.c
	libs/libzip/zip_file_error_get.c
	libs/libzip/zip_file_get_offset.c
	libs/libzip/zip_file_strerror.c
	libs/libzip/zip_filerange_crc.c
	libs/libzip/zip_fopen.c
	libs/libzip/zip_fopen_index.c
	libs/libzip/zip_fread.c
	libs/libzip/zip_free.c
	libs/libzip/zip_get_archive_comment.c
	libs/libzip/zip_get_archive_flag.c
	libs/libzip/zip_get_file_comment.c
	libs/libzip/zip_get_name.c
	libs/libzip/zip_get_num_files.c
	libs/libzip/zip_memdup.c
	libs/libzip/zip_name_locate.c
	libs/libzip/zip_new.c
	libs/libzip/zip_open.c
	libs/libzip/zip_rename.c
	libs/libzip/zip_replace.c
	libs/libzip/zip_set_archive_comment.c
	libs/libzip/zip_set_archive_flag.c
	libs/libzip/zip_set_file_comment.c
	libs/libzip/zip_set_name.c
	libs/libzip/zip_source_buffer.c
	libs/libzip/zip_source_file.c
	libs/libzip/zip_source_filep.c
	libs/libzip/zip_source_free.c
	libs/libzip/zip_source_function.c
	libs/libzip/zip_source_zip.c
	libs/libzip/zip_stat.c
	libs/libzip/zip_stat_index.c
	libs/libzip/zip_stat_init.c
	libs/libzip/zip_strerror.c
	libs/libzip/zip_unchange.c
	libs/libzip/zip_unchange_all.c
	libs/libzip/zip_unchange_archive.c
	libs/libzip/zip_unchange_data.c)
target_link_libraries(libzip)
include_directories(libs/libzip)
set(LIBZIP_LIBRARY libzip)

add_library(tiny-AES-c STATIC
    libs/tiny-AES-c/aes.c
    libs/tiny-AES-c/aes.hpp
)
include_directories(libs/tiny-AES-c)
set(AES_LIBRARY tiny-AES-c)

target_link_libraries( native-lib

                       ${log-lib}
                       ${LIBZIP_LIBRARY}
                       ${ZLIB_LIBRARY}
                       ${AES_LIBRARY}
                       )
