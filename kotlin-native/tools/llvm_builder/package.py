import argparse, subprocess, shutil, os, sys
from pathlib import Path
from typing import List
import hashlib
import shlex

vsdevcmd = None
isysroot = None

ninja = 'ninja'
cmake = 'cmake'
git = 'git'


def absolute_path(path):
    if path is not None:
        # CMake is not tolerant to backslashes in path.
        return os.path.abspath(path).replace('\\', '/')
    else:
        return None


class Host:
    @staticmethod
    def is_windows():
        return sys.platform == "win32"

    @staticmethod
    def is_linux():
        return sys.platform == "linux"

    @staticmethod
    def is_darwin():
        return sys.platform == "darwin"

    @staticmethod
    def llvm_target():
        return "Native"


def host_default_compression():
    if Host.is_windows():
        return "zip"
    else:
        return "gztar"


def default_xcode_sdk_path():
    return subprocess.check_output(['xcrun', '--show-sdk-path'],
                                   universal_newlines=True).rstrip()


def construct_cmake_flags(
        bootstrap_llvm_path: str = None,
        install_path: str = None,
        projects: List[str] = None,
        runtimes: List[str] = None,
        targets: List[str] = None
) -> List[str]:
    building_bootstrap = bootstrap_llvm_path is None

    c_compiler, cxx_compiler, linker, ar = None, None, None, None
    c_flags, cxx_flags, linker_flags = None, None, None
    if not building_bootstrap:
        if Host.is_windows():
            # CMake is not tolerant to backslashes
            c_compiler = f"{bootstrap_llvm_path}/bin/clang-cl.exe".replace('\\', '/')
            cxx_compiler = f"{bootstrap_llvm_path}/bin/clang-cl.exe".replace('\\', '/')
            linker = f"{bootstrap_llvm_path}/bin/lld-link.exe".replace('\\', '/')
            ar = f"{bootstrap_llvm_path}/bin/llvm-lib.exe".replace('\\', '/')
        elif Host.is_linux():
            c_compiler = f"{bootstrap_llvm_path}/bin/clang"
            cxx_compiler = f"{bootstrap_llvm_path}/bin/clang++"
            linker = f"{bootstrap_llvm_path}/bin/ld.lld"
            ar = f"{bootstrap_llvm_path}/bin/llvm-ar"
        elif Host.is_darwin():
            c_compiler = f"{bootstrap_llvm_path}/bin/clang"
            cxx_compiler = f"{bootstrap_llvm_path}/bin/clang++"
            # ld64.lld is not that good yet.
            linker = None
            ar = f"{bootstrap_llvm_path}/bin/llvm-ar"
            isysroot = default_xcode_sdk_path()
            c_flags = ['-isysroot', isysroot]
            cxx_flags = ['-isysroot', isysroot, '-stdlib=libc++']
            linker_flags = ['-stdlib=libc++']

    cmake_args = [
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLLVM_ENABLE_ASSERTIONS=OFF",
        "-DLLVM_ENABLE_TERMINFO=OFF",
        "-DLLVM_INCLUDE_GO_TESTS=OFF",
        "-DLLVM_ENABLE_Z3_SOLVER=OFF",
    ]

    if Host.is_darwin():
        cmake_args.append('-DLLVM_ENABLE_LIBCXX=ON')
        cmake_args.append('-DCOMPILER_RT_BUILD_BUILTINS=ON')
        cmake_args.extend([
            '-DLIBCXX_ENABLE_SHARED=OFF',
            '-DLIBCXX_ENABLE_STATIC=OFF',
            '-DLIBCXX_INCLUDE_TESTS=OFF',
            '-DLIBCXX_ENABLE_EXPERIMENTAL_LIBRARY=OFF',
        ])
        if building_bootstrap:
            cmake_args.extend([
                '-DCOMPILER_RT_BUILD_CRT=OFF',
                '-DCOMPILER_RT_BUILD_LIBFUZZER=OFF',
                '-DCOMPILER_RT_BUILD_SANITIZERS=OFF',
                '-DCOMPILER_RT_BUILD_XRAY=OFF',
                '-DCOMPILER_RT_ENABLE_IOS=OFF',
                '-DCOMPILER_RT_ENABLE_WATCHOS=OFF',
                '-DCOMPILER_RT_ENABLE_TVOS=OFF',
            ])
        else:
            cmake_args.append('-DLIBCXX_USE_COMPILER_RT=ON')
    else:
        cmake_args.append('-DCOMPILER_RT_BUILD_BUILTINS=OFF')

    if install_path is not None:
        cmake_args.append("-DCMAKE_INSTALL_PREFIX=" + install_path)
    if targets is not None:
        cmake_args.append('-DLLVM_TARGETS_TO_BUILD=' + ";".join(targets))
    if projects is not None:
        cmake_args.append('-DLLVM_ENABLE_PROJECTS=' + ";".join(projects))
    if runtimes is not None:
        cmake_args.append('-DLLVM_ENABLE_RUNTIMES=' + ";".join(runtimes))
    if c_compiler is not None:
        cmake_args.append('-DCMAKE_C_COMPILER=' + c_compiler)
    if cxx_compiler is not None:
        cmake_args.append('-DCMAKE_CXX_COMPILER=' + cxx_compiler)
    if linker is not None:
        cmake_args.append('-DCMAKE_LINKER=' + linker)
    if c_compiler is not None:
        cmake_args.append('-DCMAKE_AR=' + ar)

    if c_flags is not None:
        cmake_args.append("-DCMAKE_C_FLAGS=" + ' '.join(c_flags))
    if cxx_flags is not None:
        cmake_args.append("-DCMAKE_CXX_FLAGS=" + ' '.join(cxx_flags))
    if linker_flags is not None:
        cmake_args.append('-DCMAKE_EXE_LINKER_FLAGS=' + ' '.join(linker_flags))
        cmake_args.append('-DCMAKE_MODULE_LINKER_FLAGS=' + ' '.join(linker_flags))
        cmake_args.append('-DCMAKE_SHARED_LINKER_FLAGS=' + ' '.join(linker_flags))

    if Host.is_windows():
        cmake_args.append("-DLLVM_USE_CRT_RELEASE=MT")
        cmake_args.append("-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded")
        cmake_args.append("-DLLVM_ENABLE_DIA_SDK=OFF")
        cmake_args.append("-DCMAKE_INSTALL_UCRT_LIBRARIES=OFF")

    if not Host.is_windows():
        cmake_args.append("-LLVM_BUILD_LLVM_DYLIB=ON")
        cmake_args.append("-DLLVM_LINK_LLVM_DYLIB=ON")

    return cmake_args


def run_command(command: List[str]):
    if Host.is_windows() and vsdevcmd is None:
        sys.exit("'vsdevcmd' is not set!")
    if Host.is_windows():
        command = [vsdevcmd, "-arch=amd64", "&&"] + command
    command = [shlex.quote(arg) for arg in command]
    if not Host.is_windows():
        command = ' '.join(command)
    print("Running command: " + command)

    subprocess.run(command, shell=True, check=True)


def force_create_directory(parent, name) -> Path:
    build_path = parent / name
    print(f"Force-creating directory {build_path}")
    if build_path.exists():
        shutil.rmtree(build_path)
    os.mkdir(build_path)
    return build_path


def llvm_build_commands(
        install_path, bootstrap_path, llvm_src, targets, ninja_target, projects, runtimes
) -> List[List[str]]:
    cmake_flags = construct_cmake_flags(bootstrap_path, install_path, projects, runtimes, targets)
    cmake_command = [cmake, "-G", "Ninja"] + cmake_flags + [llvm_src + "/llvm"]
    ninja_command = [ninja, ninja_target]
    return [cmake_command, ninja_command]


def clone_llvm_repository(repo, branch, llvm_repo_destination):
    """
    Downloads a single commit from the given repository.
    """
    if Host.is_darwin():
        default_repo, default_branch = "https://github.com/apple/llvm-project", "apple/stable/20200108"
    else:
        default_repo, default_branch = "https://github.com/llvm/llvm-project", "release/11.x"
    repo = default_repo if repo is None else repo
    branch = default_branch if branch is None else branch
    # Download only single commit because we don't need whole history just for building LLVM.
    run_command([git, "clone", repo, "--branch", branch, "--depth", "1", "llvm-project"])
    return absolute_path(llvm_repo_destination)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Build LLVM toochain for Kotlin/Native")
    # Output configuration.
    parser.add_argument("--install-path", type=str, default="llvm-distribution", required=False,
                        help="Where final LLVM distribution will be installed")
    parser.add_argument("--archive-path", default=None,
                        help="Create an archive and its sha256 for final distribution at given path")
    # Build configuration
    parser.add_argument("--stage0", type=str, default=None,
                        help="Path to existing toolchain")
    parser.add_argument("--num-stages", type=int, default=2,
                        help="Number of stages in bootstrap.\n"
                             "Default is 2 meaning that we build LLVM and then use it to build itself.")
    # LLVM sources.
    parser.add_argument("--llvm-sources", dest="llvm_src", type=str, default=None,
                        help="Location of LLVM sources")
    parser.add_argument("--repo", type=str, default=None)
    parser.add_argument("--branch", type=str, default=None)
    parser.add_argument("--llvm-repo-destination", type=str, default="llvm-project",
                        help="Where LLVM repository should be downloaded.")
    # Environment setup.
    parser.add_argument("--vsdevcmd", type=str, default=None, required=Host.is_windows(),
                        help="(Windows only) Path to VsDevCmd.bat")
    parser.add_argument("--ninja", type=str, default=None,
                        help="Override path to ninja")
    parser.add_argument("--cmake", type=str, default=None,
                        help="Override path to cmake")
    parser.add_argument("--git", type=str, default=None,
                        help="Override path to git")
    parser.add_argument("--isysroot", type=str, default=None,
                        help="Override path to macOS SDK")
    # Misc.
    parser.add_argument("--save-temporary-files", type=bool, default=True,
                        help="Should intermediate build results be saved?")
    return parser


def build_distribution(args):
    """
    Performs (probably multistage) build of LLVM
    and returns path to the final distribution.
    """
    current_dir = Path().absolute()
    num_stages = args.num_stages
    bootstrap_path = args.stage0
    intermediate_build_results = []
    # Most likely, num_stages will be 1 or 2.
    # 2 means bootstrap build: we build LLVM distribution (stage 1)
    # that then compiles sources once again (stage 2). Thus, resulting
    # distribution is (almost) independent from environment, which means
    # reproducibility and less bugs.
    #
    # Sometimes it makes sense to generate yet another distribution to check
    # that it is the same as built at stage 2 (so there is no non-determinism in LLVM).
    for stage in range(1, num_stages + 1):
        building_bootstrap = num_stages > 1 and stage == 1
        building_final = stage == num_stages

        if building_bootstrap:
            # We only need a host target to start a bootstrap.
            targets = [Host.llvm_target()]
        else:
            # None targets means all available targets.
            targets = None

        if building_final:
            install_path = args.install_path
        else:
            install_path = force_create_directory(current_dir, f"llvm-stage-{stage}")
            intermediate_build_results.append(install_path)

        projects = ["clang", "lld", "libcxx", "libcxxabi", "compiler-rt"]
        runtimes = None
        ninja_target = "install"

        build_dir = force_create_directory(current_dir, f"llvm-stage-{stage}-build")
        intermediate_build_results.append(build_dir)
        commands = llvm_build_commands(
            install_path=absolute_path(install_path),
            bootstrap_path=absolute_path(bootstrap_path),
            llvm_src=absolute_path(args.llvm_src),
            targets=targets,
            ninja_target=ninja_target,
            projects=projects,
            runtimes=runtimes
        )

        os.chdir(build_dir)
        for command in commands:
            run_command(command)
        os.chdir(current_dir)
        bootstrap_path = install_path

    if not args.save_temporary_files:
        print("Cleaning up")
        for dir in intermediate_build_results:
            print(f"Removing {dir}")
            shutil.rmtree(dir)

    return args.install_path


def create_archive(input_directory, output_path, compression=host_default_compression()) -> str:
    print("Creating archive " + output_path + " from " + input_directory)
    base_directory, archive_prefix = os.path.split(os.path.normpath(input_directory))
    return shutil.make_archive(output_path, compression, base_directory, archive_prefix)


def create_checksum_file(input_path, output_path):
    chunk_size = 4096
    checksum = hashlib.sha256()
    with open(input_path, "rb") as input_contents:
        for chunk in iter(lambda: input_contents.read(chunk_size), b""):
            checksum.update(chunk)
    print(checksum.hexdigest(), file=open(output_path, "w"))
    return True


def setup_environment(args):
    """
    Setup globals that store information about script execution environment.
    """
    global vsdevcmd, ninja, cmake, git, isysroot
    if args.ninja:
        ninja = args.ninja
    elif shutil.which('ninja') is None:
        sys.exit("'ninja' is not found. Install or provide via --ninja argument.")
    if args.cmake:
        cmake = args.cmake
    elif shutil.which('cmake') is None:
        sys.exit("'cmake' is not found. Install or provide via --cmake argument.")
    if args.git:
        git = args.git
    elif shutil.which('git') is None:
        sys.exit("'git' is not found. Install or provide via --git argument.")
    if Host.is_windows():
        if args.vsdevcmd:
            vsdevcmd = args.vsdevcmd
    elif Host.is_darwin():
        if args.isysroot:
            isysroot = args.isysroot
        else:
            isysroot = default_xcode_sdk_path()


def check_args_consistency(args):
    should_use_existing_sources = args.llvm_src is not None
    should_download_llvm = args.llvm_repo_destination is not None or args.repo is not None or args.branch is not None
    if should_use_existing_sources and should_download_llvm:
        sys.exit("Cannot download LLVM and use existing sources at the same time!")


def main():
    parser = build_parser()
    args = parser.parse_args()
    check_args_consistency(args)
    setup_environment(args)
    if args.llvm_src is None:
        args.llvm_src = clone_llvm_repository(args.repo, args.branch, args.llvm_repo_destination)
    final_dist = build_distribution(args)
    if args.archive_path is not None:
        archive = create_archive(final_dist, args.archive_path)
        create_checksum_file(archive, f"{archive}.sha256")


# TODO:
# 3. Add distribution naming
# * Check that dependencies in path
if __name__ == "__main__":
    main()
