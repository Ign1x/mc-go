cmake_minimum_required(VERSION 3.22.1)
project(paper_jli_launcher)

add_library(
    paper_jli_launcher
    SHARED
    src/main/cpp/paper_jli_launcher.cpp
)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
    paper_jli_launcher
    ${log-lib}
    ${dl-lib}
)
