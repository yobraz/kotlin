import argparse, subprocess, shutil, os, sys, platform
from pathlib import Path
import abc
from typing import List, Dict
from functools import reduce
import hashlib


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


def cmake_bool_flag(flag):
    return "On" if flag else "Off"


class HostSpecificCMakeFlags:
    @abc.abstractmethod
    def build(self) -> Dict[str, str]:
        pass

    @abc.abstractmethod
    def cmake_executables(self, bootstrap_llvm_path: str) -> Dict[str, str]:
        pass

    @staticmethod
    def create():
        if Host.is_darwin():
            return DarwinCMakeFlags()
        elif Host.is_windows():
            return WindowsCMakeFlags()
        else:
            return LinuxCMakeFlags()


class WindowsCMakeFlags(HostSpecificCMakeFlags):
    def __init__(self):
        self.crt_release = "MT"
        self.crt_debug = "MT"
        self.install_ucrt_libraries = True
        self.msvc_runtime_library = "MultiThreaded"
        self.enable_dia_sdk = False

    def cmake_executables(self, bootstrap_llvm_path: str) -> Dict[str, str]:
        if bootstrap_llvm_path:
            return {
                "CMAKE_CXX_COMPILER": f"{bootstrap_llvm_path}/bin/clang-cl.exe".replace('\\', '/'),
                "CMAKE_C_COMPILER": f"{bootstrap_llvm_path}/bin/clang-cl.exe".replace('\\', '/'),
                "CMAKE_LINKER": f"{bootstrap_llvm_path}/bin/lld-link.exe".replace('\\', '/'),
                "CMAKE_AR": f"{bootstrap_llvm_path}/bin/llvm-lib.exe".replace('\\', '/'),
            }
        else:
            return {}

    def build(self) -> Dict[str, str]:
        return {
            "LLVM_USE_CRT_RELEASE": self.crt_release,
            "LLVM_USE_CRT_DEBUG": self.crt_debug,
            "CMAKE_INSTALL_UCRT_LIBRARIES": cmake_bool_flag(self.install_ucrt_libraries),
            "CMAKE_MSVC_RUNTIME_LIBRARY": self.msvc_runtime_library,
            "LLVM_ENABLE_DIA_SDK": cmake_bool_flag(self.enable_dia_sdk),
        }


class LinuxCMakeFlags(HostSpecificCMakeFlags):
    def __init__(self):
        self.enable_terminfo = False

    def build(self) -> Dict[str, str]:
        return {
            "LLVM_ENABLE_TERMINFO": cmake_bool_flag(self.enable_terminfo)
        }

    def cmake_executables(self, bootstrap_llvm_path: str) -> Dict[str, str]:
        if bootstrap_llvm_path:
            return {
                "CMAKE_CXX_COMPILER": f"{bootstrap_llvm_path}/bin/clang++",
                "CMAKE_C_COMPILER": f"{bootstrap_llvm_path}/bin/clang",
                "CMAKE_LINKER": f"{bootstrap_llvm_path}/bin/ld.lld",
                "CMAKE_AR": f"{bootstrap_llvm_path}/bin/llvm-ar",
            }
        else:
            return {}


class DarwinCMakeFlags(HostSpecificCMakeFlags):
    def __init__(self):
        self.build_llvm_c_dylib = False

    def build(self) -> Dict[str, str]:
        return {
            "LLVM_BUILD_LLVM_C_DYLIB": cmake_bool_flag(self.build_llvm_c_dylib)
        }

    def cmake_executables(self, bootstrap_llvm_path: str) -> Dict[str, str]:
        if bootstrap_llvm_path:
            return {
                "CMAKE_CXX_COMPILER": f"{bootstrap_llvm_path}/bin/clang++",
                "CMAKE_C_COMPILER": f"{bootstrap_llvm_path}/bin/clang",
                "CMAKE_LINKER": f"{bootstrap_llvm_path}/bin/ld64.lld",
                "CMAKE_AR": f"{bootstrap_llvm_path}/bin/llvm-ar",
            }
        else:
            return {}


class Environment:
    @abc.abstractmethod
    def execute_subprocess(self, commands: List[str]) -> subprocess.Popen:
        pass

    @abc.abstractmethod
    def create_distribution_archive(self, input_path: str, output_path: str) -> bool:
        pass

    @staticmethod
    def create(args):
        if Host.is_windows():
            return WindowsEnvironment(args.vsdevcmd)
        else:
            return PosixEnvironment()


class WindowsEnvironment(Environment):
    def __init__(self, vsdevcmd):
        self.vsdevcmd = vsdevcmd

    def build_vsdevcmd_call(self):
        return ["call", f"\"{self.vsdevcmd}\"", "-arch=amd64"]

    def execute_subprocess(self, commands: List[str]) -> subprocess.Popen:
        vsdevcmd = ' '.join(self.build_vsdevcmd_call())
        return subprocess.Popen(" & ".join([vsdevcmd, *commands]), shell=True)

    def create_distribution_archive(self, input_path: str, output_path: str) -> bool:
        shutil.make_archive(output_path, 'zip', input_path)
        return True


class PosixEnvironment(Environment):
    def __init__(self):
        if Host.is_darwin():
            self.build_llvm_c_dylib = False
        if Host.is_linux():
            self.enable_terminfo = False

    def execute_subprocess(self, commands: List[str]) -> subprocess.Popen:
        return subprocess.Popen("; ".join(commands), shell=True)

    def create_distribution_archive(self, input_path: str, output_path: str) -> bool:
        shutil.make_archive(output_path, 'gztar', input_path)
        return True


class CommonCMakeFlags:
    def __init__(self, install_prefix):
        self.build_type = "Release"
        self.subprojects = ["clang", "lld", "libcxx", "libcxxabi"]
        self.install_prefix = install_prefix
        self.with_assertions = False
        # LLVM_BUILD_LLVM_C_DYLIB is not working for msvc
        self.build_llvm_dylib = not Host.is_windows()
        self.link_llvm_dylib = not Host.is_windows()
        self.llvm_targets = None

    def build(self) -> Dict[str, str]:
        common_flags = {
            "CMAKE_BUILD_TYPE": self.build_type,
            "LLVM_ENABLE_ASSERTIONS": cmake_bool_flag(self.with_assertions),
            "LLVM_ENABLE_PROJECTS": f"\"{';'.join(self.subprojects)}\"",
            "CMAKE_INSTALL_PREFIX": self.install_prefix,
            "LLVM_BUILD_LLVM_DYLIB": cmake_bool_flag(self.build_llvm_dylib),
            "LLVM_LINK_LLVM_DYLIB": cmake_bool_flag(self.link_llvm_dylib),
        }
        if self.llvm_targets is not None:
            common_flags["LLVM_TARGETS_TO_BUILD"] = f"\"{';'.join(self.llvm_targets)}\""
        return {**common_flags}


def build_cmake_call(cmake_flags, ninja_flags, llvm_src):
    llvm_src_absolute = Path(llvm_src)
    return ["cmake", *ninja_flags, *[f"-D{k}={v}" for k, v in cmake_flags.items()], str(llvm_src_absolute / "llvm")]


class LlvmBuilderArguments:
    def __init__(self, install_path, bootstrap_path, llvm_src, targets, ninja_target, subprojects=None):
        self.subprojects = subprojects
        self.ninja_target = ninja_target
        self.llvm_src = llvm_src
        self.bootstrap_path = bootstrap_path
        self.install_path = install_path
        self.targets = targets


def force_create_directory(parent, name) -> Path:
    build_path = parent / name
    if build_path.exists():
        print("Removing existing build directory")
        shutil.rmtree(build_path)
    os.mkdir(build_path)
    return build_path


def llvm_build_commands(args: LlvmBuilderArguments) -> List[str]:
    def construct_cmake_flags():
        common_flags = CommonCMakeFlags(args.install_path)
        if args.targets is not None:
            common_flags.llvm_targets = args.targets
        if args.subprojects is not None:
            common_flags.subprojects = args.subprojects
        host_flags = HostSpecificCMakeFlags.create()

        return reduce(lambda a, b: {**a, **b}, [
            common_flags.build(),
            host_flags.build(),
            host_flags.cmake_executables(args.bootstrap_path)
        ])

    cmake_flags = construct_cmake_flags()
    ninja_flags = ["-G", "Ninja"]
    cmake_command = ' '.join(build_cmake_call(cmake_flags, ninja_flags, args.llvm_src))
    ninja_command = ' '.join(["ninja", args.ninja_target])
    return [cmake_command, ninja_command]


def clone_llvm_repository(repo, branch):
    if Host.is_darwin():
        default_repo, default_branch = "https://github.com/apple/llvm-project", "apple/stable/20200108"
    else:
        default_repo, default_branch = "https://github.com/llvm/llvm-project", "release/11.x"
    repo = default_repo if repo is None else repo
    branch = default_branch if branch is None else branch
    # Download only single commit because we don't need whole history just for building LLVM.
    subprocess.run(["git", "clone", repo, "--branch", branch, "--depth", "1", "llvm-project"])
    return absolute_path("llvm-project")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Build LLVM toochain for Kotlin/Native")
    parser.add_argument("--llvm-sources", dest="llvm_src", type=str, default=None,
                        help="Location of LLVM sources")
    parser.add_argument("--install-path", type=str, default="dist", required=True,
                        help="Where final LLVM distribution will be installed")
    parser.add_argument("--stage0", type=str, default=None,
                        help="Path to existing toolchain")
    parser.add_argument("--archive-path", default=None,
                        help="Create an archive for final distribution at given path")
    parser.add_argument("--vsdevcmd", type=str, default=None, required=Host.is_windows(),
                        help="(Windows only) Path to VsDevCmd.bat")
    parser.add_argument("--num-stages", type=int, default=2,
                        help="Number of stages in bootstrap.\n"
                             "Default is 2 meaning that we build LLVM and then use it to build itself.")
    parser.add_argument("--repo", type=str, default=None)
    # TODO: Use commit instead
    parser.add_argument("--branch", type=str, default=None)
    return parser


def build_distribution(args):
    environment = Environment.create(args)
    current_dir = Path().absolute()
    num_stages = args.num_stages
    bootstrap_path = args.stage0
    for stage in range(1, num_stages + 1):
        if num_stages > 1 and stage == 1:
            # We only need host target to start a bootstrap.
            targets = [Host.llvm_target()]
        else:
            # None targets means all targets.
            targets = None

        if stage == num_stages:
            install_path = args.install_path
        else:
            install_path = force_create_directory(current_dir, f"llvm-stage-{stage}")

        stage_llvm_args = LlvmBuilderArguments(
            install_path=install_path,
            bootstrap_path=bootstrap_path,
            llvm_src=absolute_path(args.llvm_src),
            targets=targets,
            ninja_target="install",
        )
        build_dir = force_create_directory(current_dir, f"llvm-stage-{stage}-build")
        commands = llvm_build_commands(stage_llvm_args)

        os.chdir(build_dir)
        p = environment.execute_subprocess(commands)
        p.wait()
        os.chdir(current_dir)
        bootstrap_path = install_path

    return args.install_path


def create_archive(args, input_directory, output_path) -> bool:
    return Environment.create(args).create_distribution_archive(input_directory, output_path)


def create_checksum_file(algorithm, input_path, output_path):
    chunk_size = 4096
    if algorithm == "sha256":
        checksum = hashlib.sha256()
        with open(input_path, "rb") as input_contents:
            for chunk in iter(lambda _: input_contents.read(chunk_size), b""):
                checksum.update(chunk)
    else:
        print(f"{algorithm} is not supported for checksum file")
        return False
    print(checksum.hexdigest(), file=open(output_path, "w"))
    return True


def main():
    parser = build_parser()
    args = parser.parse_args()
    if args.llvm_src is None:
        print("Downloading LLVM sources...")
        args.llvm_src = absolute_path(clone_llvm_repository(args.repo, args.branch))
    final_dist = build_distribution(args)
    if args.archive_path is not None:
        if not create_archive(args, final_dist, args.archive_path):
            print("Failed to create distribution archive")
            return False
        if not create_checksum_file('sha256', args.archive_path, f"{args.archive_path}.sha256"):
            print("Failed to create checksum file")
            return False


# TODO:
# 1. Add two-stage bootstrap build
# 3. Add distribution naming
# 5. Compute checksum.
# 6. Clone llvm at specific directory
# 7. Add cleanup
# 8. Resolve logic duplication in program arguments
if __name__ == "__main__":
    if not main():
        sys.exit(1)
