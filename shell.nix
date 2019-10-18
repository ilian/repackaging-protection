with import <nixpkgs> {};

stdenv.mkDerivation {
  name = "android-env";
  buildInputs = [ jdk androidsdk apktool ant gdb python python36Packages.gplaycli bc ];
}
