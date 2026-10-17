cmake_minimum_required(VERSION 3.22.1)
project(nerdproject_native C)

add_library(nerdspawn SHARED nerd_spawn.c)

add_executable(nerdlauncher nerd_launcher.c)
