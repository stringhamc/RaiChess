cmake_minimum_required(VERSION 3.18)
project(raichess_stockfish CXX)

# Builds Stockfish as a native executable, disguised as libstockfish.so so
# AGP packages it into the APK's jniLibs and Android extracts it to
# nativeLibraryDir (see packaging.jniLibs.useLegacyPackaging in
# build.gradle.kts), where StockfishNativeEngine exec()s it and speaks UCI
# over stdin/stdout. This replaces the WebView/WASM backend as the primary
# engine: field diagnostics showed the 2019 stockfish.js build never
# finishing initialization on some devices' WebViews.
#
# Stockfish 11 specifically: the last classical-evaluation release — no
# NNUE network file to bundle (SF12+ needs ~40MB nets), still far stronger
# than any strength the app's ELO slider offers.
#
# Sources are downloaded at configure time from the pinned release tag
# (no hash pin: GitHub guarantees stable source archives for tags, and
# both CI and local builds fetch over TLS from the official repo).
set(SF_TAG sf_11)
set(SF_URL "https://github.com/official-stockfish/Stockfish/archive/refs/tags/${SF_TAG}.zip")
set(SF_ZIP "${CMAKE_BINARY_DIR}/stockfish-${SF_TAG}.zip")
set(SF_SRC "${CMAKE_BINARY_DIR}/Stockfish-${SF_TAG}/src")

if(NOT EXISTS "${SF_SRC}/main.cpp")
    message(STATUS "Downloading Stockfish ${SF_TAG} sources from ${SF_URL}")
    file(DOWNLOAD "${SF_URL}" "${SF_ZIP}" TLS_VERIFY ON STATUS SF_DL_STATUS)
    list(GET SF_DL_STATUS 0 SF_DL_CODE)
    if(NOT SF_DL_CODE EQUAL 0)
        message(FATAL_ERROR "Stockfish source download failed: ${SF_DL_STATUS}")
    endif()
    file(ARCHIVE_EXTRACT INPUT "${SF_ZIP}" DESTINATION "${CMAKE_BINARY_DIR}")
    if(NOT EXISTS "${SF_SRC}/main.cpp")
        message(FATAL_ERROR "Stockfish archive did not contain expected sources")
    endif()
endif()

file(GLOB SF_SOURCES "${SF_SRC}/*.cpp" "${SF_SRC}/syzygy/*.cpp")

add_executable(stockfish ${SF_SOURCES})

# Executable named like a shared library — the AGP packaging trick that
# gets a runnable binary into nativeLibraryDir
set_target_properties(stockfish PROPERTIES OUTPUT_NAME "libstockfish.so")

# C++11, NOT the NDK's default gnu++17: SF11 predates std::clamp and
# defines its own, which is ambiguous under C++17
set_target_properties(stockfish PROPERTIES
    CXX_STANDARD 11
    CXX_STANDARD_REQUIRED ON
)
# USE_POPCNT maps to compiler builtins on every NDK target; IS_64BIT only
# where true (Stockfish's own Makefile does the same per-arch split)
target_compile_options(stockfish PRIVATE -O3 -DNDEBUG -DUSE_POPCNT -Wno-deprecated-declarations)
if(CMAKE_ANDROID_ARCH_ABI MATCHES "64")
    target_compile_definitions(stockfish PRIVATE IS_64BIT)
endif()
