cmake_minimum_required(VERSION 3.22.1)

# llama.cpp sempre otimizado, também no build debug (sem isso o teste no aparelho fica dezenas de vezes mais lento)
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

project(motor_llm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Versão do llama.cpp: fonte única em ia/llm/VERSAO_LLAMA_CPP (a mesma do treino e da avaliação no PC)
file(READ "${CMAKE_CURRENT_LIST_DIR}/../../../../ia/llm/VERSAO_LLAMA_CPP" VERSAO_LLAMA_CPP)
string(STRIP "${VERSAO_LLAMA_CPP}" VERSAO_LLAMA_CPP)

# Só a biblioteca: sem servidor, ferramentas, testes, OpenMP, curl ou OpenSSL
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# armv8.2 com dotprod e fp16 (Snapdragon 845+ e equivalentes); motor_llm.cpp recusa abrir em CPU sem eles
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
    GIT_TAG ${VERSAO_LLAMA_CPP}
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(motor_llm SHARED motor_llm.cpp)
target_link_libraries(motor_llm PRIVATE llama android log)
# Exporta só os símbolos JNI (o resto do llama.cpp fica interno e o .so encolhe)
target_link_options(motor_llm PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
